package cn.scut.raputa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据传输控制请求")
public class DataTransferControlDTO {
    
    @NotBlank(message = "设备IP不能为空")
    @Schema(description = "设备IP地址", example = "192.168.1.100", required = true)
    private String deviceIp;
    
    @NotNull(message = "控制状态不能为空")
    @Schema(description = "控制状态", example = "true", required = true)
    private Boolean enable;
    
    @Schema(description = "数据类型", example = "ALL", allowableValues = {"ALL", "IMU", "GAS", "AUDIO"})
    private String dataType = "ALL";
}






