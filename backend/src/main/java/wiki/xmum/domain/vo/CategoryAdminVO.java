package wiki.xmum.domain.vo;

import lombok.Data;
import wiki.xmum.domain.po.WikiCategory;

@Data
public class CategoryAdminVO {
    private Long id;
    private String slug;
    private String label;
    private String icon;
    private String description;
    private Integer sortOrder;
    private Long pageCount;      // 该分类下页面数（含回收站，删除守卫同口径）

    public static CategoryAdminVO from(WikiCategory c, long pageCount) {
        CategoryAdminVO v = new CategoryAdminVO();
        v.id = c.getId();
        v.slug = c.getSlug();
        v.label = c.getLabel();
        v.icon = c.getIcon();
        v.description = c.getDescription();
        v.sortOrder = c.getSortOrder();
        v.pageCount = pageCount;
        return v;
    }
}
