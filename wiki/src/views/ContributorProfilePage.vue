<template>
  <div class="cprofile">
    <div v-if="loading" class="cp-loading"><el-skeleton :rows="4" animated /></div>

    <template v-else-if="profile">
      <router-link class="cprofile-back" to="/contributors">← 返回贡献榜</router-link>
      <header class="cprofile-head">
        <el-avatar :size="72" :src="profile.avatar || undefined">{{ initial(profile.displayName) }}</el-avatar>
        <div class="cprofile-meta">
          <h1>{{ profile.displayName }}</h1>
          <p>已通过投稿 <b>{{ profile.count }}</b> 篇</p>
        </div>
      </header>

      <section class="cprofile-pages">
        <h2>参与的页面</h2>
        <el-empty v-if="!profile.pages || !profile.pages.length" description="暂无已发布的贡献页面" />
        <ul v-else class="pg-list">
          <li v-for="p in profile.pages" :key="p.path" @click="$router.push(`/docs/${p.path}`)">
            <span class="pg-title">{{ p.title }}</span>
            <span class="pg-path">{{ p.path }}</span>
            <span class="pg-go">→</span>
          </li>
        </ul>
      </section>
    </template>

    <el-empty v-else description="未找到该贡献者" />
  </div>
</template>

<script>
import { getContributorProfile } from '@/net/index.js'

export default {
  name: 'ContributorProfilePage',
  props: { id: { type: String, default: '' } },
  data() {
    return { profile: null, loading: true }
  },
  watch: {
    id() { this.load() },
  },
  methods: {
    initial(name) { return (name || '?').trim().charAt(0).toUpperCase() },
    load() {
      this.loading = true
      getContributorProfile(this.id,
        (d) => { this.profile = d; this.loading = false },
        () => { this.profile = null; this.loading = false })
    },
  },
  mounted() { this.load() },
}
</script>

<style scoped>
.cprofile {
  max-width: 820px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}
.cprofile-back {
  display: inline-block;
  margin-bottom: 20px;
  color: var(--text-secondary);
  font-size: 14px;
}
.cprofile-back:hover { color: var(--text-primary); text-decoration: none; }
.cprofile-head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--border);
}
.cprofile-meta h1 { margin: 0; font-size: 1.6rem; font-weight: 800; color: var(--text-primary); }
.cprofile-meta p { margin: 6px 0 0; color: var(--text-secondary); font-size: 14px; }
.cprofile-meta b { color: var(--brand); font-size: 1.05rem; }

.cprofile-pages { margin-top: 28px; }
.cprofile-pages h2 { font-size: 1.15rem; font-weight: 700; color: var(--text-primary); margin: 0 0 14px; }
.pg-list { list-style: none; margin: 0; padding: 0; }
.pg-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 6px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background 0.15s ease, padding-left 0.2s ease;
}
.pg-list li:hover { background: var(--bg-subtle); padding-left: 12px; }
.pg-title { font-weight: 600; color: var(--text-primary); }
.pg-path { flex: 1; min-width: 0; color: var(--text-muted); font-size: 12.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pg-go { flex-shrink: 0; color: var(--text-muted); opacity: 0; transition: opacity 0.2s ease; }
.pg-list li:hover .pg-go { opacity: 1; }
</style>
