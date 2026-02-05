package cn.scut.raputa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备发现响应")
public class DeviceDiscoveryResponseDTO {
    
    @Schema(description = "设备IP地址", example = "192.168.1.100")
    private String deviceIp;
    
    @Schema(description = "设备名称", example = "RaspberryPi-001")
    private String deviceName;
    
    @Schema(description = "设备状态", example = "ONLINE")
    private String status;
    
    @Schema(description = "发现时间戳", example = "1703123456789")
    private Long discoveryTime;
    
    @Schema(description = "设备信息", example = "{\"version\":\"1.0\",\"type\":\"raspberry\"}")
    private String deviceInfo;
}





