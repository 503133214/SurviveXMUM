package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("page_comment")
public class PageComment {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long pageId;
    private String path;
    private Long userId;
    /** 直接被回复的那条评论；null 表示主楼。 */
    private Long parentId;
    /** 所在楼层的主楼 id；null 表示自己就是主楼。 */
    private Long rootId;
    private String content;
    /** VISIBLE / HIDDEN（管理员隐藏）/ DELETED（作者自删）。 */
    private String status;
    // 恢复显示时要把理由清空，updateById 默认跳过 null。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String hiddenReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
