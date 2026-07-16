package wiki.xmum.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.vo.PublicPageRevisionVO;
import wiki.xmum.service.PageVersionService;

import java.util.List;

/** 公开页面版本历史；只返回当前仍公开页面的脱敏快照。 */
@RestController
@RequestMapping("/wiki/page/revisions")
public class PageRevisionController {

    private final PageVersionService service;

    public PageRevisionController(PageVersionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<PublicPageRevisionVO>> list(@RequestParam String path) {
        return ApiResponse.ok(service.listPublic(path));
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicPageRevisionVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.getPublic(id));
    }
}
