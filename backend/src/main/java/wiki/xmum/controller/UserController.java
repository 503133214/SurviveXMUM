package wiki.xmum.controller;

import org.springframework.web.bind.annotation.*;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.security.CurrentUser;
import wiki.xmum.service.UserContentService;

import java.util.List;
import java.util.Map;

/**
 * 用户个人内容：收藏 + 浏览历史（需登录，由 SecurityConfig 的 anyRequest().authenticated() 保护）。
 * URL / 字段名与前端 FavoritesPage.vue 既有契约保持一致。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserContentService service;

    public UserController(UserContentService service) {
        this.service = service;
    }

    private static String path(Map<String, Object> body) {
        Object p = body == null ? null : body.get("path");
        return p == null ? null : p.toString();
    }

    // ---- 收藏 ----

    @GetMapping("/favorites")
    public ApiResponse<List<Map<String, Object>>> favorites() {
        return ApiResponse.ok(service.listFavorites(CurrentUser.get().getId()));
    }

    @PostMapping("/favorites")
    public ApiResponse<Map<String, Object>> addFavorite(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.addFavorite(CurrentUser.get().getId(), path(body)));
    }

    @PostMapping("/favorites/{id}/remove")
    public ApiResponse<Void> removeFavorite(@PathVariable Long id) {
        service.removeFavorite(CurrentUser.get().getId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/favorites/check")
    public ApiResponse<Map<String, Object>> checkFavorite(@RequestParam(required = false) String path) {
        return ApiResponse.ok(service.checkFavorite(CurrentUser.get().getId(), path));
    }

    // ---- 浏览历史 ----

    @GetMapping("/history")
    public ApiResponse<List<Map<String, Object>>> history() {
        return ApiResponse.ok(service.listHistory(CurrentUser.get().getId()));
    }

    @PostMapping("/history")
    public ApiResponse<Void> recordHistory(@RequestBody(required = false) Map<String, Object> body) {
        service.recordHistory(CurrentUser.get().getId(), path(body));
        return ApiResponse.ok(null);
    }

    @PostMapping("/history/clear")
    public ApiResponse<Void> clearHistory() {
        service.clearHistory(CurrentUser.get().getId());
        return ApiResponse.ok(null);
    }
}
