package wiki.xmum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.po.User;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.po.WikiPageVersion;
import wiki.xmum.domain.po.WikiRevision;
import wiki.xmum.domain.vo.PageContributorVO;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.mapper.WikiPageVersionMapper;
import wiki.xmum.mapper.WikiRevisionMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContributorServiceTest {

    @Mock private WikiRevisionMapper revisionMapper;
    @Mock private WikiPageMapper pageMapper;
    @Mock private UserMapper userMapper;
    @Mock private WikiPageVersionMapper versionMapper;

    @Test
    void pageContributorsAggregatePublishedEventsAndSupplementLegacyRevisions() {
        WikiPage page = publicPage();
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of(
                version(1L, "REVISION_CREATE", 101L, 1L, "小明旧快照", at(1)),
                version(2L, "REVISION_UPDATE", 101L, 1L, "小明新快照", at(2)),
                // 即使源投稿后来撤回/清理，只要发布快照仍在就仍是一笔贡献。
                version(3L, "REVISION_UPDATE", 102L, 2L, "小李", at(3)),
                version(4L, "ADMIN_UPDATE", null, 1L, "小明", at(4)),
                version(5L, "ADMIN_CREATE", null, 3L, "已删除管理员", at(5)),
                version(6L, "ADMIN_PUBLISH", null, 1L, "小明", at(6)),
                version(7L, "MIGRATION", null, 4L, "迁移者", at(4)),
                version(8L, "MIGRATION", null, null, "系统迁移", at(8)),
                // MIGRATION 仅是状态基线；已有真实发布事件的作者不能再加一次。
                version(11L, "MIGRATION", null, 1L, "小明", at(8)),
                version(9L, "ROLLBACK", 101L, 1L, "小明", at(9)),
                version(10L, "ADMIN_RESTORE", null, 1L, "小明", at(10)),
                version(12L, "REVISION_RESTORE", 105L, 6L, "不应计入", at(11))));
        when(revisionMapper.selectList(any())).thenReturn(List.of(
                // 已被版本快照代表，不可重复计数。
                revision(101L, 1L, "old@example.com", at(11)),
                revision(103L, 1L, "ming@example.com", at(7)),
                revision(104L, 5L, "legacy@example.com", at(6))));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(1L, "小明"), user(2L, "小李"), user(4L, "迁移者")));

        List<PageContributorVO> result = service().pageContributors(page.getPath());

        assertEquals(5, result.size());
        assertContributor(result.get(0), 1L, "小明", 4);
        // 同为一次贡献时按最近贡献时间倒序。
        assertContributor(result.get(1), null, "le***@example.com", 1);
        assertContributor(result.get(2), null, "已删除管理员", 1);
        assertContributor(result.get(3), 4L, "迁移者", 1);
        assertContributor(result.get(4), 2L, "小李", 1);
    }

    @Test
    void pageContributorsFallBackToPageAuthorWhenThereAreNoEvents() {
        WikiPage page = publicPage();
        page.setAuthorId(9L);
        page.setUpdatedAt(at(4));
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of());
        when(revisionMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(9L, "原作者")));

        List<PageContributorVO> result = service().pageContributors(page.getPath());

        assertEquals(1, result.size());
        assertContributor(result.get(0), 9L, "原作者", 1);
    }

    @Test
    void pageContributorsSupplementPageAuthorAlongsideLaterEvents() {
        WikiPage page = publicPage();
        page.setAuthorId(9L);
        page.setCreatedAt(at(1));
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of(
                version(1L, "ADMIN_UPDATE", null, 2L, "管理员", at(4))));
        when(revisionMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(2L, "管理员"), user(9L, "原作者")));

        List<PageContributorVO> result = service().pageContributors(page.getPath());

        assertEquals(2, result.size());
        assertContributor(result.get(0), 2L, "管理员", 1);
        assertContributor(result.get(1), 9L, "原作者", 1);
    }

    @Test
    void pageContributorsUseDisplayNameAsFinalTieBreaker() {
        WikiPage page = publicPage();
        LocalDateTime sameTime = at(3);
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of(
                version(1L, "ADMIN_UPDATE", null, 1L, "Zulu", sameTime),
                version(2L, "ADMIN_UPDATE", null, 2L, "Alpha", sameTime)));
        when(revisionMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(1L, "Zulu"), user(2L, "Alpha")));

        List<PageContributorVO> result = service().pageContributors(page.getPath());

        assertEquals(List.of("Alpha", "Zulu"),
                result.stream().map(PageContributorVO::getDisplayName).toList());
    }

    @Test
    void pageContributorsNeverExposeAnonymousPlaceholderWithoutReliableFallback() {
        WikiPage page = publicPage();
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of());
        when(revisionMapper.selectList(any())).thenReturn(List.of(
                revision(105L, 99L, "invalid-email", at(3))));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());

        List<PageContributorVO> result = service().pageContributors(page.getPath());

        assertEquals(1, result.size());
        assertContributor(result.get(0), null, "已注销贡献者", 1);
    }

    @Test
    void pageContributorsRequirePageToStillBePublic() {
        WikiPage page = publicPage();
        page.setStatus("DRAFT");
        when(pageMapper.selectOne(any())).thenReturn(page);

        BizException error = assertThrows(BizException.class,
                () -> service().pageContributors(page.getPath()));

        assertEquals(404, error.getCode());
        verifyNoInteractions(versionMapper, revisionMapper, userMapper);
    }

    @Test
    void pageContributorsTreatMissingPathAsNotFound() {
        BizException error = assertThrows(BizException.class,
                () -> service().pageContributors(null));

        assertEquals(404, error.getCode());
        verifyNoInteractions(pageMapper, versionMapper, revisionMapper, userMapper);
    }

    private ContributorService service() {
        return new ContributorService(revisionMapper, pageMapper, userMapper, versionMapper);
    }

    private static WikiPage publicPage() {
        WikiPage page = new WikiPage();
        page.setId(10L);
        page.setPath("生活/交通");
        page.setTitle("交通");
        page.setStatus("PUBLISHED");
        page.setDeleted(0);
        return page;
    }

    private static WikiPageVersion version(Long id, String sourceType, Long sourceRevisionId,
                                           Long authorId, String authorName,
                                           LocalDateTime publishedAt) {
        WikiPageVersion version = new WikiPageVersion();
        version.setId(id);
        version.setPageId(10L);
        version.setSourceType(sourceType);
        version.setSourceRevisionId(sourceRevisionId);
        version.setAuthorId(authorId);
        version.setAuthorName(authorName);
        version.setPublishedAt(publishedAt);
        return version;
    }

    private static WikiRevision revision(Long id, Long authorId, String authorEmail,
                                         LocalDateTime reviewedAt) {
        WikiRevision revision = new WikiRevision();
        revision.setId(id);
        revision.setPageId(10L);
        revision.setTargetPath("生活/交通");
        revision.setStatus("APPROVED");
        revision.setAuthorId(authorId);
        revision.setAuthorEmail(authorEmail);
        revision.setReviewedAt(reviewedAt);
        return revision;
    }

    private static User user(Long id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        user.setDeleted(0);
        return user;
    }

    private static LocalDateTime at(int day) {
        return LocalDateTime.of(2026, 7, day, 12, 0);
    }

    private static void assertContributor(PageContributorVO contributor, Long userId,
                                          String displayName, int count) {
        if (userId == null) assertNull(contributor.getUserId());
        else assertEquals(userId, contributor.getUserId());
        assertEquals(displayName, contributor.getDisplayName());
        assertEquals(count, contributor.getCount());
    }
}
