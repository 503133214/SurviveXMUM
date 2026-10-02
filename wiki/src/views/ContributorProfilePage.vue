<template>
  <div class="cprofile">
    <div v-if="loading" class="cp-loading">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else-if="profile">
      <router-link class="cprofile-back" to="/contributors">
        <span aria-hidden="true">←</span> 返回贡献者与致谢
      </router-link>

      <!-- 扁平的一行：头像、名字和一行事实数据，不再做发光卡片和大号统计数字 -->
      <header class="cprofile-head">
        <el-avatar class="cprofile-avatar" :size="56" :src="profile.avatar || undefined">
          {{ initial(profile.displayName) }}
        </el-avatar>
        <div class="cprofile-meta">
          <h1>{{ profile.displayName }}</h1>
          <p class="cprofile-stats">
            已通过投稿 {{ profile.count || 0 }} 篇 · 新建 {{ profile.createdCount || 0 }}
            · 修改 {{ profile.editedCount || 0 }} · 讨论 {{ profile.commentCount || 0 }}
          </p>
          <p class="cprofile-thanks">谢谢你把经验写下来，让后来的人看得见。</p>
        </div>
      </header>

      <section v-if="badges.length" class="cprofile-section">
        <div class="section-head">
          <h2>徽章</h2>
          <span>已获得 {{ earnedCount }} / {{ badges.length }}</span>
        </div>
        <ContributorBadges :badges="badges" variant="grid" />
      </section>

      <section class="cprofile-section">
        <div class="section-head">
          <h2>参与维护的页面</h2>
          <span>{{ pages.length }} 个页面</span>
        </div>

        <el-empty v-if="!pages.length" description="暂无已发布的贡献页面" />
        <!-- 和贡献榜同一种带边框的列表：每行就是一个链接，只显示标题和路径 -->
        <ol v-else class="pg-list">
          <li v-for="page in pages" :key="page.path">
            <router-link class="pg-row" :to="`/docs/${page.path}`">
              <span class="pg-title">{{ page.title }}</span>
              <span class="pg-path">{{ page.path }}</span>
            </router-link>
          </li>
        </ol>
      </section>
    </template>

    <div v-else class="profile-empty">
      <el-empty description="未找到该贡献者">
        <el-button @click="$router.push('/contributors')">返回贡献榜</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script>
import ContributorBadges from '@/components/ContributorBadges.vue'
import { getContributorProfile } from '@/net/index.js'

export default {
  name: 'ContributorProfilePage',
  components: { ContributorBadges },
  props: { id: { type: String, default: '' } },
  data() {
    return { profile: null, loading: true }
  },
  watch: {
    id() { this.load() },
  },
  computed: {
    badges() {
      return (this.profile && this.profile.badges) || []
    },
    pages() {
      return (this.profile && this.profile.pages) || []
    },
    earnedCount() {
      return this.badges.filter((b) => b.earned).length
    },
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
  max-width: 960px;
  margin: 0 auto;
  padding: 40px 20px 72px;
}
.cp-loading,
.profile-empty { padding: 48px 0; }

.cprofile-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 20px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
}
.cprofile-back:hover { color: var(--text-primary); text-decoration: none; }

.cprofile-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--border);
}
.cprofile-head :deep(.cprofile-avatar) {
  flex-shrink: 0;
  background: var(--bg-hover);
  color: var(--text-primary);
  font-size: 22px;
  font-weight: 600;
}
.cprofile-meta { min-width: 0; }
.cprofile-meta h1 {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.3;
}
.cprofile-stats {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.cprofile-thanks {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.cprofile-section { margin-top: 32px; }
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}
.section-head h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.3;
}
.section-head > span {
  color: var(--text-muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* overflow: hidden 让首末行的悬停底色跟着圆角裁切 */
.pg-list {
  overflow: hidden;
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
}
.pg-list li + li { border-top: 1px solid var(--border); }
.pg-row {
  display: flex;
  min-height: 44px;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  padding: 10px 16px;
  color: var(--text-primary);
  text-decoration: none;
  transition: background-color var(--dur) ease;
}
.pg-row:hover { background: var(--bg-subtle); color: var(--text-primary); text-decoration: none; }
/* 容器裁掉了溢出，焦点环向内收才不会被切掉 */
.pg-row:focus-visible { outline-offset: -2px; }
.pg-title,
.pg-path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pg-title { font-size: 14px; font-weight: 600; }
.pg-path { color: var(--text-muted); font-size: 12px; }

@media (max-width: 700px) {
  .cprofile { padding: 24px 16px 56px; }
  .cprofile-back { margin-bottom: 14px; }
  /* 手机上头部只留名字和数据，致谢语让位给正文 */
  .cprofile-thanks { display: none; }
  .cprofile-section { margin-top: 28px; }
}
</style>
