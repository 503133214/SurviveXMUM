package wiki.xmum.controller;

import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.dto.UserUpsertDTO;
import wiki.xmum.domain.vo.PageResult;
import wiki.xmum.domain.vo.RevisionVO;
import wiki.xmum.domain.vo.UserAdminVO;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.RevisionService;
import wiki.xmum.service.UserAdminService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/users")
public class UserAdminController {

    private final UserAdminService service;
    private final RevisionService revisionService;

    public UserAdminController(UserAdminService service, RevisionService revisionService) {
        this.service = service;
        this.revisionService = revisionService;
    }

    /** 用户管理整段仅超级管理员（控制器层兜底，不依赖 URL 匹配规则）。 */
    private void requireSuper() {
        if (!"SUPER_ADMIN".equals(CurrentUser.get().getRole())) {
            throw new wiki.xmum.common.BizException(403, "需要超级管理员权限");
        }
    }

    @GetMapping
    public ApiResponse<PageResult<UserAdminVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeleted,
            @RequestParam(required = false, defaultValue = "1") long page,
            @RequestParam(required = false, defaultValue = "20") long size) {
        requireSuper();
        return ApiResponse.ok(service.list(keyword, role, status, includeDeleted, page, size));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody UserUpsertDTO dto) {
        requireSuper();
        return ApiResponse.ok(Map.of("id", service.create(dto)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UserUpsertDTO dto) {
        requireSuper();
        service.update(id, dto, CurrentUser.get());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        requireSuper();
        service.softDelete(id, CurrentUser.get());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        requireSuper();
        service.restore(id);
        return ApiResponse.ok(null);
    }

    /** 某用户的投稿历史（仅超管，由 SecurityConfig 限定 /admin/users/**）。 */
    @GetMapping("/{id}/revisions")
    public ApiResponse<List<RevisionVO>> userRevisions(@PathVariable Long id) {
        requireSuper();
        return ApiResponse.ok(revisionService.listByAuthor(id));
    }
}
