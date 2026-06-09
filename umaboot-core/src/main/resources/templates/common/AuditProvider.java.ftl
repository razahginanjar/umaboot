package ${basePackage}.common;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;

@Component
public class AuditProvider {

    public Instant nowInstant() {
        return Instant.now();
    }

    public LocalDateTime nowLocalDateTime() {
        return LocalDateTime.now();
    }

    public OffsetDateTime nowOffsetDateTime() {
        return OffsetDateTime.now();
    }

    public LocalDate today() {
        return LocalDate.now();
    }

    public LocalTime nowLocalTime() {
        return LocalTime.now();
    }

    public OffsetTime nowOffsetTime() {
        return OffsetTime.now();
    }

    public String currentUser() {
        return "system";
    }
}
