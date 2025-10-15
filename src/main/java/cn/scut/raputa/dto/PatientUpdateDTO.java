package cn.scut.raputa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientUpdateDTO {
    @NotBlank
    private String dept;

    @NotBlank
    private String address;
}