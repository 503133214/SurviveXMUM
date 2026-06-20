package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wiki_revision")
public class WikiRevision {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long pageId;         // 新建页投稿时为空
    private String targetPath;
    private String categorySlug;
    private String title;
    private String icon;
    private String description;
    private String tags;
    private String content;
    private String type;         // CREATE / UPDATE
    private String status;       // PENDING / APPROVED / REJECTED
    private Long authorId;
    private String authorEmail;
    private String reviewComment;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
