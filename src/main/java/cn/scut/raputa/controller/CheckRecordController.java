package cn.scut.raputa.controller;

import cn.scut.raputa.dto.CheckRecordDTO;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.CheckRecordService;
import cn.scut.raputa.vo.CheckRecordVO;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/check")
@RequiredArgsConstructor
public class CheckRecordController {

    private final CheckRecordService checkRecordService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "3") int size,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String staff,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String date) {
        Page<CheckRecordVO> pg = checkRecordService.page(page, size, id, name, staff, result, date);
        return ApiResponse.ok(new PageWrap<>(pg.getContent(), pg.getTotalElements()));
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody CheckRecordDTO dto) {
        CheckRecordVO vo = checkRecordService.create(dto);
        return ApiResponse.ok(vo);
    }

    @PostMapping("/batch")
    public ApiResponse<?> createBatch(@RequestBody List<CheckRecordDTO> dtos) {
        List<CheckRecordVO> vos = checkRecordService.createBatch(dtos);
        return ApiResponse.ok(vos);
    }

    public record PageWrap<T>(java.util.List<T> items, long total) {
    }
}
