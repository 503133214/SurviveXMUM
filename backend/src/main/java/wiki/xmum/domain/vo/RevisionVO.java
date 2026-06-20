package wiki.xmum.domain.vo;

import lombok.Data;
import wiki.xmum.domain.po.WikiRevision;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 投稿列表项（我的投稿 / 管理审核队列）。
 */
@Data
public class RevisionVO {
    private Long id;
    private String type;
    private String status;
    private String targetPath;
    private String title;
    private String categorySlug;
    private String authorEmail;
    private String reviewComment;
    private String createdAt;
    private String reviewedAt;

    private static String iso(java.time.LocalDateTime dt) {
        return dt == null ? null : dt.atOffset(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static RevisionVO from(WikiRevision r) {
        RevisionVO v = new RevisionVO();
        v.id = r.getId();
        v.type = r.getType();
        v.status = r.getStatus();
        v.targetPath = r.getTargetPath();
        v.title = r.getTitle();
        v.categorySlug = r.getCategorySlug();
        v.authorEmail = r.getAuthorEmail();
        v.reviewComment = r.getReviewComment();
        v.createdAt = iso(r.getCreatedAt());
        v.reviewedAt = iso(r.getReviewedAt());
        return v;
    }
}
