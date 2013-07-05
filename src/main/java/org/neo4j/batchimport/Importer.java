package org.neo4j.batchimport;

import org.neo4j.batchimport.importer.ChunkerLineData;
import org.neo4j.batchimport.importer.CsvLineData;
import org.neo4j.batchimport.importer.RelType;
import org.neo4j.batchimport.index.MapDbCachingIndexProvider;
import org.neo4j.batchimport.utils.Config;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.index.lucene.unsafe.batchinsert.LuceneBatchInserterIndexProvider;
import org.neo4j.kernel.impl.util.FileUtils;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;
import org.neo4j.unsafe.batchinsert.BatchInserterIndexProvider;
import org.neo4j.unsafe.batchinsert.BatchInserterIndex;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static org.neo4j.index.impl.lucene.LuceneIndexImplementation.EXACT_CONFIG;
import static org.neo4j.index.impl.lucene.LuceneIndexImplementation.FULLTEXT_CONFIG;

public class Importer {
    private static final Map<String, String> SPATIAL_CONFIG = Collections.singletonMap(IndexManager.PROVIDER,"spatial");
    private static Report report;
    private final Config config;
    private Id id;
    private BatchInserter db;
    private BatchInserterIndexProvider indexProvider;
    Map<String,BatchInserterIndex> indexes=new HashMap<String, BatchInserterIndex>();

    public Importer(File graphDb, final Config config, Id id) {
        this.config = config;
        this.id = id;
        db = createBatchInserter(graphDb, config);

        final boolean luceneOnlyIndex = config.isCachedIndexDisabled();
        indexProvider = createIndexProvider(luceneOnlyIndex);
        Collection<IndexInfo> indexInfos = config.getIndexInfos();
        if (indexInfos!=null) {
            for (IndexInfo indexInfo : indexInfos) {
                BatchInserterIndex index = indexInfo.isNodeIndex() ? nodeIndexFor(indexInfo.indexName, indexInfo.indexType) : relationshipIndexFor(indexInfo.indexName, indexInfo.indexType);
                indexes.put(indexInfo.indexName, index);
            }
        }
        id.addIndexes(indexes);

        report = createReport();
    }

    protected StdOutReport createReport() {
        return new StdOutReport(10 * 1000 * 1000, 100);
    }

    protected BatchInserterIndexProvider createIndexProvider(boolean luceneOnlyIndex) {
        return luceneOnlyIndex ? new LuceneBatchInserterIndexProvider(db) : new MapDbCachingIndexProvider(db);
    }

    protected BatchInserter createBatchInserter(File graphDb, Config config) {
        return BatchInserters.inserter(graphDb.getAbsolutePath(), config.getConfigData());
    }

    // todo multiple nodes and rels files
    // todo nodes and rels-files in config
    // todo graphdb in config
    public static void main(String[] args) throws IOException {
        System.err.println("Usage java -jar batchimport.jar data/dir nodes.csv relationships.csv [node_index node-index-name fulltext|exact nodes_index.csv rel_index rel-index-name fulltext|exact rels_index.csv ....]");

        final Config config = Config.convertArgumentsToConfig(args);

        File graphDb = new File(config.getGraphDbDirectory());
        if (graphDb.exists() && !config.keepDatabase()) {
            FileUtils.deleteRecursively(graphDb);
        }

        Importer importer = new Importer(graphDb, config, new DefaultId());
        importer.doImport();
    }

    void finish() {
        indexProvider.shutdown();
        db.shutdown();
        report.finish();
    }

    void importNodes(Reader reader) throws IOException {
        final LineData data = createLineData(reader, 0);
        report.reset();
        while (data.processLine(null)) {
            final long id = db.createNode(data.getProperties());
            for (Map.Entry<String, Map<String, Object>> entry : data.getIndexData().entrySet()) {
                final BatchInserterIndex index = indexFor(entry.getKey());
                if (index==null)
                    throw new IllegalStateException("Index "+entry.getKey()+" not configured.");
                index.add(id, entry.getValue());
            }
            report.dots();
        }
        report.finishImport("Nodes");
    }

    private BatchInserterIndex indexFor(String index) {
        return indexes.get(index);
    }

    void importRelationships(Reader reader) throws IOException {
        final int offset = 3;
        final LineData data = createLineData(reader, offset);
        final RelType relType = new RelType();
        report.reset();

        while (data.processLine(null)) {
            final Map<String, Object> properties = data.getProperties();
            final long start = id.get(data, 0);
            final long end = id.get(data,1);
            final RelType type = relType.update(data.getTypeLabels()[0]);
            final long id = db.createRelationship(start, end, type, properties);
            for (Map.Entry<String, Map<String, Object>> entry : data.getIndexData().entrySet()) {
                indexFor(entry.getKey()).add(id, entry.getValue());
            }
            report.dots();
        }
        report.finishImport("Relationships");
    }

    private LineData createLineData(Reader reader, int offset) {
        final boolean useQuotes = config.quotesEnabled();
        if (useQuotes) return new CsvLineData(reader, config.getDelimChar(this),offset);
        return new ChunkerLineData(reader, config.getDelimChar(this), offset);
    }


    void importIndex(String indexName, BatchInserterIndex index, Reader reader) throws IOException {
        final LineData data = createLineData(reader, 1);
        report.reset();
        while (data.processLine(null)) {
            final Map<String, Object> properties = data.getProperties();
            index.add(id(data.getValue(0)), properties);
            report.dots();
        }
                
        report.finishImport("Done inserting into " + indexName + " Index");
    }

    private BatchInserterIndex nodeIndexFor(String indexName, String indexType) {
        return indexProvider.nodeIndex(indexName, configFor(indexType));
    }

    private BatchInserterIndex relationshipIndexFor(String indexName, String indexType) {
        return indexProvider.relationshipIndex(indexName, configFor(indexType));
    }

    private Map<String, String> configFor(String indexType) {
        if (indexType.equalsIgnoreCase("fulltext")) return FULLTEXT_CONFIG;
        if (indexType.equalsIgnoreCase("spatial")) return SPATIAL_CONFIG;
        return EXACT_CONFIG;
    }

    private long id(Object id) {
        return Long.parseLong(id.toString());
    }

    private void importIndex(IndexInfo indexInfo) throws IOException {
        File indexFile = new File(indexInfo.indexFileName);
        if (!indexFile.exists()) {
            System.err.println("Index file "+indexFile+" does not exist");
            return;
        }
        importIndex(indexInfo.indexName, indexes.get(indexInfo.indexName), createFileReader(indexFile));
    }

    private void doImport() throws IOException {
        try {
            for (File file : config.getNodesFiles()) {
                importNodes(createFileReader(file));
            }

            for (File file : config.getRelsFiles()) {
                importRelationships(createFileReader(file));
            }

            for (IndexInfo indexInfo : config.getIndexInfos()) {
                if (indexInfo.shouldImportFile()) importIndex(indexInfo);
            }
		} finally {
            finish();
        }
    }

    final static int BUFFERED_READER_BUFFER = 4096*512;

    private Reader createFileReader(File file) {
        try {
            final String fileName = file.getName();
            if (fileName.endsWith(".gz") || fileName.endsWith(".zip")) {
                return new InputStreamReader(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file)),BUFFERED_READER_BUFFER));
            }
            final FileReader fileReader = new FileReader(file);
            return new BufferedReader(fileReader,BUFFERED_READER_BUFFER);
        } catch(Exception e) {
            throw new IllegalArgumentException("Error reading file "+file+" "+e.getMessage(),e);
        }
    }

}