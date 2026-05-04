<template>
  <div class="favorites-page">
    <el-card class="favorites-card">
      <template #header>
        <div class="card-header">
          <span>收藏与历史</span>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="我的收藏" name="favorites">
          <el-empty v-if="!favorites.length" description="暂无收藏内容">
            <el-button type="primary" @click="$router.push('/docs/README')">去浏览</el-button>
          </el-empty>
          <div v-else class="content-list">
            <el-card
              v-for="item in favorites"
              :key="item.id"
              class="content-item"
              shadow="hover"
            >
              <div class="item-header">
                <h4 @click="$router.push(item.path)" class="item-title">{{ item.title }}</h4>
                <el-tag size="small" :type="itemTagType(item.type)">{{ itemTypeText(item.type) }}</el-tag>
              </div>
              <p class="item-desc">{{ item.description }}</p>
              <div class="item-footer">
                <span class="item-time">收藏于 {{ item.createTime }}</span>
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click="removeFavorite(item.id)"
                >
                  取消收藏
                </el-button>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="浏览历史" name="history">
          <el-empty v-if="!history.length" description="暂无浏览记录">
            <el-button type="primary" @click="$router.push('/docs/README')">去浏览</el-button>
          </el-empty>
          <div v-else class="content-list">
            <el-timeline>
              <el-timeline-item
                v-for="item in history"
                :key="item.id"
                :timestamp="item.visitTime"
              >
                <el-card class="history-item" shadow="hover" @click="$router.push(item.path)">
                  <div class="history-header">
                    <h4>{{ item.title }}</h4>
                    <el-tag size="small">{{ itemTypeText(item.type) }}</el-tag>
                  </div>
                  <p class="history-desc">{{ item.description }}</p>
                </el-card>
              </el-timeline-item>
            </el-timeline>
            <div class="history-actions">
              <el-button type="danger" link @click="clearHistory">清空历史记录</el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
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

    const itemTypeText = (type) => {
      const map = { article: '文章', post: '帖子', page: '页面', author: '作者主页' }
      return map[type] || type
    }

    const itemTagType = (type) => {
      const map = { article: 'success', post: 'warning', page: 'info', author: '' }
      return map[type] || ''
    }

    const loadFavorites = () => {
      get('/user/favorites',
        (data) => {
          favorites.value = data || []
        },
        () => {
          favorites.value = []
        }
      )
    }

    const loadHistory = () => {
      get('/user/history',
        (data) => {
          history.value = data || []
        },
        () => {
          history.value = []
        }
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
          (message) => {
            ElMessage.error(message || '操作失败')
          }
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
          (message) => {
            ElMessage.error(message || '操作失败')
          }
        )
      }).catch(() => {})
    }

    onMounted(() => {
      loadFavorites()
      loadHistory()
    })

    return {
      activeTab,
      favorites,
      history,
      itemTypeText,
      itemTagType,
      removeFavorite,
      clearHistory
    }
  }
}
</script>

<style scoped>
.favorites-page {
  max-width: 900px;
  margin: 20px auto;
  padding: 0 20px;
}

.favorites-card {
  min-height: 500px;
}

.card-header {
  font-size: 1.25rem;
  font-weight: bold;
}

.content-list {
  padding: 10px;
}

.content-item {
  margin-bottom: 16px;
  cursor: default;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.item-title {
  margin: 0;
  color: #0c64c1;
  cursor: pointer;
  transition: color 0.2s;
}

.item-title:hover {
  color: #42b983;
}

.item-desc {
  margin: 0 0 12px;
  color: #666;
  font-size: 0.9rem;
  line-height: 1.5;
}

.item-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.item-time {
  font-size: 0.85rem;
  color: #999;
}

.history-item {
  cursor: pointer;
  transition: all 0.2s;
}

.history-item:hover {
  border-color: #0c64c1;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.history-header h4 {
  margin: 0;
}

.history-desc {
  margin: 0;
  color: #666;
  font-size: 0.9rem;
}

.history-actions {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #eee;
}
</style>
