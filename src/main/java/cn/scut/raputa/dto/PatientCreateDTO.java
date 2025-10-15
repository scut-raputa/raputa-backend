package cn.scut.raputa.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientCreateDTO {

    @NotBlank
    @Size(max = 64)
    private String name;

    @NotBlank
    @Pattern(regexp = "男|女")
    private String gender;

    @NotNull
    private LocalDate birth;

    @NotBlank
    @Size(max = 128)
    private String dept;

    @NotBlank
    @Size(max = 255)
    private String address;

    @NotNull
    private Boolean checked;
}
