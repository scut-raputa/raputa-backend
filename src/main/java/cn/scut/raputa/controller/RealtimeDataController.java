package cn.scut.raputa.controller;

import cn.scut.raputa.entity.AudioData;
import cn.scut.raputa.entity.GasData;
import cn.scut.raputa.entity.ImuData;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.CsvDataService;
import cn.scut.raputa.service.DataQueryService;
import cn.scut.raputa.service.RealtimeDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 实时数据控制器
 * 管理树莓派设备的实时数据接收
 * 
 * @author RAPUTA Team
 */
@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "实时数据管理", description = "树莓派设备实时数据接收和管理")
public class RealtimeDataController {

    private final RealtimeDataService realtimeDataService;
    private final DataQueryService dataQueryService;
    private final CsvDataService csvDataService;

    // ========== 设备连接管理 ==========

    @PostMapping("/connect")
    @Operation(summary = "连接设备开始接收数据", description = "连接到树莓派设备并开始接收IMU、GAS等传感器数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "连接成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public CompletableFuture<ResponseEntity<ApiResponse<Boolean>>> connectDevice(
            @Parameter(description = "设备IP地址", required = true)
            @RequestParam String deviceIp,
            @Parameter(description = "设备ID", required = true)
            @RequestParam String deviceId) {
        
        log.info("开始连接设备: {} ({})", deviceId, deviceIp);
        
        return realtimeDataService.startDataReceiving(deviceIp, deviceId)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.ok(true, "设备连接成功，开始接收数据"));
                    } else {
                        return ResponseEntity.ok(ApiResponse.<Boolean>error(500, "设备连接失败"));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("连接设备异常", throwable);
                    return ResponseEntity.ok(ApiResponse.<Boolean>error(500, "连接设备异常: " + throwable.getMessage()));
                });
    }

    @PostMapping("/disconnect")
    @Operation(summary = "断开设备连接", description = "停止接收数据并断开与设备的连接")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "断开成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> disconnectDevice(
            @Parameter(description = "设备ID", required = true)
            @RequestParam String deviceId) {
        
        log.info("断开设备连接: {}", deviceId);
        
        return realtimeDataService.stopDataReceiving(deviceId)
                .thenApply(success -> {
                    if (success) {
                        return ResponseEntity.ok(ApiResponse.ok(csvDataService.getCsvDirectory(), "设备连接已断开"));
                    } else {
                        return ResponseEntity.ok(ApiResponse.<String>error(500, "断开设备连接失败"));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("断开设备连接异常", throwable);
                    return ResponseEntity.ok(ApiResponse.<String>error(500, "断开设备连接异常: " + throwable.getMessage()));
                });
    }

    @GetMapping("/status/{deviceId}")
    @Operation(summary = "获取设备连接状态", description = "查询指定设备的连接和接收状态")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<DeviceStatusDTO>> getDeviceStatus(
            @Parameter(description = "设备ID", required = true)
            @PathVariable String deviceId) {
        
        try {
            boolean isConnected = realtimeDataService.isDeviceConnected(deviceId);
            boolean isReceiving = realtimeDataService.isDeviceReceiving(deviceId);
            LocalDateTime lastHeartbeat = realtimeDataService.getLastHeartbeat(deviceId);
            
            DeviceStatusDTO status = new DeviceStatusDTO(deviceId, isConnected, isReceiving, lastHeartbeat);
            return ResponseEntity.ok(ApiResponse.ok(status, "获取设备状态成功"));
            
        } catch (Exception e) {
            log.error("获取设备状态失败", e);
            return ResponseEntity.ok(ApiResponse.<DeviceStatusDTO>error(500, "获取设备状态失败: " + e.getMessage()));
        }
    }

    @GetMapping("/devices")
    @Operation(summary = "获取所有连接的设备", description = "获取当前所有已连接的设备列表")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<List<String>>> getConnectedDevices() {
        try {
            List<String> devices = realtimeDataService.getConnectedDevices();
            return ResponseEntity.ok(ApiResponse.ok(devices, "获取连接设备列表成功"));
            
        } catch (Exception e) {
            log.error("获取连接设备列表失败", e);
            return ResponseEntity.ok(ApiResponse.<List<String>>error(500, "获取连接设备列表失败: " + e.getMessage()));
        }
    }

    // ========== 数据查询接口 ==========

    @GetMapping("/imu/{deviceId}")
    @Operation(summary = "获取设备IMU数据", description = "查询指定设备的IMU传感器数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<List<ImuData>>> getImuData(
            @Parameter(description = "设备ID", required = true)
            @PathVariable String deviceId,
            @Parameter(description = "开始时间戳")
            @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳")
            @RequestParam(required = false) Long endTime,
            @Parameter(description = "数据条数限制")
            @RequestParam(defaultValue = "1000") int limit) {
        
        try {
            List<ImuData> data = dataQueryService.getImuDataByDevice(deviceId, startTime, endTime, limit);
            return ResponseEntity.ok(ApiResponse.ok(data, "获取IMU数据成功"));
            
        } catch (Exception e) {
            log.error("获取IMU数据失败", e);
            return ResponseEntity.ok(ApiResponse.<List<ImuData>>error(500, "获取IMU数据失败: " + e.getMessage()));
        }
    }

    @GetMapping("/gas/{deviceId}")
    @Operation(summary = "获取设备气体数据", description = "查询指定设备的气体传感器数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<List<GasData>>> getGasData(
            @Parameter(description = "设备ID", required = true)
            @PathVariable String deviceId,
            @Parameter(description = "开始时间戳")
            @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳")
            @RequestParam(required = false) Long endTime,
            @Parameter(description = "数据条数限制")
            @RequestParam(defaultValue = "1000") int limit) {
        
        try {
            List<GasData> data = dataQueryService.getGasDataByDevice(deviceId, startTime, endTime, limit);
            return ResponseEntity.ok(ApiResponse.ok(data, "获取气体数据成功"));
            
        } catch (Exception e) {
            log.error("获取气体数据失败", e);
            return ResponseEntity.ok(ApiResponse.<List<GasData>>error(500, "获取气体数据失败: " + e.getMessage()));
        }
    }

    @GetMapping("/audio/{deviceId}")
    @Operation(summary = "获取设备音频数据", description = "查询指定设备的音频数据")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<List<AudioData>>> getAudioData(
            @Parameter(description = "设备ID", required = true)
            @PathVariable String deviceId,
            @Parameter(description = "开始时间戳")
            @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳")
            @RequestParam(required = false) Long endTime,
            @Parameter(description = "数据条数限制")
            @RequestParam(defaultValue = "100") int limit) {
        
        try {
            List<AudioData> data = dataQueryService.getAudioDataByDevice(deviceId, startTime, endTime, limit);
            return ResponseEntity.ok(ApiResponse.ok(data, "获取音频数据成功"));
            
        } catch (Exception e) {
            log.error("获取音频数据失败", e);
            return ResponseEntity.ok(ApiResponse.<List<AudioData>>error(500, "获取音频数据失败: " + e.getMessage()));
        }
    }

    @GetMapping("/stats/{deviceId}")
    @Operation(summary = "获取设备数据统计", description = "获取指定设备的数据统计信息")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取数据统计成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<DataQueryService.DeviceDataStatsDTO>> getDeviceDataStats(
            @Parameter(description = "设备ID", required = true)
            @PathVariable String deviceId) {
        
        try {
            DataQueryService.DeviceDataStatsDTO stats = dataQueryService.getDeviceDataStats(deviceId);
            return ResponseEntity.ok(ApiResponse.ok(stats, "获取数据统计成功"));
            
        } catch (Exception e) {
            log.error("获取设备数据统计失败", e);
            return ResponseEntity.ok(ApiResponse.<DataQueryService.DeviceDataStatsDTO>error(500, "获取设备数据统计失败: " + e.getMessage()));
        }
    }

    // ========== CSV文件管理接口 ==========

    @GetMapping("/csv/files")
    @Operation(summary = "获取CSV文件列表", description = "获取所有生成的CSV文件列表")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取文件列表成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<List<String>>> getCsvFileList() {
        try {
            List<String> files = realtimeDataService.getCsvFileList();
            return ResponseEntity.ok(ApiResponse.ok(files, "获取CSV文件列表成功"));
            
        } catch (Exception e) {
            log.error("获取CSV文件列表失败", e);
            return ResponseEntity.ok(ApiResponse.<List<String>>error(500, "获取CSV文件列表失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/csv/files/{fileName}")
    @Operation(summary = "删除CSV文件", description = "删除指定的CSV文件")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除文件成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<Boolean>> deleteCsvFile(
            @Parameter(description = "文件名", required = true)
            @PathVariable String fileName) {
        
        try {
            boolean success = realtimeDataService.deleteCsvFile(fileName);
            if (success) {
                return ResponseEntity.ok(ApiResponse.ok(true, "删除CSV文件成功"));
            } else {
                return ResponseEntity.ok(ApiResponse.<Boolean>error(404, "文件不存在或删除失败"));
            }
            
        } catch (Exception e) {
            log.error("删除CSV文件失败", e);
            return ResponseEntity.ok(ApiResponse.<Boolean>error(500, "删除CSV文件失败: " + e.getMessage()));
        }
    }

    @GetMapping("/csv/stats/{deviceId}")
    @Operation(summary = "获取设备CSV数据统计", description = "获取指定设备的CSV数据统计信息")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取统计信息成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<RealtimeDataService.DeviceDataStats>> getDeviceCsvStats(
            @Parameter(description = "设备ID", required = true)
            @PathVariable String deviceId) {
        
        try {
            RealtimeDataService.DeviceDataStats stats = realtimeDataService.getDeviceDataStats(deviceId);
            return ResponseEntity.ok(ApiResponse.ok(stats, "获取设备CSV数据统计成功"));
            
        } catch (Exception e) {
            log.error("获取设备CSV数据统计失败", e);
            return ResponseEntity.ok(ApiResponse.<RealtimeDataService.DeviceDataStats>error(500, "获取设备CSV数据统计失败: " + e.getMessage()));
        }
    }

    // ========== DTO类 ==========

    /**
     * 设备状态DTO
     */
    public record DeviceStatusDTO(
        String deviceId,
        boolean isConnected,
        boolean isReceiving,
        LocalDateTime lastHeartbeat
    ) {}
}
