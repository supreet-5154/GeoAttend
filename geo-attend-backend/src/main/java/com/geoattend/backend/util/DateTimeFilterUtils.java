package com.geoattend.backend.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DateTimeFilterUtils {

    private DateTimeFilterUtils() {
    }

    public static LocalDateTime parseStartBoundary(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String clean = raw.trim();

        // Check if date-only format YYYY-MM-DD
        if (clean.length() == 10 && clean.charAt(4) == '-' && clean.charAt(7) == '-') {
            try {
                LocalDate date = LocalDate.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE);
                return date.atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }

        // Try standard LocalDateTime
        try {
            return LocalDateTime.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }

        // Try OffsetDateTime / ISO with Z or timezone offset
        try {
            return OffsetDateTime.parse(clean, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        // Try ZonedDateTime
        try {
            return ZonedDateTime.parse(clean, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        return null;
    }

    public static LocalDateTime parseEndBoundary(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String clean = raw.trim();

        // Check if date-only format YYYY-MM-DD -> inclusive of entire end day
        if (clean.length() == 10 && clean.charAt(4) == '-' && clean.charAt(7) == '-') {
            try {
                LocalDate date = LocalDate.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE);
                return date.atTime(LocalTime.MAX);
            } catch (DateTimeParseException ignored) {
            }
        }

        // Try standard LocalDateTime
        try {
            return LocalDateTime.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }

        // Try OffsetDateTime
        try {
            return OffsetDateTime.parse(clean, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        // Try ZonedDateTime
        try {
            return ZonedDateTime.parse(clean, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        return null;
    }
}
