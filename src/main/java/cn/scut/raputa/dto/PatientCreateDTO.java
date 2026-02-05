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

    @NotNull
    private String idCard;

    @NotBlank
    @Size(max = 128)
    private String dept;

    // 发病日期（可选），前端传入格式: yyyy-MM-dd
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate onsetDate;

    // 既往史（可选）
    @Size(max = 2048)
    private String pastHistory;

    // 病床号（可选）
    @Size(max = 32)
    private String bedNumber;

    // 病程（可选）
    @Size(max = 2048)
    private String course;

    //@NotBlank
    //@Size(max = 255)
    //private String address;

}
