// cn/scut/raputa/controller/PatientFileController.java
package cn.scut.raputa.controller;

import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.PatientFileService;
import cn.scut.raputa.vo.PatientFilesOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/patient-file")
@RequiredArgsConstructor
public class PatientFileController {

    private final PatientFileService patientFileService;

    // 概览接口：把所有患者都返回（没记录的 dates 为空）
    @GetMapping("/overview")
    public ApiResponse<List<PatientFilesOverviewVO>> overview(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date, // 前端“按天筛选”（可空）

            @RequestParam(required = false) String patientIds, // 逗号分隔
            @RequestParam(required = false) String types,      // 逗号分隔: csv,wav,pdf
            @RequestParam(required = false) String filename    // 模糊匹配
    ) {
        List<String> idList = split(patientIds);
        List<String> typeList = split(types);
        return ApiResponse.ok(
                patientFileService.overview(date, idList, typeList, filename)
        );
    }

    private List<String> split(String s) {
        if (s == null || s.isBlank()) return null;
        return Arrays.stream(s.split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).toList();
    }
}
