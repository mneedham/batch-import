package org.neo4j.batchimport;

import org.neo4j.unsafe.batchinsert.BatchInserterIndex;

import java.util.HashMap;
import java.util.Map;

public class DefaultId implements Id {
    Map<String,BatchInserterIndex> indexes=new HashMap<String, BatchInserterIndex>();

    @Override
    public void addIndexes(Map<String, BatchInserterIndex> indexes) {
        this.indexes.putAll(indexes);
    }

    @Override
    public long get(LineData data, int column) {
        final LineData.Header header = data.getHeader()[column];
        final Object value = data.getValue(column);
        if (header.indexName == null) {
            return id(value);
        }
        return lookup(header.indexName, header.name, value);
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
