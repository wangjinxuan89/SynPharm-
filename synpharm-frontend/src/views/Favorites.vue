<template>
  <div class="fav">
    <Sidebar />
    <main class="fav__main">
      <header class="fav__header">
        <div>
          <h1 class="fav__title">我的收藏</h1>
          <p class="fav__subtitle">收藏的预测结果，随时回看</p>
        </div>
        <span class="fav__count">{{ favorites.length }} 条收藏</span>
      </header>

      <section class="fav__list">
        <div v-if="loading" class="fav__state">加载中...</div>
        <div v-else-if="loadError" class="fav__state fav__state--error">{{ loadError }}</div>
        <template v-else>
          <div class="fav__grid">
            <ResultCard
              v-for="result in favorites"
              :key="String(result.id)"
              :result="result"
              :favorited="true"
              @detail="handleDetail"
              @3d="handle3D"
              @delete="handleDelete"
              @favorite="handleUnfavorite"
            />
          </div>

          <div v-if="favorites.length === 0" class="fav__empty">
            <span class="fav__empty-icon">⭐</span>
            <span class="fav__empty-text">还没有收藏任何结果</span>
            <router-link to="/results" class="fav__empty-link">去查看预测结果</router-link>
          </div>
        </template>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { favoriteApi } from '@/api/favorite'
import { resultApi } from '@/api/predict'
import Sidebar from '@/components/Sidebar.vue'
import ResultCard from '@/components/ResultCard.vue'
import type { PredictionResult } from '@/types'

const favorites = ref<PredictionResult[]>([])
const loading = ref(false)
const loadError = ref('')
const router = useRouter()

const loadFavorites = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const page = await favoriteApi.list(1, 100)
    favorites.value = page.list
  } catch (error: unknown) {
    loadError.value = error instanceof Error ? error.message : '加载收藏失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadFavorites)

const handleUnfavorite = async (result: PredictionResult) => {
  try {
    await favoriteApi.remove(result.id)
    ElMessage.success('已取消收藏')
    await loadFavorites()
  } catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : '取消收藏失败')
  }
}

const handleDetail = (result: PredictionResult) => {
  router.push('/result/' + String(result.id))
}

const handle3D = (result: PredictionResult) => {
  router.push({
    path: '/visualization',
    query: {
      id: String(result.id),
      targetName: result.targetName || '',
      targetId: result.targetId || ''
    }
  })
}

const handleDelete = async (result: PredictionResult) => {
  try {
    await ElMessageBox.confirm('确定删除该预测结果吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await favoriteApi.remove(result.id)
    await resultApi.deleteResult(result.id)
    ElMessage.success('删除成功')
    await loadFavorites()
  } catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}
</script>

<style lang="scss" scoped>
.fav {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
  padding-top: $header-height;
}

.fav__main {
  flex: 1;
  padding: $spacing-xl;
  max-width: 1100px;
}

.fav__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: $spacing-lg;
}

.fav__title {
  font-size: $font-size-2xl;
  font-weight: 700;
  color: $text-primary;
}

.fav__subtitle {
  margin-top: $spacing-xs;
  font-size: $font-size-sm;
  color: $text-muted;
}

.fav__count {
  font-size: $font-size-sm;
  color: $text-secondary;
  background: $bg-tertiary;
  padding: $spacing-xs $spacing-md;
  border-radius: 999px;
}

.fav__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: $spacing-md;
}

.fav__state {
  padding: $spacing-2xl;
  text-align: center;
  color: $text-muted;
  &--error { color: $error-color; }
}

.fav__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-3xl;
  color: $text-muted;
}

.fav__empty-icon {
  font-size: 48px;
  margin-bottom: $spacing-md;
}

.fav__empty-text {
  font-size: $font-size-base;
  margin-bottom: $spacing-md;
}

.fav__empty-link {
  color: $accent-color;
  text-decoration: none;
  font-size: $font-size-sm;
}
</style>
