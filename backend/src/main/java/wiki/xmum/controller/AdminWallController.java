package wiki.xmum.controller;

import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.WallEntryDTO;
import wiki.xmum.domain.po.WallEntry;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.WallService;

import java.util.List;
import java.util.Map;

/**
 * 致谢墙管理（/admin/** 由 SecurityConfig 限定 ROLE_ADMIN，再由 requireSuper() 收紧到超级管理员）。
 */
@RestController
@RequestMapping("/admin/wall")
public class AdminWallController {

    private final WallService service;

    public AdminWallController(WallService service) {
        this.service = service;
    }

    private void requireSuper() {
        if (!"SUPER_ADMIN".equals(CurrentUser.get().getRole())) {
            throw new BizException(403, "仅超级管理员可操作");
        }
    }

    @GetMapping
    public ApiResponse<List<WallEntry>> list() {
        requireSuper();
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody WallEntryDTO dto) {
        requireSuper();
        return ApiResponse.ok(Map.of("id", service.create(dto)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody WallEntryDTO dto) {
        requireSuper();
        service.update(id, dto);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        requireSuper();
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
