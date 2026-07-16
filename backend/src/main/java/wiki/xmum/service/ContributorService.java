package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.po.User;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.po.WikiPageVersion;
import wiki.xmum.domain.po.WikiRevision;
import wiki.xmum.domain.vo.ContributorProfileVO;
import wiki.xmum.domain.vo.ContributorVO;
import wiki.xmum.domain.vo.PageContributorVO;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.mapper.WikiPageVersionMapper;
import wiki.xmum.mapper.WikiRevisionMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 贡献榜 / 贡献者主页：派生自 status=APPROVED 的投稿（含新建 + 编辑），无独立表。公开只读。
 */
@Service
public class ContributorService {

    private final WikiRevisionMapper revisionMapper;
    private final WikiPageMapper pageMapper;
    private final UserMapper userMapper;
    private final WikiPageVersionMapper versionMapper;

    public ContributorService(WikiRevisionMapper revisionMapper, WikiPageMapper pageMapper,
                              UserMapper userMapper, WikiPageVersionMapper versionMapper) {
        this.revisionMapper = revisionMapper;
        this.pageMapper = pageMapper;
        this.userMapper = userMapper;
        this.versionMapper = versionMapper;
    }

    public List<ContributorVO> leaderboard(int limit) {
        List<WikiRevision> approved = revisionMapper.selectList(Wrappers.<WikiRevision>lambdaQuery()
                .eq(WikiRevision::getStatus, "APPROVED")
                .isNotNull(WikiRevision::getAuthorId));
        Map<Long, Long> countByAuthor = approved.stream()
                .collect(Collectors.groupingBy(WikiRevision::getAuthorId, Collectors.counting()));

        List<Map.Entry<Long, Long>> top = countByAuthor.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .toList();
        List<Long> ids = top.stream().map(Map.Entry::getKey).toList();
        Map<Long, User> users = ids.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<ContributorVO> rows = new ArrayList<>();
        for (Map.Entry<Long, Long> e : top) {
            User u = users.get(e.getKey());
            if (u == null || (u.getDeleted() != null && u.getDeleted() == 1)) continue;
            ContributorVO v = new ContributorVO();
            v.setUserId(u.getId());
            v.setDisplayName(displayName(u));
            v.setAvatar(u.getAvatar());
            v.setCount(e.getValue().intValue());
            rows.add(v);
        }
        return rows;
    }

    public ContributorProfileVO profile(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BizException(404, "用户不存在");

        List<WikiRevision> approved = revisionMapper.selectList(Wrappers.<WikiRevision>lambdaQuery()
                .eq(WikiRevision::getStatus, "APPROVED")
                .eq(WikiRevision::getAuthorId, userId)
                .orderByDesc(WikiRevision::getReviewedAt));
        LinkedHashSet<String> paths = approved.stream()
                .map(WikiRevision::getTargetPath)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ContributorProfileVO.PageRef> pages = new ArrayList<>();
        if (!paths.isEmpty()) {
            Map<String, WikiPage> pubByPath = pageMapper.selectList(Wrappers.<WikiPage>lambdaQuery()
                            .in(WikiPage::getPath, paths)
                            .eq(WikiPage::getDeleted, 0)
                            .eq(WikiPage::getStatus, "PUBLISHED"))
                    .stream().collect(Collectors.toMap(WikiPage::getPath, p -> p, (a, b) -> a));
            for (String path : paths) {
                WikiPage p = pubByPath.get(path);
                if (p != null) pages.add(new ContributorProfileVO.PageRef(p.getTitle(), p.getPath()));
            }
        }

        ContributorProfileVO vo = new ContributorProfileVO();
        vo.setUserId(u.getId());
        vo.setDisplayName(displayName(u));
        vo.setAvatar(u.getAvatar());
        vo.setCount(approved.size());
        vo.setPages(pages);
        return vo;
    }

