package wiki.xmum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.po.PageComment;
import wiki.xmum.domain.po.User;
import wiki.xmum.domain.po.UserFavorite;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.vo.CommentVO;
import wiki.xmum.domain.vo.MyCommentVO;
import wiki.xmum.mapper.PageCommentMapper;
import wiki.xmum.mapper.UserFavoriteMapper;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.security.AuthUser;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private PageCommentMapper mapper;
    @Mock private WikiPageMapper pageMapper;
    @Mock private UserMapper userMapper;
    @Mock private UserFavoriteMapper favoriteMapper;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;

    @Test
    void replyToAReplyStaysInTheSameThread() {
        stubPage();
        when(mapper.selectCount(any())).thenReturn(0L);
        PageComment reply = comment(3L, 2L, 1L, "VISIBLE");  // 2 楼下的一条回复，主楼是 1
        reply.setUserId(7L);
        when(mapper.selectById(3L)).thenReturn(reply);

        service().submit("生活/交通", "再补充一句", 3L, user(8L));

        ArgumentCaptor<PageComment> saved = ArgumentCaptor.forClass(PageComment.class);
        verify(mapper).insert(saved.capture());
        // parentId 指向被回复的那条（显示 @），rootId 仍归主楼，不产生第三层
        assertEquals(3L, saved.getValue().getParentId());
        assertEquals(1L, saved.getValue().getRootId());
    }

    @Test
    void replyNotifiesTheAuthorOfTheCommentBeingRepliedTo() {
        stubPage();
        when(mapper.selectCount(any())).thenReturn(0L);
        PageComment root = comment(1L, null, null, "VISIBLE");
        root.setUserId(7L);
        when(mapper.selectById(1L)).thenReturn(root);

        service().submit("生活/交通", "同问", 1L, user(8L));

        verify(notificationService).notify(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("COMMENT_REPLY"),
                any(), any(), any(), any());
    }

    @Test
    void replyingToYourselfSendsNoNotification() {
        stubPage();
        when(mapper.selectCount(any())).thenReturn(0L);
        PageComment root = comment(1L, null, null, "VISIBLE");
        root.setUserId(8L);
        when(mapper.selectById(1L)).thenReturn(root);

        service().submit("生活/交通", "补充一下", 1L, user(8L));

        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    void postingTooFastIsRejected() {
        stubPage();
        when(mapper.selectCount(any())).thenReturn(1L);

        BizException error = assertThrows(BizException.class,
                () -> service().submit("生活/交通", "刷屏", null, user(8L)));

        assertTrue(error.getMessage().contains("发言太快"));
        verify(mapper, never()).insert(any(PageComment.class));
    }

    @Test
    void hiddenRootKeepsAPlaceholderOnlyWhileItStillCarriesVisibleReplies() {
        stubPage();
        PageComment hiddenRoot = comment(1L, null, null, "HIDDEN");
        hiddenRoot.setUserId(7L);
        PageComment visibleReply = comment(2L, 1L, 1L, "VISIBLE");
        visibleReply.setUserId(8L);
        PageComment lonelyHidden = comment(5L, null, null, "HIDDEN");
        lonelyHidden.setUserId(7L);
        when(mapper.selectList(any())).thenReturn(List.of(hiddenRoot, visibleReply, lonelyHidden));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(7L, "阿七"), user(8L, "阿八")));

        List<CommentVO> threads = service().list("生活/交通", 8L);

        // 没有可见回复的隐藏主楼直接消失，不给读者留下噪音
        assertEquals(1, threads.size());
        CommentVO tombstone = threads.get(0);
        assertEquals(1L, tombstone.getId());
        assertEquals("该评论已被管理员隐藏", tombstone.getContent());
        assertNull(tombstone.getUserId());
        assertNull(tombstone.getDisplayName());
        assertEquals(1, tombstone.getReplies().size());
        assertEquals("阿八", tombstone.getReplies().get(0).getDisplayName());
        assertTrue(tombstone.getReplies().get(0).getMine());
    }

    @Test
    void replyToAnotherReplyRendersTheAtName() {
        stubPage();
        PageComment root = comment(1L, null, null, "VISIBLE");
        root.setUserId(7L);
        PageComment first = comment(2L, 1L, 1L, "VISIBLE");
        first.setUserId(8L);
        PageComment second = comment(3L, 2L, 1L, "VISIBLE");
        second.setUserId(9L);
        when(mapper.selectList(any())).thenReturn(List.of(root, first, second));
        when(userMapper.selectBatchIds(any()))
                .thenReturn(List.of(user(7L, "阿七"), user(8L, "阿八"), user(9L, "阿九")));

        List<CommentVO> threads = service().list("生活/交通", null);

        List<CommentVO> replies = threads.get(0).getReplies();
        assertEquals(2, replies.size());
        // 直接回复主楼的那条不显示 @，回复楼中人的才显示
        assertNull(replies.get(0).getReplyToName());
        assertEquals("阿八", replies.get(1).getReplyToName());
    }

    @Test
    void adminCannotUndoAnAuthorsOwnDeletion() {
        PageComment deleted = comment(1L, null, null, "DELETED");
        deleted.setUserId(7L);
        when(mapper.selectById(1L)).thenReturn(deleted);

        BizException error = assertThrows(BizException.class,
                () -> service().adminSetStatus(1L, "VISIBLE", null, admin()));

        assertTrue(error.getMessage().contains("作者删除"));
        verify(mapper, never()).updateById(any(PageComment.class));
    }

    @Test
    void adminCanRestoreAHiddenComment() {
        PageComment hidden = comment(1L, null, null, "HIDDEN");
        hidden.setUserId(7L);
        hidden.setHiddenReason("与本页无关");
        when(mapper.selectById(1L)).thenReturn(hidden);

        service().adminSetStatus(1L, "VISIBLE", null, admin());

        assertEquals("VISIBLE", hidden.getStatus());
        // 理由必须一起清掉，否则后台还会显示上一次的隐藏原因
        assertNull(hidden.getHiddenReason());
        verify(mapper).updateById(hidden);
    }

    @Test
    void newThreadNotifiesPageFollowersButNotItsOwnAuthor() {
        stubPage();
        when(mapper.selectCount(any())).thenReturn(0L);
        when(favoriteMapper.selectList(any()))
                .thenReturn(List.of(follower(7L), follower(8L), follower(8L)));

        service().submit("生活/交通", "这一页少了校车时刻", null, user(8L));

        // 8 是发帖者本人，重复关注行也只算一次
        verify(notificationService).notify(eq(7L), eq("COMMENT_NEW"), any(), any(), any(), any());
        verify(notificationService, never()).notify(eq(8L), any(), any(), any(), any(), any());
    }

    @Test
    void repliesDoNotSprayThePageFollowers() {
        stubPage();
        when(mapper.selectCount(any())).thenReturn(0L);
        PageComment root = comment(1L, null, null, "VISIBLE");
        root.setUserId(7L);
        when(mapper.selectById(1L)).thenReturn(root);

        service().submit("生活/交通", "同问", 1L, user(8L));

        // 只提醒被回复的人，关注者不参与
        verify(notificationService).notify(eq(7L), eq("COMMENT_REPLY"), any(), any(), any(), any());
        verify(notificationService, never()).notify(any(), eq("COMMENT_NEW"), any(), any(), any(), any());
        verify(favoriteMapper, never()).selectList(any());
    }

    @Test
    void myDiscussionsSkipSelfDeletedOnesAndFlagReplies() {
        PageComment root = comment(1L, null, null, "VISIBLE");
        PageComment reply = comment(2L, 1L, 1L, "HIDDEN");
        reply.setHiddenReason("与本页无关");
        when(mapper.selectList(any())).thenReturn(List.of(root, reply));
        WikiPage page = new WikiPage();
        page.setId(10L);
        page.setTitle("交通");
        when(pageMapper.selectBatchIds(any())).thenReturn(List.of(page));

        List<MyCommentVO> mine = service().mine(8L);

        assertEquals(2, mine.size());
        assertEquals("交通", mine.get(0).getPageTitle());
        assertFalse(mine.get(0).getReply());
        assertTrue(mine.get(1).getReply());
        assertEquals("与本页无关", mine.get(1).getHiddenReason());
    }

    private CommentService service() {
        return new CommentService(mapper, pageMapper, userMapper, favoriteMapper,
                notificationService, auditService);
    }

    private void stubPage() {
        WikiPage page = new WikiPage();
        page.setId(10L);
        page.setPath("生活/交通");
        page.setTitle("交通");
        when(pageMapper.selectOne(any())).thenReturn(page);
    }

    private static PageComment comment(Long id, Long parentId, Long rootId, String status) {
        PageComment c = new PageComment();
        c.setId(id);
        c.setPageId(10L);
        c.setPath("生活/交通");
        c.setParentId(parentId);
        c.setRootId(rootId);
        c.setContent("内容 " + id);
        c.setStatus(status);
        c.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0).plusMinutes(id));
        return c;
    }

    private static UserFavorite follower(Long userId) {
        UserFavorite f = new UserFavorite();
        f.setUserId(userId);
        f.setPageId(10L);
        f.setNotifyUpdates(1);
        return f;
    }

    private static AuthUser admin() {
        return new AuthUser(99L, "admin@xmu.edu.my", "SUPER_ADMIN");
    }

    private static AuthUser user(Long id) {
        return new AuthUser(id, "u" + id + "@xmu.edu.my", "USER");
    }

    private static User user(Long id, String nickname) {
        User u = new User();
        u.setId(id);
        u.setEmail("u" + id + "@xmu.edu.my");
        u.setNickname(nickname);
        u.setDeleted(0);
        return u;
    }
}
