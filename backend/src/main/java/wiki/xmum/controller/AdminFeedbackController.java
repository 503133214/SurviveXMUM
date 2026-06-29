package wiki.xmum.controller;

import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.dto.FeedbackReplyDTO;
import wiki.xmum.domain.vo.FeedbackVO;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.FeedbackService;

import java.util.List;

/**
 * 反馈管理（/admin/** 由 SecurityConfig 限定 ROLE_ADMIN，超管也可）。
 */
@RestController
@RequestMapping("/admin/feedback")
public class AdminFeedbackController {

    private final FeedbackService service;

    public AdminFeedbackController(FeedbackService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<FeedbackVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.adminList(status, keyword));
    }

    @PostMapping("/{id}/reply")
    public ApiResponse<Void> reply(@PathVariable Long id, @RequestBody(required = false) FeedbackReplyDTO dto) {
        String reply = dto == null ? null : dto.getReply();
        String status = dto == null ? null : dto.getStatus();
        service.reply(id, reply, status, CurrentUser.get());
        return ApiResponse.ok(null);
    }
}
