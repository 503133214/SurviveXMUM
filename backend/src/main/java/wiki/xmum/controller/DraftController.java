package wiki.xmum.controller;

import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.dto.DraftSaveDTO;
import wiki.xmum.domain.vo.DraftVO;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.DraftService;

import java.util.List;
import java.util.Map;

/**
 * 写文章草稿（需登录，由 anyRequest().authenticated() 保护）。
 */
@RestController
@RequestMapping("/wiki/drafts")
public class DraftController {

    private final DraftService service;

    public DraftController(DraftService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> save(@RequestBody DraftSaveDTO dto) {
        return ApiResponse.ok(service.save(dto, CurrentUser.get().getId()));
    }

    @GetMapping
    public ApiResponse<List<DraftVO>> mine() {
        return ApiResponse.ok(service.listMine(CurrentUser.get().getId()));
    }

    /** 注意先于 /{id} 声明的固定路径。 */
    @GetMapping("/by-path")
    public ApiResponse<DraftVO> byPath(@RequestParam String path) {
        return ApiResponse.ok(service.getByPath(path, CurrentUser.get().getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<DraftVO> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id, CurrentUser.get().getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id, CurrentUser.get().getId());
        return ApiResponse.ok(null);
    }
}
