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

    @NotBlank(message = "性别不能为空")
    @jakarta.validation.constraints.Pattern(regexp = "男|女", message = "性别只能为男或女")
    private String gender;

    @NotBlank(message = "身份证号码不能为空")
    @jakarta.validation.constraints.Pattern(
            regexp = "\\d{6}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]",
            message = "身份证号码格式不正确"
    )
    private String idCard;

    @Size(max = 32, message = "联系电话长度不能超过32个字符")
    private String phone;

    @NotBlank(message = "科室不能为空")
    @Size(max = 128, message = "科室长度不能超过128个字符")
    private String dept;

    @NotNull(message = "预约时间不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate time;
}
