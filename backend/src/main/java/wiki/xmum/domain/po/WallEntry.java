package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wall_entry")
public class WallEntry {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String avatar;
    private String description;
    private String link;
    private String category;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
