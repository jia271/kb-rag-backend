package com.example.rag.retrieval;

import com.example.rag.config.EmbeddingProperties;
import com.example.rag.config.SearchProperties;
import com.example.rag.document.domain.DocumentChunk;
import com.example.rag.embedding.EmbeddingClient;
import com.example.rag.embedding.EmbeddingRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lucene 索引的读写入口。
 *
 * <p>索引目录按「向量化提供方-维度」分子目录：换向量模型时维度通常会变，
 * 新旧向量混在同一个索引里会直接报错，分开存放最省事，切换后重建一次索引即可。
 *
 * <p>中文分词用的是 Lucene 自带的 CJK 二元切分：不需要额外依赖，
 * 效果比标准分词器的逐字切分好得多。生产环境通常会换成带词典的分词器（IK、jieba）。
 */
@Slf4j
@Component
public class ChunkIndexService {

    private static final String F_CHUNK_ID = "chunkId";
    private static final String F_DOCUMENT_ID = "documentId";
    private static final String F_FILE_NAME = "fileName";
    private static final String F_ORDER_INDEX = "orderIndex";
    private static final String F_PAGE_NUMBER = "pageNumber";
    private static final String F_HEADING = "heading";
    private static final String F_CONTENT = "content";
    private static final String F_VECTOR = "vector";

    private final SearchProperties searchProperties;
    private final EmbeddingProperties embeddingProperties;
    private final EmbeddingRegistry embeddingRegistry;
    private final Analyzer analyzer = new CJKAnalyzer();

    private Directory directory;
    private IndexWriter writer;
    private SearcherManager searcherManager;
    private String indexDescription;

    public ChunkIndexService(SearchProperties searchProperties,
                             EmbeddingProperties embeddingProperties,
                             EmbeddingRegistry embeddingRegistry) {
        this.searchProperties = searchProperties;
        this.embeddingProperties = embeddingProperties;
        this.embeddingRegistry = embeddingRegistry;
    }

    @PostConstruct
    void open() throws IOException {
        EmbeddingClient client = embeddingRegistry.defaultClient();
        Path indexDir = Path.of(searchProperties.getIndexDir()).toAbsolutePath().normalize()
                .resolve(client.providerName() + "-" + client.dimensions());
        Files.createDirectories(indexDir);

        this.directory = FSDirectory.open(indexDir);
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.writer = new IndexWriter(directory, config);
        this.searcherManager = new SearcherManager(writer, new SearcherFactory());
        this.indexDescription = client.providerName() + "-" + client.dimensions();
        log.info("检索索引已打开：{}（{} 维），当前已索引 {} 个块",
                indexDir, client.dimensions(), indexedCount());
    }

    @PreDestroy
    void close() throws IOException {
        if (searcherManager != null) {
            searcherManager.close();
        }
        if (writer != null) {
            writer.close();
        }
        if (directory != null) {
            directory.close();
        }
    }

    public String describe() {
        return indexDescription;
    }

    public int indexedCount() {
        return writer == null ? 0 : writer.getDocStats().numDocs;
    }

    /** 重建某份文档的所有块；先按文档删除，避免残留上一版切分出来的旧块 */
    public int indexDocument(Long documentId, String fileName, List<DocumentChunk> chunks) throws IOException {
        if (chunks.isEmpty()) {
            deleteDocument(documentId);
            return 0;
        }
        EmbeddingClient client = embeddingRegistry.defaultClient();
        List<float[]> vectors = embedAll(client, chunks);

        writer.deleteDocuments(new Term(F_DOCUMENT_ID, String.valueOf(documentId)));
        for (int index = 0; index < chunks.size(); index++) {
            writer.addDocument(toLuceneDocument(fileName, chunks.get(index), vectors.get(index)));
        }
        writer.commit();
        searcherManager.maybeRefresh();
        return chunks.size();
    }

