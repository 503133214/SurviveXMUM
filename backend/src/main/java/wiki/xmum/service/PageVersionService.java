package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.po.User;
import wiki.xmum.domain.po.UserFavorite;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.po.WikiPageVersion;
import wiki.xmum.domain.vo.PublicPageRevisionVO;
import wiki.xmum.mapper.UserFavoriteMapper;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.mapper.WikiPageVersionMapper;
import wiki.xmum.util.JsonUtil;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 统一页面公开版本：落完整快照、向关注者广播，以及提供脱敏后的公开历史。
 */
@Service
public class PageVersionService {

    private final WikiPageVersionMapper versionMapper;
    private final WikiPageMapper pageMapper;
    private final UserMapper userMapper;
    private final UserFavoriteMapper favoriteMapper;
    private final NotificationService notificationService;

    public PageVersionService(WikiPageVersionMapper versionMapper, WikiPageMapper pageMapper,
                              UserMapper userMapper, UserFavoriteMapper favoriteMapper,
                              NotificationService notificationService) {
        this.versionMapper = versionMapper;
        this.pageMapper = pageMapper;
        this.userMapper = userMapper;
        this.favoriteMapper = favoriteMapper;
        this.notificationService = notificationService;
    }

    /**
     * 为当前公开页追加不可变快照，并通知选择关注更新的收藏者。
     * 非公开/已删除页面不会产生公开快照。
     */
    public WikiPageVersion publish(WikiPage page, String sourceType, Long sourceRevisionId,
                                   Long authorId, String summary, Collection<Long> excludedUserIds) {
        if (!isPublic(page)) return null;

        WikiPageVersion snapshot = new WikiPageVersion();
        snapshot.setPageId(page.getId());
        snapshot.setVersion(normalizeVersion(page.getVersion()));
        snapshot.setPath(page.getPath());
        snapshot.setCategorySlug(page.getCategorySlug());
        snapshot.setTitle(page.getTitle());
        snapshot.setIcon(page.getIcon());
        snapshot.setDescription(page.getDescription());
        snapshot.setTags(page.getTags());
        snapshot.setHeadings(page.getHeadings());
        snapshot.setContent(page.getContent());
        snapshot.setSourceType(sourceType);
        snapshot.setSourceRevisionId(sourceRevisionId);
        snapshot.setAuthorId(authorId);
        snapshot.setAuthorName(resolveAuthorName(sourceType, authorId));
        snapshot.setSummary(summary);
        snapshot.setPublishedAt(LocalDateTime.now());
        // 快照是版本完整性的关键路径；写入失败应让外层发布事务一起回滚。
        versionMapper.insert(snapshot);

        notifyFollowers(page, snapshot.getId(), excludedUserIds);
        return snapshot;
    }

