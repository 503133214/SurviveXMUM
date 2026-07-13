package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wiki_draft")
public class WikiDraft {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String type;          // CREATE / UPDATE
    // 以下字段草稿续写时都可能被"清空回 null"，updateStrategy=ALWAYS 保证真的写库
    // （updateById 默认跳过 null，会让删掉的标题/简介在草稿里复活）。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String targetPath;    // UPDATE 草稿 = 目标页路径；CREATE 为 null
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String categorySlug;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String title;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String icon;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String tags;          // JSON 数组字符串
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String content;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer baseVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
