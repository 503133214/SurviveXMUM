package wiki.xmum.domain.dto;

import lombok.Data;

@Data
public class FeedbackSubmitDTO {
    private String type;     // bug / feature / ui / other
    private String title;
    private String content;
    private Integer rating;
    private String contact;
}
