package cn.scut.raputa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "raputa.inference")
public class InferenceProperties {
    private String baseUrl = "http://127.0.0.1:8000";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 15000;
    private String livePath = "/health/live";
    private String readyPath = "/health/ready";
    private String modelsPath = "/models";
    private String uploadPredictPath = "/upload_predict/";
    private int healthCheckIntervalSeconds = 10;
}