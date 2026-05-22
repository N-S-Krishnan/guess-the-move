import { createRouter, createWebHistory } from 'vue-router'
import ImportView from '@/views/ImportView.vue'
import SessionListView from '@/views/SessionListView.vue'
import SetupView from '@/views/SetupView.vue'
import StudyView from '@/views/StudyView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: SessionListView,
    },
    {
      path: '/import',
      name: 'import',
      component: ImportView,
    },
    {
      path: '/setup/:id',
      name: 'setup',
      component: SetupView,
    },
    {
      path: '/study/:id',
      name: 'study',
      component: StudyView,
    },
  ],
})

export default router
