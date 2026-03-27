package cn.scut.raputa.dto;

import lombok.Data;

@Data
public class DoctorDTO {
    private String name;
    private String department;
    private String title;   // 主任医师 / 副主任医师 / 主治医师 / 住院医师
    private String phone;
}