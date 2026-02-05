package cn.scut.raputa.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentCreateDTO {
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过64个字符")
    private String name;

    @NotBlank(message = "科室不能为空")
    @Size(max = 128, message = "科室长度不能超过128个字符")
    private String dept;

    @NotNull(message = "预约时间不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate time;
}
