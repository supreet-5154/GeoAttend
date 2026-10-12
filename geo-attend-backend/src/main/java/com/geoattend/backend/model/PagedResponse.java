package com.geoattend.backend.model;

import java.util.Collections;
import java.util.List;

public class PagedResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public PagedResponse() {
        this.content = Collections.emptyList();
    }

    public PagedResponse(List<T> content, int page, int size, long totalElements) {
        this.content = content != null ? content : Collections.emptyList();
        this.page = Math.max(0, page);
        this.size = Math.max(1, size);
        this.totalElements = Math.max(0L, totalElements);
        this.totalPages = this.size > 0 ? (int) Math.ceil((double) this.totalElements / this.size) : 0;
        this.first = this.page == 0;
        this.last = this.totalPages == 0 || this.page >= this.totalPages - 1;
    }

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isFirst() {
        return first;
    }

    public void setFirst(boolean first) {
        this.first = first;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }
}
