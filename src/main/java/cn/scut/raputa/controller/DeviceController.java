package cn.scut.raputa.controller;

import cn.scut.raputa.dto.DeviceDTO;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.DeviceService;
import cn.scut.raputa.vo.DeviceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String responsible,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String storageLocation) {
        Page<DeviceVO> pg = deviceService.page(page, size, id, name, responsible, status, storageLocation);
        return ApiResponse.ok(new PageWrap<>(pg.getContent(), pg.getTotalElements()));
    }

    @GetMapping("/locations")
    public ApiResponse<?> locations() {
        return ApiResponse.ok(deviceService.distinctLocations());
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody DeviceDTO dto) {
        return ApiResponse.ok(deviceService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody DeviceDTO dto) {
        return ApiResponse.ok(deviceService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        deviceService.delete(id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<?> toggleStatus(@PathVariable String id) {
        return ApiResponse.ok(deviceService.toggleStatus(id));
    }

    public record PageWrap<T>(java.util.List<T> items, long total) {
    }
}