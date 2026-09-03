package wiki.xmum.domain.vo;

import lombok.Data;

/** 后台评论管理行；仅管理员可见，含作者邮箱。 */
@Data
public class AdminCommentVO {
    private Long id;
    private Long parentId;
    private String path;
    private String pageTitle;
    private Long userId;
    private String userEmail;
    private String displayName;
    private String content;
    private String status;
    private String hiddenReason;
    private String createdAt;
}
