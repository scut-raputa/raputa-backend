package cn.scut.raputa.service;

import cn.scut.raputa.config.InferenceProperties;
import cn.scut.raputa.vo.InferenceHealthVO;
import cn.scut.raputa.vo.RuntimeModelVO;
import cn.scut.raputa.vo.RuntimeSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class InferenceRuntimeService {

    private final InferenceProperties inferenceProperties;
    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${raputa.model.runtime-cache-ttl-seconds:10}")
    private long runtimeCacheTtlSeconds;

    private final Object cacheLock = new Object();
    private volatile CacheSnapshot cacheSnapshot = CacheSnapshot.empty();

    public InferenceHealthVO getHealth() {
        return getSnapshot(false).health;
    }

    public List<RuntimeModelVO> getRuntimeModels() {
        return getSnapshot(false).models;
    }

    public RuntimeSummaryVO getRuntimeSummary() {
        CacheSnapshot snapshot = getSnapshot(false);
        long discovered = snapshot.models.size();
        long loadedCount = snapshot.models.stream().filter(RuntimeModelVO::isLoaded).count();
        long availableCount = snapshot.models.stream()
                .filter(model -> model.isLoaded() && model.isServiceLive() && model.isServiceReady())
                .count();
        long unavailableCount = Math.max(discovered - availableCount, 0);

        return RuntimeSummaryVO.builder()
                .serviceLive(snapshot.health.isServiceLive())
                .serviceReady(snapshot.health.isServiceReady())
                .discoveredModelCount(discovered)
                .loadedModelCount(loadedCount)
                .availableModelCount(availableCount)
                .unavailableModelCount(unavailableCount)
                .lastHealthCheckAt(snapshot.health.getLastHealthCheckAt())
                .lastHealthError(snapshot.health.getLastHealthError())
                .build();
    }

    private CacheSnapshot getSnapshot(boolean forceRefresh) {
        CacheSnapshot current = cacheSnapshot;
        if (!forceRefresh && !current.isExpired(runtimeCacheTtlSeconds)) {
            return current;
        }

        synchronized (cacheLock) {
            current = cacheSnapshot;
            if (!forceRefresh && !current.isExpired(runtimeCacheTtlSeconds)) {
                return current;
            }

            CacheSnapshot refreshed = fetchSnapshot();
            cacheSnapshot = refreshed;
            return refreshed;
        }
    }

    private CacheSnapshot fetchSnapshot() {
        String checkedAt = OffsetDateTime.now().toString();
        String errorMessage = null;
        boolean serviceLive = false;
        boolean serviceReady = false;
        String serviceName = "inference-service";

        try {
            Map<String, Object> liveBody = getBodyAsMap(inferenceProperties.getLivePath());
            serviceLive = asBoolean(liveBody.get("live"));
            serviceName = asString(liveBody.get("serviceName"), serviceName);
        } catch (Exception ex) {
            errorMessage = shortMessage("live", ex);
            log.warn("Inference live check failed: {}", ex.getMessage());
        }

        if (serviceLive) {
            try {
                Map<String, Object> readyBody = getBodyAsMap(inferenceProperties.getReadyPath());
                serviceReady = asBoolean(readyBody.get("ready"));
                serviceName = asString(readyBody.get("serviceName"), serviceName);
                if (readyBody.get("lastError") != null) {
                    String readyError = String.valueOf(readyBody.get("lastError"));
                    if (!readyError.isBlank() && !"null".equalsIgnoreCase(readyError)) {
                        errorMessage = readyError;
                    }
                }
            } catch (Exception ex) {
                serviceReady = false;
                errorMessage = shortMessage("ready", ex);
                log.warn("Inference ready check failed: {}", ex.getMessage());
            }
        }

        List<RuntimeModelVO> runtimeModels = Collections.emptyList();
        if (serviceLive) {
            try {
                Map<String, Object> modelsBody = getBodyAsMap(inferenceProperties.getModelsPath());
                runtimeModels = parseModels(modelsBody, serviceLive, serviceReady, checkedAt, errorMessage);
                serviceName = asString(modelsBody.get("serviceName"), serviceName);
            } catch (Exception ex) {
                errorMessage = shortMessage("models", ex);
                runtimeModels = Collections.emptyList();
                log.warn("Inference model list check failed: {}", ex.getMessage());
            }
        }

        InferenceHealthVO health = InferenceHealthVO.builder()
                .serviceName(serviceName)
                .inferenceBaseUrl(inferenceProperties.getBaseUrl())
                .serviceLive(serviceLive)
                .serviceReady(serviceReady)
                .lastHealthCheckAt(checkedAt)
                .lastHealthError(errorMessage)
                .build();

        return new CacheSnapshot(health, runtimeModels, Instant.now());
    }

    private Map<String, Object> getBodyAsMap(String path) {
        RestTemplate restTemplate = restTemplateBuilder
            .connectTimeout(Duration.ofMillis(inferenceProperties.getConnectTimeoutMs()))
            .readTimeout(Duration.ofMillis(inferenceProperties.getReadTimeoutMs()))
                .build();

        String url = buildUrl(path);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("unexpected status: " + response.getStatusCode().value());
        }
        if (response.getBody() == null) {
            throw new IllegalStateException("empty response body");
        }
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<RuntimeModelVO> parseModels(
            Map<String, Object> modelsBody,
            boolean serviceLive,
            boolean serviceReady,
            String checkedAt,
            String lastError
    ) {
        Object itemsObj = modelsBody.get("items");
        if (!(itemsObj instanceof List<?> rawItems)) {
            return Collections.emptyList();
        }

        List<RuntimeModelVO> items = new ArrayList<>();
        for (Object rawItem : rawItems) {
            if (!(rawItem instanceof Map<?, ?> modelMapRaw)) {
                continue;
            }
            Map<String, Object> modelMap = (Map<String, Object>) modelMapRaw;
            String modelError = asString(modelMap.get("error"), null);
            String mergedError = modelError == null || modelError.isBlank() ? lastError : modelError;

            items.add(RuntimeModelVO.builder()
                    .name(asString(modelMap.get("name"), ""))
                    .taskType(asString(modelMap.get("taskType"), ""))
                    .modelVersion(asString(modelMap.get("version"), "unknown"))
                    .deployPath(asString(modelMap.get("modelPath"), ""))
                    .loaded(asBoolean(modelMap.get("loaded")))
                    .serviceLive(serviceLive)
                    .serviceReady(serviceReady)
                    .device(asString(modelMap.get("device"), ""))
                    .serviceName(asString(modelMap.get("serviceName"), "inference-service"))
                    .lastHealthCheckAt(checkedAt)
                    .lastHealthError(mergedError)
                    .build());
        }

        return items;
    }

    private String buildUrl(String path) {
        String base = inferenceProperties.getBaseUrl() == null ? "" : inferenceProperties.getBaseUrl().trim();
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedPath = (path == null || path.isBlank()) ? "" : (path.startsWith("/") ? path : "/" + path);
        return normalizedBase + normalizedPath;
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String asString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String str = String.valueOf(value);
        return str.isBlank() ? defaultValue : str;
    }

    private String shortMessage(String stage, Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return stage + " check failed: " + message;
    }

    private record CacheSnapshot(InferenceHealthVO health, List<RuntimeModelVO> models, Instant loadedAt) {
        private static CacheSnapshot empty() {
            InferenceHealthVO emptyHealth = InferenceHealthVO.builder()
                    .serviceName("inference-service")
                    .inferenceBaseUrl("")
                    .serviceLive(false)
                    .serviceReady(false)
                    .lastHealthCheckAt(OffsetDateTime.now().toString())
                    .lastHealthError("not checked yet")
                    .build();
            return new CacheSnapshot(emptyHealth, Collections.emptyList(), Instant.EPOCH);
        }

        private boolean isExpired(long ttlSeconds) {
            long ttl = Math.max(ttlSeconds, 1);
            return loadedAt.plusSeconds(ttl).isBefore(Instant.now());
        }
    }
}