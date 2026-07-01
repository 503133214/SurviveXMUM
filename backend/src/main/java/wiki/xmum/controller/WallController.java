package wiki.xmum.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.po.WallEntry;
import wiki.xmum.service.WallService;

import java.util.List;

/**
 * 赞助 / 致谢墙（公开只读，见 SecurityConfig permitAll）。
 */
@RestController
@RequestMapping("/wall")
public class WallController {

    private final WallService service;

    public WallController(WallService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<WallEntry>> list() {
        return ApiResponse.ok(service.list());
    }
}
