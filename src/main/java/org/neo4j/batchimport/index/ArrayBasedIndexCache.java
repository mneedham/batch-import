package org.neo4j.batchimport.index;

import java.util.Arrays;

/**
 * @author mh
 * @since 20.05.14
 */
public class ArrayBasedIndexCache implements IndexCache {
    private Object[] data;
    private int limit =0;
    private String index;
    private int capacity;
//    private long idOffset;

    public ArrayBasedIndexCache(String index,int capacity) {
        this.index = index;
        this.capacity = capacity;
//        this.idOffset = idOffset;
        data = new Object[capacity];
    }

    @Override
    public void add(Object value) {
        if ( limit ==capacity) {
            capacity *= 2;
            data = Arrays.copyOf(data, capacity);
        }
        data[limit++]=value;
    }

    @Override
    public void doneInsert() {
        data = Arrays.copyOf(data, limit ); // todo only when capacity >>> limit
        Arrays.sort(data,0, limit );
    }

    @Override
    public int get(Object value) {
        int idx = Arrays.binarySearch(data, 0, limit, value.toString().trim());
        // 22fensome@gmail.com
        // ﻿22fensome@gmail.com


        return (idx < 0 ? -1 : idx);
    }

    @Override
    public void set(Object value, long id) {
        if (id > Integer.MAX_VALUE) throw new ArrayIndexOutOfBoundsException("Don't support values > " + Integer.MAX_VALUE);
        if (id >= capacity) {
            capacity = id > capacity * 2 ? (int) (id * 1.1) : capacity * 2;
            data = Arrays.copyOf(data, capacity);
        }
        data[(int)id]=value;
    }
}
