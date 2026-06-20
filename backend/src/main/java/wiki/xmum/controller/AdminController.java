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
    public ApiResponse<List<RevisionVO>> revisions(@RequestParam(required = false, defaultValue = "PENDING") String status) {
        return ApiResponse.ok(revisionService.listByStatus(status));
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
}
