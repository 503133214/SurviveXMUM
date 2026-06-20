package wiki.xmum.domain.dto;

import lombok.Data;

@Data
public class ReviewDTO {
    /** 驳回理由（reject 时使用） */
    private String comment;
}
