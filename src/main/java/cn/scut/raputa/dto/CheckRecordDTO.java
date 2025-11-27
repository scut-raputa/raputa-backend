package cn.scut.raputa.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CheckRecordDTO {
    private String patientId;
    private String name;
    private String staff;
    private String result;
    private String checkTime;
}
