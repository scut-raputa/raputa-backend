package cn.scut.raputa.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CsvMappingRequestDTO {

    @NotNull(message = "采样率不能为空")
    @Min(value = 1, message = "采样率必须为正整数")
    private Integer sampleRate;

    private ImuAxisMap imuAxisMap;
    private String gasCol;
    private String audioCol;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImuAxisMap {
        private String X;
        private String Y;
        private String Z;
    }
}
