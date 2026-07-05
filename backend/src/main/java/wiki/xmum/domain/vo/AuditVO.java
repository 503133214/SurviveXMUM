package wiki.xmum.domain.vo;

import lombok.Data;
import wiki.xmum.domain.po.AuditLog;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Data
public class AuditVO {
    private Long id;
    private String actorEmail;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private String createdAt;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static AuditVO from(AuditLog a) {
        AuditVO v = new AuditVO();
        v.id = a.getId();
        v.actorEmail = a.getActorEmail();
        v.action = a.getAction();
        v.targetType = a.getTargetType();
        v.targetId = a.getTargetId();
        v.detail = a.getDetail();
        v.createdAt = a.getCreatedAt() == null ? null
                : a.getCreatedAt().atOffset(ZoneOffset.ofHours(8)).format(FMT);
        return v;
    }
}
