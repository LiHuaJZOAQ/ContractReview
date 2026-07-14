<template>
  <el-container style="height:100vh">
    <el-header class="header">
      <span class="title">智能合同风险审查系统</span>
      <el-button type="danger" text @click="handleLogout">退出登录</el-button>
    </el-header>
    <el-container>
      <el-aside width="200px">
        <el-menu :router="true" default-active="/upload" style="height:100%">
          <el-menu-item index="/upload">
            <el-icon><Upload /></el-icon>
            <span>合同上传</span>
          </el-menu-item>
          <el-menu-item index="/history">
            <el-icon><Histogram /></el-icon>
            <span>审查历史</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Upload, Histogram } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #dcdfe6;
  box-shadow: 0 1px 4px rgba(0,0,0,0.1);
}
.title {
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
}
</style>
