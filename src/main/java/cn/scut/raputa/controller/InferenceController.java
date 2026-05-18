package cn.scut.raputa.controller;

import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.FileDetectService;
import cn.scut.raputa.service.InferenceRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/inference")
@RequiredArgsConstructor
public class InferenceController {

    private final InferenceRuntimeService inferenceRuntimeService;
    private final FileDetectService fileDetectService;

    @GetMapping("/health")
    public ApiResponse<?> health() {
        return ApiResponse.ok(inferenceRuntimeService.getHealth());
    }

    @GetMapping("/models")
    public ApiResponse<?> models() {
        return ApiResponse.ok(inferenceRuntimeService.getRuntimeModels());
    }

    @PostMapping(value = "/file-detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> fileDetect(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("imu") MultipartFile imu,
            @RequestParam("gas") MultipartFile gas,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) String taskType) {
        return ApiResponse.ok(fileDetectService.detect(audio, imu, gas, patientId, patientName, taskType));
    }
}
