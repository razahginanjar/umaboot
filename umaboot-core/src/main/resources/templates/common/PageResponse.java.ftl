package ${basePackage}.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

/**
 * Stable JSON response shape for paginated results.
 *
 * <p>Used in place of Spring Data's {@link Page} on the wire so that downstream
 * consumers don't depend on Spring Data internals (sortable fields, pageable
 * descriptor, etc.) which can change across major Spring Boot versions.</p>
 *
 * <pre>{@code
 * {
 *   "content":       [...items...],
 *   "page":          0,
 *   "size":          20,
 *   "totalElements": 137,
 *   "totalPages":    7,
 *   "last":          false
 * }
 * }</pre>
 */
public final class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean last;

    private PageResponse(List<T> content, int page, int size, long totalElements,
                         int totalPages, boolean last) {
        this.content = Objects.requireNonNull(content, "content");
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.last = last;
    }

    /** Wraps a Spring Data {@link Page} (used by JPA / jOOQ / MyBatis pageable services). */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    /** Manual constructor used by Hexagonal / DDD services that return raw lists. */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean last = totalElements == 0 || (long) (page + 1) * size >= totalElements;
        return new PageResponse<>(content, page, size, totalElements, totalPages, last);
    }

    public List<T> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public boolean isLast() { return last; }
}
