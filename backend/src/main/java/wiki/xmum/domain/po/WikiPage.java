package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    // updateStrategy=ALWAYS：这两个字段允许被"清空"（置 null）。
    // updateById 默认跳过 null 字段，会让清空图标/简介静默失效；
    // 所有更新路径都是先 selectById 再整体更新，ALWAYS 不会误清其它值。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String icon;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
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
