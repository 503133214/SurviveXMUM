package wiki.xmum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.po.PageComment;
import wiki.xmum.domain.po.User;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.vo.AdminCommentVO;
import wiki.xmum.domain.vo.CommentVO;
import wiki.xmum.domain.vo.PageResult;
import wiki.xmum.mapper.PageCommentMapper;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.security.AuthUser;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文档页讨论区。
 *
 * <p>展示为两层：主楼 + 楼中回复。数据里 parentId 指“直接回复了谁”（用来显示 @），
 * rootId 指楼层归属；回复回复时 rootId 继承主楼，因此永远不会出现深层嵌套。
 *
 * <p>可见性：VISIBLE 正常显示；HIDDEN（管理员隐藏）/ DELETED（作者自删）只在
 * 主楼且楼里还有可见回复时保留一个不含作者信息的占位，避免回复变成孤儿。
 */
@Service
public class CommentService {

    private static final int MAX_LEN = 1000;
    /** 同一用户两条评论的最小间隔，挡住手滑连点与脚本刷屏。 */
    private static final int MIN_INTERVAL_SECONDS = 15;
    /** 同页同内容的去重窗口。 */
    private static final int DUPLICATE_WINDOW_MINUTES = 5;
    private static final int MAX_PER_PAGE = 500;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String HIDDEN_PLACEHOLDER = "该评论已被管理员隐藏";
    private static final String DELETED_PLACEHOLDER = "该评论已被作者删除";

