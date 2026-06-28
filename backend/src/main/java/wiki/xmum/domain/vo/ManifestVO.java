package wiki.xmum.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class ManifestVO {
    private List<Object> tree;
    private List<PageNodeVO> pages;
    private PageNodeVO home;
    private String generatedAt;
}