    private void notifyFollowers(WikiPage page, Long snapshotId, Collection<Long> excludedUserIds) {
        Set<Long> excluded = new HashSet<>();
        if (excludedUserIds != null) {
            excludedUserIds.stream().filter(Objects::nonNull).forEach(excluded::add);
        }
        favoriteMapper.selectList(Wrappers.<UserFavorite>lambdaQuery()
                        .eq(UserFavorite::getPageId, page.getId())
                        .eq(UserFavorite::getNotifyUpdates, 1))
                .stream()
                .map(UserFavorite::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .filter(userId -> !excluded.contains(userId))
                .forEach(userId -> notificationService.notify(userId, "PAGE_UPDATED",
                        "关注的页面已更新",
                        "你关注的《" + page.getTitle() + "》已有新版本。",
                        "/docs/" + page.getPath(), snapshotId));
    }

    /** 当前页面 version 对应的快照；用于安全撤销投稿。 */
    public WikiPageVersion currentSnapshot(Long pageId, Integer version) {
        if (pageId == null) return null;
        return versionMapper.selectOne(Wrappers.<WikiPageVersion>lambdaQuery()
                .eq(WikiPageVersion::getPageId, pageId)
                .eq(WikiPageVersion::getVersion, normalizeVersion(version)));
    }

    /** 小于给定版本号的最近快照。 */
    public WikiPageVersion previousSnapshot(Long pageId, Integer beforeVersion) {
        if (pageId == null || beforeVersion == null) return null;
        return versionMapper.selectList(Wrappers.<WikiPageVersion>lambdaQuery()
                        .eq(WikiPageVersion::getPageId, pageId)
                        .lt(WikiPageVersion::getVersion, beforeVersion)
                        .orderByDesc(WikiPageVersion::getVersion)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    /** 某投稿在指定版本之前最近一次真正发布的快照（排除由回滚复制出的快照）。 */
    public WikiPageVersion latestRevisionSnapshot(Long pageId, Long revisionId, Integer beforeVersion) {
        if (pageId == null || revisionId == null || beforeVersion == null) return null;
        return versionMapper.selectList(Wrappers.<WikiPageVersion>lambdaQuery()
                        .eq(WikiPageVersion::getPageId, pageId)
                        .eq(WikiPageVersion::getSourceRevisionId, revisionId)
                        .in(WikiPageVersion::getSourceType, "REVISION_CREATE", "REVISION_UPDATE")
                        .lt(WikiPageVersion::getVersion, beforeVersion)
                        .orderByDesc(WikiPageVersion::getVersion)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    /** 页面被彻底删除时清理其不可达快照；软删除时保留，以便恢复后继续追溯。 */
    public int purgePageVersions(Long pageId) {
        return versionMapper.delete(Wrappers.<WikiPageVersion>lambdaQuery()
                .eq(WikiPageVersion::getPageId, pageId));
    }

    /** 删除某投稿产生或作为内容来源的全部公开快照，用于超管紧急清除历史正文。 */
    public int purgeRevisionVersions(Long revisionId) {
        if (revisionId == null) return 0;
        return versionMapper.delete(Wrappers.<WikiPageVersion>lambdaQuery()
                .eq(WikiPageVersion::getSourceRevisionId, revisionId));
    }

    /** 超管紧急删除单个历史版本；当前公开版本必须先被修正版替代。 */
    @Transactional
    public WikiPageVersion purgeVersion(Long id) {
        WikiPageVersion version = versionMapper.selectById(id);
        if (version == null) throw new BizException(404, "页面版本不存在");
        WikiPage page = pageMapper.selectById(version.getPageId());
        WikiPageVersion current = page == null ? null : currentSnapshot(page.getId(), page.getVersion());
        if (page != null && current == null) {
            throw new BizException("页面当前状态缺少版本快照，请先发布修正版");
        }
        // 即使页面处于草稿/回收站，当前内容仍可能在 restore 后重新公开，因此同样不可删除当前快照。
        if (current != null && current.getId().equals(version.getId())) {
            throw new BizException("不能删除页面当前版本，请先发布修正版");
        }
        versionMapper.deleteById(id);
        return version;
    }

    /** 公开历史，按版本号倒序。 */
    public List<PublicPageRevisionVO> listPublic(String path) {
        WikiPage page = requirePublicPage(path);
        List<WikiPageVersion> versions = versionMapper.selectList(Wrappers.<WikiPageVersion>lambdaQuery()
                .eq(WikiPageVersion::getPageId, page.getId())
                .orderByDesc(WikiPageVersion::getVersion));
        int currentVersion = normalizeVersion(page.getVersion());
        return versions.stream()
                .map(v -> toPublic(v, currentVersion, false, null))
                .toList();
    }

    /** 单个公开历史详情；只有其所属页面当前仍公开时才允许读取。 */
    public PublicPageRevisionVO getPublic(Long id) {
        WikiPageVersion version = versionMapper.selectById(id);
        if (version == null) throw new BizException(404, "页面版本不存在");
        WikiPage page = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getId, version.getPageId())
                .eq(WikiPage::getDeleted, 0)
                .eq(WikiPage::getStatus, "PUBLISHED"));
        if (!isPublic(page)) throw new BizException(404, "页面版本不存在");

        WikiPageVersion previous = previousSnapshot(version.getPageId(), version.getVersion());
        return toPublic(version, normalizeVersion(page.getVersion()), true, previous);
    }

    private WikiPage requirePublicPage(String path) {
        if (path == null || path.isBlank()) throw new BizException(404, "页面不存在");
        WikiPage page = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getPath, path.trim())
                .eq(WikiPage::getDeleted, 0)
                .eq(WikiPage::getStatus, "PUBLISHED"));
        if (!isPublic(page)) throw new BizException(404, "页面不存在");
        return page;
    }

    private String resolveAuthorName(String sourceType, Long authorId) {
        if ("MIGRATION".equals(sourceType) && authorId == null) return "系统迁移";
        User user = authorId == null ? null : userMapper.selectById(authorId);
        return displayName(user, sourceType);
    }

    private static String displayName(User user, String sourceType) {
        if (user == null) return missingAuthorName(sourceType);
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname().trim();
        }
        String maskedEmail = maskEmail(user.getEmail());
        return maskedEmail == null ? missingAuthorName(sourceType) : maskedEmail;
    }

