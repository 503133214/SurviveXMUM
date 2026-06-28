package wiki.xmum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.po.WikiRevision;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevisionServiceTest {

    @Mock private WikiRevisionMapper revisionMapper;
    @Mock private WikiPageMapper pageMapper;
    @Mock private WikiCategoryMapper categoryMapper;
    @Mock private UserMapper userMapper;

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

    private RevisionService service() {
        return new RevisionService(revisionMapper, pageMapper, categoryMapper, userMapper);
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
}
