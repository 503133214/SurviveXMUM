package wiki.xmum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wiki.xmum.domain.po.WikiPageVersion;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageVersionAdminServiceTest {

    @Mock private PageVersionService versionService;
    @Mock private AuditService auditService;

    @Test
    void purgeUsesStrictAuditAndPropagatesAuditFailureForTransactionRollback() {
        WikiPageVersion version = new WikiPageVersion();
        version.setId(90L);
        version.setPageId(10L);
        version.setVersion(2);
        when(versionService.purgeVersion(90L)).thenReturn(version);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditService).logStrict(any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> service().purge(90L));

        verify(versionService).purgeVersion(90L);
        verify(auditService).logStrict(any(), any(), any(), any());
    }

    private PageVersionAdminService service() {
        return new PageVersionAdminService(versionService, auditService);
    }
}
