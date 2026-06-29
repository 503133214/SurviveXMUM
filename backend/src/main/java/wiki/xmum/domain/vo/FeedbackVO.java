package wiki.xmum.domain.vo;

import lombok.Data;
import wiki.xmum.domain.po.Feedback;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 反馈项。字段名严格匹配前端 FeedbackPage.vue（createTime / replyTime / 小写 status）。
 * userEmail 仅后台列表回填。
 */
@Data
public class FeedbackVO {
    private Long id;
    private String type;
    private String title;
    private String content;
    private Integer rating;
    private String contact;
    private String status;
    private String reply;
    private String replyTime;
    private String createTime;
    private String userEmail;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static String fmt(java.time.LocalDateTime dt) {
        return dt == null ? null : dt.atOffset(ZoneOffset.ofHours(8)).format(FMT);
    }

    public static FeedbackVO from(Feedback f) {
        FeedbackVO v = new FeedbackVO();
        v.id = f.getId();
        v.type = f.getType();
        v.title = f.getTitle();
        v.content = f.getContent();
        v.rating = f.getRating();
        v.contact = f.getContact();
        v.status = f.getStatus();
        v.reply = f.getReply();
        v.replyTime = fmt(f.getRepliedAt());
        v.createTime = fmt(f.getCreatedAt());
        return v;
    }
}
