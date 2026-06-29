package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import wiki.xmum.domain.po.Notification;
import wiki.xmum.domain.vo.NotificationVO;
import wiki.xmum.mapper.NotificationMapper;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationMapper mapper;

    public NotificationService(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    /** 发送一条通知（内部调用）。通知属非关键路径，失败不抛出以免阻断主流程。 */
    public void notify(Long userId, String type, String title, String content, String link, Long refId) {
        if (userId == null) return;
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setLink(link);
            n.setRefId(refId);
            n.setIsRead(0);
            mapper.insert(n);
        } catch (Exception ignore) {
            // 忽略：通知失败不应影响审核/回复等主流程
        }
    }

    /** 最近 30 条通知（倒序）。 */
    public List<NotificationVO> list(Long userId) {
        return mapper.selectList(Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt)
                        .last("LIMIT 30"))
                .stream().map(NotificationVO::from).toList();
    }

    public long unreadCount(Long userId) {
        return mapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    public void markRead(Long userId, Long id) {
        // 仅允许标记本人通知
        mapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .set(Notification::getIsRead, 1)
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    public void markAllRead(Long userId) {
        mapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .set(Notification::getIsRead, 1)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }
}
