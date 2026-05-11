package cn.scut.raputa.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class SessionPathResolver {

    private static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public String buildSessionKey(String mode, String patientId, String patientName) {
        String safeMode = sanitizeSegment(mode == null ? "session" : mode.toLowerCase());
        String safePatientId = sanitizeSegment(patientId);
        String safePatientName = sanitizeSegment(patientName);
        String ts = LocalDateTime.now(ZONE_CN).format(TS_FMT);
        return safeMode + "_" + safePatientId + "_" + safePatientName + "_" + ts;
    }

    public String sanitizeSegment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim();
        normalized = normalized.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        normalized = normalized.replaceAll("\\s+", "_");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
