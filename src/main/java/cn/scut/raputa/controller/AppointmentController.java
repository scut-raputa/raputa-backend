package cn.scut.raputa.controller;

import cn.scut.raputa.dto.AppointmentCreateDTO;
import cn.scut.raputa.dto.AppointmentUpdateDTO;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.AppointmentService;
import cn.scut.raputa.vo.AppointmentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointment")
@RequiredArgsConstructor
@Validated
public class

AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "3") int size,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String date) {
        Page<AppointmentVO> pg = appointmentService.page(page, size, id, name, dept, date);
        return ApiResponse.ok(new PageWrap<>(pg.getContent(), pg.getTotalElements()));
    }

    @PostMapping
    public ApiResponse<AppointmentVO> create(@RequestBody @Valid AppointmentCreateDTO dto) {
        AppointmentVO vo = appointmentService.create(dto);
        return ApiResponse.ok(vo);
    }

    @PatchMapping("/{id}")
    public ApiResponse<AppointmentVO> update(@PathVariable String id,
                                             @RequestBody @Valid AppointmentUpdateDTO dto) {
        AppointmentVO vo = appointmentService.update(id, dto);
        return ApiResponse.ok(vo);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        appointmentService.deleteById(id);
        return ApiResponse.ok(null);
    }

    public record PageWrap<T>(java.util.List<T> items, long total) {
    }
}
