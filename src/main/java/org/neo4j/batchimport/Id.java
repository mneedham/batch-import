package org.neo4j.batchimport;

import org.neo4j.unsafe.batchinsert.BatchInserterIndex;

import java.util.Map;

public interface Id {
    long get(LineData data, int column);
    void addIndexes(Map<String, BatchInserterIndex> indexes);
}

