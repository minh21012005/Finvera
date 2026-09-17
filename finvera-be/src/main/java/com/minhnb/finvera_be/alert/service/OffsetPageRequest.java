package com.minhnb.finvera_be.alert.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Pageable dùng offset tuyệt đối để public API không bỏ hoặc lặp bản ghi. */
final class OffsetPageRequest implements Pageable {
    private final long offset;
    private final int limit;
    private final Sort sort;

    private OffsetPageRequest(long offset, int limit, Sort sort) {
        if (offset < 0 || limit < 1) {
            throw new IllegalArgumentException("Pagination values are invalid");
        }
        this.offset = offset;
        this.limit = limit;
        this.sort = sort;
    }

    static OffsetPageRequest of(long offset, int limit) {
        return new OffsetPageRequest(offset, limit, Sort.unsorted());
    }

    @Override public int getPageNumber() { return Math.toIntExact(offset / limit); }
    @Override public int getPageSize() { return limit; }
    @Override public long getOffset() { return offset; }
    @Override public Sort getSort() { return sort; }
    @Override public Pageable next() { return new OffsetPageRequest(offset + limit, limit, sort); }
    @Override public Pageable previousOrFirst() { return hasPrevious() ? new OffsetPageRequest(Math.max(0, offset - limit), limit, sort) : first(); }
    @Override public Pageable first() { return new OffsetPageRequest(0, limit, sort); }
    @Override public Pageable withPage(int pageNumber) { return new OffsetPageRequest((long) pageNumber * limit, limit, sort); }
    @Override public boolean hasPrevious() { return offset > 0; }
}
