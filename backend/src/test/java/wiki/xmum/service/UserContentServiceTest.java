package wiki.xmum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wiki.xmum.domain.po.UserFavorite;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.common.BizException;
import wiki.xmum.mapper.UserFavoriteMapper;
import wiki.xmum.mapper.UserViewHistoryMapper;
import wiki.xmum.mapper.WikiPageMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserContentServiceTest {

    @Mock private UserFavoriteMapper favoriteMapper;
    @Mock private UserViewHistoryMapper historyMapper;
    @Mock private WikiPageMapper pageMapper;

    @Test
    void existingFavoriteCanOptInToUpdateNotifications() {
        WikiPage page = new WikiPage();
        page.setId(10L);
        page.setPath("生活/交通");
        page.setStatus("PUBLISHED");
        page.setDeleted(0);
        UserFavorite favorite = new UserFavorite();
        favorite.setId(20L);
        favorite.setUserId(1L);
        favorite.setPageId(10L);
        favorite.setNotifyUpdates(0);
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(favoriteMapper.selectOne(any())).thenReturn(favorite);

        Map<String, Object> result = service().addFavorite(1L, page.getPath(), true);

        assertTrue((Boolean) result.get("notifyUpdates"));
        ArgumentCaptor<UserFavorite> updated = ArgumentCaptor.forClass(UserFavorite.class);
        verify(favoriteMapper).updateById(updated.capture());
        assertEquals(1, updated.getValue().getNotifyUpdates());
    }

    @Test
    void notificationToggleCanBeTurnedOffWithoutRemovingFavorite() {
        UserFavorite favorite = new UserFavorite();
        favorite.setId(20L);
        favorite.setUserId(1L);
        favorite.setNotifyUpdates(1);
        when(favoriteMapper.selectOne(any())).thenReturn(favorite);

        Map<String, Object> result = service().setFavoriteNotification(1L, 20L, false);

        assertEquals(false, result.get("notifyUpdates"));
        verify(favoriteMapper).updateById(favorite);
        assertEquals(0, favorite.getNotifyUpdates());
    }

    @Test
    void userCannotChangeAnotherUsersFavoriteNotification() {
        when(favoriteMapper.selectOne(any())).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> service().setFavoriteNotification(1L, 99L, true));

        assertEquals(404, error.getCode());
        verify(favoriteMapper, never()).updateById(any(UserFavorite.class));
    }

    @Test
    void checkAndListExposeNotifyUpdates() {
        WikiPage page = new WikiPage();
        page.setId(10L);
        page.setPath("生活/交通");
        page.setStatus("PUBLISHED");
        page.setDeleted(0);
        UserFavorite favorite = new UserFavorite();
        favorite.setId(20L);
        favorite.setUserId(1L);
        favorite.setPageId(10L);
        favorite.setPath(page.getPath());
        favorite.setNotifyUpdates(1);
        when(pageMapper.selectOne(any())).thenReturn(page);
        when(favoriteMapper.selectOne(any())).thenReturn(favorite);
        when(favoriteMapper.selectList(any())).thenReturn(java.util.List.of(favorite));

        Map<String, Object> checked = service().checkFavorite(1L, page.getPath());
        java.util.List<Map<String, Object>> listed = service().listFavorites(1L);

        assertEquals(true, checked.get("notifyUpdates"));
        assertEquals(true, listed.get(0).get("notifyUpdates"));
    }

    private UserContentService service() {
        return new UserContentService(favoriteMapper, historyMapper, pageMapper);
    }
}
