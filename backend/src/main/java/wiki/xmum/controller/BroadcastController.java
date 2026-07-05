package wiki.xmum.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.BroadcastDTO;
import wiki.xmum.domain.po.User;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.AuditService;
import wiki.xmum.service.NotificationService;

import java.util.List;
import java.util.Map;

/**
 * 系统公告广播（仅超级管理员）：给全体在册用户发站内通知。
 */
@RestController
@RequestMapping("/admin/broadcast")
public class BroadcastController {

    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public BroadcastController(UserMapper userMapper, NotificationService notificationService,
                               AuditService auditService) {
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    private void requireSuper() {
        if (!"SUPER_ADMIN".equals(CurrentUser.get().getRole())) {
            throw new BizException(403, "仅超级管理员可操作");
        }
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> send(@RequestBody BroadcastDTO dto) {
        requireSuper();
        String title = dto.getTitle() == null ? "" : dto.getTitle().trim();
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        String link = dto.getLink() == null || dto.getLink().isBlank() ? null : dto.getLink().trim();
        if (title.isEmpty()) throw new BizException("公告标题不能为空");
        if (title.length() > 200) throw new BizException("标题过长（最多 200 字）");
        if (content.isEmpty()) throw new BizException("公告内容不能为空");
        if (content.length() > 500) throw new BizException("内容过长（最多 500 字）");
        if (link != null && link.length() > 400) throw new BizException("链接过长");

        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getDeleted, 0)
                .eq(User::getStatus, "ACTIVE")
                .select(User::getId));
        for (User u : users) {
            notificationService.notify(u.getId(), "ANNOUNCEMENT", title, content, link, null);
        }
        auditService.log("BROADCAST", "SYSTEM", null,
                "发布公告《" + title + "》→ " + users.size() + " 位用户");
        return ApiResponse.ok(Map.of("sent", users.size()));
    }
}
