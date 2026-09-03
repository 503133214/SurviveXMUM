package wiki.xmum.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.CommentStatusDTO;
import wiki.xmum.domain.vo.AdminCommentVO;
import wiki.xmum.domain.vo.PageResult;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.CommentService;

/**
 * 评论管理（/admin/** 由 SecurityConfig 限定 ROLE_ADMIN；彻底删除另行限定超管）。
 */
@RestController
@RequestMapping("/admin/comments")
public class AdminCommentController {

    private final CommentService service;

    public AdminCommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminCommentVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(service.adminList(status, path, keyword, page, size));
    }

    /** 隐藏 / 恢复显示。 */
    @PostMapping("/{id}/status")
    public ApiResponse<Void> setStatus(@PathVariable Long id, @RequestBody CommentStatusDTO dto) {
        service.adminSetStatus(id, dto == null ? null : dto.getStatus(),
                dto == null ? null : dto.getReason(), CurrentUser.get());
        return ApiResponse.ok(null);
    }

    /** 彻底删除（不可恢复），仅超级管理员。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> purge(@PathVariable Long id) {
        if (!"SUPER_ADMIN".equals(CurrentUser.get().getRole())) {
            throw new BizException(403, "仅超级管理员可操作");
        }
        service.purge(id);
        return ApiResponse.ok(null);
    }
}
