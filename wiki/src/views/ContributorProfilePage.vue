<template>
  <main class="cprofile">
    <div v-if="loading" class="cp-loading">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else-if="profile">
      <router-link class="cprofile-back" to="/contributors">
        <span aria-hidden="true">←</span> 返回贡献者与致谢
      </router-link>

      <header class="cprofile-head" v-reveal>
        <div class="profile-avatar">
          <el-avatar :size="88" :src="profile.avatar || undefined">
            {{ initial(profile.displayName) }}
          </el-avatar>
          <span class="avatar-orbit" aria-hidden="true"></span>
        </div>
        <div class="cprofile-meta">
          <p class="profile-kicker">COMMUNITY CONTRIBUTOR</p>
          <h1>{{ profile.displayName }}</h1>
          <p>谢谢你把经验写下来，让后来的人看得见。</p>
        </div>
        <div class="profile-count">
          <strong>{{ profile.count }}</strong>
          <span>篇已通过投稿</span>
        </div>
      </header>

      <section class="cprofile-pages" v-reveal>
        <div class="pages-head">
          <div>
            <p>CONTRIBUTED PAGES</p>
            <h2>参与维护的页面</h2>
          </div>
          <span>{{ profile.pages?.length || 0 }} 个页面</span>
        </div>

        <el-empty
          v-if="!profile.pages || !profile.pages.length"
          description="暂无已发布的贡献页面"
        />
        <div v-else class="pg-list">
          <router-link
            v-for="(page, index) in profile.pages"
            :key="page.path"
            class="pg-card"
            :to="`/docs/${page.path}`"
            v-reveal="{ delay: Math.min(index, 8) * 45 }"
          >
            <span class="pg-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="pg-copy">
              <strong>{{ page.title }}</strong>
              <span>{{ page.path }}</span>
            </span>
            <span class="pg-go" aria-hidden="true">→</span>
          </router-link>
        </div>
      </section>
    </template>

    <div v-else class="profile-empty">
      <el-empty description="未找到该贡献者">
        <el-button @click="$router.push('/contributors')">返回贡献榜</el-button>
      </el-empty>
    </div>
  </main>
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
    initial(name) {
      return (name || '?').trim().charAt(0).toUpperCase()
    },
    load() {
      this.loading = true
      getContributorProfile(
        this.id,
        (data) => {
          this.profile = data
          this.loading = false
        },
        () => {
          this.profile = null
          this.loading = false
        },
      )
    },
  },
  mounted() {
    this.load()
  },
}
</script>

<style scoped>
.cprofile {
  width: 100%;
  max-width: 1000px;
  margin: 0 auto;
  padding: 38px 28px 76px;
}
.cp-loading,
.profile-empty { padding: 50px 0; }
.cprofile-back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 650;
}
.cprofile-back:hover { color: var(--text-primary); text-decoration: none; }
.cprofile-back span { transition: transform .25s var(--ease-out); }
.cprofile-back:hover span { transform: translateX(-3px); }

.cprofile-head {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 26px;
  min-height: 230px;
  overflow: hidden;
  padding: 42px 46px;
  border: 1px solid var(--border);
  border-radius: 26px;
  background:
    radial-gradient(circle at 94% 14%, color-mix(in srgb, var(--text-primary) 8%, transparent), transparent 28%),
    var(--bg-surface);
}
.cprofile-head::after {
  content: "";
  position: absolute;
  right: -58px;
  bottom: -104px;
  width: 230px;
  height: 230px;
  border: 1px solid var(--border);
  border-radius: 50%;
}
.profile-avatar { position: relative; z-index: 1; padding: 10px; }
.profile-avatar :deep(.el-avatar) {
  position: relative;
  z-index: 2;
  border: 4px solid var(--bg-surface);
  background: var(--text-primary);
  color: var(--bg-page);
  box-shadow: 0 0 0 1px var(--border-strong), var(--shadow-md);
  font-size: 28px;
  font-weight: 850;
}
.avatar-orbit {
  position: absolute;
  inset: -2px;
  border: 1px dashed var(--border-strong);
  border-radius: 50%;
  animation: profile-orbit 16s linear infinite;
}
.avatar-orbit::before {
  content: "";
  position: absolute;
  top: 5px;
  left: 15px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-primary);
}
.cprofile-meta { position: relative; z-index: 1; min-width: 0; }
.profile-kicker {
  margin: 0 0 7px;
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: .14em;
}
.cprofile-meta h1 {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-size: clamp(1.8rem, 4vw, 2.8rem);
  font-weight: 850;
  letter-spacing: -.04em;
  line-height: 1.12;
}
.cprofile-meta > p:last-child {
  margin: 10px 0 0;
  color: var(--text-secondary);
  font-size: 14px;
}
.profile-count {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 126px;
  flex-direction: column;
  align-items: flex-end;
  padding-left: 28px;
  border-left: 1px solid var(--border);
}
.profile-count strong {
  color: var(--text-primary);
  font-size: 38px;
  font-weight: 850;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.profile-count span { margin-top: 8px; color: var(--text-muted); font-size: 12px; white-space: nowrap; }

.cprofile-pages { margin-top: 58px; }
.pages-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
  padding-bottom: 17px;
  border-bottom: 1px solid var(--border);
}
.pages-head p {
  margin: 0 0 5px;
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: .14em;
}
.pages-head h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.35rem;
  font-weight: 800;
  letter-spacing: -.025em;
}
.pages-head > span { color: var(--text-muted); font-size: 12px; white-space: nowrap; }
.pg-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.pg-card {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 20px;
  align-items: center;
  gap: 14px;
  min-height: 90px;
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--bg-surface);
  color: var(--text-primary);
  text-decoration: none;
  transition: border-color .2s ease, transform .3s var(--ease-out), box-shadow .3s ease;
}
.pg-card:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
  text-decoration: none;
  transform: translateY(-3px);
}
.pg-index {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.pg-copy { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.pg-copy strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 14.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pg-copy span {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pg-go {
  color: var(--text-muted);
  opacity: 0;
  transform: translateX(-5px);
  transition: opacity .2s ease, transform .25s var(--ease-out);
}
.pg-card:hover .pg-go { opacity: 1; transform: none; }

@keyframes profile-orbit {
  to { transform: rotate(360deg); }
}

@media (max-width: 700px) {
  .cprofile { padding: 20px 14px 52px; }
  .cprofile-back { margin-bottom: 14px; }
  .cprofile-head {
    grid-template-columns: auto minmax(0, 1fr);
    gap: 17px;
    min-height: 0;
    padding: 28px 22px;
    border-radius: 21px;
  }
  .profile-avatar { padding: 6px; }
  .profile-avatar :deep(.el-avatar) {
    width: 66px !important;
    height: 66px !important;
    font-size: 22px;
  }
  .cprofile-meta > p:last-child { display: none; }
  .profile-kicker { font-size: 8px; letter-spacing: .1em; }
  .cprofile-meta h1 { font-size: 1.45rem; }
  .profile-count {
    grid-column: 1 / -1;
    align-items: flex-start;
    padding: 18px 0 0;
    border-top: 1px solid var(--border);
    border-left: 0;
  }
  .profile-count strong { font-size: 28px; }
  .profile-count span { margin-top: 5px; }
  .cprofile-pages { margin-top: 44px; }
  .pages-head h2 { font-size: 1.15rem; }
  .pg-list { grid-template-columns: 1fr; }
  .pg-card { min-height: 78px; padding: 13px 14px; }
  .pg-go { opacity: 1; transform: none; }
}
</style>
