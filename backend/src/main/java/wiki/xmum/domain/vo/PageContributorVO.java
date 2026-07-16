package wiki.xmum.domain.vo;

import lombok.Data;

/** 单个公开页面的贡献者；与全站贡献榜口径相互独立。 */
@Data
public class PageContributorVO {
    private Long userId;
    private String displayName;
    private String avatar;
    private Integer count;
}
