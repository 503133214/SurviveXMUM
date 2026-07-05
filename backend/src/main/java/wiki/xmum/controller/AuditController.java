package wiki.xmum.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.vo.AuditVO;
import wiki.xmum.domain.vo.PageResult;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.AuditService;

/**
 * 审计日志查询（仅超级管理员）。
 */
@RestController
@RequestMapping("/admin/audit")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    private void requireSuper() {
        if (!"SUPER_ADMIN".equals(CurrentUser.get().getRole())) {
            throw new BizException(403, "仅超级管理员可操作");
        }
    }

    @GetMapping
    public ApiResponse<PageResult<AuditVO>> query(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false, defaultValue = "1") long page,
            @RequestParam(required = false, defaultValue = "20") long size) {
        requireSuper();
        return ApiResponse.ok(service.query(from, to, keyword, action, page, size));
    }
}