    public void deleteDocument(Long documentId) throws IOException {
        writer.deleteDocuments(new Term(F_DOCUMENT_ID, String.valueOf(documentId)));
        writer.commit();
        searcherManager.maybeRefresh();
    }

    /** 清空整个索引，全量重建前调用 */
    public void deleteAll() throws IOException {
        writer.deleteAll();
        writer.commit();
        searcherManager.maybeRefresh();
    }

    public List<SearchHit> searchKeyword(String keyword, int topK) throws IOException {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            TopDocs topDocs = searcher.search(toKeywordQuery(keyword), topK);
            return toHits(searcher, topDocs, true);
        } finally {
            searcherManager.release(searcher);
        }
    }

    public List<SearchHit> searchVector(float[] vector, int topK) throws IOException {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            TopDocs topDocs = searcher.search(new KnnFloatVectorQuery(F_VECTOR, vector, topK), topK);
            return toHits(searcher, topDocs, false);
        } finally {
            searcherManager.release(searcher);
        }
    }

    public boolean isEmpty() throws IOException {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            return searcher.count(new MatchAllDocsQuery()) == 0;
        } finally {
            searcherManager.release(searcher);
        }
    }

    private Query toKeywordQuery(String keyword) {
        try {
            // 转义用户输入，避免 ":"、"(" 这类字符把查询语法弄坏
            return new QueryParser(F_CONTENT, analyzer).parse(QueryParser.escape(keyword));
        } catch (ParseException error) {
            throw new IllegalArgumentException("检索关键词无法解析：" + keyword, error);
        }
    }

    private List<float[]> embedAll(EmbeddingClient client, List<DocumentChunk> chunks) {
        int batchSize = Math.max(1, embeddingProperties.getBatchSize());
        List<float[]> vectors = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(chunks.size(), start + batchSize);
            List<String> texts = new ArrayList<>(end - start);
            for (int i = start; i < end; i++) {
                texts.add(chunks.get(i).getContent());
            }
            vectors.addAll(client.embed(texts));
        }
        return vectors;
    }

    private Document toLuceneDocument(String fileName, DocumentChunk chunk, float[] vector) {
        Document document = new Document();
        document.add(new StringField(F_CHUNK_ID, String.valueOf(chunk.getId()), Field.Store.YES));
        document.add(new StringField(F_DOCUMENT_ID, String.valueOf(chunk.getDocumentId()), Field.Store.YES));
        document.add(new StoredField(F_FILE_NAME, fileName));
        document.add(new StoredField(F_ORDER_INDEX, chunk.getOrderIndex()));
        if (chunk.getPageNumber() != null) {
            document.add(new StoredField(F_PAGE_NUMBER, chunk.getPageNumber()));
        }
        if (chunk.getHeading() != null) {
            document.add(new StoredField(F_HEADING, chunk.getHeading()));
        }
        document.add(new TextField(F_CONTENT, chunk.getContent(), Field.Store.YES));
        document.add(new KnnFloatVectorField(F_VECTOR, vector, VectorSimilarityFunction.COSINE));
        return document;
    }

    private List<SearchHit> toHits(IndexSearcher searcher, TopDocs topDocs, boolean fromKeyword) throws IOException {
        List<SearchHit> hits = new ArrayList<>(topDocs.scoreDocs.length);
        int rank = 1;
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document document = searcher.storedFields().document(scoreDoc.doc);
            String pageNumber = document.get(F_PAGE_NUMBER);
            hits.add(new SearchHit(
                    Long.parseLong(document.get(F_CHUNK_ID)),
                    Long.parseLong(document.get(F_DOCUMENT_ID)),
                    document.get(F_FILE_NAME),
                    Integer.parseInt(document.get(F_ORDER_INDEX)),
                    pageNumber == null ? null : Integer.valueOf(pageNumber),
                    document.get(F_HEADING),
                    document.get(F_CONTENT),
                    fromKeyword ? rank : null,
                    fromKeyword ? null : rank,
                    scoreDoc.score));
            rank++;
        }
        return hits;
    }

}
