package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wiki.xmum.common.BizException;
import wiki.xmum.common.DateRangeFilter;
import wiki.xmum.domain.dto.RevisionSubmitDTO;
import wiki.xmum.domain.po.WikiCategory;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.po.WikiPageVersion;
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
import wiki.xmum.util.TitleUtil;

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
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final PageVersionService pageVersionService;

    public RevisionService(WikiRevisionMapper revisionMapper, WikiPageMapper pageMapper,
                           WikiCategoryMapper categoryMapper, UserMapper userMapper,
                           NotificationService notificationService, AuditService auditService,
                           PageVersionService pageVersionService) {
        this.revisionMapper = revisionMapper;
        this.pageMapper = pageMapper;
        this.categoryMapper = categoryMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.pageVersionService = pageVersionService;
    }

    // ---------- 用户投稿 ----------

    public Long submit(RevisionSubmitDTO dto, AuthUser user) {
        String type = dto.getType();
        if (!"CREATE".equals(type) && !"UPDATE".equals(type)) {
            throw new BizException("非法的投稿类型");
        }
        String title = TitleUtil.cleanTitle(dto.getTitle());

        String icon = dto.getIcon() == null ? null : dto.getIcon().trim();
        if (icon != null && icon.length() > 40) throw new BizException("图标过长，请使用单个 emoji");

        // 简介留空时自动从正文提取首段（编辑页对用户的承诺在这里兑现）
        String description = dto.getDescription() == null ? null : dto.getDescription().trim();
        if (description != null && description.length() > 500) throw new BizException("简介过长（最多 500 字）");
        if (description == null || description.isEmpty()) {
            description = MarkdownUtil.extractSummary(dto.getContent(), 120);
        }

        // 标签：去重去空，限制数量与长度
        List<String> tags = dto.getTags() == null ? List.of() : dto.getTags().stream()
                .filter(java.util.Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .distinct().toList();
        if (tags.size() > 10) throw new BizException("标签最多 10 个");
        if (tags.stream().anyMatch(t -> t.length() > 30)) throw new BizException("单个标签最多 30 字");

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
            // CREATE：路径 = 分类/标题（无分类则用标题）。slug 与标题同规则，
            // 否则 "a#b" / "生活/.." 这类分类会拼出打不开的页面路径。
            String cat = TitleUtil.cleanCategorySlug(dto.getCategorySlug());
            targetPath = cat == null ? title : cat + "/" + title;
            if (targetPath.length() > 380) throw new BizException("标题过长，路径超出限制");
            WikiPage existing = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery().eq(WikiPage::getPath, targetPath));
            if (existing != null) {
                throw new BizException("已存在同路径页面，请改用「编辑」或更换标题");
            }
        }

        WikiRevision rev = new WikiRevision();
        rev.setPageId(pageId);
        rev.setTargetPath(targetPath);
        rev.setCategorySlug(TitleUtil.cleanCategorySlug(dto.getCategorySlug()));
        rev.setTitle(title);
        rev.setIcon(icon);
        rev.setDescription(description);
        rev.setTags(JsonUtil.toJson(tags));
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
        return listByStatus(status, null, null, null);
    }

    /**
     * 审核队列查询，支持按提交日期区间 + 关键词筛选。
     * 日期为闭区间含当天：from 00:00:00 ≤ createdAt < (to+1天) 00:00:00；
     * from/to 必须是今天或更早的有效日期；非法、未来或反向区间返回业务错误。
     * 关键词命中标题 / 目标路径 / 作者邮箱之一。
     */
    public List<RevisionVO> listByStatus(String status, String from, String to, String keyword) {
        var q = Wrappers.<WikiRevision>lambdaQuery();
        if (status != null && !status.isBlank()) {
            q.eq(WikiRevision::getStatus, status);
        }
        DateRangeFilter.Range dates = DateRangeFilter.parse(from, to);
        if (dates.from() != null) q.ge(WikiRevision::getCreatedAt, dates.from().atStartOfDay());
        if (dates.to() != null) q.lt(WikiRevision::getCreatedAt, dates.to().plusDays(1).atStartOfDay());
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(WikiRevision::getTitle, kw)
                    .or().like(WikiRevision::getTargetPath, kw)
                    .or().like(WikiRevision::getAuthorEmail, kw));
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

        WikiPage page = applyToPage(r);

        r.setStatus("APPROVED");
        r.setReviewerId(reviewer.getId());
        r.setReviewedAt(LocalDateTime.now());
        revisionMapper.updateById(r);

        pageVersionService.publish(page,
                "CREATE".equals(r.getType()) ? "REVISION_CREATE" : "REVISION_UPDATE",
                r.getId(), r.getAuthorId(),
                "CREATE".equals(r.getType()) ? "贡献者创建页面" : "贡献者更新页面",
                excluded(r.getAuthorId(), reviewer.getId()));

        notificationService.notify(r.getAuthorId(), "REVISION_APPROVED",
                "投稿已通过",
                "你的投稿《" + r.getTitle() + "》已通过审核并发布。",
                "/docs/" + r.getTargetPath(), r.getId());
        auditService.log("REVISION_APPROVE", "REVISION", r.getId(),
                "通过投稿《" + r.getTitle() + "》(" + r.getTargetPath() + ")");
    }

    /** 把投稿内容落到 wiki_page：无页则新建，有页则覆盖并 version++（approve / reapprove 共用）。 */
    private WikiPage applyToPage(WikiRevision r) {
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
            p.setIcon(blankToNull(r.getIcon()));
            p.setDescription(blankToNull(r.getDescription()));
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
            return p;
        } else {
            page.setCategorySlug(r.getCategorySlug() != null ? r.getCategorySlug() : page.getCategorySlug());
            page.setCategoryId(categoryId != null ? categoryId : page.getCategoryId());
            page.setTitle(r.getTitle());
            // 非 null 即覆盖：空串表示“清空图标”（编辑页始终回填原值，提交的就是期望终态）
            if (r.getIcon() != null) page.setIcon(blankToNull(r.getIcon()));
            if (r.getDescription() != null) page.setDescription(blankToNull(r.getDescription()));
            page.setTags(r.getTags());
            page.setHeadings(JsonUtil.toJson(headings));
            page.setContent(r.getContent());
            page.setStatus("PUBLISHED");
            page.setDeleted(0);
            page.setVersion((page.getVersion() == null ? 0 : page.getVersion()) + 1);
            page.setUpdatedAt(LocalDateTime.now());
            pageMapper.updateById(page);
            return page;
        }
    }

    // ---------- 超管改判（已通过/已驳回的管理） ----------

    /** 改判通过：把此前被驳回的投稿发布上线（仅 REJECTED）。 */
    @Transactional
    public void reapprove(Long id, AuthUser reviewer) {
        WikiRevision r = revisionMapper.selectById(id);
        if (r == null) throw new BizException(404, "投稿不存在");
        if (!"REJECTED".equals(r.getStatus())) throw new BizException("仅可对已驳回的投稿改判通过");

        WikiPage page = applyToPage(r);

        // 用显式 UpdateWrapper：updateById 会跳过 null 字段，无法把 review_comment 清空
        revisionMapper.update(null, Wrappers.<WikiRevision>lambdaUpdate()
                .set(WikiRevision::getStatus, "APPROVED")
                .set(WikiRevision::getReviewComment, null)   // 不再处于驳回状态，清空原因
                .set(WikiRevision::getReviewerId, reviewer.getId())
                .set(WikiRevision::getReviewedAt, LocalDateTime.now())
                .eq(WikiRevision::getId, id));

        pageVersionService.publish(page,
                "CREATE".equals(r.getType()) ? "REVISION_CREATE" : "REVISION_UPDATE",
                r.getId(), r.getAuthorId(),
                "CREATE".equals(r.getType()) ? "贡献者创建页面（复核通过）" : "贡献者更新页面（复核通过）",
                excluded(r.getAuthorId(), reviewer.getId()));

        notificationService.notify(r.getAuthorId(), "REVISION_APPROVED",
                "投稿已通过",
                "你的投稿《" + r.getTitle() + "》经复核已通过并发布。",
                "/docs/" + r.getTargetPath(), r.getId());
        auditService.log("REVISION_REAPPROVE", "REVISION", r.getId(),
                "改判通过投稿《" + r.getTitle() + "》(" + r.getTargetPath() + ")");
    }

    /**
     * 撤销通过（仅 APPROVED，且必须是该路径最新一次通过的投稿，防止回滚冲掉更新的内容）。
     * 页面按可行性三档处理，返回 pageAction：
     * ROLLED_BACK=已回滚到上一 APPROVED 快照；PAGE_DELETED=无更早快照的 CREATE，页面软删（可恢复）；
     * CONTENT_KEPT=无更早快照的 UPDATE（页面来自初始导入），内容保留需手工处理。
     */
    @Transactional
    public String revoke(Long id, String comment, AuthUser reviewer) {
        WikiRevision r = revisionMapper.selectById(id);
        if (r == null) throw new BizException(404, "投稿不存在");
        if (!"APPROVED".equals(r.getStatus())) throw new BizException("仅可撤销已通过的投稿");

        WikiPage currentPage = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getPath, r.getTargetPath()));
        if (currentPage == null || !"PUBLISHED".equals(currentPage.getStatus())
                || currentPage.getDeleted() == null || currentPage.getDeleted() != 0) {
            throw new BizException("页面已被管理员下架，不能通过撤销投稿重新发布");
        }
        WikiPageVersion currentSnapshot = currentPage == null ? null
                : pageVersionService.currentSnapshot(currentPage.getId(), currentPage.getVersion());

        // 有统一快照后，以当前公开快照为准：若管理员或其他投稿已发布新版本，禁止撤销旧投稿覆盖新内容。
        if (currentSnapshot != null && currentSnapshot.getSourceRevisionId() != null
                && !r.getId().equals(currentSnapshot.getSourceRevisionId())) {
            throw new BizException("仅可撤销该页面当前公开版本对应的投稿");
        }
        if (currentSnapshot != null && currentSnapshot.getSourceRevisionId() == null
                && !"MIGRATION".equals(currentSnapshot.getSourceType())) {
            throw new BizException("页面已被管理员更新，不能撤销较早的投稿");
        }

        // 兼容部署前的旧投稿：初始迁移快照无法关联 revision，仍用原审核时间守卫。
        // 按 reviewedAt 倒序，再以雪花 id 倒序作确定性 tiebreak。
        WikiRevision latest = revisionMapper.selectList(Wrappers.<WikiRevision>lambdaQuery()
                        .eq(WikiRevision::getTargetPath, r.getTargetPath())
                        .eq(WikiRevision::getStatus, "APPROVED")
                        .orderByDesc(WikiRevision::getReviewedAt)
                        .orderByDesc(WikiRevision::getId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        if (latest == null || !latest.getId().equals(r.getId())) {
            throw new BizException("仅可撤销该页面最新一次通过的投稿");
        }

        String pageAction;
        WikiPageVersion rollbackOrigin = currentSnapshot;
        if (currentSnapshot != null && "ROLLBACK".equals(currentSnapshot.getSourceType())
                && r.getId().equals(currentSnapshot.getSourceRevisionId())) {
            // 当前内容是一次回滚复制：先找到这份内容真正发布的版本，再回到它之前的状态。
            rollbackOrigin = pageVersionService.latestRevisionSnapshot(
                    currentSnapshot.getPageId(), r.getId(), currentSnapshot.getVersion());
        }
        WikiPageVersion previousSnapshot = rollbackOrigin == null ? null
                : pageVersionService.previousSnapshot(rollbackOrigin.getPageId(), rollbackOrigin.getVersion());
        // CREATE 首次发布→撤销软删→复核再次发布时，紧邻的旧快照仍来自同一 CREATE，不能拿它回滚。
        if (previousSnapshot != null && r.getId().equals(previousSnapshot.getSourceRevisionId())
                && ("REVISION_CREATE".equals(previousSnapshot.getSourceType())
                    || "REVISION_UPDATE".equals(previousSnapshot.getSourceType()))) {
            previousSnapshot = null;
        }
        if (currentSnapshot != null && r.getId().equals(currentSnapshot.getSourceRevisionId())
                && previousSnapshot != null && currentPage != null) {
            restoreSnapshot(currentPage, previousSnapshot);
            pageVersionService.publish(currentPage, "ROLLBACK", previousSnapshot.getSourceRevisionId(),
                    reviewer.getId(),
                    "撤销投稿并恢复上一公开版本", excluded(r.getAuthorId(), reviewer.getId()));
            pageAction = "ROLLED_BACK";
        } else {
            // 旧数据兼容：部署前没有统一快照时，仍从上一份 APPROVED 投稿恢复。
            WikiRevision prev = revisionMapper.selectList(Wrappers.<WikiRevision>lambdaQuery()
                            .eq(WikiRevision::getTargetPath, r.getTargetPath())
                            .eq(WikiRevision::getStatus, "APPROVED")
                            .ne(WikiRevision::getId, r.getId())
                            .orderByDesc(WikiRevision::getReviewedAt)
                            .orderByDesc(WikiRevision::getId)
                            .last("LIMIT 1"))
                    .stream().findFirst().orElse(null);
            if (prev != null) {
                WikiPage rolledBack = applyToPage(prev);
                pageVersionService.publish(rolledBack, "ROLLBACK", prev.getId(), reviewer.getId(),
                        "撤销投稿并恢复上一公开版本", excluded(r.getAuthorId(), reviewer.getId()));
                pageAction = "ROLLED_BACK";
            } else if ("CREATE".equals(r.getType()) && currentPage != null) {
                currentPage.setDeleted(1);   // 内容完全来自该投稿：软删页面（页面管理可恢复）
                pageMapper.updateById(currentPage);
                pageAction = "PAGE_DELETED";
            } else {
                pageAction = "CONTENT_KEPT"; // 页面来自初始导入，无快照可回滚，保留现内容
            }
        }

        r.setStatus("REJECTED");
        r.setReviewComment(comment == null || comment.isBlank() ? "（撤销发布）" : comment.trim());
        r.setReviewerId(reviewer.getId());
        r.setReviewedAt(LocalDateTime.now());
        revisionMapper.updateById(r);

        String reason = (comment != null && !comment.isBlank()) ? "：" + comment.trim() : "。";
        notificationService.notify(r.getAuthorId(), "REVISION_REJECTED",
                "投稿被撤销发布",
                "你的投稿《" + r.getTitle() + "》经复核被撤销发布" + reason,
                "/profile", r.getId());
        auditService.log("REVISION_REVOKE", "REVISION", r.getId(),
                "撤销通过投稿《" + r.getTitle() + "》(" + r.getTargetPath() + ") → " + pageAction + reason);
        return pageAction;
    }

    /** 恢复某一历史快照，但页面版本保持单调递增，回滚本身也成为新的公开版本。 */
    private void restoreSnapshot(WikiPage page, WikiPageVersion snapshot) {
        page.setCategorySlug(snapshot.getCategorySlug());
        page.setCategoryId(ensureCategory(snapshot.getCategorySlug()));
        page.setTitle(snapshot.getTitle());
        page.setIcon(snapshot.getIcon());
        page.setDescription(snapshot.getDescription());
        page.setTags(snapshot.getTags());
        page.setHeadings(snapshot.getHeadings());
        page.setContent(snapshot.getContent());
        page.setStatus("PUBLISHED");
        page.setDeleted(0);
        page.setVersion((page.getVersion() == null ? 0 : page.getVersion()) + 1);
        page.setUpdatedAt(LocalDateTime.now());
        pageMapper.updateById(page);
    }

    /** 修改驳回原因（仅 REJECTED）。 */
    public void updateComment(Long id, String comment, AuthUser reviewer) {
        WikiRevision r = revisionMapper.selectById(id);
        if (r == null) throw new BizException(404, "投稿不存在");
        if (!"REJECTED".equals(r.getStatus())) throw new BizException("仅可修改已驳回投稿的原因");
        // 显式 set：允许清空为 null（updateById 会跳过 null 字段）
        revisionMapper.update(null, Wrappers.<WikiRevision>lambdaUpdate()
                .set(WikiRevision::getReviewComment, comment == null || comment.isBlank() ? null : comment.trim())
                .eq(WikiRevision::getId, id));
        notificationService.notify(r.getAuthorId(), "REVISION_REJECTED",
                "驳回原因已更新",
                "你的投稿《" + r.getTitle() + "》的驳回原因已更新，可到个人中心查看。",
                "/profile", r.getId());
        auditService.log("REVISION_COMMENT_EDIT", "REVISION", r.getId(),
                "修改投稿《" + r.getTitle() + "》的驳回原因");
    }

    /** 彻底删除投稿记录（仅非 PENDING；删除 APPROVED 会减少贡献榜计数并丢失一版回滚快照）。 */
    @Transactional
    public void purge(Long id) {
        WikiRevision r = revisionMapper.selectById(id);
        if (r == null) throw new BizException(404, "投稿不存在");
        if ("PENDING".equals(r.getStatus())) throw new BizException("待审核投稿请先通过或驳回后再删除");
        WikiPage page = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getPath, r.getTargetPath()));
        WikiPageVersion current = page == null ? null
                : pageVersionService.currentSnapshot(page.getId(), page.getVersion());
        if (page != null && current == null) {
            throw new BizException("页面当前状态缺少版本快照，请先发布修正版");
        }
        if (current != null && r.getId().equals(current.getSourceRevisionId())) {
            throw new BizException("该投稿仍是页面当前内容来源，请先撤销或更新页面后再删除");
        }
        int snapshots = pageVersionService.purgeRevisionVersions(id);
        revisionMapper.deleteById(id);
        auditService.log("REVISION_PURGE", "REVISION", id,
                "彻底删除投稿记录《" + r.getTitle() + "》(" + r.getTargetPath() + "，原状态 "
                        + r.getStatus() + "，清理公开快照 " + snapshots + ")");
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

        String reason = (comment != null && !comment.isBlank()) ? "：" + comment.trim() : "。";
        notificationService.notify(r.getAuthorId(), "REVISION_REJECTED",
                "投稿被驳回",
                "你的投稿《" + r.getTitle() + "》未通过审核" + reason,
                "/profile", r.getId());
        auditService.log("REVISION_REJECT", "REVISION", r.getId(),
                "驳回投稿《" + r.getTitle() + "》(" + r.getTargetPath() + ")" + reason);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static Set<Long> excluded(Long... ids) {
        Set<Long> result = new HashSet<>();
        if (ids != null) {
            for (Long id : ids) if (id != null) result.add(id);
        }
        return result;
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
