package org.neo4j.batchimport;

import org.neo4j.unsafe.batchinsert.BatchInserterIndex;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

public class CustomId implements Id {
    private Map<String,BatchInserterIndex> indexes=new HashMap<String, BatchInserterIndex>();
    private Map<Object, Long> mappings = new HashMap<Object, Long>();

    public long get(LineData data, int column) {
        final LineData.Header header = data.getHeader()[column];
        final Object value = data.getValue(column);
        if (header.indexName == null) {
            return mappings.get(value);
        }
        return lookup(header.indexName, header.name, value);
    }

    public void addIndexes(Map<String, BatchInserterIndex> indexes) {
        this.indexes.putAll(indexes);
    }

    public void addMapping(Object id, long nodeId) {
        mappings.put(id, nodeId);
    }

    private long id(Object id) {
        return Long.parseLong(id.toString());
    }

    private long lookup(String index,String property,Object value) {
        return indexFor(index).get(property, value).getSingle();
    }

    private BatchInserterIndex indexFor(String index) {
        return indexes.get(index);
    }
}
