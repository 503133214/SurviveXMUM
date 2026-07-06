package wiki.xmum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.PageUpsertDTO;
import wiki.xmum.domain.po.User;
import wiki.xmum.domain.po.WikiCategory;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.vo.PageAdminVO;
import wiki.xmum.domain.vo.PageResult;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.mapper.WikiCategoryMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.security.AuthUser;
import wiki.xmum.util.JsonUtil;
import wiki.xmum.util.MarkdownUtil;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PageAdminService {

    private final WikiPageMapper pageMapper;
    private final WikiCategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final wiki.xmum.mapper.UserFavoriteMapper favoriteMapper;
    private final wiki.xmum.mapper.UserViewHistoryMapper historyMapper;

    public PageAdminService(WikiPageMapper pageMapper, WikiCategoryMapper categoryMapper,
                            UserMapper userMapper, AuditService auditService,
                            wiki.xmum.mapper.UserFavoriteMapper favoriteMapper,
                            wiki.xmum.mapper.UserViewHistoryMapper historyMapper) {
        this.pageMapper = pageMapper;
        this.categoryMapper = categoryMapper;
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.favoriteMapper = favoriteMapper;
        this.historyMapper = historyMapper;
    }

    public PageResult<PageAdminVO> list(String keyword, String category, boolean includeDeleted,
                                        long page, long size) {
        LambdaQueryWrapper<WikiPage> q = Wrappers.<WikiPage>lambdaQuery();
        if (!includeDeleted) q.eq(WikiPage::getDeleted, 0);
        if (category != null && !category.isBlank()) q.eq(WikiPage::getCategorySlug, category);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(WikiPage::getTitle, keyword).or().like(WikiPage::getPath, keyword));
        }
        q.orderByDesc(WikiPage::getUpdatedAt);
        Page<WikiPage> p = pageMapper.selectPage(new Page<>(Math.max(1, page), clampSize(size)), q);

        List<PageAdminVO> rows = p.getRecords().stream().map(PageAdminVO::from).toList();
        List<Long> authorIds = rows.stream().map(PageAdminVO::getAuthorId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (!authorIds.isEmpty()) {
            Map<Long, String> emailById = userMapper.selectBatchIds(authorIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
            rows.forEach(r -> r.setAuthorEmail(emailById.get(r.getAuthorId())));
        }
        return new PageResult<>(rows, p.getTotal(), p.getCurrent(), p.getSize());
    }

    public PageAdminVO get(Long id) {
        WikiPage p = pageMapper.selectById(id);
        if (p == null) throw new BizException(404, "页面不存在");
        PageAdminVO v = PageAdminVO.detail(p);
        if (p.getAuthorId() != null) {
            User a = userMapper.selectById(p.getAuthorId());
            if (a != null) v.setAuthorEmail(a.getEmail());
        }
        return v;
    }

    public Long create(PageUpsertDTO dto, AuthUser actor) {
        String title = wiki.xmum.util.TitleUtil.cleanTitle(dto.getTitle());
        String cat = blankToNull(dto.getCategorySlug());
        String path = cat == null ? title : cat + "/" + title;
        if (path.length() > 380) throw new BizException("标题过长，路径超出限制");
        if (pageMapper.selectCount(Wrappers.<WikiPage>lambdaQuery().eq(WikiPage::getPath, path)) > 0) {
            throw new BizException("已存在同路径页面：" + path);
        }
        WikiPage p = new WikiPage();
        p.setCategorySlug(cat);
        p.setCategoryId(ensureCategory(cat));
        p.setPath(path);
        p.setSlug(path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path);
        p.setTitle(title);
        p.setIcon(blankToNull(dto.getIcon()));
        p.setDescription(blankToNull(dto.getDescription()));
        p.setTags(dto.getTags() == null ? "[]" : JsonUtil.toJson(dto.getTags()));
        p.setHeadings(JsonUtil.toJson(MarkdownUtil.collectHeadings(dto.getContent())));
        p.setContent(dto.getContent());
        p.setSortOrder(dto.getSortOrder() == null ? 999 : dto.getSortOrder());
        p.setStatus(normalizeStatus(dto.getStatus()));
        p.setVersion(0);
        p.setDeleted(0);
        p.setAuthorId(actor.getId());
        p.setViewCount(0);
        pageMapper.insert(p);
        auditService.log("PAGE_CREATE", "PAGE", p.getId(), "创建页面 " + path);
        return p.getId();
    }

    public void update(Long id, PageUpsertDTO dto) {
        WikiPage p = pageMapper.selectById(id);
        if (p == null) throw new BizException(404, "页面不存在");
        int current = p.getVersion() == null ? 0 : p.getVersion();
        if (dto.getVersion() == null || dto.getVersion() != current) {
            throw new BizException(409, "页面已被他人修改（当前版本 v" + current + "），请刷新后重试");
        }
        if (dto.getTitle() != null) p.setTitle(dto.getTitle().trim());
        if (dto.getCategorySlug() != null) {
            String cat = blankToNull(dto.getCategorySlug());
            p.setCategorySlug(cat);
            p.setCategoryId(ensureCategory(cat));
        }
        if (dto.getIcon() != null) p.setIcon(blankToNull(dto.getIcon()));
        if (dto.getDescription() != null) p.setDescription(blankToNull(dto.getDescription()));
        if (dto.getTags() != null) p.setTags(JsonUtil.toJson(dto.getTags()));
        if (dto.getSortOrder() != null) p.setSortOrder(dto.getSortOrder());
        if (dto.getStatus() != null) p.setStatus(normalizeStatus(dto.getStatus()));
        if (dto.getContent() != null) {
            p.setContent(dto.getContent());
            p.setHeadings(JsonUtil.toJson(MarkdownUtil.collectHeadings(dto.getContent())));
        }
        p.setVersion(current + 1);
        pageMapper.updateById(p);
        auditService.log("PAGE_UPDATE", "PAGE", p.getId(), "编辑页面 " + p.getPath() + " → v" + (current + 1));
    }

    public void softDelete(Long id) {
        WikiPage p = pageMapper.selectById(id);
        if (p == null) throw new BizException(404, "页面不存在");
        p.setDeleted(1);
        pageMapper.updateById(p);
        auditService.log("PAGE_DELETE", "PAGE", p.getId(), "删除页面 " + p.getPath());
    }

    public void restore(Long id) {
        WikiPage p = pageMapper.selectById(id);
        if (p == null) throw new BizException(404, "页面不存在");
        p.setDeleted(0);
        pageMapper.updateById(p);
        auditService.log("PAGE_RESTORE", "PAGE", p.getId(), "恢复页面 " + p.getPath());
    }

    /**
     * 彻底删除（仅超管，控制器把关）：只允许对回收站中的页面执行，物理删除并级联清掉
     * 收藏/浏览历史里的引用（防止脏引用点开 404）。投稿历史(wiki_revision)保留作追溯。
     */
    @org.springframework.transaction.annotation.Transactional
    public void purge(Long id) {
        WikiPage p = pageMapper.selectById(id);
        if (p == null) throw new BizException(404, "页面不存在");
        if (p.getDeleted() == null || p.getDeleted() != 1) {
            throw new BizException("请先删除页面（移入回收站）后再彻底删除");
        }
        int favs = favoriteMapper.delete(Wrappers.<wiki.xmum.domain.po.UserFavorite>lambdaQuery()
                .eq(wiki.xmum.domain.po.UserFavorite::getPageId, id));
        int hists = historyMapper.delete(Wrappers.<wiki.xmum.domain.po.UserViewHistory>lambdaQuery()
                .eq(wiki.xmum.domain.po.UserViewHistory::getPageId, id));
        pageMapper.deleteById(id);
        auditService.log("PAGE_PURGE", "PAGE", id,
                "彻底删除页面 " + p.getPath() + "（清理收藏 " + favs + "、历史 " + hists + "）");
    }

    private Long ensureCategory(String slug) {
        if (slug == null || slug.isBlank()) return null;
        WikiCategory c = categoryMapper.selectOne(
                Wrappers.<WikiCategory>lambdaQuery().eq(WikiCategory::getSlug, slug));
        if (c != null) return c.getId();
        WikiCategory nc = new WikiCategory();
        nc.setSlug(slug);
        nc.setLabel(slug);
        nc.setIcon("📁");
        nc.setSortOrder(999);
        categoryMapper.insert(nc);
        return nc.getId();
    }

    private static int clampSize(long size) {
        if (size < 1) return 20;
        return (int) Math.min(size, 100);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "PUBLISHED" : status;
    }
}
