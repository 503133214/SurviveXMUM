package wiki.xmum.controller;

import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.vo.NotificationVO;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.NotificationService;

import java.util.List;
import java.util.Map;

/**
 * 站内通知（需登录，由 SecurityConfig 的 anyRequest().authenticated() 保护）。
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<NotificationVO>> list() {
        return ApiResponse.ok(service.list(CurrentUser.get().getId()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unread() {
        return ApiResponse.ok(Map.of("count", service.unreadCount(CurrentUser.get().getId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        service.markRead(CurrentUser.get().getId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll() {
        service.markAllRead(CurrentUser.get().getId());
        return ApiResponse.ok(null);
    }
}
