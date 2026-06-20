package wiki.xmum.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class RevisionSubmitDTO {
    /** CREATE 新建页 / UPDATE 修改已有页 */
    @NotBlank(message = "缺少投稿类型")
    private String type;

    /** UPDATE 时为已有页路径；CREATE 时可空（由 categorySlug + title 生成） */
    private String path;

    /** 所属分类 slug，如 生活篇 */
    private String categorySlug;

    @NotBlank(message = "请填写标题")
    private String title;

    private String icon;
    private String description;
    private List<String> tags;

    @NotBlank(message = "内容不能为空")
    private String content;
}
