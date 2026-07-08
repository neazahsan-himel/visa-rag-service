package com.neaz.travel.visa.common.model;

import java.util.List;

/**
 * Generic wrapper for a single page of results returned by list endpoints.
 *
 * @param content       the items on this page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total number of elements across all pages
 * @param totalPages    total number of pages
 * @param <T>           the item type
 */
public record PagedResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PagedResult {
        content = List.copyOf(content);
    }

    public static <T> PagedResult<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PagedResult<>(content, page, size, totalElements, totalPages);
    }
}