package wiki.xmum.domain.vo;

import lombok.Data;

/** 一枚贡献者徽章。全部由已有数据派生，没有独立的授予记录表。 */
@Data
public class BadgeVO {
    private String id;
    private String icon;
    private String name;
    private String description;
    private Boolean earned;
    /** 当前进度与目标值，用于展示「3 / 10」；已获得的徽章 progress 会被裁到 target。 */
    private Integer progress;
    private Integer target;
}
