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

    public PageAdminService(WikiPageMapper pageMapper, WikiCategoryMapper categoryMapper, UserMapper userMapper) {
        this.pageMapper = pageMapper;
        this.categoryMapper = categoryMapper;
        this.userMapper = userMapper;
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
        String title = require(dto.getTitle(), "标题不能为空");
        String cat = blankToNull(dto.getCategorySlug());
        String path = cat == null ? title : cat + "/" + title;
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
    }

    public void softDelete(Long id) {
        WikiPage p = pageMapper.selectById(id);
        if (p == null) throw new BizException(404, "页面不存在");
        p.setDeleted(1);
        pageMapper.updateById(p);
    }

    public void restore(Long id) {
        WikiPage p = pageMapper.selectById(id);
        if (p == null) throw new BizException(404, "页面不存在");
        p.setDeleted(0);
        pageMapper.updateById(p);
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

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) throw new BizException(message);
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "PUBLISHED" : status;
    }
}
