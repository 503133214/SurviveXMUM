package wiki.xmum.controller;

import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.vo.ContributorProfileVO;
import wiki.xmum.domain.vo.ContributorVO;
import wiki.xmum.service.ContributorService;

import java.util.List;

/**
 * 贡献榜 / 贡献者主页（公开只读，见 SecurityConfig permitAll）。
 */
@RestController
@RequestMapping("/contributors")
public class ContributorController {

    private final ContributorService service;

    public ContributorController(ContributorService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ContributorVO>> list(@RequestParam(required = false, defaultValue = "50") int limit) {
        return ApiResponse.ok(service.leaderboard(Math.max(1, Math.min(limit, 100))));
    }

    @GetMapping("/{id}")
    public ApiResponse<ContributorProfileVO> profile(@PathVariable Long id) {
        return ApiResponse.ok(service.profile(id));
    }
}
