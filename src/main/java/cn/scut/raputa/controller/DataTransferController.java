package cn.scut.raputa.controller;

import cn.scut.raputa.dto.*;
import cn.scut.raputa.entity.AudioData;
import cn.scut.raputa.entity.GasData;
import cn.scut.raputa.entity.ImuData;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.DataTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 数据传输控制器
 * 
 * @author RAPUTA Team
 */
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "数据传输管理", description = "音频、IMU、气体传感器数据传输接口")
public class DataTransferController {

    private final DataTransferService dataTransferService;

    // ========== 数据传输控制 ==========

    @PostMapping("/control")
    @Operation(summary = "发送数据传输控制命令", description = "控制设备开始或停止数据传输")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "控制命令发送成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public CompletableFuture<ResponseEntity<ApiResponse<Boolean>>> sendControlCommand(
            @Parameter(description = "数据传输控制参数", required = true)
            @RequestBody @Valid DataTransferControlDTO request) {
        
        log.info("发送数据传输控制命令: deviceIp={}, enable={}, dataType={}", 
                request.getDeviceIp(), request.getEnable(), request.getDataType());
        
        return dataTransferService.sendControlCommand(request)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.ok(true, "控制命令发送成功"));
                    } else {
                        return ResponseEntity.ok(ApiResponse.<Boolean>error(500, "控制命令发送失败"));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("发送控制命令异常", throwable);
                    return ResponseEntity.ok(ApiResponse.<Boolean>error(500, "发送控制命令异常: " + throwable.getMessage()));
                });
    }

    // ========== IMU数据传输 ==========

    @PostMapping("/imu")
    @Operation(summary = "保存IMU数据", description = "保存单个IMU传感器数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "IMU数据保存成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<ImuData>> saveImuData(
            @Parameter(description = "IMU数据", required = true)
            @RequestBody @Valid ImuDataDTO request,
            @Parameter(description = "设备ID", required = true)
            @RequestParam String deviceId) {
        
        try {
            ImuData saved = dataTransferService.saveImuData(request, deviceId);
            return ResponseEntity.ok(ApiResponse.ok(saved, "IMU数据保存成功"));
        } catch (Exception e) {
            log.error("保存IMU数据失败", e);
            return ResponseEntity.ok(ApiResponse.<ImuData>error(500, "保存IMU数据失败: " + e.getMessage()));
        }
    }

    @PostMapping("/imu/batch")
    @Operation(summary = "批量保存IMU数据", description = "批量保存多个IMU传感器数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "IMU数据批量保存成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<List<ImuData>>> saveImuDataBatch(
            @Parameter(description = "IMU数据列表", required = true)
            @RequestBody @Valid List<ImuDataDTO> requests,
            @Parameter(description = "设备ID", required = true)
            @RequestParam String deviceId) {
        
        try {
            List<ImuData> saved = dataTransferService.saveImuDataBatch(requests, deviceId);
            return ResponseEntity.ok(ApiResponse.ok(saved, "IMU数据批量保存成功"));
        } catch (Exception e) {
            log.error("批量保存IMU数据失败", e);
            return ResponseEntity.ok(ApiResponse.<List<ImuData>>error(500, "批量保存IMU数据失败: " + e.getMessage()));
        }
    }

    @GetMapping("/imu/{deviceId}")
    @Operation(summary = "获取设备IMU数据", description = "根据设备ID获取IMU传感器数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取IMU数据成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<List<ImuData>>> getImuData(
            @Parameter(description = "设备ID", required = true)
            @PathVariable String deviceId,
            @Parameter(description = "开始时间戳")
            @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳")
            @RequestParam(required = false) Long endTime) {
        
        try {
            List<ImuData> data = dataTransferService.getImuDataByDevice(deviceId, startTime, endTime);
            return ResponseEntity.ok(ApiResponse.ok(data, "获取IMU数据成功"));
        } catch (Exception e) {
            log.error("获取IMU数据失败", e);
            return ResponseEntity.ok(ApiResponse.<List<ImuData>>error(500, "获取IMU数据失败: " + e.getMessage()));
        }
    }

    // ========== 气体传感器数据传输 ==========

//    @PostMapping("/gas")
//    @Operation(summary = "保存气体数据", description = "保存单个气体传感器数据")
//    @ApiResponses(value = {
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "气体数据保存成功"),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
//    })
//    public ResponseEntity<ApiResponse<GasData>> saveGasData(
//            @Parameter(description = "气体数据", required = true)
//            @RequestBody @Valid GasDataDTO request,
//            @Parameter(description = "设备ID", required = true)
//            @RequestParam String deviceId) {
//
//        try {
//            GasData saved = dataTransferService.saveGasData(request, deviceId);
//            return ResponseEntity.ok(ApiResponse.ok(saved, "气体数据保存成功"));
//        } catch (Exception e) {
//            log.error("保存气体数据失败", e);
//            return ResponseEntity.ok(ApiResponse.<GasData>error(500, "保存气体数据失败: " + e.getMessage()));
//        }
//    }

//    @PostMapping("/gas/batch")
//    @Operation(summary = "批量保存气体数据", description = "批量保存多个气体传感器数据")
//    @ApiResponses(value = {
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "气体数据批量保存成功"),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
//            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
//    })
//    public ResponseEntity<ApiResponse<List<GasData>>> saveGasDataBatch(
//            @Parameter(description = "气体数据列表", required = true)
//            @RequestBody @Valid List<GasDataDTO> requests,
//            @Parameter(description = "设备ID", required = true)
//            @RequestParam String deviceId) {
//
//        try {
//            List<GasData> saved = dataTransferService.saveGasDataBatch(requests, deviceId);
//            return ResponseEntity.ok(ApiResponse.ok(saved, "气体数据批量保存成功"));
//        } catch (Exception e) {
//            log.error("批量保存气体数据失败", e);
//            return ResponseEntity.ok(ApiResponse.<List<GasData>>error(500, "批量保存气体数据失败: " + e.getMessage()));
//        }
//    }

    @GetMapping("/gas/{deviceId}")
    @Operation(summary = "获取设备气体数据", description = "根据设备ID获取气体传感器数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取气体数据成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<List<GasData>>> getGasData(
            @Parameter(description = "设备ID", required = true)
            @PathVariable String deviceId,
            @Parameter(description = "开始时间戳")
            @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳")
            @RequestParam(required = false) Long endTime) {
        
        try {
            List<GasData> data = dataTransferService.getGasDataByDevice(deviceId, startTime, endTime);
            return ResponseEntity.ok(ApiResponse.ok(data, "获取气体数据成功"));
        } catch (Exception e) {
            log.error("获取气体数据失败", e);
            return ResponseEntity.ok(ApiResponse.<List<GasData>>error(500, "获取气体数据失败: " + e.getMessage()));
        }
    }

    // ========== 音频数据传输 ==========

    @PostMapping("/audio")
    @Operation(summary = "保存音频数据", description = "保存音频数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "音频数据保存成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<AudioData>> saveAudioData(
            @Parameter(description = "音频数据", required = true)
            @RequestBody @Valid AudioDataDTO request) {
        
        try {
            AudioData saved = dataTransferService.saveAudioData(request);
            return ResponseEntity.ok(ApiResponse.ok(saved, "音频数据保存成功"));
        } catch (Exception e) {
            log.error("保存音频数据失败", e);
            return ResponseEntity.ok(ApiResponse.<AudioData>error(500, "保存音频数据失败: " + e.getMessage()));
        }
    }

    @GetMapping("/audio/{deviceId}")
    @Operation(summary = "获取设备音频数据", description = "根据设备ID获取音频数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取音频数据成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<List<AudioData>>> getAudioData(
            @Parameter(description = "设备ID", required = true)
            @PathVariable String deviceId,
            @Parameter(description = "开始时间戳")
            @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳")
            @RequestParam(required = false) Long endTime) {
        
        try {
            List<AudioData> data = dataTransferService.getAudioDataByDevice(deviceId, startTime, endTime);
            return ResponseEntity.ok(ApiResponse.ok(data, "获取音频数据成功"));
        } catch (Exception e) {
            log.error("获取音频数据失败", e);
            return ResponseEntity.ok(ApiResponse.<List<AudioData>>error(500, "获取音频数据失败: " + e.getMessage()));
        }
    }

    // ========== 数据管理 ==========

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "删除设备数据", description = "删除指定设备的数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "数据删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<String>> deleteDeviceData(
            @Parameter(description = "设备ID", required = true)
            @PathVariable String deviceId,
            @Parameter(description = "数据类型", example = "ALL")
            @RequestParam(defaultValue = "ALL") String dataType) {
        
        try {
            dataTransferService.deleteDeviceData(deviceId, dataType);
            return ResponseEntity.ok(ApiResponse.ok("数据删除成功"));
        } catch (Exception e) {
            log.error("删除设备数据失败", e);
            return ResponseEntity.ok(ApiResponse.<String>error(500, "删除设备数据失败: " + e.getMessage()));
        }
    }

    @GetMapping("/stats/{deviceId}")
    @Operation(summary = "获取设备数据统计", description = "获取指定设备的数据统计信息")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取数据统计成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<DataTransferService.DeviceDataStatsDTO>> getDeviceDataStats(
            @Parameter(description = "设备ID", required = true)
            @PathVariable String deviceId) {
        
        try {
            DataTransferService.DeviceDataStatsDTO stats = dataTransferService.getDeviceDataStats(deviceId);
            return ResponseEntity.ok(ApiResponse.ok(stats, "获取数据统计成功"));
        } catch (Exception e) {
            log.error("获取设备数据统计失败", e);
            return ResponseEntity.ok(ApiResponse.<DataTransferService.DeviceDataStatsDTO>error(500, "获取设备数据统计失败: " + e.getMessage()));
        }
    }
}






