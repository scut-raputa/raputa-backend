package cn.scut.raputa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningRecordDTO {
    private String appointmentId;
    private String subjectName;
    private String subjectGender;
    private Integer subjectAge;
    private String subjectIdCard;
    private String subjectPhone;
    private String subjectDept;
    private String checkDept;
    private String deviceId;
    private String mode;
    private String sessionId;
    private String result;
    private String riskLevel;
    private String staff;
    private Integer totalSwallows;
    private Integer normalSwallows;
    private Integer dysphagiaSwallows;
    private Integer aspirationSwallows;
    private String checkTime;
}
