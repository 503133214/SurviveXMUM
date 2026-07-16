package wiki.xmum.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wiki.xmum.domain.po.WikiPageVersion;

/** 不可逆版本治理操作：删除与严格审计必须在同一事务内成功。 */
@Service
public class PageVersionAdminService {

    private final PageVersionService versionService;
    private final AuditService auditService;

    public PageVersionAdminService(PageVersionService versionService, AuditService auditService) {
        this.versionService = versionService;
        this.auditService = auditService;
    }

    @Transactional
    public void purge(Long id) {
        WikiPageVersion deleted = versionService.purgeVersion(id);
        auditService.logStrict("PAGE_VERSION_PURGE", "PAGE", deleted.getPageId(),
                "删除公开历史版本 v" + deleted.getVersion() + "（versionId=" + deleted.getId()
                        + "，原因：超级管理员紧急清除历史内容）");
    }
}
