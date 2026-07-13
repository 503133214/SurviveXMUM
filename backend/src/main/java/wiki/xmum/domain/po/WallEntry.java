package wiki.xmum.domain.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    // updateStrategy=ALWAYS：允许编辑时清空（updateById 默认跳过 null，清空会静默失效）
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String avatar;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String link;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String category;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
