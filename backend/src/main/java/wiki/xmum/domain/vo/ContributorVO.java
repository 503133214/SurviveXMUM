package wiki.xmum.domain.vo;

import lombok.Data;

/** 贡献榜条目（公开；不暴露原始邮箱，displayName 为昵称或打码邮箱）。 */
@Data
public class ContributorVO {
    private Long userId;
    private String displayName;
    private String avatar;
    private Integer count;   // 已通过投稿数
}
