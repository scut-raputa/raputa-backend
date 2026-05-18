package cn.scut.raputa.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeSummaryVO {
    private boolean serviceLive;
    private boolean serviceReady;
    private long discoveredModelCount;
    private long loadedModelCount;
    private long availableModelCount;
    private long unavailableModelCount;
    private String lastHealthCheckAt;
    private String lastHealthError;
}