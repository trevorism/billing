<script>
import axios from 'axios'

export default {
  data() {
    return {
      count: 0,
      val: ''
    }
  },
  mounted() {
    axios
      .get('api/ping')
      .then((x) => {
        this.val = x.data
      })
      .catch(() => {
        this.val = 'Error'
      })
  }
}
</script>

<script setup>
defineProps({
  msg: String
})
</script>

<template>
  <h1 class="grid justify-items-center">{{ msg }}</h1>
  <h1 class="grid justify-items-center">{{ val }}</h1>

  <va-icon class="grid justify-items-center" name="home" size="large" color="red"></va-icon>
</template>

<style scoped></style>
