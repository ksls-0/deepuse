/**
 * 把 design/banner-9x16.svg 渲染成高清 PNG。
 *
 * 为什么不用无头浏览器：这个环境里 Chrome 的 mojo 命名管道被禁止，
 * headless 起不来。resvg 是纯进程内光栅化，不需要管道，且用 Windows
 * 自带字体（微软雅黑 / 等线）渲染中文，字重和字形都可控。
 *
 * 用法： node render.js [目标宽度]
 *   默认 2160（即 1080 的 2 倍，9:16 = 2160x3840）
 */
const fs = require('fs');
const path = require('path');
const { Resvg } = require('@resvg/resvg-js');

const width = Number(process.argv[2] || 2160);
const dir = __dirname;

const svg = fs.readFileSync(path.join(dir, 'banner-9x16.svg'), 'utf8');

const fontFiles = [
  'C:/Windows/Fonts/msyh.ttc',   // 微软雅黑 Regular
  'C:/Windows/Fonts/msyhbd.ttc', // 微软雅黑 Bold
  'C:/Windows/Fonts/msyhl.ttc',  // 微软雅黑 Light
  'C:/Windows/Fonts/consola.ttf', // Consolas（等宽小标签）
  'C:/Windows/Fonts/consolab.ttf'
].filter((f) => fs.existsSync(f));

const resvg = new Resvg(svg, {
  fitTo: { mode: 'width', value: width },
  background: '#050c18',
  font: {
    fontFiles,
    loadSystemFonts: true,
    defaultFontFamily: 'Microsoft YaHei'
  }
});

const rendered = resvg.render();
const png = rendered.asPng();
const out = path.join(dir, `banner-${rendered.width}x${rendered.height}.png`);
fs.writeFileSync(out, png);

console.log(`written ${out}  ${rendered.width}x${rendered.height}  ${(png.length / 1024 / 1024).toFixed(2)} MB`);
console.log(`fonts loaded: ${fontFiles.length}`);
