package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.po.UserFavorite;
import wiki.xmum.domain.po.UserViewHistory;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.mapper.UserFavoriteMapper;
import wiki.xmum.mapper.UserViewHistoryMapper;
import wiki.xmum.mapper.WikiPageMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户个人内容：收藏 + 浏览历史。接口契约严格匹配前端 FavoritesPage.vue 已写死的字段名。
 */
@Service
public class UserContentService {

    private final UserFavoriteMapper favoriteMapper;
    private final UserViewHistoryMapper historyMapper;
    private final WikiPageMapper pageMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int HISTORY_LIMIT = 50;

    public UserContentService(UserFavoriteMapper favoriteMapper, UserViewHistoryMapper historyMapper,
                              WikiPageMapper pageMapper) {
        this.favoriteMapper = favoriteMapper;
        this.historyMapper = historyMapper;
        this.pageMapper = pageMapper;
    }

    private static String fmt(LocalDateTime dt) {
        return dt == null ? null : dt.atOffset(ZoneOffset.ofHours(8)).format(FMT);
    }

    private WikiPage findPage(String path) {
        if (path == null || path.isBlank()) return null;
        return pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getPath, path.trim())
                .eq(WikiPage::getDeleted, 0));
    }

    private WikiPage requirePage(String path) {
        WikiPage page = findPage(path);
        if (page == null) throw new BizException(404, "页面不存在");
        return page;
    }

    // ---------- 收藏 ----------

    public List<Map<String, Object>> listFavorites(Long userId) {
        return favoriteMapper.selectList(Wrappers.<UserFavorite>lambdaQuery()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreatedAt))
                .stream().map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", f.getId());
                    m.put("path", "/docs/" + f.getPath());
                    m.put("title", f.getTitle());
                    m.put("description", f.getDescription());
                    m.put("type", "page");
                    m.put("createTime", fmt(f.getCreatedAt()));
                    return m;
                }).toList();
    }

    public Map<String, Object> addFavorite(Long userId, String path) {
        WikiPage page = requirePage(path);
        UserFavorite existing = favoriteMapper.selectOne(Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getPageId, page.getId()));
        if (existing != null) {
            return favResult(existing.getId());
        }
        UserFavorite f = new UserFavorite();
        f.setUserId(userId);
        f.setPageId(page.getId());
        f.setPath(page.getPath());
        f.setTitle(page.getTitle());
        f.setDescription(page.getDescription());
        try {
            favoriteMapper.insert(f);
            return favResult(f.getId());
        } catch (DuplicateKeyException dup) {
            // 并发下唯一键冲突：重查返回既有记录
            UserFavorite again = favoriteMapper.selectOne(Wrappers.<UserFavorite>lambdaQuery()
                    .eq(UserFavorite::getUserId, userId)
                    .eq(UserFavorite::getPageId, page.getId()));
            return favResult(again == null ? null : again.getId());
        }
    }

    private static Map<String, Object> favResult(Long id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("favorited", true);
        m.put("id", id);
        return m;
    }

    public void removeFavorite(Long userId, Long favoriteId) {
        // 仅删除属于当前用户的收藏
        favoriteMapper.delete(Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getId, favoriteId)
                .eq(UserFavorite::getUserId, userId));
    }

    public Map<String, Object> checkFavorite(Long userId, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        WikiPage page = findPage(path);
        if (page == null) {
            m.put("favorited", false);
            return m;
        }
        UserFavorite f = favoriteMapper.selectOne(Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getPageId, page.getId()));
        m.put("favorited", f != null);
        m.put("id", f == null ? null : f.getId());
        return m;
    }

    // ---------- 浏览历史 ----------

    public List<Map<String, Object>> listHistory(Long userId) {
        return historyMapper.selectList(Wrappers.<UserViewHistory>lambdaQuery()
                        .eq(UserViewHistory::getUserId, userId)
                        .orderByDesc(UserViewHistory::getVisitedAt)
                        .last("LIMIT " + HISTORY_LIMIT))
                .stream().map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", h.getId());
                    m.put("path", "/docs/" + h.getPath());
                    m.put("title", h.getTitle());
                    m.put("description", h.getDescription());
                    m.put("type", "page");
                    m.put("visitTime", fmt(h.getVisitedAt()));
                    return m;
                }).toList();
    }

    /** 记录一次浏览：每页一行，复访刷新时间；顺带累加页面浏览量。 */
    public void recordHistory(Long userId, String path) {
        WikiPage page = requirePage(path);
        LocalDateTime now = LocalDateTime.now();
        UserViewHistory existing = historyMapper.selectOne(Wrappers.<UserViewHistory>lambdaQuery()
                .eq(UserViewHistory::getUserId, userId)
                .eq(UserViewHistory::getPageId, page.getId()));
        if (existing != null) {
            existing.setVisitedAt(now);
            existing.setPath(page.getPath());
            existing.setTitle(page.getTitle());
            existing.setDescription(page.getDescription());
            historyMapper.updateById(existing);
        } else {
            UserViewHistory h = new UserViewHistory();
            h.setUserId(userId);
            h.setPageId(page.getId());
            h.setPath(page.getPath());
            h.setTitle(page.getTitle());
            h.setDescription(page.getDescription());
            h.setVisitedAt(now);
            try {
                historyMapper.insert(h);
            } catch (DuplicateKeyException ignore) {
                // 并发：已存在记录，忽略
            }
        }
        // 浏览量自增已统一在 WikiContentService.getPage()（匿名+登录都计一次），此处不再重复累加。
    }

    public void clearHistory(Long userId) {
        historyMapper.delete(Wrappers.<UserViewHistory>lambdaQuery()
                .eq(UserViewHistory::getUserId, userId));
    }
}
