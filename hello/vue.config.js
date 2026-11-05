const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  // 关闭 thread-loader 并行编译：它依赖 child_process.spawn + 管道 stdio，
  // 在受限沙箱/CI 环境下会抛 spawn EPERM。单进程构建稍慢但更稳。
  parallel: false,
  devServer: {
    port: 8082,
    // 路由使用 history 模式，深链（如 /explore/sets/1）必须回退到 index.html，
    // 否则刷新或直接访问会 404。生产部署时也需要服务器做同样的 fallback。
    historyApiFallback: true,
    // 前端 axios 用相对路径（baseURL 为空），开发期由这里代理到后端 8081，
    // 因此开发期完全不依赖 CORS。
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/user': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
