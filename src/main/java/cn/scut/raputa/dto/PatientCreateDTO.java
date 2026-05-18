package cn.scut.raputa.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

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

    @NotBlank
    @Size(min = 18, max = 18)
    private String idCard;

    @NotBlank
    @Size(max = 128)
    private String dept;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate onsetDate;

    @Size(max = 2048)
    private String pastHistory;

    @Size(max = 32)
    private String bedNumber;

    @Size(max = 2048)
    private String course;

}
