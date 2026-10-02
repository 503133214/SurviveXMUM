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

        <!-- 每行是真正的 router-link，键盘 Tab 可达；取消收藏放在链接外，避免按钮嵌在 <a> 里 -->
        <ul v-else class="fav-list">
          <li v-for="item in favorites" :key="item.id" class="fav-item">
            <router-link :to="item.path" class="fav-row">
              <span class="fav-main">
                <span class="fav-title">{{ item.title }}</span>
                <span class="fav-desc">{{ item.description || '暂无简介' }}</span>
              </span>
              <span class="fav-time">收藏于 {{ item.createTime }}</span>
            </router-link>
            <el-button class="fav-remove" type="danger" link size="small" @click.stop="removeFavorite(item.id)">
              取消收藏
            </el-button>
          </li>
        </ul>
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
          <ul class="fav-list">
            <li v-for="item in history" :key="item.id" class="fav-item">
              <router-link :to="item.path" class="fav-row">
                <span class="fav-main">
                  <span class="fav-title">{{ item.title }}</span>
                  <span class="fav-desc">{{ item.description || '暂无简介' }}</span>
                </span>
                <span class="fav-time">浏览于 {{ item.visitTime }}</span>
              </router-link>
            </li>
          </ul>
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
  width: 100%;
  max-width: 860px;
  margin: 0 auto;
  padding: 40px 20px 64px;
}

.fav-header {
  margin-bottom: 24px;
}
.fav-header h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0;
  color: var(--text-primary);
}
.fav-sub {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.fav-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  font-weight: 500;
}
/* 数量是中性小徽标：灰底、2px 圆角，不用胶囊 */
.tab-count {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  font-variant-numeric: tabular-nums;
}

.hist-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.hist-hint { color: var(--text-muted); font-size: 13px; }

/* 收藏 / 历史本质是列表：一个描边容器，行间 1px 分隔，悬停只换底色 */
.fav-list {
  overflow: hidden;
  margin: 0;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  list-style: none;
}
.fav-item {
  display: flex;
  align-items: center;
  background: var(--bg-surface);
  transition: background var(--dur);
}
.fav-item + .fav-item { border-top: 1px solid var(--border); }
.fav-item:hover { background: var(--bg-subtle); }

.fav-row {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 16px;
  min-width: 0;
  min-height: 44px;
  padding: 10px 16px;
  color: inherit;
}
.fav-row:hover { text-decoration: none; }
/* 容器 overflow: hidden 会裁掉外扩的焦点框，所以行内收 */
.fav-row:focus-visible { outline-offset: -2px; }

.fav-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}
.fav-title {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fav-desc {
  overflow: hidden;
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fav-time {
  flex-shrink: 0;
  color: var(--text-muted);
  font-size: 12px;
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.fav-remove {
  flex-shrink: 0;
  margin-right: 16px;
}

@media (max-width: 640px) {
  .fav-page { padding: 24px 16px 48px; }
  .fav-header h1 { font-size: 24px; }
  .hist-bar { align-items: flex-start; gap: 10px; }
  /* 手机上时间换到简介下方，标题才有足够宽度 */
  .fav-row { flex-direction: column; align-items: stretch; gap: 4px; }
  .fav-time { text-align: left; }
  .fav-remove { margin-right: 12px; }
}

@media (max-width: 360px) {
  .hist-bar { flex-direction: column; }
  .hist-bar :deep(.el-button) { margin-left: 0; padding-left: 0; }
}
</style>
