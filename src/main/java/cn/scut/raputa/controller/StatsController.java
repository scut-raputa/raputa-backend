package cn.scut.raputa.controller;

import cn.scut.raputa.dto.StatsDTO;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 统计数据控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "统计管理", description = "数据统计相关接口")
public class StatsController {

    private final StatsService statsService;

    /**
     * 获取统计数据
     */
    @GetMapping
    @Operation(summary = "获取统计数据", description = "获取数据统计模块所需的所有统计数据")
    public ApiResponse<StatsDTO.StatsResponse> getStats(
            @Parameter(description = "开始日期 (格式: yyyy-MM-dd)")
            @RequestParam(required = false) String startDate,

            @Parameter(description = "结束日期 (格式: yyyy-MM-dd)")
            @RequestParam(required = false) String endDate,

            @Parameter(description = "最近N天 (默认7天，当startDate和endDate都未指定时生效)")
            @RequestParam(required = false) Integer days
    ) {
        log.info("获取统计数据: startDate={}, endDate={}, days={}", startDate, endDate, days);

        StatsDTO.StatsQuery query = new StatsDTO.StatsQuery(startDate, endDate, days);
        StatsDTO.StatsResponse stats = statsService.getStats(query);

        return ApiResponse.ok(stats);
    }

    /**
     * 获取每日检测患者数量
     */
    @GetMapping("/daily-patient-count")
    @Operation(summary = "获取每日检测患者数量", description = "用于折线图展示")
    public ApiResponse<StatsDTO.StatsResponse> getDailyPatientCount(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer days
    ) {
        StatsDTO.StatsQuery query = new StatsDTO.StatsQuery(startDate, endDate, days);
        StatsDTO.StatsResponse stats = statsService.getStats(query);

        return ApiResponse.ok(stats);
    }
}
