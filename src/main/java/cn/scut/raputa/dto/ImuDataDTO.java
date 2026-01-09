package cn.scut.raputa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "IMU数据传输请求")
public class ImuDataDTO {
    
    @NotNull(message = "时间戳不能为空")
    @Schema(description = "时间戳(秒)", example = "1703123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long timestamp;
    
    @NotNull(message = "微秒时间戳不能为空")
    @Schema(description = "微秒时间戳", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long timestampus;
    
    @NotNull(message = "加速度计数据不能为空")
    @Schema(description = "加速度计数据", requiredMode = Schema.RequiredMode.REQUIRED)
    private AccDataDTO acc;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "加速度计数据")
    public static class AccDataDTO {
        @NotNull(message = "X轴数据不能为空")
        @Schema(description = "X轴加速度", example = "1024", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer x;
        
        @NotNull(message = "Y轴数据不能为空")
        @Schema(description = "Y轴加速度", example = "512", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer y;
        
        @NotNull(message = "Z轴数据不能为空")
        @Schema(description = "Z轴加速度", example = "256", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer z;
    }
}
