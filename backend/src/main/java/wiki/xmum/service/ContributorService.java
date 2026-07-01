package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.po.User;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.po.WikiRevision;
import wiki.xmum.domain.vo.ContributorProfileVO;
import wiki.xmum.domain.vo.ContributorVO;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.mapper.WikiRevisionMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 贡献榜 / 贡献者主页：派生自 status=APPROVED 的投稿（含新建 + 编辑），无独立表。公开只读。
 */
@Service
public class ContributorService {

    private final WikiRevisionMapper revisionMapper;
    private final WikiPageMapper pageMapper;
    private final UserMapper userMapper;

    public ContributorService(WikiRevisionMapper revisionMapper, WikiPageMapper pageMapper, UserMapper userMapper) {
        this.revisionMapper = revisionMapper;
        this.pageMapper = pageMapper;
        this.userMapper = userMapper;
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

    private static String displayName(User u) {
        if (u.getNickname() != null && !u.getNickname().isBlank()) return u.getNickname().trim();
        return maskEmail(u.getEmail());
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
