package cn.scut.raputa.service;

import cn.scut.raputa.config.InferenceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ModelPredictionService {

    private static final long MANUAL_SEGMENT_MODEL_LEAD_MS = 200L;

    private final RestTemplate restTemplate;
    private final InferenceProperties inferenceProperties;

    public ModelPredictionService(InferenceProperties inferenceProperties) {
        this.inferenceProperties = inferenceProperties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(inferenceProperties.getConnectTimeoutMs(), 100));
        factory.setReadTimeout(Math.max(inferenceProperties.getReadTimeoutMs(), 100));
        this.restTemplate = new RestTemplate(factory);
    }

    public PredictionResult uploadAndPredict(File audioFile, File imuFile, File gasFile) {
        return uploadAndPredict("asp", audioFile, imuFile, gasFile, null);
    }

    public PredictionResult uploadAndPredict(
            String taskType,
            File audioFile,
            File imuFile,
            File gasFile) {
        return uploadAndPredict(taskType, audioFile, imuFile, gasFile, null);
    }

    public PredictionResult uploadAndPredict(
            String taskType,
            File audioFile,
            File imuFile,
            File gasFile,
            List<List<Number>> manualSwallowEvents) {
        return uploadAndPredict(taskType, audioFile, imuFile, gasFile, manualSwallowEvents, null);
    }

    public PredictionResult uploadAndPredict(
            String taskType,
            File audioFile,
            File imuFile,
            File gasFile,
            List<List<Number>> manualSwallowEvents,
            Integer predictionWindowSeconds) {
        DetectionTask task = DetectionTask.from(taskType);
        if (manualSwallowEvents != null) {
            return uploadManualSegmentsAndPredict(
                    task,
                    audioFile,
                    imuFile,
                    gasFile,
                    manualSwallowEvents,
                    predictionWindowSeconds);
        }

        return uploadAutomaticAndPredict(task, audioFile, imuFile, gasFile);
    }

    private PredictionResult uploadAutomaticAndPredict(
            DetectionTask task,
            File audioFile,
            File imuFile,
            File gasFile) {
        Path cleanAudioPath = null;
        try {
            String modelApiUrl = buildPredictUrl(task, false);
            cleanAudioPath = Files.createTempFile("raputa_auto_audio_", ".wav");
            WavFileUtils.writeCleanCopy(audioFile.toPath(), cleanAudioPath);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("audio", new FileSystemResource(cleanAudioPath.toFile()));
            body.add("imu", new FileSystemResource(imuFile));
            body.add("gas", new FileSystemResource(gasFile));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            log.info("调用{}自动分割模型API: {}", task.label(), modelApiUrl);
            log.info("上传文件: audio={}, imu={}, gas={}",
                cleanAudioPath.getFileName(), imuFile.getName(), gasFile.getName());

            ResponseEntity<Map<String, Object>> response = exchangeForMap(modelApiUrl, requestEntity);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                log.info("模型预测成功，结果: {}", result);
                return parsePredictionResult(result);
            } else {
                log.error("模型预测失败: status={}", response.getStatusCode());
                return null;
            }

        } catch (RestClientException e) {
            log.error("调用模型API失败", e);
            return null;
        } catch (IOException e) {
            log.error("自动分割音频规范化失败: {}", audioFile.getName(), e);
            return null;
        } finally {
            if (cleanAudioPath != null) {
                try {
                    Files.deleteIfExists(cleanAudioPath);
                } catch (IOException e) {
                    log.warn("删除自动分割临时音频失败: {}", cleanAudioPath, e);
                }
            }
        }
    }

    private PredictionResult uploadManualSegmentsAndPredict(
            DetectionTask task,
            File audioFile,
            File imuFile,
            File gasFile,
            List<List<Number>> manualSwallowEvents,
            Integer predictionWindowSeconds) {
        if (manualSwallowEvents == null || manualSwallowEvents.isEmpty()) {
            PredictionResult empty = new PredictionResult();
            empty.setMessage("未检测到人工吞咽段");
            empty.setPredictionWindowSeconds(predictionWindowSeconds);
            return empty;
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("raputa_manual_predict_");
            String modelApiUrl = buildPredictUrl(task, true);
            PredictionResult aggregate = new PredictionResult();
            aggregate.setSwallowEvents(manualSwallowEvents);
            aggregate.setPredictionWindowSeconds(predictionWindowSeconds);
            List<Map<String, Object>> dysphagiaResults = new ArrayList<>();
            List<Map<String, Object>> aspirationResults = new ArrayList<>();

            for (int i = 0; i < manualSwallowEvents.size(); i++) {
                List<Number> event = manualSwallowEvents.get(i);
                if (event == null || event.size() < 2) {
                    continue;
                }
                long annotatedStartMs = Math.max(0L, Math.round(event.get(0).doubleValue()));
                long annotatedEndMs = Math.max(annotatedStartMs + 1L, Math.round(event.get(1).doubleValue()));
                long appliedLeadMs = Math.min(MANUAL_SEGMENT_MODEL_LEAD_MS, annotatedStartMs);
                long modelStartMs = annotatedStartMs - appliedLeadMs;
                long modelEndMs = Math.max(modelStartMs + 1L, annotatedEndMs - appliedLeadMs);

                Path segmentDir = tempDir.resolve("segment_" + i);
                Files.createDirectories(segmentDir);
                Path segmentAudio = segmentDir.resolve("audio.wav");
                Path segmentImu = segmentDir.resolve("imu.csv");
                Path segmentGas = segmentDir.resolve("gas.csv");
                WavFileUtils.writeSliceAsCleanWav(audioFile.toPath(), segmentAudio, modelStartMs, modelEndMs);
                sliceCsvByRelativeMillis(imuFile.toPath(), segmentImu, modelStartMs, modelEndMs);
                if (task == DetectionTask.DYSPHAGIA) {
                    sliceCsvByRelativeMillis(gasFile.toPath(), segmentGas, modelStartMs, modelEndMs);
                }

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("audio", new FileSystemResource(segmentAudio.toFile()));
                body.add("imu", new FileSystemResource(segmentImu.toFile()));
                if (task == DetectionTask.DYSPHAGIA) {
                    body.add("gas", new FileSystemResource(segmentGas.toFile()));
                }

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                log.info("调用{}人工分割直接分类API: {}, annotated={}ms-{}ms, model={}ms-{}ms",
                        task.label(), modelApiUrl, annotatedStartMs, annotatedEndMs, modelStartMs, modelEndMs);
                ResponseEntity<Map<String, Object>> response = exchangeForMap(modelApiUrl, requestEntity);
                if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                    log.error("人工分割直接分类失败: status={}", response.getStatusCode());
                    return null;
                }

                PredictionResult parsed = parsePredictionResult(response.getBody());
                if (parsed.getMessage() != null && !parsed.getMessage().isBlank()) {
                    aggregate.setMessage(parsed.getMessage());
                }
                if (task == DetectionTask.DYSPHAGIA && parsed.getDysphagia() != null) {
                    dysphagiaResults.addAll(parsed.getDysphagia());
                }
                if (task == DetectionTask.ASPIRATION && parsed.getAspiration() != null) {
                    aspirationResults.addAll(parsed.getAspiration());
                }
            }

            if (!dysphagiaResults.isEmpty()) {
                aggregate.setDysphagia(dysphagiaResults);
            }
            if (!aspirationResults.isEmpty()) {
                aggregate.setAspiration(aspirationResults);
            }
            return aggregate;
        } catch (Exception e) {
            log.error("人工分割直接分类调用失败", e);
            return null;
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    private ResponseEntity<Map<String, Object>> exchangeForMap(
            String modelApiUrl,
            HttpEntity<MultiValueMap<String, Object>> requestEntity) {
        return restTemplate.exchange(
                modelApiUrl,
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
    }

    private String buildPredictUrl(DetectionTask task, boolean direct) {
        String baseUrl = task == DetectionTask.DYSPHAGIA
                ? inferenceProperties.getDysphagiaBaseUrl()
                : inferenceProperties.getAspirationBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("推理服务地址未配置: " + task.label());
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String uploadPath = switch (task) {
            case DYSPHAGIA -> direct
                    ? inferenceProperties.getDysphagiaDirectPredictPath()
                    : inferenceProperties.getDysphagiaAutoPredictPath();
            case ASPIRATION -> direct
                    ? inferenceProperties.getAspirationDirectPredictPath()
                    : inferenceProperties.getAspirationAutoPredictPath();
        };
        if (uploadPath == null || uploadPath.isBlank()) {
            uploadPath = "/upload_predict/";
        }
        String normalizedPath = uploadPath.startsWith("/") ? uploadPath : "/" + uploadPath;
        return normalizedBase + normalizedPath;
    }

    private PredictionResult parsePredictionResult(Map<String, Object> result) {
        PredictionResult predictionResult = new PredictionResult();

        if (result.containsKey("message")) {
            predictionResult.setMessage((String) result.get("message"));
            return predictionResult;
        }

        if (result.containsKey("swallow_events")) {
            @SuppressWarnings("unchecked")
            List<List<Number>> events = (List<List<Number>>) result.get("swallow_events");
            predictionResult.setSwallowEvents(events);
        }

        if (result.containsKey("dysphagia")) {
            predictionResult.setDysphagia(normalizeResultList(result.get("dysphagia")));
        }

        if (result.containsKey("aspiration")) {
            predictionResult.setAspiration(normalizeResultList(result.get("aspiration")));
        }

        return predictionResult;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeResultList(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    normalized.add((Map<String, Object>) map);
                }
            }
            return normalized;
        }
        if (raw instanceof Map<?, ?> map) {
            return List.of((Map<String, Object>) map);
        }
        return List.of();
    }

    private void sliceCsvByRelativeMillis(Path sourcePath, Path targetPath, long startMs, long endMs) throws IOException {
        List<String> lines = Files.readAllLines(sourcePath);
        if (lines.size() <= 1) {
            throw new IOException("CSV 数据不足: " + sourcePath.getFileName());
        }

        String header = lines.get(0);
        Long baseTimestamp = null;
        List<String> selected = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] parts = line.split(",");
            if (parts.length == 0) {
                continue;
            }
            Long timestamp = parseLong(parts[0]);
            if (timestamp == null) {
                continue;
            }
            if (baseTimestamp == null) {
                baseTimestamp = timestamp;
            }
            long relativeMs = timestamp - baseTimestamp;
            if (relativeMs >= startMs && relativeMs <= endMs) {
                selected.add(line);
            }
        }

        if (selected.isEmpty()) {
            throw new IOException("CSV 在人工分割区间内没有数据: "
                    + sourcePath.getFileName() + " " + startMs + "-" + endMs + "ms");
        }

        List<String> output = new ArrayList<>();
        output.add(header);
        output.addAll(selected);
        Files.write(targetPath, output);
    }

    private Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim().replace("\"", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void deleteRecursively(Path root) {
        try {
            if (!Files.exists(root)) {
                return;
            }
            try (var paths = Files.walk(root)) {
                paths.sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.warn("删除临时文件失败: {}", path);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("清理人工分割临时目录失败: {}", root);
        }
    }

    private enum DetectionTask {
        DYSPHAGIA("吞咽障碍筛查"),
        ASPIRATION("误吸");

        private final String label;

        DetectionTask(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private static DetectionTask from(String raw) {
            if (raw == null || raw.isBlank()) {
                return ASPIRATION;
            }
            String normalized = raw.trim().toLowerCase();
            if ("dys".equals(normalized)
                    || "dysphagia".equals(normalized)
                    || normalized.contains("吞咽障碍")) {
                return DYSPHAGIA;
            }
            return ASPIRATION;
        }
    }

    public static class PredictionResult {
        private String message;
        private Integer predictionWindowSeconds;
        private List<List<Number>> swallowEvents;
        private List<Map<String, Object>> dysphagia;
        private List<Map<String, Object>> aspiration;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Integer getPredictionWindowSeconds() {
            return predictionWindowSeconds;
        }

        public void setPredictionWindowSeconds(Integer predictionWindowSeconds) {
            this.predictionWindowSeconds = predictionWindowSeconds;
        }

        public List<List<Number>> getSwallowEvents() {
            return swallowEvents;
        }

        public void setSwallowEvents(List<List<Number>> swallowEvents) {
            this.swallowEvents = swallowEvents;
        }

        public List<Map<String, Object>> getDysphagia() {
            return dysphagia;
        }

        public void setDysphagia(List<Map<String, Object>> dysphagia) {
            this.dysphagia = dysphagia;
        }

        public List<Map<String, Object>> getAspiration() {
            return aspiration;
        }

        public void setAspiration(List<Map<String, Object>> aspiration) {
            this.aspiration = aspiration;
        }

        public boolean hasSwallowEvents() {
            return swallowEvents != null && !swallowEvents.isEmpty();
        }
    }
}
