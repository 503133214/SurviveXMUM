package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;        // 收件人
    private String type;        // REVISION_APPROVED / REVISION_REJECTED / FEEDBACK_REPLIED
    private String title;
    private String content;
    private String link;        // 点击跳转的前端路由
    private Long refId;
    private Integer isRead;     // 0/1
    private LocalDateTime createdAt;
}
