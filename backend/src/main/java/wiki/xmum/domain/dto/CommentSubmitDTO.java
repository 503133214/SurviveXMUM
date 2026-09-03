package wiki.xmum.domain.dto;

import lombok.Data;

@Data
public class CommentSubmitDTO {
    private String path;
    private String content;
    /** 回复目标评论 id；为空表示发主楼。 */
    private Long parentId;
}
