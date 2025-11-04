package cn.scut.raputa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备发现请求")
public class DeviceDiscoveryDTO {
    
    @NotNull(message = "UDP端口不能为空")
    @Min(value = 1024, message = "端口号不能小于1024")
    @Max(value = 65535, message = "端口号不能大于65535")
    @Schema(description = "UDP监听端口", example = "6666", required = true)
    private Integer port = 6666;
    
    @Min(value = 1000, message = "超时时间不能小于1000毫秒")
    @Max(value = 30000, message = "超时时间不能大于30000毫秒")
    @Schema(description = "UDP接收超时时间(毫秒)", example = "5000")
    private Integer timeout = 5000;
    
    @Min(value = 100, message = "扫描间隔不能小于100毫秒")
    @Max(value = 10000, message = "扫描间隔不能大于10000毫秒")
    @Schema(description = "设备扫描间隔(毫秒)", example = "20")
    private Integer scanInterval = 20;
}


