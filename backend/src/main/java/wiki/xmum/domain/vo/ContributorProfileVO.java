package wiki.xmum.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 贡献者公开主页。 */
@Data
public class ContributorProfileVO {
    private Long userId;
    private String displayName;
    private String avatar;
    private Integer count;
    private Integer createdCount;
    private Integer editedCount;
    private Integer commentCount;
    private List<PageRef> pages;
    /** 已获得的徽章，外加每个家族里尚未达成的下一档（带进度）。 */
    private List<BadgeVO> badges;

    @Data
    @AllArgsConstructor
    public static class PageRef {
        private String title;
        private String path;
    }
}