    /** 仅保留邮箱开头 1–2 个字符与域名；公开快照绝不保存原始邮箱。 */
    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String normalized = email.trim();
        int at = normalized.indexOf('@');
        if (at <= 0) return null;
        String local = normalized.substring(0, at);
        String head = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return head + "***" + normalized.substring(at);
    }

    private static String storedAuthorName(WikiPageVersion version) {
        if ("MIGRATION".equals(version.getSourceType()) && version.getAuthorId() == null) {
            return "系统迁移";
        }
        String name = version.getAuthorName();
        return name == null || name.isBlank()
                ? missingAuthorName(version.getSourceType()) : name.trim();
    }

    private static String missingAuthorName(String sourceType) {
        return isAdminAction(sourceType) ? "已注销管理员" : "已注销贡献者";
    }

    private static boolean isAdminAction(String sourceType) {
        return "ROLLBACK".equals(sourceType)
                || (sourceType != null && sourceType.startsWith("ADMIN"));
    }

    private static PublicPageRevisionVO toPublic(WikiPageVersion version,
                                                  int currentVersion, boolean detail,
                                                  WikiPageVersion previous) {
        PublicPageRevisionVO vo = new PublicPageRevisionVO();
        vo.setId(version.getId());
        vo.setVersion(version.getVersion());
        vo.setTitle(version.getTitle());
        vo.setAuthorName(storedAuthorName(version));
        vo.setCreatedAt(iso(version.getPublishedAt()));
        vo.setSummary(version.getSummary());
        vo.setSourceType(version.getSourceType());
        vo.setCurrent(normalizeVersion(version.getVersion()) == currentVersion);
        if (detail) {
            vo.setChangedFields(changedFields(previous, version));
            vo.setBeforeContent(previous == null ? null : previous.getContent());
            vo.setAfterContent(version.getContent());
        }
        return vo;
    }

    private static List<String> changedFields(WikiPageVersion before, WikiPageVersion after) {
        java.util.ArrayList<String> fields = new java.util.ArrayList<>();
        if (!Objects.equals(before == null ? null : before.getTitle(), after.getTitle())) fields.add("title");
        if (!Objects.equals(before == null ? null : before.getCategorySlug(), after.getCategorySlug())) {
            fields.add("categorySlug");
        }
        if (!Objects.equals(before == null ? null : before.getIcon(), after.getIcon())) fields.add("icon");
        if (!Objects.equals(before == null ? null : before.getDescription(), after.getDescription())) {
            fields.add("description");
        }
        List<String> beforeTags = before == null ? List.of() : JsonUtil.toStringList(before.getTags());
        List<String> afterTags = JsonUtil.toStringList(after.getTags());
        if (!Objects.equals(beforeTags, afterTags)) fields.add("tags");
        if (!Objects.equals(before == null ? null : before.getContent(), after.getContent())) fields.add("content");
        return fields;
    }

    private static boolean isPublic(WikiPage page) {
        return page != null && page.getId() != null
                && "PUBLISHED".equals(page.getStatus())
                && (page.getDeleted() == null || page.getDeleted() == 0);
    }

    private static int normalizeVersion(Integer version) {
        return version == null ? 0 : version;
    }

    private static String iso(LocalDateTime dt) {
        return dt == null ? null : dt.atOffset(ZoneOffset.ofHours(8))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
