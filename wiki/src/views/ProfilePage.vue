<template>
  <div class="profile-page">
    <el-card class="profile-card">
      <template #header>
        <div class="card-header">
          <span>个人中心</span>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="个人资料" name="profile">
          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-width="100px"
          >
            <el-form-item label="头像">
              <el-upload
                class="avatar-uploader"
                action="/api/upload/avatar"
                :headers="uploadHeaders"
                :show-file-list="false"
                :on-success="handleAvatarSuccess"
                :before-upload="beforeAvatarUpload"
              >
                <img v-if="profileForm.avatar" :src="profileForm.avatar" class="avatar" />
                <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
              </el-upload>
            </el-form-item>

            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profileForm.nickname" placeholder="请输入昵称" />
            </el-form-item>

            <el-form-item label="邮箱">
              <el-input v-model="profileForm.userEmail" disabled />
            </el-form-item>

            <el-form-item label="个人简介" prop="bio">
              <el-input
                v-model="profileForm.bio"
                type="textarea"
                :rows="4"
                placeholder="介绍一下自己吧"
              />
            </el-form-item>

            <el-form-item label="兴趣偏好" prop="interests">
              <el-select
                v-model="profileForm.interests"
                multiple
                placeholder="请选择感兴趣的标签"
                style="width: 100%"
              >
                <el-option label="学习" value="study" />
                <el-option label="生活" value="life" />
                <el-option label="美食" value="food" />
                <el-option label="旅行" value="travel" />
                <el-option label="科技" value="tech" />
                <el-option label="娱乐" value="entertainment" />
              </el-select>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="saveProfile" :loading="isSaving">
                保存修改
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="我的数据" name="data">
          <el-empty v-if="!userData.length" description="暂无数据" />
          <div v-else class="data-list">
            <el-timeline>
              <el-timeline-item
                v-for="(item, index) in userData"
                :key="index"
                :timestamp="item.time"
                :type="item.type"
              >
                <el-card class="data-item">
                  <h4>{{ item.title }}</h4>
                  <p>{{ item.content }}</p>
                  <el-link type="primary" @click="$router.push(item.path)">查看详情</el-link>
                </el-card>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-tab-pane>

        <el-tab-pane label="修改密码" name="password">
          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-width="100px"
          >
            <el-form-item label="当前密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>

            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>

            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="changePassword" :loading="isChangingPassword">
                修改密码
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { get, post, accessHeader } from '@/net/index.js'
import { useUserStore } from '@/store/userStore.js'

export default {
  name: 'ProfilePage',
  components: { Plus },
  setup() {
    const userStore = useUserStore()
    const activeTab = ref('profile')
    const isSaving = ref(false)
    const isChangingPassword = ref(false)
    const profileFormRef = ref(null)
    const passwordFormRef = ref(null)

    const profileForm = reactive({
      avatar: '',
      nickname: '',
      userEmail: '',
      bio: '',
      interests: []
    })

    const profileRules = {
      nickname: [
        { max: 20, message: '昵称不能超过20个字符', trigger: 'blur' }
      ],
      bio: [
        { max: 500, message: '个人简介不能超过500个字符', trigger: 'blur' }
      ]
    }

    const passwordForm = reactive({
      oldPassword: '',
      newPassword: '',
      confirmPassword: ''
    })

    const validateConfirmPassword = (rule, value, callback) => {
      if (value !== passwordForm.newPassword) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    }

    const passwordRules = {
      oldPassword: [
        { required: true, message: '请输入当前密码', trigger: 'blur' }
      ],
      newPassword: [
        { required: true, message: '请输入新密码', trigger: 'blur' },
        { min: 6, max: 20, message: '密码长度应为6-20位', trigger: 'blur' }
      ],
      confirmPassword: [
        { required: true, message: '请确认新密码', trigger: 'blur' },
        { validator: validateConfirmPassword, trigger: 'blur' }
      ]
    }

    const userData = ref([])

    const uploadHeaders = computed(() => accessHeader())

    const loadUserInfo = () => {
      get('/user/info',
        (data) => {
          Object.assign(profileForm, data)
          userStore.setUserInfo(data)
        },
        (message) => {
          ElMessage.error(message || '获取用户信息失败')
        }
      )
    }

    const loadUserData = () => {
      get('/user/data',
        (data) => {
          userData.value = data || []
        },
        () => {
          userData.value = []
        }
      )
    }

    const saveProfile = () => {
      profileFormRef.value.validate((valid) => {
        if (!valid) return
        isSaving.value = true
        post('/user/update',
          {
            nickname: profileForm.nickname,
            bio: profileForm.bio,
            interests: profileForm.interests,
            avatar: profileForm.avatar
          },
          () => {
            isSaving.value = false
            ElMessage.success('个人资料保存成功')
            userStore.setUserInfo({ ...userStore.userInfo, ...profileForm })
          },
          (message) => {
            isSaving.value = false
            ElMessage.error(message || '保存失败')
          }
        )
      })
    }

    const changePassword = () => {
      passwordFormRef.value.validate((valid) => {
        if (!valid) return
        isChangingPassword.value = true
        post('/user/password',
          {
            oldPassword: passwordForm.oldPassword,
            newPassword: passwordForm.newPassword
          },
          () => {
            isChangingPassword.value = false
            ElMessage.success('密码修改成功')
            passwordForm.oldPassword = ''
            passwordForm.newPassword = ''
            passwordForm.confirmPassword = ''
          },
          (message) => {
            isChangingPassword.value = false
            ElMessage.error(message || '密码修改失败')
          }
        )
      })
    }

    const handleAvatarSuccess = (response) => {
      if (response.code === 0 && response.data) {
        profileForm.avatar = response.data
        ElMessage.success('头像上传成功')
      } else {
        ElMessage.error(response.message || '上传失败')
      }
    }

    const beforeAvatarUpload = (file) => {
      const isJPG = file.type === 'image/jpeg'
      const isPNG = file.type === 'image/png'
      const isLt2M = file.size / 1024 / 1024 < 2

      if (!isJPG && !isPNG) {
        ElMessage.error('头像图片只能是 JPG 或 PNG 格式!')
        return false
      }
      if (!isLt2M) {
        ElMessage.error('头像图片大小不能超过 2MB!')
        return false
      }
      return true
    }

    onMounted(() => {
      loadUserInfo()
      loadUserData()
    })

    return {
      activeTab,
      profileFormRef,
      profileForm,
      profileRules,
      passwordFormRef,
      passwordForm,
      passwordRules,
      isSaving,
      isChangingPassword,
      userData,
      uploadHeaders,
      saveProfile,
      changePassword,
      handleAvatarSuccess,
      beforeAvatarUpload
    }
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 800px;
  margin: 20px auto;
  padding: 0 20px;
}

.profile-card {
  min-height: 500px;
}

.card-header {
  font-size: 1.25rem;
  font-weight: bold;
}

.avatar-uploader {
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: var(--el-transition-duration-fast);
  width: 120px;
  height: 120px;
}

.avatar-uploader:hover {
  border-color: var(--el-color-primary);
}

.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 120px;
  height: 120px;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar {
  width: 120px;
  height: 120px;
  display: block;
  object-fit: cover;
}

.data-list {
  padding: 10px;
}

.data-item {
  margin-bottom: 10px;
}

.data-item h4 {
  margin: 0 0 8px;
}

.data-item p {
  margin: 0;
  color: #666;
  font-size: 0.9rem;
}
</style>