    private final PageCommentMapper mapper;
    private final WikiPageMapper pageMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public CommentService(PageCommentMapper mapper, WikiPageMapper pageMapper, UserMapper userMapper,
                          NotificationService notificationService, AuditService auditService) {
        this.mapper = mapper;
        this.pageMapper = pageMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // ---------- 公开读取 ----------

    /** 某页的讨论串；viewerId 可为 null（未登录）。 */
    public List<CommentVO> list(String path, Long viewerId) {
        WikiPage page = findPage(path);
        if (page == null) return List.of();

        List<PageComment> all = mapper.selectList(Wrappers.<PageComment>lambdaQuery()
                .eq(PageComment::getPageId, page.getId())
                .orderByAsc(PageComment::getCreatedAt)
                .orderByAsc(PageComment::getId)
                .last("LIMIT " + MAX_PER_PAGE));
        if (all.isEmpty()) return List.of();

        Map<Long, User> users = loadUsers(all.stream().map(PageComment::getUserId).toList());
        Map<Long, PageComment> byId = all.stream()
                .collect(Collectors.toMap(PageComment::getId, c -> c, (a, b) -> a));

        // 先收可见回复，再决定哪些主楼要保留（被隐藏的主楼只有还挂着可见回复时才留占位）。
        Map<Long, List<CommentVO>> repliesByRoot = new HashMap<>();
        for (PageComment c : all) {
            if (c.getRootId() == null || !"VISIBLE".equals(c.getStatus())) continue;
            CommentVO vo = toVO(c, users, viewerId);
            PageComment parent = c.getParentId() == null ? null : byId.get(c.getParentId());
            if (parent != null && !Objects.equals(parent.getId(), c.getRootId())) {
                vo.setReplyToName(displayName(users.get(parent.getUserId())));
            }
            repliesByRoot.computeIfAbsent(c.getRootId(), k -> new ArrayList<>()).add(vo);
        }

        List<CommentVO> roots = new ArrayList<>();
        for (PageComment c : all) {
            if (c.getRootId() != null) continue;
            List<CommentVO> replies = repliesByRoot.getOrDefault(c.getId(), List.of());
            boolean visible = "VISIBLE".equals(c.getStatus());
            if (!visible && replies.isEmpty()) continue;
            CommentVO vo = visible ? toVO(c, users, viewerId) : tombstone(c);
            vo.setReplies(new ArrayList<>(replies));
            roots.add(vo);
        }
        return roots;
    }

    // ---------- 发表 / 自删 ----------

    @Transactional
    public Long submit(String path, String rawContent, Long parentId, AuthUser user) {
        String content = normalize(rawContent);
        if (content.isEmpty()) throw new BizException("评论内容不能为空");
        if (content.length() > MAX_LEN) throw new BizException("评论过长（最多 " + MAX_LEN + " 字）");

        WikiPage page = findPage(path);
        if (page == null) throw new BizException(404, "页面不存在");

        PageComment parent = null;
        if (parentId != null) {
            parent = mapper.selectById(parentId);
            if (parent == null || !Objects.equals(parent.getPageId(), page.getId())
                    || !"VISIBLE".equals(parent.getStatus())) {
                throw new BizException("被回复的评论不存在或已被删除");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (mapper.selectCount(Wrappers.<PageComment>lambdaQuery()
                .eq(PageComment::getUserId, user.getId())
                .ge(PageComment::getCreatedAt, now.minusSeconds(MIN_INTERVAL_SECONDS))) > 0) {
            throw new BizException("发言太快了，休息 " + MIN_INTERVAL_SECONDS + " 秒再试");
        }
        if (mapper.selectCount(Wrappers.<PageComment>lambdaQuery()
                .eq(PageComment::getUserId, user.getId())
                .eq(PageComment::getPageId, page.getId())
                .eq(PageComment::getContent, content)
                .ge(PageComment::getCreatedAt, now.minusMinutes(DUPLICATE_WINDOW_MINUTES))) > 0) {
            throw new BizException("刚刚已经发过一模一样的内容了");
        }

        PageComment c = new PageComment();
        c.setPageId(page.getId());
        c.setPath(page.getPath());
        c.setUserId(user.getId());
        c.setContent(content);
        c.setStatus("VISIBLE");
        if (parent != null) {
            c.setParentId(parent.getId());
            c.setRootId(parent.getRootId() == null ? parent.getId() : parent.getRootId());
        }
        mapper.insert(c);

        if (parent != null && !Objects.equals(parent.getUserId(), user.getId())) {
            notificationService.notify(parent.getUserId(), "COMMENT_REPLY",
                    "有人回复了你的评论",
                    "在《" + page.getTitle() + "》：" + preview(content),
                    "/docs/" + page.getPath(), c.getId());
        }
        return c.getId();
    }

    /** 作者删除自己的评论（软删，保留楼层结构）。 */
    public void deleteOwn(Long id, AuthUser user) {
        PageComment c = mapper.selectById(id);
        if (c == null || !"VISIBLE".equals(c.getStatus())) throw new BizException(404, "评论不存在");
        if (!Objects.equals(c.getUserId(), user.getId())) throw new BizException(403, "只能删除自己的评论");
        mapper.update(null, Wrappers.<PageComment>lambdaUpdate()
                .set(PageComment::getStatus, "DELETED")
                .eq(PageComment::getId, id)
                .eq(PageComment::getUserId, user.getId()));
    }

    // ---------- 后台管理 ----------

    public PageResult<AdminCommentVO> adminList(String status, String path, String keyword, long page, long size) {
        LambdaQueryWrapper<PageComment> q = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) q.eq(PageComment::getStatus, status.trim());
        if (path != null && !path.isBlank()) q.eq(PageComment::getPath, path.trim());
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(PageComment::getContent, kw).or().like(PageComment::getPath, kw));
        }
        q.orderByDesc(PageComment::getCreatedAt).orderByDesc(PageComment::getId);

        Page<PageComment> p = mapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100)), q);
        List<PageComment> rows = p.getRecords();

        Map<Long, User> users = loadUsers(rows.stream().map(PageComment::getUserId).toList());
        Map<Long, String> titles = loadPageTitles(rows.stream().map(PageComment::getPageId).toList());

        List<AdminCommentVO> list = rows.stream().map(c -> {
            User u = users.get(c.getUserId());
            AdminCommentVO v = new AdminCommentVO();
            v.setId(c.getId());
            v.setParentId(c.getParentId());
            v.setPath(c.getPath());
            v.setPageTitle(titles.get(c.getPageId()));
            v.setUserId(c.getUserId());
            v.setUserEmail(u == null ? null : u.getEmail());
            v.setDisplayName(displayName(u));
            v.setContent(c.getContent());
            v.setStatus(c.getStatus());
            v.setHiddenReason(c.getHiddenReason());
            v.setCreatedAt(c.getCreatedAt() == null ? null
                    : c.getCreatedAt().atOffset(ZoneOffset.ofHours(8)).format(FMT_SEC));
            return v;
        }).toList();
        return new PageResult<>(list, p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 隐藏 / 恢复一条评论。隐藏时给作者发通知说明原因。 */
    @Transactional
    public void adminSetStatus(Long id, String status, String reason, AuthUser admin) {
        PageComment c = mapper.selectById(id);
        if (c == null) throw new BizException(404, "评论不存在");
        boolean hide = "HIDDEN".equals(status);
        if (!hide && !"VISIBLE".equals(status)) throw new BizException("不支持的状态");
        // 作者自删是作者的意思表示，管理员不能替他撤销（隐藏同理，本来就没得可隐藏）
        if ("DELETED".equals(c.getStatus())) throw new BizException("该评论已被作者删除，无法再操作");

        String trimmed = reason == null || reason.isBlank() ? null : reason.trim();
        if (trimmed != null && trimmed.length() > 200) trimmed = trimmed.substring(0, 200);

        c.setStatus(hide ? "HIDDEN" : "VISIBLE");
        c.setHiddenReason(hide ? trimmed : null);
        mapper.updateById(c);

        if (hide && !Objects.equals(c.getUserId(), admin.getId())) {
            notificationService.notify(c.getUserId(), "COMMENT_HIDDEN",
                    "你的一条评论已被隐藏",
                    (trimmed == null ? "管理员隐藏了你在讨论区的一条评论。" : "原因：" + trimmed)
                            + "（内容：" + preview(c.getContent()) + "）",
                    "/docs/" + c.getPath(), c.getId());
        }
        auditService.log(hide ? "COMMENT_HIDE" : "COMMENT_SHOW", "COMMENT", c.getId(),
                (hide ? "隐藏" : "恢复") + "评论 @" + c.getPath() + "：" + preview(c.getContent())
                        + (hide && trimmed != null ? "（原因：" + trimmed + "）" : ""));
    }

    /** 彻底删除一条评论；删主楼时连同楼中回复一起清掉（仅超管，控制器把关）。 */
    @Transactional
    public void purge(Long id) {
        PageComment c = mapper.selectById(id);
        if (c == null) throw new BizException(404, "评论不存在");
        int replies = 0;
        if (c.getRootId() == null) {
            replies = mapper.delete(Wrappers.<PageComment>lambdaQuery().eq(PageComment::getRootId, id));
        }
        mapper.deleteById(id);
        auditService.log("COMMENT_DELETE", "COMMENT", id,
                "彻底删除评论 @" + c.getPath() + "：" + preview(c.getContent())
                        + (replies > 0 ? "（连带 " + replies + " 条回复）" : ""));
    }

    // ---------- 内部 ----------

    private WikiPage findPage(String path) {
        if (path == null || path.isBlank()) return null;
        return pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getPath, path.trim())
                .eq(WikiPage::getDeleted, 0)
                .eq(WikiPage::getStatus, "PUBLISHED"));
    }

    private Map<Long, User> loadUsers(List<Long> rawIds) {
        List<Long> ids = rawIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private Map<Long, String> loadPageTitles(List<Long> rawIds) {
        List<Long> ids = rawIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> titles = new LinkedHashMap<>();
        for (WikiPage p : pageMapper.selectBatchIds(ids)) titles.put(p.getId(), p.getTitle());
        return titles;
    }

    private CommentVO toVO(PageComment c, Map<Long, User> users, Long viewerId) {
        User u = users.get(c.getUserId());
        CommentVO v = new CommentVO();
        v.setId(c.getId());
        v.setParentId(c.getParentId());
        v.setUserId(u == null ? null : u.getId());
        v.setDisplayName(displayName(u));
        v.setAvatar(u == null ? null : u.getAvatar());
        v.setContent(c.getContent());
        v.setStatus(c.getStatus());
        v.setCreatedAt(c.getCreatedAt() == null ? null
                : c.getCreatedAt().atOffset(ZoneOffset.ofHours(8)).format(FMT));
        v.setMine(viewerId != null && Objects.equals(viewerId, c.getUserId()));
        return v;
    }

    /** 占位主楼：不带作者信息，只为撑住楼里的可见回复。 */
    private CommentVO tombstone(PageComment c) {
        CommentVO v = new CommentVO();
        v.setId(c.getId());
        v.setStatus(c.getStatus());
        v.setContent("HIDDEN".equals(c.getStatus()) ? HIDDEN_PLACEHOLDER : DELETED_PLACEHOLDER);
        v.setCreatedAt(c.getCreatedAt() == null ? null
                : c.getCreatedAt().atOffset(ZoneOffset.ofHours(8)).format(FMT));
        v.setMine(false);
        return v;
    }

    private static String displayName(User u) {
        if (u == null || (u.getDeleted() != null && u.getDeleted() == 1)) return "已注销用户";
        if (u.getNickname() != null && !u.getNickname().isBlank()) return u.getNickname().trim();
        return ContributorService.maskEmail(u.getEmail());
    }

    /** 去掉首尾空白，并把 3 个以上连续换行压成 2 个，避免用空行把版面撑开。 */
    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private static String preview(String content) {
        if (content == null) return "";
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= 40 ? flat : flat.substring(0, 40) + "…";
    }
}
