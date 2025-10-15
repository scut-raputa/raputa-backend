package cn.scut.raputa.controller;

import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.CheckRecordService;
import cn.scut.raputa.vo.CheckRecordVO;
import lombok.RequiredArgsConstructor;
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

    public record PageWrap<T>(java.util.List<T> items, long total) {
    }
}
