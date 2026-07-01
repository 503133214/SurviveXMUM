package wiki.xmum.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.vo.ManifestVO;
import wiki.xmum.domain.vo.PageDetailVO;
import wiki.xmum.service.WikiContentService;

@RestController
public class WikiController {

    private final WikiContentService contentService;

    public WikiController(WikiContentService contentService) {
        this.contentService = contentService;
    }

    /** 内容清单（侧栏/首页/搜索），替代静态 wiki.data.js。 */
    @GetMapping("/wiki/manifest")
    public ApiResponse<ManifestVO> manifest() {
        return ApiResponse.ok(contentService.getManifest());
    }

    /** 单页详情（含 markdown content）。 */
    @GetMapping("/wiki/page")
    public ApiResponse<PageDetailVO> page(
            @RequestParam(required = false) String path,
            @RequestParam(required = false, defaultValue = "false") boolean track) {
        return ApiResponse.ok(contentService.getPage(path, track));
    }
}
