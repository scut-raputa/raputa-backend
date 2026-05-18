package cn.scut.raputa.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentUpdateDTO {
    @Size(max = 32, message = "联系电话长度不能超过32个字符")
    private String phone;

    @Size(max = 128, message = "科室长度不能超过128个字符")
    private String dept;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate time;
}
