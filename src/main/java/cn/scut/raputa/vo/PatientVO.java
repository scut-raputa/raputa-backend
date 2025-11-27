package cn.scut.raputa.vo;

import lombok.Builder;
import lombok.Data;

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
    private String address;
    private Boolean checked;
}
