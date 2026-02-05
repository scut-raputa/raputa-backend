package cn.scut.raputa.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class AppointmentVO {
    private String id;
    private String name;
    private String dept;
    private String time;
}
