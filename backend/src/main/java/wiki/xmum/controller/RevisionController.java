package wiki.xmum.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.dto.RevisionSubmitDTO;
import wiki.xmum.domain.vo.RevisionVO;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.RevisionService;

import java.util.List;
import java.util.Map;

/**
 * 登录用户投稿（需认证，由 SecurityConfig 的 anyRequest().authenticated() 保护）。
 */
@RestController
@RequestMapping("/wiki")
public class RevisionController {

    private final RevisionService revisionService;

    public RevisionController(RevisionService revisionService) {
        this.revisionService = revisionService;
    }

    @PostMapping("/revision")
    public ApiResponse<Map<String, Object>> submit(@RequestBody @Valid RevisionSubmitDTO dto) {
        Long id = revisionService.submit(dto, CurrentUser.get());
        return ApiResponse.ok(Map.of("id", id));
    }

    @GetMapping("/revision/mine")
    public ApiResponse<List<RevisionVO>> mine() {
        return ApiResponse.ok(revisionService.mine(CurrentUser.get()));
    }
}
