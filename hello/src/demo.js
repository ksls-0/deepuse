/**
 * 演示模式配置。
 *
 * 演示版去掉了登录/注册入口，进站自动用下面这个账号登录。
 * 后端完全没动（JWT、鉴权、/user/** 接口都还在），只是前端不再让人看见登录页。
 *
 * ⚠️ 账号密码会被打进前端 bundle，打包后能被搜到。
 *    这**只适合短期演示**：演示结束请立刻改密码，或把 DEMO_MODE 关掉重新构建。
 *
 * 想恢复登录：把 DEMO_MODE 改成 false，再把 router 里那三条 login/register
 * 路由取消注释即可（文件都还在）。
 */
export const DEMO_MODE = true;

export const DEMO_ACCOUNT = process.env.VUE_APP_DEMO_ACCOUNT || '13800138000';
export const DEMO_PASSWORD = process.env.VUE_APP_DEMO_PASSWORD || 'demo123456';
