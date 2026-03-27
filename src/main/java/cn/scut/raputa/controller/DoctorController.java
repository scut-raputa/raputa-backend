package cn.scut.raputa.controller;

import cn.scut.raputa.dto.DoctorDTO;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.DoctorService;
import cn.scut.raputa.vo.DoctorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String title) {
        Page<DoctorVO> pg = doctorService.page(page, size, id, name, department, phone, title);
        return ApiResponse.ok(new PageWrap<>(pg.getContent(), pg.getTotalElements()));
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody DoctorDTO dto) {
        return ApiResponse.ok(doctorService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody DoctorDTO dto) {
        return ApiResponse.ok(doctorService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        doctorService.delete(id);
        return ApiResponse.ok(null);
    }

    public record PageWrap<T>(java.util.List<T> items, long total) {
    }
}