package cn.scut.raputa.vo;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorVO {
    private String id;
    private String name;
    private String department;
    private String title;
    private String phone;
}