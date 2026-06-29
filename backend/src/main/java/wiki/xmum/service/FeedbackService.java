package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.FeedbackSubmitDTO;
import wiki.xmum.domain.po.Feedback;
import wiki.xmum.domain.po.User;
import wiki.xmum.domain.vo.FeedbackVO;
import wiki.xmum.mapper.FeedbackMapper;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.security.AuthUser;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FeedbackService {

    private static final List<String> TYPES = List.of("bug", "feature", "ui", "other");
    private static final List<String> STATUSES = List.of("pending", "processing", "resolved", "rejected");

    private final FeedbackMapper mapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public FeedbackService(FeedbackMapper mapper, UserMapper userMapper, NotificationService notificationService) {
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    public Long submit(FeedbackSubmitDTO dto, AuthUser user) {
        String title = dto.getTitle() == null ? "" : dto.getTitle().trim();
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (title.isEmpty()) throw new BizException("反馈标题不能为空");
        if (title.length() > 100) throw new BizException("标题过长（最多 100 字）");
        if (content.isEmpty()) throw new BizException("详细描述不能为空");
        if (content.length() > 1000) throw new BizException("描述过长（最多 1000 字）");
        String type = TYPES.contains(dto.getType()) ? dto.getType() : "other";
        int rating = dto.getRating() == null ? 0 : Math.max(0, Math.min(5, dto.getRating()));

        Feedback f = new Feedback();
        f.setUserId(user.getId());
        f.setType(type);
        f.setTitle(title);
        f.setContent(content);
        f.setRating(rating);
        f.setContact(dto.getContact() == null ? null : dto.getContact().trim());
        f.setStatus("pending");
        mapper.insert(f);
        return f.getId();
    }

    public List<FeedbackVO> mine(Long userId) {
        return mapper.selectList(Wrappers.<Feedback>lambdaQuery()
                        .eq(Feedback::getUserId, userId)
                        .orderByDesc(Feedback::getCreatedAt))
                .stream().map(FeedbackVO::from).toList();
    }

    public List<FeedbackVO> adminList(String status, String keyword) {
        var q = Wrappers.<Feedback>lambdaQuery();
        if (status != null && !status.isBlank()) q.eq(Feedback::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(Feedback::getTitle, kw).or().like(Feedback::getContent, kw));
        }
        q.orderByDesc(Feedback::getCreatedAt);
        List<Feedback> list = mapper.selectList(q);
        List<FeedbackVO> rows = list.stream().map(FeedbackVO::from).toList();
        List<Long> userIds = list.stream().map(Feedback::getUserId).filter(Objects::nonNull).distinct().toList();
        if (!userIds.isEmpty()) {
            Map<Long, String> emailById = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).setUserEmail(emailById.get(list.get(i).getUserId()));
            }
        }
        return rows;
    }

    @Transactional
    public void reply(Long id, String reply, String status, AuthUser admin) {
        Feedback f = mapper.selectById(id);
        if (f == null) throw new BizException(404, "反馈不存在");
        String st = STATUSES.contains(status) ? status : "resolved";
        f.setReply(reply == null ? null : reply.trim());
        f.setStatus(st);
        f.setReviewerId(admin.getId());
        f.setRepliedAt(LocalDateTime.now());
        mapper.updateById(f);

        notificationService.notify(f.getUserId(), "FEEDBACK_REPLIED",
                "反馈已回复",
                "你的反馈《" + f.getTitle() + "》已收到官方回复。",
                "/feedback", f.getId());
    }
}
