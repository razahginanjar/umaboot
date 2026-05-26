package ${basePackage}.common;

import java.util.List;
<#if springBoot2>
import java.util.Objects;
</#if>

/**
 * Cursor-paginated response. Stable JSON shape:
 *
 * <pre>{@code
 * {
 *   "content":    [...],
 *   "nextCursor": "BASE64==",   // null when no more pages
 *   "limit":      20,
 *   "hasMore":    true
 * }
 * }</pre>
 *
 * <p>Cursor pagination is forward-only over the entity's simple primary key
 * (sorted ascending). Clients pass {@code nextCursor} from the previous response
 * back in as the {@code cursor} query parameter on the next call.</p>
 *
 * @param <T> response element type
 */
<#if springBoot3>
public record CursorPage<T>(List<T> content, String nextCursor, int limit, boolean hasMore) {

    public static <T> CursorPage<T> of(List<T> content, String nextCursor, int limit) {
        return new CursorPage<>(content, nextCursor, limit, nextCursor != null);
    }
}
<#else>
public final class CursorPage<T> {

    private final List<T> content;
    private final String nextCursor;
    private final int limit;
    private final boolean hasMore;

    public CursorPage(List<T> content, String nextCursor, int limit, boolean hasMore) {
        this.content = Objects.requireNonNull(content, "content");
        this.nextCursor = nextCursor;
        this.limit = limit;
        this.hasMore = hasMore;
    }

    public static <T> CursorPage<T> of(List<T> content, String nextCursor, int limit) {
        return new CursorPage<>(content, nextCursor, limit, nextCursor != null);
    }

    public List<T> getContent() { return content; }
    public String getNextCursor() { return nextCursor; }
    public int getLimit() { return limit; }
    public boolean isHasMore() { return hasMore; }
}
</#if>
