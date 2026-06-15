<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import MenuBar from '@trevorism/ui-header-bar'
import PaymentForm from './components/PaymentForm.vue'
import { isLoggedIn } from './session'

// The session cookie is shared across trevorism.com tabs and can change after this tab loaded (login in
// another tab, or expiry). Re-check whenever the tab regains focus/visibility so the UI doesn't get stuck
// in the wrong auth state until a manual reload.
const loggedIn = ref(isLoggedIn())
const refresh = () => { loggedIn.value = isLoggedIn() }

onMounted(() => {
  window.addEventListener('focus', refresh)
  document.addEventListener('visibilitychange', refresh)
})
onUnmounted(() => {
  window.removeEventListener('focus', refresh)
  document.removeEventListener('visibilitychange', refresh)
})
</script>

<template>
  <menu-bar></menu-bar>
  <payment-form v-if="loggedIn"></payment-form>
  <va-alert v-else color="warning" border="left" class="login-note">
    Please log in to make payments.
  </va-alert>
</template>

<style scoped>
.login-note { max-width: 640px; margin: 2rem auto; }
</style>
