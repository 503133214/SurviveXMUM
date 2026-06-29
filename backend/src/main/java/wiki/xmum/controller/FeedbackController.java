package wiki.xmum.controller;

import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.dto.FeedbackSubmitDTO;
import wiki.xmum.domain.vo.FeedbackVO;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.FeedbackService;

import java.util.List;
import java.util.Map;

/**
 * 系统反馈（需登录）。URL / 字段名与前端 FeedbackPage.vue 既有契约保持一致。
 */
@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> submit(@RequestBody FeedbackSubmitDTO dto) {
        Long id = service.submit(dto, CurrentUser.get());
        return ApiResponse.ok(Map.of("id", id));
    }

    @GetMapping("/my")
    public ApiResponse<List<FeedbackVO>> my() {
        return ApiResponse.ok(service.mine(CurrentUser.get().getId()));
    }
}
