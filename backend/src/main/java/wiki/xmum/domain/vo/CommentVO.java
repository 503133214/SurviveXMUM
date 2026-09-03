package wiki.xmum.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 讨论区里的一条评论（公开）。不暴露原始邮箱，displayName 为昵称或打码邮箱。
 * status=VISIBLE 时 content 为原文；被隐藏/自删但仍有可见回复时，content 是占位提示。
 */
@Data
public class CommentVO {
    private Long id;
    private Long parentId;
    private Long userId;
    private String displayName;
    private String avatar;
    /** 被回复者的显示名，仅回复有值，用于「回复 @某某」。 */
    private String replyToName;
    private String content;
    private String status;
    private String createdAt;
    /** 当前请求者是否为本条作者（前端据此显示删除按钮）。 */
    private Boolean mine = false;
    /** 仅主楼有值：楼中回复，按时间正序。 */
    private List<CommentVO> replies = new ArrayList<>();
}
