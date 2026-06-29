package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feedback")
public class Feedback {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String type;        // bug / feature / ui / other
    private String title;
    private String content;
    private Integer rating;
    private String contact;
    private String status;      // pending / processing / resolved / rejected（小写）
    private String reply;
    private Long reviewerId;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
