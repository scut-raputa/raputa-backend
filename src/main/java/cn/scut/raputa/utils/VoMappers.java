package cn.scut.raputa.utils;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import cn.scut.raputa.entity.Appointment;
import cn.scut.raputa.entity.CheckRecord;
import cn.scut.raputa.entity.Device;
import cn.scut.raputa.entity.Model;
import cn.scut.raputa.entity.Patient;
import cn.scut.raputa.entity.User;
import cn.scut.raputa.vo.AppointmentVO;
import cn.scut.raputa.vo.CheckRecordVO;
import cn.scut.raputa.vo.DeviceVO;
import cn.scut.raputa.vo.ModelVO;
import cn.scut.raputa.vo.PatientVO;
import cn.scut.raputa.vo.UserVO;

public final class VoMappers {
    private VoMappers() {
    }

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DTMF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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
        LocalDate birth = null;
        if (p != null && p.getIdCard() != null && !p.getIdCard().isBlank()) {
            birth = parseBirthFromIdCard(p.getIdCard());
            if (birth != null) {
                age = Period.between(birth, LocalDate.now(CCT)).getYears();
            }
        }
        return PatientVO.builder()
                .id(p.getId())
                .outpatientId(p.getOutpatientId())
                .idCard(p.getIdCard())
                .name(p.getName())
                .gender(p.getGender())
                .age(age)
                .birth(birth == null ? null : birth.format(DTF))
                .admit(p.getAdmit() == null ? null : p.getAdmit().format(DTF))
                .dept(p.getDept())
                .onsetDate(p.getOnsetDate())
                .bedNumber(p.getBedNumber())
                .course(p.getCourse())
                .pastHistory(p.getPastHistory())
                .checked(p.isChecked())
                .build();
    }

    private static LocalDate parseBirthFromIdCard(String idCard) {
        if (idCard == null) return null;
        String s = idCard.trim();
        try {
            if (s.length() == 18) {
                String ymd = s.substring(6, 14); // yyyyMMdd
                return LocalDate.parse(ymd, DateTimeFormatter.ofPattern("yyyyMMdd"));
            } else if (s.length() == 15) {
                String yy = s.substring(6, 8);
                String mm = s.substring(8, 10);
                String dd = s.substring(10, 12);
                String yyyy = "19" + yy; // 15位身份证通常为19xx
                return LocalDate.parse(yyyy + mm + dd, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            // 解析失败返回 null
        }
        return null;
    }

    public static AppointmentVO toAppointmentVO(Appointment a) {
        DateTimeFormatter TF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate birth = parseBirthFromIdCard(a.getIdCard());
        return AppointmentVO.builder()
                .id(a.getId())
                .name(a.getName())
                .gender(a.getGender())
                .idCard(a.getIdCard())
                .birth(birth == null ? null : birth.format(DTF))
                .age(birth == null ? null : Period.between(birth, LocalDate.now(CCT)).getYears())
                .phone(a.getPhone())
                .dept(a.getDept())
                .time(a.getApptTime() == null ? null : a.getApptTime().format(TF))
                .status(a.getStatus() == null || a.getStatus().isBlank() ? "PENDING" : a.getStatus())
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

    public static DeviceVO toDeviceVO(Device d) {
        return DeviceVO.builder()
                .id(d.getId())
                .name(d.getName())
                .ip(d.getIp())
                .hardwareId(d.getHardwareId())
                .lastConnectedTime(d.getLastConnectedTime() == null ? null
                        : d.getLastConnectedTime().format(DTMF))
            .lastSeenAt(d.getLastSeenAt() == null ? null
                : d.getLastSeenAt().format(DTMF))
                .status(d.getStatus())
            .accessMode(d.getAccessMode())
            .controlPort(d.getControlPort())
            .rtspPath(d.getRtspPath())
            .enabled(d.getEnabled())
                .description(d.getDescription())
                .storageLocation(d.getStorageLocation())
                .responsible(d.getResponsible())
                .build();
    }

}
