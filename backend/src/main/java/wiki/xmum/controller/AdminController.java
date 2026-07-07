package wiki.xmum.controller;

import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.dto.ReviewDTO;
import wiki.xmum.domain.vo.RevisionDetailVO;
import wiki.xmum.domain.vo.RevisionVO;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.RevisionService;

import java.util.List;

/**
 * 管理端（/admin/** 由 SecurityConfig 限定 ROLE_ADMIN）。
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final RevisionService revisionService;

    public AdminController(RevisionService revisionService) {
        this.revisionService = revisionService;
    }

    @GetMapping("/revisions")
    public ApiResponse<List<RevisionVO>> revisions(
            @RequestParam(required = false, defaultValue = "PENDING") String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(revisionService.listByStatus(status, from, to, keyword));
    }

    @GetMapping("/revisions/counts")
    public ApiResponse<java.util.Map<String, Long>> revisionCounts() {
        return ApiResponse.ok(revisionService.counts());
    }

    @GetMapping("/revision/{id}")
    public ApiResponse<RevisionDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(revisionService.detail(id));
    }

    @PostMapping("/revision/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        revisionService.approve(id, CurrentUser.get());
        return ApiResponse.ok(null);
    }

    @PostMapping("/revision/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id, @RequestBody(required = false) ReviewDTO dto) {
        String comment = dto == null ? null : dto.getComment();
        revisionService.reject(id, comment, CurrentUser.get());
        return ApiResponse.ok(null);
    }

    // ---------- 超管改判（已通过/已驳回的管理） ----------

    private void requireSuper() {
        if (!"SUPER_ADMIN".equals(CurrentUser.get().getRole())) {
            throw new wiki.xmum.common.BizException(403, "仅超级管理员可操作");
        }
    }

    /** 改判通过：把已驳回投稿发布上线。 */
    @PostMapping("/revision/{id}/reapprove")
    public ApiResponse<Void> reapprove(@PathVariable Long id) {
        requireSuper();
        revisionService.reapprove(id, CurrentUser.get());
        return ApiResponse.ok(null);
    }

    /** 撤销通过：回滚/下架已发布内容，返回 pageAction 说明页面的处理结果。 */
    @PostMapping("/revision/{id}/revoke")
    public ApiResponse<java.util.Map<String, Object>> revoke(@PathVariable Long id,
                                                             @RequestBody(required = false) ReviewDTO dto) {
        requireSuper();
        String action = revisionService.revoke(id, dto == null ? null : dto.getComment(), CurrentUser.get());
        return ApiResponse.ok(java.util.Map.of("pageAction", action));
    }

    /** 修改驳回原因。 */
    @PutMapping("/revision/{id}/comment")
    public ApiResponse<Void> updateComment(@PathVariable Long id, @RequestBody(required = false) ReviewDTO dto) {
        requireSuper();
        revisionService.updateComment(id, dto == null ? null : dto.getComment(), CurrentUser.get());
        return ApiResponse.ok(null);
    }

    /** 彻底删除投稿记录（非 PENDING）。 */
    @DeleteMapping("/revision/{id}")
    public ApiResponse<Void> purgeRevision(@PathVariable Long id) {
        requireSuper();
        revisionService.purge(id);
        return ApiResponse.ok(null);
    }
}
