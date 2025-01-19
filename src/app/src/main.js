import { createApp } from 'vue'
import App from './App.vue'

import VueClickAway from 'vue3-click-away'
import { createVuestic } from 'vuestic-ui'
import config from '../vuestic.config.js'
import VueMixpanel from 'vue-mixpanel'
import './style.css'

const app = createApp(App)
app.use(VueClickAway)
app.use(createVuestic({ config }))
app.use(VueMixpanel, {
  token: '3ad96fc246692627d5addd73aa6072ae',
  config: {
    track_pageview: true,
    secure_cookie: true,
    same_site: 'None'
  }
})
app.mount('#app')
