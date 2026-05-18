package cn.scut.raputa.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class ScreeningRecordVO {
    private String id;
    private String source;
    private String status;
    private String appointmentId;
    private String patientId;
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
    private String archivedAt;
}
