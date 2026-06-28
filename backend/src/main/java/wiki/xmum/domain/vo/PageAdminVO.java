package wiki.xmum.domain.vo;

import lombok.Data;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.util.JsonUtil;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
public class PageAdminVO {
    private Long id;
    private String path;
    private String slug;
    private String title;
    private String categorySlug;
    private String icon;
    private String description;
    private List<String> tags;
    private String content;
    private Integer sortOrder;
    private String status;
    private Integer version;
    private boolean deleted;
    private Long authorId;
    private String authorEmail;
    private Integer viewCount;
    private String createdAt;
    private String updatedAt;

    private static String iso(java.time.LocalDateTime dt) {
        return dt == null ? null : dt.atOffset(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static PageAdminVO from(WikiPage p) {
        PageAdminVO v = new PageAdminVO();
        v.id = p.getId();
        v.path = p.getPath();
        v.slug = p.getSlug();
        v.title = p.getTitle();
        v.categorySlug = p.getCategorySlug();
        v.icon = p.getIcon();
        v.description = p.getDescription();
        v.tags = JsonUtil.toStringList(p.getTags());
        v.sortOrder = p.getSortOrder();
        v.status = p.getStatus();
        v.version = p.getVersion() == null ? 0 : p.getVersion();
        v.deleted = p.getDeleted() != null && p.getDeleted() == 1;
        v.authorId = p.getAuthorId();
        v.viewCount = p.getViewCount();
        v.createdAt = iso(p.getCreatedAt());
        v.updatedAt = iso(p.getUpdatedAt());
        return v;
    }

    public static PageAdminVO detail(WikiPage p) {
        PageAdminVO v = from(p);
        v.content = p.getContent();
        return v;
    }
}
