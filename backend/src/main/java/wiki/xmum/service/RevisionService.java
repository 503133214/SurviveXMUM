package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.RevisionSubmitDTO;
import wiki.xmum.domain.po.WikiCategory;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.po.WikiRevision;
import wiki.xmum.domain.po.User;
import wiki.xmum.domain.vo.RevisionDetailVO;
import wiki.xmum.domain.vo.RevisionVO;
import wiki.xmum.mapper.WikiCategoryMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.mapper.WikiRevisionMapper;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.security.AuthUser;
import wiki.xmum.util.JsonUtil;
import wiki.xmum.util.MarkdownUtil;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RevisionService {

    private final WikiRevisionMapper revisionMapper;
    private final WikiPageMapper pageMapper;
    private final WikiCategoryMapper categoryMapper;
    private final UserMapper userMapper;

    public RevisionService(WikiRevisionMapper revisionMapper, WikiPageMapper pageMapper,
                           WikiCategoryMapper categoryMapper, UserMapper userMapper) {
        this.revisionMapper = revisionMapper;
        this.pageMapper = pageMapper;
        this.categoryMapper = categoryMapper;
        this.userMapper = userMapper;
    }

    // ---------- 用户投稿 ----------

    public Long submit(RevisionSubmitDTO dto, AuthUser user) {
        String type = dto.getType();
        if (!"CREATE".equals(type) && !"UPDATE".equals(type)) {
            throw new BizException("非法的投稿类型");
        }

        String targetPath;
        Long pageId = null;
        Integer baseVersion = null;
        if ("UPDATE".equals(type)) {
            if (dto.getPath() == null || dto.getPath().isBlank()) {
                throw new BizException("缺少要修改的页面路径");
            }
            WikiPage page = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                    .eq(WikiPage::getPath, dto.getPath())
                    .eq(WikiPage::getDeleted, 0));
            if (page == null) {
                throw new BizException("要修改的页面不存在");
            }
            targetPath = page.getPath();
            pageId = page.getId();
            baseVersion = dto.getBaseVersion() != null
                    ? dto.getBaseVersion() : (page.getVersion() == null ? 0 : page.getVersion());
        } else {
            // CREATE：路径 = 分类/标题（无分类则用标题）
            String cat = dto.getCategorySlug();
            String title = dto.getTitle().trim();
            targetPath = (cat == null || cat.isBlank()) ? title : cat + "/" + title;
            WikiPage existing = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery().eq(WikiPage::getPath, targetPath));
            if (existing != null) {
                throw new BizException("已存在同路径页面，请改用「编辑」或更换标题");
            }
        }

        WikiRevision rev = new WikiRevision();
        rev.setPageId(pageId);
        rev.setTargetPath(targetPath);
        rev.setCategorySlug(dto.getCategorySlug());
        rev.setTitle(dto.getTitle().trim());
        rev.setIcon(dto.getIcon());
        rev.setDescription(dto.getDescription());
        rev.setTags(dto.getTags() == null ? "[]" : JsonUtil.toJson(dto.getTags()));
        rev.setContent(dto.getContent());
        rev.setBaseVersion(baseVersion);
        rev.setType(type);
        rev.setStatus("PENDING");
        rev.setAuthorId(user.getId());
        rev.setAuthorEmail(user.getEmail());
        revisionMapper.insert(rev);
        return rev.getId();
    }

    public List<RevisionVO> mine(AuthUser user) {
        List<WikiRevision> list = revisionMapper.selectList(
                Wrappers.<WikiRevision>lambdaQuery()
                        .eq(WikiRevision::getAuthorId, user.getId())
                        .orderByDesc(WikiRevision::getCreatedAt));
        Set<String> approvedPaths = list.stream()
                .filter(r -> "APPROVED".equals(r.getStatus()))
                .map(WikiRevision::getTargetPath)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> publishedPaths = approvedPaths.isEmpty()
                ? Set.of()
                : new HashSet<>(pageMapper.selectList(Wrappers.<WikiPage>lambdaQuery()
                        .in(WikiPage::getPath, approvedPaths)
                        .eq(WikiPage::getDeleted, 0)
                        .eq(WikiPage::getStatus, "PUBLISHED"))
                        .stream().map(WikiPage::getPath).toList());

        return list.stream().map(r -> {
            RevisionVO vo = RevisionVO.from(r);
            if ("APPROVED".equals(r.getStatus()) && !publishedPaths.contains(r.getTargetPath())) {
                vo.setStatus("REMOVED");
            }
            return vo;
        }).toList();
    }

    public RevisionDetailVO mineDetail(Long id, AuthUser user) {
        WikiRevision revision = revisionMapper.selectById(id);
        if (revision == null || !user.getId().equals(revision.getAuthorId())) {
            throw new BizException(404, "投稿不存在");
        }
        return detail(id);
    }

    // ---------- 管理审核 ----------

    public List<RevisionVO> listByStatus(String status) {
        var q = Wrappers.<WikiRevision>lambdaQuery();
        if (status != null && !status.isBlank()) {
            q.eq(WikiRevision::getStatus, status);
        }
        // 待审核按提交时间正序（先到先审），其余倒序
        if ("PENDING".equals(status)) {
            q.orderByAsc(WikiRevision::getCreatedAt);
        } else {
            q.orderByDesc(WikiRevision::getCreatedAt);
        }
        return toVOsWithReviewer(revisionMapper.selectList(q));
    }

    /** 某用户的全部投稿历史（倒序）。 */
    public List<RevisionVO> listByAuthor(Long authorId) {
        return toVOsWithReviewer(revisionMapper.selectList(Wrappers.<WikiRevision>lambdaQuery()
                .eq(WikiRevision::getAuthorId, authorId)
                .orderByDesc(WikiRevision::getCreatedAt)));
    }

    /** 各状态投稿数量，供前端 tab 角标。 */
    public java.util.Map<String, Long> counts() {
        java.util.Map<String, Long> m = new java.util.LinkedHashMap<>();
        for (String s : List.of("PENDING", "APPROVED", "REJECTED")) {
            m.put(s, revisionMapper.selectCount(Wrappers.<WikiRevision>lambdaQuery().eq(WikiRevision::getStatus, s)));
        }
        return m;
    }

    /** 列表项转 VO，并按 reviewerId 批量回填审核人邮箱。 */
    private List<RevisionVO> toVOsWithReviewer(List<WikiRevision> list) {
        List<RevisionVO> rows = list.stream().map(RevisionVO::from).toList();
        List<Long> reviewerIds = list.stream().map(WikiRevision::getReviewerId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (!reviewerIds.isEmpty()) {
            var emailById = userMapper.selectBatchIds(reviewerIds).stream()
                    .collect(java.util.stream.Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
            for (int i = 0; i < rows.size(); i++) {
                Long rid = list.get(i).getReviewerId();
                if (rid != null) rows.get(i).setReviewerEmail(emailById.get(rid));
            }
        }
        return rows;
    }

    public RevisionDetailVO detail(Long id) {
        WikiRevision r = revisionMapper.selectById(id);
        if (r == null) throw new BizException(404, "投稿不存在");
        WikiPage page = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getPath, r.getTargetPath())
                .eq(WikiPage::getDeleted, 0));
        String currentContent = page == null ? null : page.getContent();
        Integer currentVersion = page == null ? null
                : (page.getVersion() == null ? 0 : page.getVersion());
        RevisionDetailVO vo = RevisionDetailVO.from(r, currentContent);
        vo.setCurrentVersion(currentVersion);
        vo.setStale(r.getBaseVersion() != null && currentVersion != null
                && !r.getBaseVersion().equals(currentVersion));
        if (r.getAuthorId() != null) {
            User author = userMapper.selectById(r.getAuthorId());
            if (author != null) vo.setAuthorNickname(author.getNickname());
        }
        if (r.getReviewerId() != null) {
            User reviewer = userMapper.selectById(r.getReviewerId());
            if (reviewer != null) vo.setReviewerEmail(reviewer.getEmail());
        }
        return vo;
    }

    @Transactional
    public void approve(Long id, AuthUser reviewer) {
        WikiRevision r = revisionMapper.selectById(id);
        if (r == null) throw new BizException(404, "投稿不存在");
        if (!"PENDING".equals(r.getStatus())) throw new BizException("该投稿已被处理");

        WikiPage page = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery().eq(WikiPage::getPath, r.getTargetPath()));
        Long categoryId = ensureCategory(r.getCategorySlug());
        List<String> headings = MarkdownUtil.collectHeadings(r.getContent());

        if (page == null) {
            // 新建页（CREATE，或 UPDATE 但原页已被删）
            WikiPage p = new WikiPage();
            p.setCategorySlug(r.getCategorySlug());
            p.setCategoryId(categoryId);
            String path = r.getTargetPath();
            p.setPath(path);
            p.setSlug(path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path);
            p.setTitle(r.getTitle());
            p.setIcon(r.getIcon());
            p.setDescription(r.getDescription());
            p.setTags(r.getTags());
            p.setHeadings(JsonUtil.toJson(headings));
            p.setContent(r.getContent());
            p.setSortOrder(999);
            p.setStatus("PUBLISHED");
            p.setVersion(0);
            p.setDeleted(0);
            p.setAuthorId(r.getAuthorId());
            p.setViewCount(0);
            pageMapper.insert(p);
        } else {
            page.setCategorySlug(r.getCategorySlug() != null ? r.getCategorySlug() : page.getCategorySlug());
            page.setCategoryId(categoryId != null ? categoryId : page.getCategoryId());
            page.setTitle(r.getTitle());
            if (r.getIcon() != null) page.setIcon(r.getIcon());
            if (r.getDescription() != null) page.setDescription(r.getDescription());
            page.setTags(r.getTags());
            page.setHeadings(JsonUtil.toJson(headings));
            page.setContent(r.getContent());
            page.setDeleted(0);
            page.setVersion((page.getVersion() == null ? 0 : page.getVersion()) + 1);
            pageMapper.updateById(page);
        }

        r.setStatus("APPROVED");
        r.setReviewerId(reviewer.getId());
        r.setReviewedAt(LocalDateTime.now());
        revisionMapper.updateById(r);
    }

    public void reject(Long id, String comment, AuthUser reviewer) {
        WikiRevision r = revisionMapper.selectById(id);
        if (r == null) throw new BizException(404, "投稿不存在");
        if (!"PENDING".equals(r.getStatus())) throw new BizException("该投稿已被处理");
        r.setStatus("REJECTED");
        r.setReviewComment(comment);
        r.setReviewerId(reviewer.getId());
        r.setReviewedAt(LocalDateTime.now());
        revisionMapper.updateById(r);
    }

    /** 确保分类存在，返回 categoryId；无 slug 返回 null。 */
    private Long ensureCategory(String slug) {
        if (slug == null || slug.isBlank()) return null;
        WikiCategory c = categoryMapper.selectOne(Wrappers.<WikiCategory>lambdaQuery().eq(WikiCategory::getSlug, slug));
        if (c != null) return c.getId();
        WikiCategory nc = new WikiCategory();
        nc.setSlug(slug);
        nc.setLabel(slug);
        nc.setIcon("📁");
        nc.setSortOrder(999);
        categoryMapper.insert(nc);
        return nc.getId();
    }
}
