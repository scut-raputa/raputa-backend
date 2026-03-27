package cn.scut.raputa.vo;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelStatsVO {
    private long totalCount;
    private long weekNewCount;
    private long lastWeekNewCount;
    private String topUploader;
    private long topUploaderCount;
    private double topUploaderRatio;
}