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
@Schema(description = "音频数据传输请求")
public class AudioDataDTO {
    
    @NotBlank(message = "设备IP不能为空")
    @Schema(
        description = "设备IP地址",
        example = "192.168.1.100",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String deviceIp;
    
    @NotNull(message = "音频数据不能为空")
    @Schema(
        description = "音频数据(Base64编码)",
        example = "UklGRnoGAABXQVZFZm10IBAAAAABAAEA...",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String audioData;
    
    @NotNull(message = "采样率不能为空")
    @Schema(
        description = "采样率",
        example = "44100",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Integer sampleRate;
    
    @NotNull(message = "声道数不能为空")
    @Schema(
        description = "声道数",
        example = "2",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Integer channels;
    
    @NotNull(message = "时间戳不能为空")
    @Schema(
        description = "时间戳",
        example = "1703123456789",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Long timestamp;
}
