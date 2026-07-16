package wiki.xmum.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 公开页面版本。刻意不包含邮箱、审核意见、审核人 id 等管理信息。
 * 详情接口在列表字段之外补充 beforeContent / afterContent，供 Diff 使用。
 */
@Data
public class PublicPageRevisionVO {
    private Long id;
    private Integer version;
    private String title;
    private String authorName;
    private String createdAt;
    private String summary;
    private String sourceType;
    private boolean current;
    private List<String> changedFields;
    private String beforeContent;
    private String afterContent;
}
