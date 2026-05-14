package cn.scut.raputa.service;

import cn.scut.raputa.config.InferenceProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;

/**
 * 模型预测服务 - 调用外部模型API
 */
@Slf4j
@Service
public class ModelPredictionService {

    private final RestTemplate restTemplate;
    private final InferenceProperties inferenceProperties;
    private final ObjectMapper objectMapper;

    public ModelPredictionService(InferenceProperties inferenceProperties) {
        this.inferenceProperties = inferenceProperties;
        this.objectMapper = new ObjectMapper();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(inferenceProperties.getConnectTimeoutMs(), 100));
        factory.setReadTimeout(Math.max(inferenceProperties.getReadTimeoutMs(), 100));
        this.restTemplate = new RestTemplate(factory);
    }
    
    /**
     * 上传文件并调用模型预测
     * 
     * @param audioFile 音频WAV文件
     * @param imuFile IMU CSV文件
     * @param gasFile GAS CSV文件
     * @return 预测结果
     */
    public PredictionResult uploadAndPredict(File audioFile, File imuFile, File gasFile) {
        return uploadAndPredict(audioFile, imuFile, gasFile, null);
    }

    /**
     * 上传文件并调用模型预测，可选传入人工分割的吞咽段。
     */
    public PredictionResult uploadAndPredict(
            File audioFile,
            File imuFile,
            File gasFile,
            List<List<Number>> manualSwallowEvents) {
        return uploadAndPredict(audioFile, imuFile, gasFile, manualSwallowEvents, null);
    }

    public PredictionResult uploadAndPredict(
            File audioFile,
            File imuFile,
            File gasFile,
            List<List<Number>> manualSwallowEvents,
            Integer predictionWindowSeconds) {
        try {
            String modelApiUrl = buildUploadPredictUrl();

            // 构建multipart请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("audio", new FileSystemResource(audioFile));
            body.add("imu", new FileSystemResource(imuFile));
            body.add("gas", new FileSystemResource(gasFile));
            if (manualSwallowEvents != null) {
                body.add("segmentation_mode", "manual");
                body.add("swallow_events", objectMapper.writeValueAsString(manualSwallowEvents));
            }
            if (predictionWindowSeconds != null && predictionWindowSeconds > 0) {
                body.add("prediction_window_seconds", String.valueOf(predictionWindowSeconds));
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            log.info("调用模型API: {}", modelApiUrl);
            log.info("上传文件: audio={}, imu={}, gas={}", 
                audioFile.getName(), imuFile.getName(), gasFile.getName());
            if (manualSwallowEvents != null) {
                log.info("使用人工吞咽段进行推理: {}", manualSwallowEvents);
            }

            // 发送请求
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                modelApiUrl,
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );

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
        } catch (JsonProcessingException e) {
            log.error("人工吞咽段序列化失败", e);
            return null;
        }
    }

    private String buildUploadPredictUrl() {
        String baseUrl = inferenceProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("raputa.inference.base-url 未配置");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String uploadPath = inferenceProperties.getUploadPredictPath();
        if (uploadPath == null || uploadPath.isBlank()) {
            uploadPath = "/upload_predict/";
        }
        String normalizedPath = uploadPath.startsWith("/") ? uploadPath : "/" + uploadPath;
        return normalizedBase + normalizedPath;
    }
    
    /**
     * 解析预测结果
     */
    private PredictionResult parsePredictionResult(Map<String, Object> result) {
        PredictionResult predictionResult = new PredictionResult();
        
        // 检查是否有错误消息
        if (result.containsKey("message")) {
            predictionResult.setMessage((String) result.get("message"));
            return predictionResult;
        }
        
        // 吞咽事件
        if (result.containsKey("swallow_events")) {
            @SuppressWarnings("unchecked")
            List<List<Number>> events = (List<List<Number>>) result.get("swallow_events");
            predictionResult.setSwallowEvents(events);
        }
        
        // 吞咽障碍检测结果
        if (result.containsKey("dysphagia")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dysphagia = (List<Map<String, Object>>) result.get("dysphagia");
            predictionResult.setDysphagia(dysphagia);
        }
        
        // 误吸检测结果
        if (result.containsKey("aspiration")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> aspiration = (List<Map<String, Object>>) result.get("aspiration");
            predictionResult.setAspiration(aspiration);
        }
        
        return predictionResult;
    }
    
    /**
     * 预测结果类
     */
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
