package wiki.xmum.domain.vo;

import lombok.Data;
import wiki.xmum.domain.po.Notification;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Data
public class NotificationVO {
    private Long id;
    private String type;
    private String title;
    private String content;
    private String link;
    private boolean read;
    private String createTime;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static NotificationVO from(Notification n) {
        NotificationVO v = new NotificationVO();
        v.id = n.getId();
        v.type = n.getType();
        v.title = n.getTitle();
        v.content = n.getContent();
        v.link = n.getLink();
        v.read = n.getIsRead() != null && n.getIsRead() == 1;
        v.createTime = n.getCreatedAt() == null ? null
                : n.getCreatedAt().atOffset(ZoneOffset.ofHours(8)).format(FMT);
        return v;
    }
}
