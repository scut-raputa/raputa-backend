package cn.scut.raputa.controller;

import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.ModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

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
}
