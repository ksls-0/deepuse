import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { useUiStore } from './stores/ui';
// 长辈版设计系统（.s-* 工具类 + html.mode-senior 全局规则），全局引一次
import './styles/senior.css';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);

// 必须在 mount 之前实例化：它会把上次选的界面模式写到 <html> 的 class 上，
// 晚一步就会先闪一下大众版样式。
useUiStore(pinia);

app.use(router);
app.mount('#app');
