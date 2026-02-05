package cn.scut.raputa.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder(toBuilder = true)
public class PatientVO {
    private String id;
    private String outpatientId;
    private String name;
    private String gender;
    private Integer age;
    //private String birth;
    private String admit;
    private String dept;
    //private String address;
    private Boolean checked;
    private String idCard;
    private LocalDate onsetDate; // 发病日期
    private String pastHistory; // 既往史
    private String bedNumber; // 病床号
    private String course; // 病程
}
