package cn.scut.raputa.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeModelVO {
    private String name;
    private String taskType;
    private String modelVersion;
    private String deployPath;
    private boolean loaded;
    private boolean serviceLive;
    private boolean serviceReady;
    private String device;
    private String serviceName;
    private String lastHealthCheckAt;
    private String lastHealthError;
}