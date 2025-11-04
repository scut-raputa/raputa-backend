package cn.scut.raputa.controller;

import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.RealtimeDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 * 用于测试实时数据接收功能
 * 
 * @author RAPUTA Team
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "测试接口", description = "测试实时数据接收功能")
public class TestController {

    private final RealtimeDataService realtimeDataService;

    @PostMapping("/connect-device")
    @Operation(summary = "测试连接设备", description = "测试连接到树莓派设备")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testConnectDevice(
            @RequestParam String deviceIp,
            @RequestParam String deviceId) {
        
        log.info("测试连接设备: {} ({})", deviceId, deviceIp);
        
        try {
            // 异步连接设备
            realtimeDataService.startDataReceiving(deviceIp, deviceId)
                    .thenAccept(success -> {
                        if (success) {
                            log.info("设备 {} 连接成功", deviceId);
                        } else {
                            log.error("设备 {} 连接失败", deviceId);
                        }
                    });
            
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", deviceId);
            result.put("deviceIp", deviceIp);
            result.put("message", "连接请求已发送，请检查日志");
            
            return ResponseEntity.ok(ApiResponse.ok(result, "测试连接请求已发送"));
            
        } catch (Exception e) {
            log.error("测试连接设备失败", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(500, "测试连接失败: " + e.getMessage()));
        }
    }

    @GetMapping("/device-status/{deviceId}")
    @Operation(summary = "测试设备状态", description = "测试获取设备连接状态")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testDeviceStatus(@PathVariable String deviceId) {
        
        try {
            boolean isConnected = realtimeDataService.isDeviceConnected(deviceId);
            boolean isReceiving = realtimeDataService.isDeviceReceiving(deviceId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", deviceId);
            result.put("isConnected", isConnected);
            result.put("isReceiving", isReceiving);
            
            return ResponseEntity.ok(ApiResponse.ok(result, "获取设备状态成功"));
            
        } catch (Exception e) {
            log.error("获取设备状态失败", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(500, "获取设备状态失败: " + e.getMessage()));
        }
    }

    @PostMapping("/disconnect-device")
    @Operation(summary = "测试断开设备", description = "测试断开设备连接")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testDisconnectDevice(@RequestParam String deviceId) {
        
        log.info("测试断开设备: {}", deviceId);
        
        try {
            // 异步断开设备
            realtimeDataService.stopDataReceiving(deviceId)
                    .thenAccept(success -> {
                        if (success) {
                            log.info("设备 {} 断开成功", deviceId);
                        } else {
                            log.error("设备 {} 断开失败", deviceId);
                        }
                    });
            
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", deviceId);
            result.put("message", "断开请求已发送，请检查日志");
            
            return ResponseEntity.ok(ApiResponse.ok(result, "测试断开请求已发送"));
            
        } catch (Exception e) {
            log.error("测试断开设备失败", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(500, "测试断开失败: " + e.getMessage()));
        }
    }

    @PostMapping("/test-raw-data")
    @Operation(summary = "测试原始数据接收", description = "测试接收原始数据包")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testRawDataReceiving(
            @RequestParam String deviceIp,
            @RequestParam String deviceId) {
        
        log.info("测试原始数据接收: {} ({})", deviceId, deviceIp);
        
        try {
            // 直接调用processSensorData方法测试
            String testJson = "{\"timestamp\":1703123456,\"timestampus\":123456,\"acc\":{\"x\":1024,\"y\":512,\"z\":256},\"flow\":100}";
            realtimeDataService.processSensorData(testJson, deviceId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("deviceId", deviceId);
            result.put("testData", testJson);
            result.put("message", "测试数据处理成功");
            
            return ResponseEntity.ok(ApiResponse.ok(result, "测试数据处理成功"));
            
        } catch (Exception e) {
            log.error("测试数据处理失败", e);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>error(500, "测试数据处理失败: " + e.getMessage()));
        }
    }
}
