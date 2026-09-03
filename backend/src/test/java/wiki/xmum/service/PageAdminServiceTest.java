package wiki.xmum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.PageUpsertDTO;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.mapper.UserFavoriteMapper;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.mapper.UserViewHistoryMapper;
import wiki.xmum.mapper.WikiCategoryMapper;
import wiki.xmum.mapper.WikiDraftMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.security.AuthUser;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageAdminServiceTest {

    @Mock private WikiPageMapper pageMapper;
    @Mock private WikiCategoryMapper categoryMapper;
    @Mock private UserMapper userMapper;
    @Mock private AuditService auditService;
    @Mock private UserFavoriteMapper favoriteMapper;
    @Mock private UserViewHistoryMapper historyMapper;
    @Mock private WikiDraftMapper draftMapper;
    @Mock private wiki.xmum.mapper.PageCommentMapper commentMapper;
    @Mock private PageVersionService pageVersionService;

    @Test
    void sortOnlyUpdateDoesNotCreatePublicVersionOrNotifyFollowers() {
        WikiPage page = page();
        LocalDateTime originalUpdatedAt = page.getUpdatedAt();
        PageUpsertDTO dto = new PageUpsertDTO();
        dto.setVersion(2);
        dto.setSortOrder(99);
        when(pageMapper.selectById(10L)).thenReturn(page);

        service().update(10L, dto, actor());

        assertEquals(2, page.getVersion());
        assertEquals(99, page.getSortOrder());
        assertEquals(originalUpdatedAt, page.getUpdatedAt());
        verify(pageMapper).updateById(page);
        verify(pageVersionService, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void contentUpdateRefreshesArticleTimestamp() {
        WikiPage page = page();
        LocalDateTime originalUpdatedAt = page.getUpdatedAt();
        PageUpsertDTO dto = new PageUpsertDTO();
        dto.setVersion(2);
        dto.setContent("new content");
        when(pageMapper.selectById(10L)).thenReturn(page);

        service().update(10L, dto, actor());

        assertEquals(3, page.getVersion());
        assertNotNull(page.getUpdatedAt());
        assertTrue(page.getUpdatedAt().isAfter(originalUpdatedAt));
        verify(pageVersionService).publish(page, "ADMIN_UPDATE", null, 99L,
                "管理员更新页面", java.util.Set.of(99L));
    }

    @Test
    void restoreRejectsPageThatIsNotInRecycleBin() {
        WikiPage page = page();
        when(pageMapper.selectById(10L)).thenReturn(page);

        BizException error = assertThrows(BizException.class,
                () -> service().restore(10L, actor()));

        assertEquals("页面不在回收站中，无需恢复", error.getMessage());
        verify(pageMapper, never()).updateById(any(WikiPage.class));
        verify(pageVersionService, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void restoreCreatesNewVersionAttributedToAdminActor() {
        WikiPage page = page();
        page.setDeleted(1);
        when(pageMapper.selectById(10L)).thenReturn(page);

        service().restore(10L, actor());

        assertEquals(0, page.getDeleted());
        assertEquals(3, page.getVersion());
        verify(pageVersionService).publish(page, "ADMIN_RESTORE", null, 99L,
                "管理员恢复页面", java.util.Set.of(99L));
    }

    private PageAdminService service() {
        return new PageAdminService(pageMapper, categoryMapper, userMapper, auditService,
                favoriteMapper, historyMapper, draftMapper, commentMapper, pageVersionService);
    }

    private static AuthUser actor() {
        return new AuthUser(99L, "admin@example.com", "SUPER_ADMIN");
    }

    private static WikiPage page() {
        WikiPage page = new WikiPage();
        page.setId(10L);
        page.setPath("生活/交通");
        page.setTitle("交通");
        page.setContent("content");
        page.setTags("[]");
        page.setHeadings("[]");
        page.setSortOrder(1);
        page.setStatus("PUBLISHED");
        page.setDeleted(0);
        page.setVersion(2);
        page.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return page;
    }
}