    /**
     * 单页贡献者以实际发布快照为主：投稿按 sourceRevisionId 去重，管理员发布动作逐次计数。
     * 旧数据中没有对应快照的已通过投稿会被补入，因而不会因版本快照功能上线较晚而漏人。
     */
    public List<PageContributorVO> pageContributors(String path) {
        WikiPage page = requirePublicPage(path);
        List<WikiPageVersion> versions = versionMapper.selectList(Wrappers.<WikiPageVersion>query()
                // 贡献者聚合不读取每个版本的 MEDIUMTEXT 正文，避免页面历史较长时放大内存与 I/O。
                .select("id", "source_type", "source_revision_id", "author_id", "author_name", "published_at")
                .eq("page_id", page.getId())
                .orderByDesc("published_at"));

        Map<Long, WikiPageVersion> revisionVersions = new HashMap<>();
        List<WikiPageVersion> directVersions = new ArrayList<>();
        Map<Long, WikiPageVersion> migrationVersions = new HashMap<>();
        Set<Long> representedRevisionIds = new HashSet<>();
        for (WikiPageVersion version : versions) {
            String sourceType = version.getSourceType();
            if (isRevisionContribution(sourceType)) {
                Long revisionId = version.getSourceRevisionId();
                if (revisionId != null) {
                    representedRevisionIds.add(revisionId);
                    revisionVersions.merge(revisionId, version, ContributorService::newerVersion);
                }
            } else if ("MIGRATION".equals(sourceType) && version.getAuthorId() != null) {
                migrationVersions.merge(version.getAuthorId(), version, ContributorService::newerVersion);
            } else if (isDirectContribution(version)) {
                directVersions.add(version);
            }
        }

        Map<Long, Contribution> byAuthor = new HashMap<>();
        revisionVersions.values().forEach(version -> addVersionContribution(byAuthor, version));
        directVersions.forEach(version -> addVersionContribution(byAuthor, version));

        List<WikiRevision> legacyApproved = revisionMapper.selectList(Wrappers.<WikiRevision>query()
                .select("id", "page_id", "target_path", "author_id", "author_email",
                        "reviewed_at", "created_at")
                .eq("status", "APPROVED")
                .and(query -> query.eq("page_id", page.getId())
                        .or(nested -> nested.isNull("page_id")
                                .eq("target_path", page.getPath()))));
        for (WikiRevision revision : legacyApproved) {
            Long revisionId = revision.getId();
            if (revisionId == null || !representedRevisionIds.add(revisionId)
                    || revision.getAuthorId() == null) {
                continue;
            }
            LocalDateTime occurredAt = revision.getReviewedAt() == null
                    ? revision.getCreatedAt() : revision.getReviewedAt();
            addContribution(byAuthor, revision.getAuthorId(), occurredAt,
                    maskedEmailOrNull(revision.getAuthorEmail()));
        }

        // MIGRATION 是历史状态基线，不与已存在的投稿/管理发布重复计数；仅补出尚未出现的旧作者。
        for (Map.Entry<Long, WikiPageVersion> entry : migrationVersions.entrySet()) {
            if (!byAuthor.containsKey(entry.getKey())) {
                addVersionContribution(byAuthor, entry.getValue());
            }
        }

        if (page.getAuthorId() != null && !byAuthor.containsKey(page.getAuthorId())) {
            LocalDateTime occurredAt = page.getCreatedAt() == null
                    ? page.getUpdatedAt() : page.getCreatedAt();
            addContribution(byAuthor, page.getAuthorId(), occurredAt, null);
        }
        return resolvePageContributors(byAuthor);
    }

