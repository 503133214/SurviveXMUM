package wiki.xmum.domain.vo;

import lombok.Data;

/** 个人中心「我的讨论」一条；只会返回本人的评论。 */
@Data
public class MyCommentVO {
    private Long id;
    private String path;
    private String pageTitle;
    private String content;
    /** VISIBLE / HIDDEN（被管理员隐藏，附 hiddenReason）。自删的不返回。 */
    private String status;
    private String hiddenReason;
    /** true 表示这是楼中回复，false 表示主楼。 */
    private Boolean reply;
    private String createdAt;
}
