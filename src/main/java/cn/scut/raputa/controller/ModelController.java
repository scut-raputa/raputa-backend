package cn.scut.raputa.controller;

import cn.scut.raputa.dto.ModelDTO;
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

    @GetMapping("/runtime-list")
    public ApiResponse<?> runtimeList(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Boolean loaded,
            @RequestParam(required = false) Boolean available) {
        return ApiResponse.ok(modelService.runtimeList(name, taskType, loaded, available));
    }

    @GetMapping("/runtime-summary")
    public ApiResponse<?> runtimeSummary() {
        return ApiResponse.ok(modelService.runtimeSummary());
    }

    @GetMapping("/stats")
    public ApiResponse<?> stats() {
        return ApiResponse.ok(modelService.stats());
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody ModelDTO dto) {
        return ApiResponse.ok(modelService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody ModelDTO dto) {
        return ApiResponse.ok(modelService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        modelService.delete(id);
        return ApiResponse.ok(null);
    }

    public record PageWrap<T>(java.util.List<T> items, long total) {
    }
}