    private WikiPage requirePublicPage(String path) {
        if (path == null || path.isBlank()) throw new BizException(404, "页面不存在");
        WikiPage page = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getPath, path.trim())
                .eq(WikiPage::getDeleted, 0)
                .eq(WikiPage::getStatus, "PUBLISHED"));
        if (page == null || page.getId() == null || !"PUBLISHED".equals(page.getStatus())
                || (page.getDeleted() != null && page.getDeleted() != 0)) {
            throw new BizException(404, "页面不存在");
        }
        return page;
    }

    private List<PageContributorVO> resolvePageContributors(Map<Long, Contribution> byAuthor) {
        if (byAuthor.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectBatchIds(byAuthor.keySet()).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        List<ResolvedPageContributor> resolved = new ArrayList<>();
        for (Map.Entry<Long, Contribution> entry : byAuthor.entrySet()) {
            Long authorId = entry.getKey();
            Contribution contribution = entry.getValue();
            User user = users.get(authorId);
            boolean activeUser = user != null && (user.getDeleted() == null || user.getDeleted() == 0);

            PageContributorVO vo = new PageContributorVO();
            vo.setUserId(activeUser ? authorId : null);
            vo.setDisplayName(activeUser ? pageDisplayName(user) : contribution.fallbackName());
            vo.setAvatar(activeUser ? user.getAvatar() : null);
            vo.setCount(contribution.count());
            resolved.add(new ResolvedPageContributor(vo, contribution.latestAt(), authorId));
        }

        Comparator<ResolvedPageContributor> byCount = Comparator.comparingInt(
                row -> row.contributor().getCount());
        resolved.sort(byCount.reversed()
                .thenComparing(ResolvedPageContributor::latestAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(row -> row.contributor().getDisplayName())
                .thenComparing(ResolvedPageContributor::sourceAuthorId));
        return resolved.stream().map(ResolvedPageContributor::contributor).toList();
    }

    private static boolean isRevisionContribution(String sourceType) {
        return "REVISION_CREATE".equals(sourceType) || "REVISION_UPDATE".equals(sourceType);
    }

    private static boolean isDirectContribution(WikiPageVersion version) {
        return switch (version.getSourceType() == null ? "" : version.getSourceType()) {
            case "ADMIN_CREATE", "ADMIN_UPDATE", "ADMIN_PUBLISH" -> true;
            default -> false;
        };
    }

    private static WikiPageVersion newerVersion(WikiPageVersion first, WikiPageVersion second) {
        LocalDateTime firstAt = first.getPublishedAt();
        LocalDateTime secondAt = second.getPublishedAt();
        if (firstAt == null) return secondAt == null ? first : second;
        if (secondAt == null) return first;
        return secondAt.isAfter(firstAt) ? second : first;
    }

    private static void addVersionContribution(Map<Long, Contribution> byAuthor,
                                               WikiPageVersion version) {
        if (version.getAuthorId() == null) return;
        addContribution(byAuthor, version.getAuthorId(), version.getPublishedAt(),
                version.getAuthorName());
    }

    private static void addContribution(Map<Long, Contribution> byAuthor, Long authorId,
                                        LocalDateTime occurredAt, String fallbackName) {
        byAuthor.computeIfAbsent(authorId, ignored -> new Contribution())
                .add(occurredAt, fallbackName);
    }

    private static final class Contribution {
        private int count;
        private LocalDateTime latestAt;
        private String fallbackName;
        private LocalDateTime fallbackNameAt;

        void add(LocalDateTime occurredAt, String candidateName) {
            count++;
            if (latestAt == null || (occurredAt != null && occurredAt.isAfter(latestAt))) {
                latestAt = occurredAt;
            }
            if (candidateName != null && !candidateName.isBlank()
                    && (fallbackName == null || fallbackNameAt == null
                    || (occurredAt != null && !occurredAt.isBefore(fallbackNameAt)))) {
                fallbackName = candidateName.trim();
                fallbackNameAt = occurredAt;
            }
        }

        int count() {
            return count;
        }

        LocalDateTime latestAt() {
            return latestAt;
        }

        String fallbackName() {
            return fallbackName == null ? "已注销贡献者" : fallbackName;
        }
    }

    private record ResolvedPageContributor(PageContributorVO contributor, LocalDateTime latestAt,
                                           Long sourceAuthorId) {
    }

    private static String displayName(User u) {
        if (u.getNickname() != null && !u.getNickname().isBlank()) return u.getNickname().trim();
        return maskEmail(u.getEmail());
    }

    private static String pageDisplayName(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname().trim();
        }
        String maskedEmail = maskedEmailOrNull(user.getEmail());
        return maskedEmail == null ? "已注销贡献者" : maskedEmail;
    }

    private static String maskedEmailOrNull(String email) {
        if (email == null || email.isBlank()) return null;
        String normalized = email.trim();
        int at = normalized.indexOf('@');
        if (at <= 0) return null;
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at);
        String head = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return head + "***" + domain;
    }

    /** 打码邮箱：ab***@xmu.edu.my（保护隐私，不公开原始邮箱）。 */
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "匿名用户";
        int at = email.indexOf('@');
        if (at <= 0) return "匿名用户";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String head = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return head + "***" + domain;
    }
}
