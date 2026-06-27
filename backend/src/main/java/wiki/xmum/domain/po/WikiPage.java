package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wiki_page")
public class WikiPage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long categoryId;
    private String categorySlug;
    private String slug;
    private String path;
    private String title;
    private String icon;
    private String description;
    private String tags;       // JSON 数组字符串
    private String headings;   // JSON 数组字符串
    private String content;    // markdown
    private Integer sortOrder;
    private String status;     // PUBLISHED
    private Integer version;   // 乐观锁版本号
    private Integer deleted;   // 软删除 0/1
    private Long authorId;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
