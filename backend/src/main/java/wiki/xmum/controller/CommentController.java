package wiki.xmum.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.dto.CommentSubmitDTO;
import wiki.xmum.domain.vo.CommentVO;
import wiki.xmum.security.AuthUser;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.CommentService;

import java.util.List;
import java.util.Map;

/**
 * 文档页讨论区。读取公开（SecurityConfig 放行 GET /wiki/comments），发表与删除需登录。
 */
@RestController
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping("/wiki/comments")
    public ApiResponse<List<CommentVO>> list(@RequestParam(required = false) String path) {
        AuthUser viewer = CurrentUser.getOrNull();
        return ApiResponse.ok(service.list(path, viewer == null ? null : viewer.getId()));
    }

    @PostMapping("/comments")
    public ApiResponse<Map<String, Object>> submit(@RequestBody CommentSubmitDTO dto) {
        Long id = service.submit(dto.getPath(), dto.getContent(), dto.getParentId(), CurrentUser.get());
        return ApiResponse.ok(Map.of("id", id));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteOwn(id, CurrentUser.get());
        return ApiResponse.ok(null);
    }
}
