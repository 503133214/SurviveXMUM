package wiki.xmum.controller;

import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.dto.UserUpsertDTO;
import wiki.xmum.domain.vo.PageResult;
import wiki.xmum.domain.vo.UserAdminVO;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.UserAdminService;

import java.util.Map;

@RestController
@RequestMapping("/admin/users")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<UserAdminVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeleted,
            @RequestParam(required = false, defaultValue = "1") long page,
            @RequestParam(required = false, defaultValue = "20") long size) {
        return ApiResponse.ok(service.list(keyword, role, status, includeDeleted, page, size));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody UserUpsertDTO dto) {
        return ApiResponse.ok(Map.of("id", service.create(dto)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UserUpsertDTO dto) {
        service.update(id, dto, CurrentUser.get());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.softDelete(id, CurrentUser.get());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        service.restore(id);
        return ApiResponse.ok(null);
    }
}
