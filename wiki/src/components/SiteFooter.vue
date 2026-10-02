<template>
  <footer class="site-footer" :class="{ compact }">
    <div class="sf-inner">
      <!-- 一行可换行的链接 + 一行版权，学生手册用不着多栏页脚 -->
      <nav class="sf-links" aria-label="页脚链接">
        <router-link :to="`/docs/${HOME_PATH}`">文档首页</router-link>
        <router-link to="/changes">站点动态</router-link>
        <router-link to="/contributors">贡献榜</router-link>
        <router-link to="/docs/贡献指南">贡献指南</router-link>
        <router-link to="/edit">写文章</router-link>
        <router-link to="/feedback">意见反馈</router-link>
        <router-link to="/docs/api/api-overview">API 文档</router-link>
        <a :href="REPO" target="_blank" rel="noopener noreferrer">GitHub <span aria-hidden="true">↗</span></a>
      </nav>
      <p class="sf-copy">© 2023–{{ year }} XMUM Wiki Team · 内容以 GPL-3.0 发布 · 由同学投稿、管理员审核后发布</p>
    </div>
  </footer>
</template>

<script>
import { HOME_PATH, REPO } from "@/wiki";

export default {
  name: "SiteFooter",
  props: {
    // 文档页里用：去掉上方留白，宽度跟随正文区
    compact: { type: Boolean, default: false },
  },
  data() {
    return { HOME_PATH, REPO, year: new Date().getFullYear() };
  },
};
</script>

<style scoped>
.site-footer {
  margin-top: 32px;
  border-top: 1px solid var(--border);
  background: var(--bg-page);
  color: var(--text-secondary);
}

.sf-inner {
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 24px max(16px, env(safe-area-inset-left)) calc(24px + env(safe-area-inset-bottom));
}

.sf-links {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  font-size: var(--fs-sm);
}
/* 行高撑到 24px：手机上链接排得密，给手指留点余量 */
.sf-links a {
  color: var(--text-secondary);
  line-height: 24px;
  transition: color var(--dur) ease;
}
.sf-links a:hover { color: var(--text-primary); }

.sf-copy {
  margin-top: 8px;
  color: var(--text-muted);
  font-size: var(--fs-xs);
  line-height: var(--lh-ui);
}

.site-footer.compact { margin-top: 0; }
.site-footer.compact .sf-inner { padding-top: 20px; }
</style>
