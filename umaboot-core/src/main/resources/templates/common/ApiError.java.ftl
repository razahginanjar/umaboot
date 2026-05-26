package ${apiErrorPackage};

import java.time.Instant;
<#if springBoot2>
import java.util.Objects;
</#if>

/**
 * Stable error envelope returned from the global exception handler when
 * {@code exception.style: envelope} is configured.
 *
 * <p>JSON shape on the wire:</p>
 * <pre>{@code
 * {
 *   "code": "NOT_FOUND",
 *   "message": "Customer 42 not found",
 *   "path": "/api/customers/42",
 *   "timestamp": "2026-01-15T10:30:00Z"
 * }
 * }</pre>
 */
<#if springBoot3>
public record ApiError(String code, String message, String path, Instant timestamp) {

    /** Convenience factory that timestamps the error at construction time. */
    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, path, Instant.now());
    }
}
<#else>
public final class ApiError {

    private final String code;
    private final String message;
    private final String path;
    private final Instant timestamp;

    public ApiError(String code, String message, String path, Instant timestamp) {
        this.code = code;
        this.message = message;
        this.path = path;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    /** Convenience factory that timestamps the error at construction time. */
    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, path, Instant.now());
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public Instant getTimestamp() { return timestamp; }
}
</#if>
