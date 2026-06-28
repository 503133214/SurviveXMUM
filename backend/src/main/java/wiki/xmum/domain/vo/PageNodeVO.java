package wiki.xmum.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * manifest 树中的「页面」节点，形状对齐前端 wiki.data.js 的 page 节点。
 */
@Data
public class PageNodeVO {
    private final String type = "page";
    private String title;
    private String path;
    private String category;
    private String icon;
    private Integer order;
    private String description;
    private List<String> tags;
    private List<String> headings;
    private Boolean draft = false;
    private String lastUpdated;
}
