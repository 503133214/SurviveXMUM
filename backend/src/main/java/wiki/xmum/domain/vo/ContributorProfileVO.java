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
    private List<PageRef> pages;

    @Data
    @AllArgsConstructor
    public static class PageRef {
        private String title;
        private String path;
    }
}
