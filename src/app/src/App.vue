<script setup>
import { MenuBar } from '@trevorism/ui-header-bar'
import { useAuth } from '@trevorism/ui-auth'
import PaymentForm from './components/PaymentForm.vue'

// The shared session is reactive, and the library re-checks it when a hidden tab
// comes back, so this tab no longer has to listen for focus to notice a login or
// an expiry that happened somewhere else.
const { isAuthenticated: loggedIn, loading } = useAuth()
</script>

<template>
  <menu-bar></menu-bar>
  <payment-form v-if="loggedIn"></payment-form>
  <va-alert v-else-if="loading" color="info" border="left" class="login-note">
    Checking your session…
  </va-alert>
  <va-alert v-else color="warning" border="left" class="login-note">
    Please log in to make payments.
  </va-alert>
</template>

<style scoped>
.login-note { max-width: 640px; margin: 2rem auto; }
</style>
