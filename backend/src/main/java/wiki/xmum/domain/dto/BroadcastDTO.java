package wiki.xmum.domain.dto;

import lombok.Data;

@Data
public class BroadcastDTO {
    private String title;
    private String content;
    private String link;   // 可选，点击通知的跳转路由
}
