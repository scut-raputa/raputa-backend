package cn.scut.raputa.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InferenceHealthVO {
    private String serviceName;
    private String inferenceBaseUrl;
    private boolean serviceLive;
    private boolean serviceReady;
    private String lastHealthCheckAt;
    private String lastHealthError;
}