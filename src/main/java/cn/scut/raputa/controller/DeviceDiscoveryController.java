package cn.scut.raputa.controller;

import cn.scut.raputa.dto.DeviceDiscoveryDTO;
import cn.scut.raputa.dto.DeviceDiscoveryResponseDTO;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.DeviceDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * 设备发现控制器
 * 
 * @author RAPUTA Team
 */
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "设备管理", description = "设备发现、连接等操作")
public class DeviceDiscoveryController {

    private final DeviceDiscoveryService deviceDiscoveryService;

    @PostMapping("/discover")
    @Operation(summary = "开始设备发现", description = "通过UDP广播发现网络中的树莓派设备")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "设备发现成功",
                    content = @Content(schema = @Schema(implementation = DeviceDiscoveryResponseDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public CompletableFuture<ResponseEntity<ApiResponse<DeviceDiscoveryResponseDTO>>> discoverDevice(
            @Parameter(description = "设备发现参数", required = true)
            @RequestBody @Valid DeviceDiscoveryDTO request) {
        
        log.info("开始设备发现，端口: {}, 超时: {}ms", request.getPort(), request.getTimeout());
        
        return deviceDiscoveryService.startDeviceDiscovery(request)
                .thenApply(result -> {
                    if ("ONLINE".equals(result.getStatus())) {
                        return ResponseEntity.ok(ApiResponse.ok(result, "设备发现成功"));
                    } else if ("NOT_FOUND".equals(result.getStatus())) {
                        return ResponseEntity.ok(ApiResponse.ok(result, "未发现设备"));
                    } else {
                        return ResponseEntity.ok(ApiResponse.ok(result, "设备发现失败"));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("设备发现异常", throwable);
                    return ResponseEntity.ok(ApiResponse.<DeviceDiscoveryResponseDTO>error(500, "设备发现过程中发生异常: " + throwable.getMessage()));
                });
    }

    @PostMapping("/discover/stop")
    @Operation(summary = "停止设备发现", description = "停止正在进行的设备发现过程")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "停止成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<String>> stopDeviceDiscovery() {
        try {
            deviceDiscoveryService.stopDeviceDiscovery();
            return ResponseEntity.ok(ApiResponse.ok("设备发现已停止"));
        } catch (Exception e) {
            log.error("停止设备发现失败", e);
            return ResponseEntity.ok(ApiResponse.<String>error(500, "停止设备发现失败: " + e.getMessage()));
        }
    }

    @GetMapping("/discover/status")
    @Operation(summary = "获取设备发现状态", description = "查询当前设备发现是否正在进行")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<ApiResponse<Boolean>> getDiscoveryStatus() {
        try {
            boolean isDiscovering = deviceDiscoveryService.isDiscovering();
            return ResponseEntity.ok(ApiResponse.ok(isDiscovering, 
                isDiscovering ? "设备发现正在进行中" : "设备发现已停止"));
        } catch (Exception e) {
            log.error("获取设备发现状态失败", e);
            return ResponseEntity.ok(ApiResponse.<Boolean>error(500, "获取设备发现状态失败: " + e.getMessage()));
        }
    }

    @PostMapping("/discover/quick")
    @Operation(summary = "快速设备发现", description = "使用默认参数快速发现设备")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "设备发现成功",
                    content = @Content(schema = @Schema(implementation = DeviceDiscoveryResponseDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public CompletableFuture<ResponseEntity<ApiResponse<DeviceDiscoveryResponseDTO>>> quickDiscoverDevice() {
        // 使用默认参数
        DeviceDiscoveryDTO defaultRequest = new DeviceDiscoveryDTO();
        defaultRequest.setPort(6666);
        defaultRequest.setTimeout(5000);
        defaultRequest.setScanInterval(20);
        
        log.info("开始快速设备发现");
        
        return deviceDiscoveryService.startDeviceDiscovery(defaultRequest)
                .thenApply(result -> {
                    if ("ONLINE".equals(result.getStatus())) {
                        return ResponseEntity.ok(ApiResponse.ok(result, "快速设备发现成功"));
                    } else if ("NOT_FOUND".equals(result.getStatus())) {
                        return ResponseEntity.ok(ApiResponse.<DeviceDiscoveryResponseDTO>error(404, "未发现设备，请检查设备状态"));
                    } else {
                        return ResponseEntity.ok(ApiResponse.<DeviceDiscoveryResponseDTO>error(500, "快速设备发现失败"));
                    }
                })
                .exceptionally(throwable -> {
                    log.error("快速设备发现异常", throwable);
                    return ResponseEntity.ok(ApiResponse.<DeviceDiscoveryResponseDTO>error(500, "快速设备发现过程中发生异常: " + throwable.getMessage()));
                });
    }
}
