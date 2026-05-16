package cn.scut.raputa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "raputa.inference")
public class InferenceProperties {
    private String baseUrl = "http://127.0.0.1:8000";
    private String dysphagiaBaseUrl = "http://127.0.0.1:8001";
    private String aspirationBaseUrl = "http://127.0.0.1:8002";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 15000;
    private String healthPath = "/health";
    private String livePath = "/health/live";
    private String readyPath = "/health/ready";
    private String modelsPath = "/models";
    private String uploadPredictPath = "/upload_predict/";
    private String dysphagiaAutoPredictPath = "/upload_predict/dys/";
    private String dysphagiaDirectPredictPath = "/upload_predict/dys_direct/";
    private String aspirationAutoPredictPath = "/upload_predict/";
    private String aspirationDirectPredictPath = "/upload_predict/asp_direct/";
    private int healthCheckIntervalSeconds = 10;
}
