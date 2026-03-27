package cn.scut.raputa.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ModelDTO {
    private String func;
    private String name;
    private String uploadTime; // ISO "yyyy-MM-ddTHH:mm:ss" or "yyyy-MM-dd HH:mm:ss"
    private String uploader;
    private String remark;
    private String location;
    private BigDecimal accuracy;
    private BigDecimal sensitivity;
    private BigDecimal specificity;
}