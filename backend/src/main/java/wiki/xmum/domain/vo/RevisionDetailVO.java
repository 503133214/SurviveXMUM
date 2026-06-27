package wiki.xmum.domain.vo;

import lombok.Data;
import wiki.xmum.domain.po.WikiRevision;
import wiki.xmum.util.JsonUtil;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 投稿详情（管理员审核用，含提交内容 + 当前线上内容用于对比）。
 */
@Data
public class RevisionDetailVO {
    private Long id;
    private String type;
    private String status;
    private String targetPath;
    private String categorySlug;
    private String title;
    private String icon;
    private String description;
    private List<String> tags;
    private String content;          // 投稿内容
    private String currentContent;   // 当前线上内容（UPDATE 时有；CREATE 时为 null）
    private String authorEmail;
    private String authorNickname;
    private String reviewComment;
    private String reviewerEmail;
    private Integer baseVersion;
    private Integer currentVersion;
    private boolean stale;
    private String createdAt;
    private String reviewedAt;

    private static String iso(java.time.LocalDateTime dt) {
        return dt == null ? null : dt.atOffset(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static RevisionDetailVO from(WikiRevision r, String currentContent) {
        RevisionDetailVO v = new RevisionDetailVO();
        v.id = r.getId();
        v.type = r.getType();
        v.status = r.getStatus();
        v.targetPath = r.getTargetPath();
        v.categorySlug = r.getCategorySlug();
        v.title = r.getTitle();
        v.icon = r.getIcon();
        v.description = r.getDescription();
        v.tags = JsonUtil.toStringList(r.getTags());
        v.content = r.getContent();
        v.currentContent = currentContent;
        v.authorEmail = r.getAuthorEmail();
        v.reviewComment = r.getReviewComment();
        v.baseVersion = r.getBaseVersion();
        v.createdAt = iso(r.getCreatedAt());
        v.reviewedAt = iso(r.getReviewedAt());
        return v;
    }
}
