package wiki.xmum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageVersionServiceTest {

    @Mock private WikiPageVersionMapper versionMapper;
    @Mock private WikiPageMapper pageMapper;
    @Mock private UserMapper userMapper;
    @Mock private UserFavoriteMapper favoriteMapper;
    @Mock private NotificationService notificationService;

    @Test
    void publishingFansOutOnlyToWatchersNotAuthorOrOperator() {
        WikiPage page = publicPage();
        UserFavorite watcher = favorite(2L, page.getId());
        UserFavorite authorFavorite = favorite(3L, page.getId());
        User author = new User();
        author.setId(3L);
        author.setNickname("小明");
        author.setEmail("private@example.com");
        when(userMapper.selectById(3L)).thenReturn(author);
        when(favoriteMapper.selectList(any())).thenReturn(List.of(watcher, authorFavorite));
        doAnswer(invocation -> {
            WikiPageVersion version = invocation.getArgument(0);
            version.setId(99L);
            return 1;
        }).when(versionMapper).insert(any(WikiPageVersion.class));

        service().publish(page, "REVISION_UPDATE", 50L, 3L, "贡献者更新页面", Set.of(3L, 4L));

        verify(notificationService).notify(eq(2L), eq("PAGE_UPDATED"), any(), any(),
                eq("/docs/生活/交通"), eq(99L));
        verify(notificationService, never()).notify(eq(3L), any(), any(), any(), any(), anyLong());
        verify(notificationService, never()).notify(eq(4L), any(), any(), any(), any(), anyLong());
        ArgumentCaptor<WikiPageVersion> snapshot = ArgumentCaptor.forClass(WikiPageVersion.class);
        verify(versionMapper).insert(snapshot.capture());
        assertEquals("小明", snapshot.getValue().getAuthorName());
    }

    @Test
    void publishingMasksEmailWhenContributorHasNoNickname() {
        WikiPage page = publicPage();
        User author = new User();
        author.setId(3L);
        author.setEmail("student123@xmu.edu.my");
        when(userMapper.selectById(3L)).thenReturn(author);
        when(favoriteMapper.selectList(any())).thenReturn(List.of());

        service().publish(page, "REVISION_UPDATE", 50L, 3L,
                "贡献者更新页面", Set.of(3L));

        ArgumentCaptor<WikiPageVersion> snapshot = ArgumentCaptor.forClass(WikiPageVersion.class);
        verify(versionMapper).insert(snapshot.capture());
        assertEquals("st***@xmu.edu.my", snapshot.getValue().getAuthorName());
        assertFalse(snapshot.getValue().getAuthorName().contains("student123"));
    }

    @Test
    void publishingUsesRoleSpecificLabelWhenUserNoLongerExists() {
        WikiPage page = publicPage();
        when(favoriteMapper.selectList(any())).thenReturn(List.of());

        service().publish(page, "ADMIN_UPDATE", null, 30L,
                "管理员更新页面", Set.of(30L));
        service().publish(page, "REVISION_UPDATE", 50L, 31L,
                "贡献者更新页面", Set.of(31L));

        ArgumentCaptor<WikiPageVersion> snapshots = ArgumentCaptor.forClass(WikiPageVersion.class);
        verify(versionMapper, org.mockito.Mockito.times(2)).insert(snapshots.capture());
        assertEquals("已注销管理员", snapshots.getAllValues().get(0).getAuthorName());
        assertEquals("已注销贡献者", snapshots.getAllValues().get(1).getAuthorName());
    }

    @Test
    void migrationWithKnownAuthorKeepsConcreteDisplayName() {
        WikiPage page = publicPage();
        User author = new User();
        author.setId(3L);
        author.setNickname("历史修稿者");
        author.setDeleted(1); // 软删除仍保留署名；只有 user 行真正缺失才降级。
        when(userMapper.selectById(3L)).thenReturn(author);
        when(favoriteMapper.selectList(any())).thenReturn(List.of());

        service().publish(page, "MIGRATION", null, 3L,
                "部署前当前公开版本", Set.of());

        ArgumentCaptor<WikiPageVersion> snapshot = ArgumentCaptor.forClass(WikiPageVersion.class);
        verify(versionMapper).insert(snapshot.capture());
        assertEquals("历史修稿者", snapshot.getValue().getAuthorName());
    }

    @Test
    void historyRequiresPageToStillBePublic() {
        WikiPage draft = publicPage();
        draft.setStatus("DRAFT");
        when(pageMapper.selectOne(any())).thenReturn(draft);

        BizException error = assertThrows(BizException.class,
                () -> service().listPublic("生活/交通"));

        assertEquals(404, error.getCode());
        verifyNoInteractions(versionMapper);
    }

    @Test
    void publicHistoryUsesSnapshottedNameAndNeverExposesEmail() throws Exception {
        WikiPage page = publicPage();
        WikiPageVersion version = version(90L, page.getId(), 2, "新正文");
        version.setAuthorId(3L);
        version.setAuthorName("小明");
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of(version));

        List<PublicPageRevisionVO> result = service().listPublic(page.getPath());
        String json = new ObjectMapper().writeValueAsString(result);

        assertEquals("小明", result.get(0).getAuthorName());
        assertTrue(result.get(0).isCurrent());
        assertFalse(json.contains("private@example.com"));
        assertFalse(json.contains("reviewComment"));
        assertFalse(json.contains("reviewerId"));
        verifyNoInteractions(userMapper);
    }

    @Test
    void publicHistoryUsesSnapshottedAdministratorName() {
        WikiPage page = publicPage();
        WikiPageVersion version = version(90L, page.getId(), 2, "管理员更新后的正文");
        version.setSourceType("ADMIN_UPDATE");
        version.setAuthorId(3L);
        version.setAuthorName("维护者小王");
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of(version));

        List<PublicPageRevisionVO> result = service().listPublic(page.getPath());

        assertEquals("维护者小王", result.get(0).getAuthorName());
        verifyNoInteractions(userMapper);
    }

    @Test
    void publicHistorySupportsMigrationSnapshotWithoutAuthor() {
        WikiPage page = publicPage();
        WikiPageVersion version = version(90L, page.getId(), 2, "迁移后的正文");
        version.setSourceType("MIGRATION");
        version.setAuthorId(null);
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of(version));

        List<PublicPageRevisionVO> result = service().listPublic(page.getPath());

        assertEquals("系统迁移", result.get(0).getAuthorName());
        verifyNoInteractions(userMapper);
    }

    @Test
    void publicHistoryKeepsKnownAuthorOnMigrationSnapshot() {
        WikiPage page = publicPage();
        WikiPageVersion version = version(90L, page.getId(), 2, "迁移后的正文");
        version.setSourceType("MIGRATION");
        version.setAuthorId(3L);
        version.setAuthorName("历史修稿者");
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of(version));

        List<PublicPageRevisionVO> result = service().listPublic(page.getPath());

        assertEquals("历史修稿者", result.get(0).getAuthorName());
        verifyNoInteractions(userMapper);
    }

    @Test
    void detailReturnsBeforeAndAfterContentForDiff() {
        WikiPage page = publicPage();
        WikiPageVersion current = version(90L, page.getId(), 2, "新正文");
        WikiPageVersion previous = version(80L, page.getId(), 1, "旧正文");
        when(versionMapper.selectById(90L)).thenReturn(current);
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of(previous));

        PublicPageRevisionVO detail = service().getPublic(90L);

        assertEquals("旧正文", detail.getBeforeContent());
        assertEquals("新正文", detail.getAfterContent());
        assertEquals(2, detail.getVersion());
        assertEquals(List.of("content"), detail.getChangedFields());
    }

    @Test
    void detailReportsMetadataOnlyChanges() {
        WikiPage page = publicPage();
        WikiPageVersion current = version(90L, page.getId(), 2, "相同正文");
        current.setDescription("新简介");
        WikiPageVersion previous = version(80L, page.getId(), 1, "相同正文");
        previous.setDescription("旧简介");
        when(versionMapper.selectById(90L)).thenReturn(current);
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of(previous));

        PublicPageRevisionVO detail = service().getPublic(90L);

        assertEquals(List.of("description"), detail.getChangedFields());
        assertEquals("相同正文", detail.getBeforeContent());
        assertEquals("相同正文", detail.getAfterContent());
    }

    @Test
    void emergencyPurgeRejectsCurrentPublicVersion() {
        WikiPage page = publicPage();
        // 下架/软删也不能绕过：页面仍持有这份正文，恢复后可能再次公开。
        page.setStatus("DRAFT");
        page.setDeleted(1);
        WikiPageVersion current = version(90L, page.getId(), 2, "正文");
        when(versionMapper.selectById(90L)).thenReturn(current);
        when(pageMapper.selectById(page.getId())).thenReturn(page);
        when(versionMapper.selectOne(any())).thenReturn(current);

        BizException error = assertThrows(BizException.class, () -> service().purgeVersion(90L));

        assertEquals("不能删除页面当前版本，请先发布修正版", error.getMessage());
        verify(versionMapper, never()).deleteById(90L);
    }

    @Test
    void emergencyPurgeAllowsHistoricalVersion() {
        WikiPage page = publicPage();
        WikiPageVersion old = version(80L, page.getId(), 1, "旧正文");
        WikiPageVersion current = version(90L, page.getId(), 2, "新正文");
        when(versionMapper.selectById(80L)).thenReturn(old);
        when(pageMapper.selectById(page.getId())).thenReturn(page);
        when(versionMapper.selectOne(any())).thenReturn(current);

        WikiPageVersion deleted = service().purgeVersion(80L);

        assertEquals(80L, deleted.getId());
        verify(versionMapper).deleteById(80L);
    }

    @Test
    void emergencyPurgeIsBlockedAfterPublishedPageBecomesDraftWithoutSnapshot() {
        WikiPage page = publicPage();
        page.setStatus("DRAFT");
        page.setVersion(3); // v2 公开后管理员仅改为 DRAFT；v3 不产生公开快照
        WikiPageVersion published = version(80L, page.getId(), 2, "敏感正文");
        when(versionMapper.selectById(80L)).thenReturn(published);
        when(pageMapper.selectById(page.getId())).thenReturn(page);
        when(versionMapper.selectOne(any())).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service().purgeVersion(80L));

        assertEquals("页面当前状态缺少版本快照，请先发布修正版", error.getMessage());
        verify(versionMapper, never()).deleteById(80L);
    }

    private PageVersionService service() {
        return new PageVersionService(versionMapper, pageMapper, userMapper, favoriteMapper, notificationService);
    }

    private static WikiPage publicPage() {
        WikiPage page = new WikiPage();
        page.setId(10L);
        page.setPath("生活/交通");
        page.setTitle("交通");
        page.setStatus("PUBLISHED");
        page.setDeleted(0);
        page.setVersion(2);
        page.setContent("新正文");
        return page;
    }

    private static UserFavorite favorite(Long userId, Long pageId) {
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setPageId(pageId);
        favorite.setNotifyUpdates(1);
        return favorite;
    }

    private static WikiPageVersion version(Long id, Long pageId, int number, String content) {
        WikiPageVersion version = new WikiPageVersion();
        version.setId(id);
        version.setPageId(pageId);
        version.setVersion(number);
        version.setTitle("交通");
        version.setContent(content);
        version.setSourceType("REVISION_UPDATE");
        version.setPublishedAt(LocalDateTime.of(2026, 7, 16, 12, 0));
        return version;
    }
}
