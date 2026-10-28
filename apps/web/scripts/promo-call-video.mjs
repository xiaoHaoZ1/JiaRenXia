// 生成首条宣传短视频：纯字幕 + 合成数据截图，不使用真人和真实家庭资料。
//
// 在 apps/web 下运行：
//   npm run promo-call-video
// 也可在命令末尾传入输出目录。默认产物写到产品根目录 runtime/promo/，不入库。

import { mkdirSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "@playwright/test";

const HERE = resolve(fileURLToPath(import.meta.url), "..");
const ROOT = resolve(HERE, "../../..");
const OUTPUT = resolve(process.argv[2] || join(ROOT, "runtime", "promo"));
const CALL_SHOT = join(ROOT, "docs", "images", "realdevice_call_reminder.jpg");
const AFFAIRS_SHOT = join(ROOT, "docs", "images", "screenshot_reminder.png");

mkdirSync(OUTPUT, { recursive: true });

function dataUri(file, mime) {
  return `data:${mime};base64,${readFileSync(file).toString("base64")}`;
}

const callShot = dataUri(CALL_SHOT, "image/jpeg");
const affairsShot = dataUri(AFFAIRS_SHOT, "image/png");

const html = `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <style>
    * { box-sizing: border-box; }
    html, body { margin: 0; width: 1080px; height: 1920px; overflow: hidden; background: #fdf8f2; }
    canvas { display: block; width: 1080px; height: 1920px; }
  </style>
</head>
<body>
<canvas id="video" width="1080" height="1920"></canvas>
<script>
const W = 1080;
const H = 1920;
const DURATION = 18;
const canvas = document.querySelector("#video");
const ctx = canvas.getContext("2d");
const colors = {
  bg: "#fdf8f2",
  surface: "#ffffff",
  primary: "#b4473c",
  primarySoft: "#fcebe5",
  text: "#211b18",
  muted: "#776a63",
  border: "#ecded3",
  green: "#57755b",
};

const callShot = new Image();
callShot.src = ${JSON.stringify(callShot)};
const affairsShot = new Image();
affairsShot.src = ${JSON.stringify(affairsShot)};

function clamp(v, min = 0, max = 1) { return Math.max(min, Math.min(max, v)); }
function ease(v) { v = clamp(v); return 1 - Math.pow(1 - v, 3); }
function sceneOpacity(t, start, end, fade = 0.45) {
  return clamp((t - start) / fade) * clamp((end - t) / fade);
}
function roundedPath(x, y, w, h, r) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}
function fillRound(x, y, w, h, r, fill, stroke = null, lineWidth = 1) {
  roundedPath(x, y, w, h, r);
  ctx.fillStyle = fill;
  ctx.fill();
  if (stroke) {
    ctx.strokeStyle = stroke;
    ctx.lineWidth = lineWidth;
    ctx.stroke();
  }
}
function text(value, x, y, size, weight = 500, color = colors.text, align = "left") {
  ctx.fillStyle = color;
  ctx.textAlign = align;
  ctx.textBaseline = "alphabetic";
  ctx.font = weight + " " + size + 'px system-ui, "Microsoft YaHei", sans-serif';
  ctx.fillText(value, x, y);
}
function drawLogo(x, y, scale = 1) {
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(scale, scale);
  ctx.beginPath();
  ctx.moveTo(0, 56);
  ctx.bezierCurveTo(-42, 20, -55, -4, -36, -23);
  ctx.bezierCurveTo(-20, -39, -3, -32, 0, -17);
  ctx.bezierCurveTo(3, -32, 20, -39, 36, -23);
  ctx.bezierCurveTo(55, -4, 42, 20, 0, 56);
  ctx.fillStyle = colors.primary;
  ctx.fill();
  ctx.beginPath();
  ctx.moveTo(-21, 7);
  ctx.lineTo(0, -11);
  ctx.lineTo(21, 7);
  ctx.lineTo(15, 7);
  ctx.lineTo(15, 29);
  ctx.lineTo(-15, 29);
  ctx.lineTo(-15, 7);
  ctx.closePath();
  ctx.fillStyle = "#f8eee5";
  ctx.fill();
  ctx.restore();
}
function drawBrandHeader() {
  drawLogo(92, 83, 0.55);
  text("家人侠", 140, 100, 42, 800);
}
function drawPhone(image, y, progress = 1) {
  const w = 730;
  const h = w * image.naturalHeight / image.naturalWidth;
  const x = (W - w) / 2;
  const lift = (1 - ease(progress)) * 45;
  ctx.save();
  ctx.shadowColor = "rgba(69, 43, 32, 0.18)";
  ctx.shadowBlur = 38;
  ctx.shadowOffsetY = 18;
  fillRound(x - 13, y - 13 + lift, w + 26, h + 26, 54, "#fff", colors.border, 2);
  ctx.shadowColor = "transparent";
  roundedPath(x, y + lift, w, h, 42);
  ctx.clip();
  ctx.drawImage(image, x, y + lift, w, h);
  ctx.restore();
}
function drawIntro(t, alpha) {
  ctx.save();
  ctx.globalAlpha = alpha;
  drawBrandHeader();
  const p = ease((t - 0.2) / 1.2);
  ctx.translate(0, (1 - p) * 34);
  text("总想着给爷爷", W / 2, 620, 92, 850, colors.text, "center");
  text("打个电话", W / 2, 742, 112, 900, colors.primary, "center");
  text("可一忙，又忘了。", W / 2, 900, 66, 650, colors.text, "center");
  fillRound(160, 1080, 760, 150, 75, colors.primarySoft);
  text("不是不惦记，是日子过得太快。", W / 2, 1174, 44, 600, colors.primary, "center");
  ctx.restore();
}
function drawReminder(t, alpha) {
  ctx.save();
  ctx.globalAlpha = alpha;
  text("到时间，轻轻提醒一下", W / 2, 126, 58, 800, colors.text, "center");
  text("不用登录，也不上传家人资料", W / 2, 194, 34, 500, colors.muted, "center");
  drawPhone(callShot, 245, (t - 3.8) / 1.2);
  ctx.restore();
}
function drawRecord(t, alpha) {
  ctx.save();
  ctx.globalAlpha = alpha;
  text("打完电话，点一下「记一次」", W / 2, 126, 56, 800, colors.text, "center");
  text("下次该联系的日子，重新开始算", W / 2, 194, 34, 500, colors.muted, "center");
  const phoneY = 315;
  drawPhone(affairsShot, phoneY, (t - 8.4) / 1.1);

  const pulse = 1 + Math.sin((t - 9.2) * 4) * 0.06;
  const cx = 784;
  const cy = 907;
  ctx.translate(cx, cy);
  ctx.scale(pulse, pulse);
  ctx.beginPath();
  ctx.arc(0, 0, 84, 0, Math.PI * 2);
  ctx.strokeStyle = colors.primary;
  ctx.lineWidth = 8;
  ctx.stroke();
  ctx.restore();
}
function drawOutro(t, alpha) {
  ctx.save();
  ctx.globalAlpha = alpha;
  const p = ease((t - 12.8) / 1.1);
  ctx.translate(0, (1 - p) * 35);
  drawLogo(W / 2, 480, 2.0);
  text("家人侠", W / 2, 700, 96, 900, colors.text, "center");
  text("惦记，不能只靠记性。", W / 2, 830, 62, 750, colors.primary, "center");
  fillRound(128, 990, 824, 190, 36, colors.surface, colors.border, 3);
  text("免费 · 无广告 · 不用注册", W / 2, 1066, 42, 700, colors.text, "center");
  text("家人资料只存在你的手机里", W / 2, 1138, 36, 500, colors.muted, "center");
  fillRound(245, 1320, 590, 112, 56, colors.primary);
  text("jiarenxia.com", W / 2, 1393, 45, 750, "#fff", "center");
  text("Android 8.0 及以上", W / 2, 1495, 30, 500, colors.muted, "center");
  ctx.restore();
}

function renderAt(t) {
  ctx.fillStyle = colors.bg;
  ctx.fillRect(0, 0, W, H);
  const glow = ctx.createRadialGradient(880, 1700, 0, 880, 1700, 520);
  glow.addColorStop(0, "rgba(180,71,60,0.10)");
  glow.addColorStop(1, "rgba(180,71,60,0)");
  ctx.fillStyle = glow;
  ctx.fillRect(0, 0, W, H);

  drawIntro(t, sceneOpacity(t, 0, 4.2));
  drawReminder(t, sceneOpacity(t, 3.8, 8.8));
  drawRecord(t, sceneOpacity(t, 8.4, 13.2));
  drawOutro(t, sceneOpacity(t, 12.8, 18.01));
}

window.renderAt = renderAt;
window.startRecording = async () => {
  await Promise.all([callShot.decode(), affairsShot.decode()]);
  renderAt(0);
  const stream = canvas.captureStream(30);
  const preferred = "video/mp4;codecs=avc1.42E01E";
  const mimeType = MediaRecorder.isTypeSupported(preferred) ? preferred : "video/mp4";
  const recorder = new MediaRecorder(stream, { mimeType, videoBitsPerSecond: 7_000_000 });
  const chunks = [];
  recorder.ondataavailable = event => {
    if (event.data.size) chunks.push(event.data);
  };
  const stopped = new Promise(resolve => recorder.addEventListener("stop", resolve, { once: true }));
  recorder.start(500);

  const started = performance.now();
  await new Promise(resolve => {
    function frame(now) {
      const elapsed = (now - started) / 1000;
      renderAt(Math.min(elapsed, DURATION));
      if (elapsed < DURATION) requestAnimationFrame(frame);
      else resolve();
    }
    requestAnimationFrame(frame);
  });
  recorder.stop();
  await stopped;

  const blob = new Blob(chunks, { type: mimeType });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = "jiarenxia-xhs-call-vertical.mp4";
  document.body.appendChild(link);
  link.click();
  return { mimeType, size: blob.size };
};
</script>
</body>
</html>`;

const browser = await chromium.launch({ channel: "msedge", headless: true });
const page = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
await page.setContent(html, { waitUntil: "load" });
await page.evaluate(async () => {
  const images = [...document.images];
  await Promise.all(images.map(image => image.decode()));
  await Promise.all([
    document.fonts.load('900 112px system-ui'),
    document.fonts.load('800 58px system-ui'),
  ]);
  window.renderAt(1.8);
});

const cover = join(OUTPUT, "jiarenxia-xhs-call-cover.png");
await page.locator("canvas").screenshot({ path: cover });

const previewFrames = [
  [5.7, "jiarenxia-xhs-call-frame-reminder.png"],
  [10.7, "jiarenxia-xhs-call-frame-record.png"],
  [15.6, "jiarenxia-xhs-call-frame-outro.png"],
];
for (const [time, name] of previewFrames) {
  await page.evaluate(value => window.renderAt(value), time);
  await page.locator("canvas").screenshot({ path: join(OUTPUT, name) });
}

const downloadPromise = page.waitForEvent("download", { timeout: 30_000 });
const result = await page.evaluate(() => window.startRecording());
const download = await downloadPromise;
const video = join(OUTPUT, "jiarenxia-xhs-call-vertical.mp4");
await download.saveAs(video);
await browser.close();

console.log(`宣传视频生成完成：\n  ${video}\n  ${cover}\n  ${previewFrames.length} 张分镜预览\n  ${result.mimeType}，${(result.size / 1024 / 1024).toFixed(1)} MB`);
