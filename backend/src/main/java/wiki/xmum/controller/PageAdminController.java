package wiki.xmum.controller;

import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.domain.dto.PageUpsertDTO;
import wiki.xmum.domain.vo.PageAdminVO;
import wiki.xmum.domain.vo.PageResult;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.PageAdminService;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class PageAdminController {

    private final PageAdminService service;

    public PageAdminController(PageAdminService service) {
        this.service = service;
    }

    @GetMapping("/pages")
    public ApiResponse<PageResult<PageAdminVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeleted,
            @RequestParam(required = false, defaultValue = "1") long page,
            @RequestParam(required = false, defaultValue = "20") long size) {
        return ApiResponse.ok(service.list(keyword, category, includeDeleted, page, size));
    }

    @GetMapping("/page/{id}")
    public ApiResponse<PageAdminVO> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/pages")
    public ApiResponse<Map<String, Object>> create(@RequestBody PageUpsertDTO dto) {
        return ApiResponse.ok(Map.of("id", service.create(dto, CurrentUser.get())));
    }

    @PutMapping("/page/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody PageUpsertDTO dto) {
        service.update(id, dto);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/page/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/page/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        service.restore(id);
        return ApiResponse.ok(null);
    }
}
