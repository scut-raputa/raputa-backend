package cn.scut.raputa.utils;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import cn.scut.raputa.entity.Appointment;
import cn.scut.raputa.entity.CheckRecord;
import cn.scut.raputa.entity.Model;
import cn.scut.raputa.entity.Patient;
import cn.scut.raputa.entity.User;
import cn.scut.raputa.vo.AppointmentVO;
import cn.scut.raputa.vo.CheckRecordVO;
import cn.scut.raputa.vo.ModelVO;
import cn.scut.raputa.vo.PatientVO;
import cn.scut.raputa.vo.UserVO;

public final class VoMappers {
    private VoMappers() {
    }

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId CCT = ZoneId.of("Asia/Shanghai");

    public static UserVO toUserVO(User u) {
        if (u == null)
            return null;
        return UserVO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .hospitalName(u.getHospitalName())
                .departmentName(u.getDepartmentName())
                .enabled(u.getEnabled())
                .createdAt(u.getCreatedAt())
                .lastLoginAt(u.getLastLoginAt())
                .lastLoginIp(u.getLastLoginIp())
                .avatarUrl(u.getAvatarUrl() != null ? u.getAvatarUrl() : "/images/default-avatar.png")
                .role(u.getRole())
                .build();
    }

    public static PatientVO toPatientVO(Patient p) {
        Integer age = null;
        if (p.getBirth() != null) {
            age = Period.between(p.getBirth(), LocalDate.now(CCT)).getYears();
        }
        return PatientVO.builder()
                .id(p.getId())
                .name(p.getName())
                .gender(p.getGender())
                .age(age)
                .birth(p.getBirth() == null ? null : p.getBirth().format(DTF))
                .admit(p.getAdmit() == null ? null : p.getAdmit().format(DTF))
                .dept(p.getDept())
                .address(p.getAddress())
                .checked(p.isChecked())
                .build();
    }

    public static AppointmentVO toAppointmentVO(Appointment a) {
        DateTimeFormatter TF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return AppointmentVO.builder()
                .id(a.getId())
                .name(a.getName())
                .dept(a.getDept())
                .time(a.getApptTime() == null ? null : a.getApptTime().format(TF))
                .build();
    }

    public static CheckRecordVO toCheckVO(CheckRecord r) {
        DateTimeFormatter TF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return CheckRecordVO.builder()
                .id(r.getPatientId())
                .name(r.getName())
                .staff(r.getStaff())
                .result(r.getResult() == null ? null : r.getResult().getLabel())
                .date(r.getCheckTime() == null ? null : r.getCheckTime().format(TF))
                .build();
    }

    public static ModelVO toModelVO(Model m) {
        return ModelVO.builder()
                .id(m.getId())
                .func(m.getFunc())
                .name(m.getName())
                .uploadTime(m.getUploadTime() == null ? null : m.getUploadTime().toString())
                .uploader(m.getUploader())
                .remark(m.getRemark())
                .accuracy(m.getAccuracy() == null ? null : m.getAccuracy().doubleValue())
                .sensitivity(m.getSensitivity() == null ? null : m.getSensitivity().doubleValue())
                .specificity(m.getSpecificity() == null ? null : m.getSpecificity().doubleValue())
                .build();
    }

}
