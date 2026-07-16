package wiki.xmum.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.common.BizException;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.PageVersionAdminService;

/** 公开版本的紧急治理接口。 */
@RestController
@RequestMapping("/admin/page-version")
public class PageVersionAdminController {

    private final PageVersionAdminService service;

    public PageVersionAdminController(PageVersionAdminService service) {
        this.service = service;
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> purge(@PathVariable Long id) {
        if (!"SUPER_ADMIN".equals(CurrentUser.get().getRole())) {
            throw new BizException(403, "仅超级管理员可操作");
        }
        service.purge(id);
        return ApiResponse.ok(null);
    }
}
