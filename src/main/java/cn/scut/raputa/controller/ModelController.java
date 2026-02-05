package cn.scut.raputa.controller;

import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.ModelService;
import cn.scut.raputa.vo.ModelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String func,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String uploader,
            @RequestParam(required = false) String date) {
        Page<ModelVO> pg = modelService.page(page, size, id, func, name, uploader, date);
        return ApiResponse.ok(new PageWrap<>(pg.getContent(), pg.getTotalElements()));
    }

    public record PageWrap<T>(java.util.List<T> items, long total) {
    }
}
