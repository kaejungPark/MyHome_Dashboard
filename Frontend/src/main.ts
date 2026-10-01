import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// 등록·수정·목록 화면의 공통 스타일
import './assets/common.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
