package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 一次公开发布后的完整页面快照。记录不可变，只追加不覆盖。 */
@Data
@TableName("wiki_page_version")
public class WikiPageVersion {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long pageId;
    private Integer version;
    private String path;
    private String categorySlug;
    private String title;
    private String icon;
    private String description;
    private String tags;
    private String headings;
    private String content;
    private String sourceType;
    private Long sourceRevisionId;
    private Long authorId;
    private String authorName;
    private String summary;
    private LocalDateTime publishedAt;
}
