package wiki.xmum.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 单页详情（含 markdown 内容），供文档页渲染。
 */
@Data
public class PageDetailVO {
    private Long id;
    private String path;
    private String title;
    private String category;
    private String categorySlug;
    private String icon;
    private String description;
    private List<String> tags;
    private List<String> headings;
    private String content;
    private Integer version;
    private String lastUpdated;
}
