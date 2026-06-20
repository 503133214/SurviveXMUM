package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String email;
    private String password;
    private String nickname;
    private String avatar;
    private String role;      // USER / ADMIN
    private String status;    // ACTIVE / BANNED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
