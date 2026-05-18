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
    private String birth;
    private String admit;
    private String dept;
    private Boolean checked;
    private String idCard;
    private LocalDate onsetDate;
    private String pastHistory;
    private String bedNumber;
    private String course;
}
