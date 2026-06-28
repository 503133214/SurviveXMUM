package wiki.xmum.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * manifest 树中的「分类」节点，形状对齐前端 wiki.data.js 的 category 节点。
 */
@Data
public class CategoryNodeVO {
    private final String type = "category";
    private String slug;
    private String label;
    private String icon;
    private Integer order;
    private String description;
    private List<Object> children;
}
