package cn.scut.raputa.controller;

import cn.scut.raputa.dto.DeviceDTO;
import cn.scut.raputa.entity.User;
import cn.scut.raputa.enums.UserRole;
import cn.scut.raputa.response.ApiResponse;
import cn.scut.raputa.service.DeviceService;
import cn.scut.raputa.service.UserService;
import cn.scut.raputa.vo.DeviceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final UserService userService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String storageLocation) {
        Page<DeviceVO> pg = deviceService.page(page, size, id, name, status, storageLocation);
        return ApiResponse.ok(new PageWrap<>(pg.getContent(), pg.getTotalElements()));
    }

    @GetMapping("/locations")
    public ApiResponse<?> locations() {
        return ApiResponse.ok(deviceService.distinctLocations());
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody DeviceDTO dto) {
        return ApiResponse.ok(deviceService.update(id, dto));
    }

    @GetMapping("/registry")
    public ApiResponse<?> registry(@RequestParam(defaultValue = "false") boolean onlineOnly) {
        return ApiResponse.ok(deviceService.registry(onlineOnly));
    }

    @PostMapping("/{id}/force-release")
    public ApiResponse<?> forceRelease(@PathVariable String id, Authentication authentication) {
        boolean released = deviceService.forceRelease(id, resolveRequesterLabel(authentication));
        return ApiResponse.ok(released);
    }

    @PostMapping("/manual-connect")
    public ApiResponse<?> manualConnect(@RequestBody ManualDeviceReq req) {
        return ApiResponse.ok(deviceService.upsertManualDevice(req.ip(), req.name()));
    }

    public record PageWrap<T>(java.util.List<T> items, long total) {
    }

    public record ManualDeviceReq(String ip, String name) {
    }

    private String resolveRequesterLabel(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "其他账户";
        }
        User user = userService.findByUsername(authentication.getName());
        if (user == null) {
            return "账户（" + authentication.getName() + "）";
        }
        if (user.getRole() == UserRole.ADMIN) {
            return "系统管理员（" + user.getUsername() + "）";
        }
        String department = user.getDepartmentName();
        if (department != null && !department.isBlank()) {
            return department.trim() + "（" + user.getUsername() + "）";
        }
        return "科室账户（" + user.getUsername() + "）";
    }
}
