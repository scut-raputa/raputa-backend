package cn.scut.raputa.controller;

import cn.scut.raputa.dto.ScreeningArchiveDTO;
import cn.scut.raputa.dto.ScreeningRecordDTO;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.ScreeningRecordService;
import cn.scut.raputa.vo.ScreeningRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screening-record")
@RequiredArgsConstructor
public class ScreeningRecordController {

    private final ScreeningRecordService screeningRecordService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String appointmentId,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status) {
        Page<ScreeningRecordVO> pg = screeningRecordService.page(page, size, appointmentId, patientId, name, status);
        return ApiResponse.ok(new PageWrap<>(pg.getContent(), pg.getTotalElements()));
    }

    @PostMapping
    public ApiResponse<ScreeningRecordVO> create(@RequestBody ScreeningRecordDTO dto) {
        return ApiResponse.ok(screeningRecordService.create(dto));
    }

    @PatchMapping("/{id}/archive")
    public ApiResponse<ScreeningRecordVO> archive(
            @PathVariable String id,
            @RequestBody ScreeningArchiveDTO dto) {
        return ApiResponse.ok(screeningRecordService.archive(id, dto));
    }

    public record PageWrap<T>(java.util.List<T> items, long total) {
    }
}
