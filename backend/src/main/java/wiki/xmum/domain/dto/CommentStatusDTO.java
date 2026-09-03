package wiki.xmum.domain.dto;

import lombok.Data;

@Data
public class CommentStatusDTO {
    /** VISIBLE 恢复显示 / HIDDEN 隐藏。 */
    private String status;
    private String reason;
}
