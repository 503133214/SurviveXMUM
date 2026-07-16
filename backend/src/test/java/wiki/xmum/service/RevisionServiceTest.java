package wiki.xmum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.po.WikiRevision;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.po.WikiPageVersion;
import wiki.xmum.domain.vo.RevisionVO;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.mapper.WikiCategoryMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.mapper.WikiRevisionMapper;
import wiki.xmum.security.AuthUser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevisionServiceTest {

    @Mock private WikiRevisionMapper revisionMapper;
    @Mock private WikiPageMapper pageMapper;
    @Mock private WikiCategoryMapper categoryMapper;
    @Mock private UserMapper userMapper;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private PageVersionService pageVersionService;

    @Test
    void mineReturnsRejectedAndMarksApprovedRevisionWhosePageWasRemoved() {
        RevisionService service = service();
        WikiRevision rejected = revision(1L, "REJECTED", "api/rejected");
        WikiRevision approved = revision(2L, "APPROVED", "api/removed");
        when(revisionMapper.selectList(any())).thenReturn(List.of(rejected, approved));
        when(pageMapper.selectList(any())).thenReturn(List.of());

        List<RevisionVO> result = service.mine(user(10L));

        assertEquals(List.of("REJECTED", "REMOVED"),
                result.stream().map(RevisionVO::getStatus).toList());
    }

    @Test
    void mineDetailDoesNotExposeAnotherUsersSubmission() {
        RevisionService service = service();
        when(revisionMapper.selectById(1L)).thenReturn(revision(1L, "REJECTED", "api/private"));

        BizException error = assertThrows(BizException.class,
                () -> service.mineDetail(1L, user(99L)));

        assertEquals(404, error.getCode());
    }

    @Test
    void reapprovedCreateDoesNotRollbackToEarlierSnapshotFromSameRevision() {
        RevisionService service = service();
        WikiRevision create = revision(1L, "APPROVED", "api/new-page");
        WikiPage page = page(100L, 1, "api/new-page", "created");
        WikiPageVersion current = snapshot(11L, 100L, 1, 1L, "REVISION_CREATE", "created");
        WikiPageVersion sameCreateBefore = snapshot(10L, 100L, 0, 1L, "REVISION_CREATE", "created");
        when(revisionMapper.selectById(1L)).thenReturn(create);
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(pageVersionService.currentSnapshot(100L, 1)).thenReturn(current);
        when(pageVersionService.previousSnapshot(100L, 1)).thenReturn(sameCreateBefore);
        when(revisionMapper.selectList(any())).thenReturn(List.of(create), List.of());

        String action = service.revoke(1L, null, user(99L));

        assertEquals("PAGE_DELETED", action);
        assertEquals(1, page.getDeleted());
        verify(pageVersionService, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rollbackTracksRestoredRevisionSoConsecutiveRevokesFollowContentHistory() {
        RevisionService service = service();
        WikiRevision older = revision(1L, "APPROVED", "api/page");
        older.setType("UPDATE");
        WikiRevision newer = revision(2L, "APPROVED", "api/page");
        newer.setType("UPDATE");
        WikiPage page = page(100L, 2, "api/page", "newer");
        WikiPageVersion currentNewer = snapshot(12L, 100L, 2, 2L, "REVISION_UPDATE", "newer");
        WikiPageVersion originalOlder = snapshot(11L, 100L, 1, 1L, "REVISION_UPDATE", "older");
        WikiPageVersion base = snapshot(10L, 100L, 0, null, "MIGRATION", "base");
        WikiPageVersion rollbackToOlder = snapshot(13L, 100L, 3, 1L, "ROLLBACK", "older");

        when(revisionMapper.selectById(2L)).thenReturn(newer);
        when(revisionMapper.selectById(1L)).thenReturn(older);
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(pageVersionService.currentSnapshot(100L, 2)).thenReturn(currentNewer);
        when(pageVersionService.currentSnapshot(100L, 3)).thenReturn(rollbackToOlder);
        when(pageVersionService.previousSnapshot(100L, 2)).thenReturn(originalOlder);
        when(pageVersionService.latestRevisionSnapshot(100L, 1L, 3)).thenReturn(originalOlder);
        when(pageVersionService.previousSnapshot(100L, 1)).thenReturn(base);
        when(revisionMapper.selectList(any())).thenReturn(List.of(newer), List.of(older));

        assertEquals("ROLLED_BACK", service.revoke(2L, null, user(99L)));
        assertEquals("older", page.getContent());
        assertEquals(3, page.getVersion());
        assertEquals("ROLLED_BACK", service.revoke(1L, null, user(99L)));
        assertEquals("base", page.getContent());
        assertEquals(4, page.getVersion());

        org.mockito.ArgumentCaptor<Long> sourceRevision = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(pageVersionService, times(2)).publish(any(), org.mockito.ArgumentMatchers.eq("ROLLBACK"),
                sourceRevision.capture(), any(), any(), any());
        assertEquals(java.util.Arrays.asList(1L, null), sourceRevision.getAllValues());
    }

    @Test
    void revokeNeverRepublishesPageThatAdminAlreadyTookOffline() {
        RevisionService service = service();
        WikiRevision approved = revision(1L, "APPROVED", "api/offline");
        WikiPage page = page(100L, 1, "api/offline", "content");
        page.setDeleted(1);
        when(revisionMapper.selectById(1L)).thenReturn(approved);
        when(pageMapper.selectOne(any())).thenReturn(page);

        BizException error = assertThrows(BizException.class,
                () -> service.revoke(1L, null, user(99L)));

        assertEquals("页面已被管理员下架，不能通过撤销投稿重新发布", error.getMessage());
        verify(pageVersionService, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void purgeRevisionCannotRemoveCurrentSnapshotWhilePageIsSoftDeleted() {
        RevisionService service = service();
        WikiRevision rejected = revision(1L, "REJECTED", "api/offline");
        WikiPage page = page(100L, 2, "api/offline", "sensitive");
        page.setDeleted(1);
        WikiPageVersion current = snapshot(90L, 100L, 2, 1L, "REVISION_UPDATE", "sensitive");
        when(revisionMapper.selectById(1L)).thenReturn(rejected);
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(pageVersionService.currentSnapshot(100L, 2)).thenReturn(current);

        BizException error = assertThrows(BizException.class, () -> service.purge(1L));

        assertEquals("该投稿仍是页面当前内容来源，请先撤销或更新页面后再删除", error.getMessage());
        verify(pageVersionService, never()).purgeRevisionVersions(1L);
        verify(revisionMapper, never()).deleteById(1L);
    }

    @Test
    void purgeRevisionIsBlockedAfterPublishedPageBecomesDraftWithoutSnapshot() {
        RevisionService service = service();
        WikiRevision approved = revision(1L, "APPROVED", "api/draft");
        WikiPage page = page(100L, 3, "api/draft", "sensitive");
        page.setStatus("DRAFT");
        when(revisionMapper.selectById(1L)).thenReturn(approved);
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(pageVersionService.currentSnapshot(100L, 3)).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service.purge(1L));

        assertEquals("页面当前状态缺少版本快照，请先发布修正版", error.getMessage());
        verify(pageVersionService, never()).purgeRevisionVersions(1L);
        verify(revisionMapper, never()).deleteById(1L);
    }

    private RevisionService service() {
        return new RevisionService(revisionMapper, pageMapper, categoryMapper, userMapper,
                notificationService, auditService, pageVersionService);
    }

    private static AuthUser user(Long id) {
        return new AuthUser(id, "user@example.com", "USER");
    }

    private static WikiRevision revision(Long id, String status, String path) {
        WikiRevision revision = new WikiRevision();
        revision.setId(id);
        revision.setAuthorId(10L);
        revision.setType("CREATE");
        revision.setStatus(status);
        revision.setTargetPath(path);
        revision.setTitle(path);
        return revision;
    }

    private static WikiPage page(Long id, int version, String path, String content) {
        WikiPage page = new WikiPage();
        page.setId(id);
        page.setVersion(version);
        page.setPath(path);
        page.setTitle(path);
        page.setContent(content);
        page.setStatus("PUBLISHED");
        page.setDeleted(0);
        return page;
    }

    private static WikiPageVersion snapshot(Long id, Long pageId, int version, Long sourceRevisionId,
                                            String sourceType, String content) {
        WikiPageVersion snapshot = new WikiPageVersion();
        snapshot.setId(id);
        snapshot.setPageId(pageId);
        snapshot.setVersion(version);
        snapshot.setTitle("page");
        snapshot.setSourceRevisionId(sourceRevisionId);
        snapshot.setSourceType(sourceType);
        snapshot.setContent(content);
        return snapshot;
    }
}
