package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_favorite")
public class UserFavorite {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long pageId;
    private String path;          // wiki_page.path 快照
    private String title;
    private String description;
    private LocalDateTime createdAt;
}
