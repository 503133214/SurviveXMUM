package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private String targetPath;    // UPDATE 草稿 = 目标页路径；CREATE 为 null
    private String categorySlug;
    private String title;
    private String icon;
    private String description;
    private String tags;          // JSON 数组字符串
    private String content;
    private Integer baseVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
