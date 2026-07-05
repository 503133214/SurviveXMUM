package wiki.xmum.domain.dto;

import lombok.Data;

@Data
public class CategoryUpsertDTO {
    private String slug;      // 仅创建时使用；已固化进页面路径，不可修改
    private String label;
    private String icon;
    private String description;
    private Integer sortOrder;
}
