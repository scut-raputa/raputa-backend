package cn.scut.raputa.service;

import cn.scut.raputa.config.InferenceProperties;
import cn.scut.raputa.entity.CaptureSession;
import cn.scut.raputa.exception.BizException;
import cn.scut.raputa.repository.CaptureSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileDetectService {

    private final CaptureSessionRepository captureSessionRepository;
    private final ModelPredictionService modelPredictionService;
    private final InferenceProperties inferenceProperties;
    private final FileStorageService fileStorageService;
    private final SessionPathResolver sessionPathResolver;

    public Map<String, Object> detect(
            MultipartFile audio,
            MultipartFile imu,
            MultipartFile gas,
            String patientId,
            String patientName) {

        validateFile("audio", audio, "wav");
        validateFile("imu", imu, "csv");
        validateFile("gas", gas, "csv");

        String normalizedPatientId = normalize(patientId);
        String normalizedPatientName = normalize(patientName);

        CaptureSession session = createSession(normalizedPatientId, normalizedPatientName);
        Path sessionDir = fileStorageService.resolveSessionDir(session.getSessionDir());

        try {
            Path audioPath = sessionDir.resolve("audio.wav");
            Path imuPath = sessionDir.resolve("imu.csv");
            Path gasPath = sessionDir.resolve("gas.csv");

            saveMultipart(audio, audioPath);
            saveMultipart(imu, imuPath);
            saveMultipart(gas, gasPath);

            session.setStatus("PROCESSING");
            captureSessionRepository.save(session);

            ModelPredictionService.PredictionResult result = modelPredictionService.uploadAndPredict(
                    audioPath.toFile(),
                    imuPath.toFile(),
                    gasPath.toFile());

            if (result == null) {
                throw new BizException(502, "推理服务调用失败");
            }

            session.setStatus("SUCCEEDED");
            session.setModelSnapshotJson("{\"source\":\"python-runtime\"}");
            captureSessionRepository.save(session);

            return toResponse(session.getId(), session.getStatus(), result);
        } catch (Exception ex) {
            session.setStatus("FAILED");
            captureSessionRepository.save(session);

            if (ex instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException(500, "文件检测失败: " + ex.getMessage());
        }
    }

    private CaptureSession createSession(String patientId, String patientName) {
        String sessionKey = sessionPathResolver.buildSessionKey("file", patientId, patientName);
        Path sessionDir = fileStorageService.ensureSessionDirectory(sessionKey);
        String relativeSessionDir = fileStorageService.toRelativePath(sessionDir);
        LocalDateTime now = LocalDateTime.now(CaptureSession.ZONE_CN);

        CaptureSession session = CaptureSession.builder()
                .mode("FILE")
                .status("CREATED")
                .patientId(patientId)
                .patientNameSnapshot(patientName)
                .sessionKey(sessionKey)
                .sessionDir(relativeSessionDir)
                .inferenceServiceUrl(inferenceProperties.getBaseUrl())
                .startedAt(now)
                .build();

        return captureSessionRepository.save(session);
    }

    private Map<String, Object> toResponse(String sessionId, String status, ModelPredictionService.PredictionResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("status", status);

        if (result.getMessage() != null && !result.getMessage().isBlank()) {
            payload.put("message", result.getMessage());
        }
        if (result.getSwallowEvents() != null) {
            payload.put("swallow_events", result.getSwallowEvents());
        }
        if (result.getDysphagia() != null) {
            payload.put("dysphagia", result.getDysphagia());
        }
        if (result.getAspiration() != null) {
            payload.put("aspiration", result.getAspiration());
        }
        return payload;
    }

    private void saveMultipart(MultipartFile part, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.copy(part.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(500, "保存文件失败: " + target.getFileName());
        }
    }

    private void validateFile(String field, MultipartFile file, String expectedExt) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, field + " 文件不能为空");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith("." + expectedExt)) {
            throw new BizException(400, field + " 文件格式错误，应为 ." + expectedExt);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }
}
