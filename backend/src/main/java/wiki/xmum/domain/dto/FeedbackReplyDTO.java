package wiki.xmum.domain.dto;

import lombok.Data;

@Data
public class FeedbackReplyDTO {
    private String reply;
    private String status;   // pending / processing / resolved / rejected
}
