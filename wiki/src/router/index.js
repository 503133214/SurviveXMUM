import {createRouter, createWebHistory} from "vue-router";

const routes = [
  {
    path: "/",
    name: "Home",
    component: () => import("@/views/HomePage.vue"),
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/LoginPage.vue"),
  },
  {
    path: "/profile",
    name: "Profile",
    component: () => import("@/views/ProfilePage.vue"),
    meta: { requiresAuth: true }
  },
  {
    path: "/favorites",
    name: "Favorites",
    component: () => import("@/views/FavoritesPage.vue"),
    meta: { requiresAuth: true }
  },
  {
    path: "/feedback",
    name: "Feedback",
    component: () => import("@/views/FeedbackPage.vue"),
  },
  {
    path: "/docs/:pathMatch(.*)*",
    name: "DocPage",
    component: () => import("@/views/DocPage.vue"),
    props: route => {
      const pathMatch = route.params.pathMatch;
      const pathString = Array.isArray(pathMatch) ? pathMatch.join('/') : pathMatch;
      return { pathMatch: pathString || '' };
    },
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: () => import("@/views/NotFound.vue"),
  },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  let isLoggedIn = false
  if (token) {
    try {
      const authObj = JSON.parse(token)
      isLoggedIn = authObj.expire > Date.now()
    } catch {
      isLoggedIn = false
    }
  }
  if (to.meta.requiresAuth && !isLoggedIn) {
    next('/login')
  } else {
    next()
  }
})

export default router;
