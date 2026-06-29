<template>
  <div class="fav-page">
    <header class="fav-header">
      <h1>收藏与历史</h1>
      <p class="fav-sub">你收藏的页面与最近的浏览记录</p>
    </header>

    <el-tabs v-model="activeTab" class="fav-tabs">
      <!-- 我的收藏 -->
      <el-tab-pane name="favorites">
        <template #label>
          我的收藏<span v-if="favorites.length" class="tab-count">{{ favorites.length }}</span>
        </template>

        <el-empty v-if="!favorites.length" description="还没有收藏任何内容">
          <el-button type="primary" @click="$router.push('/docs/README')">去浏览文档</el-button>
        </el-empty>

        <div v-else class="card-grid">
          <article
            v-for="item in favorites"
            :key="item.id"
            class="grid-card"
            @click="$router.push(item.path)"
          >
            <h3 class="gc-title">{{ item.title }}</h3>
            <p class="gc-desc">{{ item.description || '暂无简介' }}</p>
            <div class="gc-foot">
              <span class="gc-time">收藏于 {{ item.createTime }}</span>
              <el-button type="danger" link size="small" @click.stop="removeFavorite(item.id)">
                取消收藏
              </el-button>
            </div>
          </article>
        </div>
      </el-tab-pane>

      <!-- 浏览历史 -->
      <el-tab-pane name="history">
        <template #label>
          浏览历史<span v-if="history.length" class="tab-count">{{ history.length }}</span>
        </template>

        <el-empty v-if="!history.length" description="暂无浏览记录">
          <el-button type="primary" @click="$router.push('/docs/README')">去浏览文档</el-button>
        </el-empty>

        <template v-else>
          <div class="hist-bar">
            <span class="hist-hint">仅保留最近 50 条</span>
            <el-button type="danger" link @click="clearHistory">清空历史记录</el-button>
          </div>
          <div class="card-grid">
            <article
              v-for="item in history"
              :key="item.id"
              class="grid-card"
              @click="$router.push(item.path)"
            >
              <h3 class="gc-title">{{ item.title }}</h3>
              <p class="gc-desc">{{ item.description || '暂无简介' }}</p>
              <div class="gc-foot">
                <span class="gc-time">浏览于 {{ item.visitTime }}</span>
              </div>
            </article>
          </div>
        </template>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post } from '@/net/index.js'

export default {
  name: 'FavoritesPage',
  setup() {
    const activeTab = ref('favorites')
    const favorites = ref([])
    const history = ref([])

    const loadFavorites = () => {
      get('/user/favorites',
        (data) => { favorites.value = data || [] },
        () => { favorites.value = [] }
      )
    }

    const loadHistory = () => {
      get('/user/history',
        (data) => { history.value = data || [] },
        () => { history.value = [] }
      )
    }

    const removeFavorite = (id) => {
      ElMessageBox.confirm('确定要取消收藏吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        post(`/user/favorites/${id}/remove`, {},
          () => {
            ElMessage.success('已取消收藏')
            favorites.value = favorites.value.filter(item => item.id !== id)
          },
          (message) => { ElMessage.error(message || '操作失败') }
        )
      }).catch(() => {})
    }

    const clearHistory = () => {
      ElMessageBox.confirm('确定要清空所有浏览历史吗？此操作不可恢复。', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        post('/user/history/clear', {},
          () => {
            ElMessage.success('浏览历史已清空')
            history.value = []
          },
          (message) => { ElMessage.error(message || '操作失败') }
        )
      }).catch(() => {})
    }

    onMounted(() => {
      loadFavorites()
      loadHistory()
    })

    return { activeTab, favorites, history, removeFavorite, clearHistory }
  }
}
</script>

<style scoped>
.fav-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}

.fav-header {
  margin-bottom: 20px;
}
.fav-header h1 {
  margin: 0;
  font-size: 1.8rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}
.fav-sub {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 14px;
}

.fav-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 600;
}
.tab-count {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--bg-hover);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
}

.hist-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.hist-hint { color: var(--text-muted); font-size: 13px; }

/* 响应式网格：宽屏多列铺满，窄屏自动单列 */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.grid-card {
  display: flex;
  flex-direction: column;
  min-height: 132px;
  padding: 18px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}
.grid-card:hover {
  border-color: var(--brand);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}
.gc-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gc-desc {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13.5px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.gc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 14px;
}
.gc-time { color: var(--text-muted); font-size: 12px; }

@media (max-width: 767px) {
  .fav-page { padding: 20px 14px 48px; }
  .fav-header h1 { font-size: 1.5rem; }
  .card-grid { grid-template-columns: 1fr; gap: 12px; }
}
</style>
