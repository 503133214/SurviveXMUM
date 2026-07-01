package wiki.xmum.domain.dto;

import lombok.Data;

@Data
public class WallEntryDTO {
    private String name;
    private String avatar;
    private String description;
    private String link;
    private String category;
    private Integer sortOrder;
}
