package cn.scut.raputa.vo;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelVO {
    private String id;
    private String func;
    private String name;
    private String uploadTime;
    private String uploader;
    private String remark;
    private Double accuracy;
    private Double sensitivity;
    private Double specificity;
}
