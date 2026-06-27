package wiki.xmum.domain.vo;

import lombok.Data;
import wiki.xmum.domain.po.User;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Data
public class UserAdminVO {
    private Long id;
    private String email;
    private String nickname;
    private String avatar;
    private String role;
    private String status;
    private boolean deleted;
    private String createdAt;
    private String updatedAt;

    private static String iso(java.time.LocalDateTime dt) {
        return dt == null ? null : dt.atOffset(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static UserAdminVO from(User u) {
        UserAdminVO v = new UserAdminVO();
        v.id = u.getId();
        v.email = u.getEmail();
        v.nickname = u.getNickname();
        v.avatar = u.getAvatar();
        v.role = u.getRole();
        v.status = u.getStatus();
        v.deleted = u.getDeleted() != null && u.getDeleted() == 1;
        v.createdAt = iso(u.getCreatedAt());
        v.updatedAt = iso(u.getUpdatedAt());
        return v;
    }
}
