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

    @GetMapping("/overview")
    public ApiResponse<List<PatientFilesOverviewVO>> overview(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,

            @RequestParam(required = false) String patientIds,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String filename
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
