<template>
  <div id="app">
    <Header v-if="showHeader" />
    <router-view />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Header from './components/Header.vue'

const route = useRoute()

const showHeader = computed(() => {
  // 登录页不显示 Header
  return route.path !== '/login' && localStorage.getItem('token')
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
body {
  font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
  background: #f5f7fa;
}
#app {
  min-height: 100vh;
}
</style>
