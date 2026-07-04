package wiki.xmum.domain.vo;

import lombok.Data;
import wiki.xmum.domain.po.WikiDraft;
import wiki.xmum.util.JsonUtil;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 草稿。列表用 from()（不带正文），详情用 detail()（含正文）。 */
@Data
public class DraftVO {
    private Long id;
    private String type;
    private String targetPath;
    private String categorySlug;
    private String title;
    private String icon;
    private String description;
    private List<String> tags;
    private String content;
    private Integer baseVersion;
    private String updatedAt;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static String fmt(java.time.LocalDateTime dt) {
        return dt == null ? null : dt.atOffset(ZoneOffset.ofHours(8)).format(FMT);
    }

    public static DraftVO from(WikiDraft d) {
        DraftVO v = new DraftVO();
        v.id = d.getId();
        v.type = d.getType();
        v.targetPath = d.getTargetPath();
        v.categorySlug = d.getCategorySlug();
        v.title = d.getTitle();
        v.icon = d.getIcon();
        v.updatedAt = fmt(d.getUpdatedAt());
        return v;
    }

    public static DraftVO detail(WikiDraft d) {
        DraftVO v = from(d);
        v.description = d.getDescription();
        v.tags = JsonUtil.toStringList(d.getTags());
        v.content = d.getContent();
        v.baseVersion = d.getBaseVersion();
        return v;
    }
}
