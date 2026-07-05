package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long actorId;
    private String actorEmail;
    private String action;      // REVISION_APPROVE / PAGE_DELETE / USER_UPDATE / BROADCAST …
    private String targetType;  // REVISION / PAGE / USER / FEEDBACK / WALL / CATEGORY / SYSTEM
    private Long targetId;
    private String detail;
    private LocalDateTime createdAt;
}
