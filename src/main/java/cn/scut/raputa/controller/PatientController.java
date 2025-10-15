package cn.scut.raputa.controller;

import cn.scut.raputa.dto.PatientCreateDTO;
import cn.scut.raputa.dto.PatientUpdateDTO;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.PatientService;
import cn.scut.raputa.vo.PatientVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String admit,
            @RequestParam(required = false) Boolean checked) {
        Page<PatientVO> pg = patientService.page(page, size, id, name, dept, address, gender, admit, checked);
        return ApiResponse.ok(new PageWrap<>(pg.getContent(), pg.getTotalElements()));
    }

    @PostMapping
    public ApiResponse<PatientVO> create(@RequestBody @Validated PatientCreateDTO dto) {
        PatientVO vo = patientService.create(dto);
        return ApiResponse.ok(vo);
    }

    @PatchMapping("/{id}")
    public ApiResponse<PatientVO> update(@PathVariable String id,
            @Valid @RequestBody PatientUpdateDTO dto) {
        PatientVO vo = patientService.updateDeptAndAddress(id, dto.getDept(), dto.getAddress());
        return ApiResponse.ok(vo);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        patientService.deleteById(id);
        return ApiResponse.ok(null);
    }

    public record PageWrap<T>(java.util.List<T> items, long total) {
    }
}
