# 家人侠首轮宣传素材

## 第一条：联系爷爷

定位：不拍真人，不使用真实家庭资料，用生活痛点、合成数据实拍图和字幕讲清楚
「联系提醒」。竖屏成片 1080×1920、18 秒、无配乐，上传平台时可直接选平台曲库中的
轻音乐；字幕本身已经能独立讲完内容。

生成命令（在 `apps/web` 下）：

```powershell
npm ci
npm run promo-call-video
```

产物默认写到产品根目录 `runtime/promo/`：

- `jiarenxia-xhs-call-vertical.mp4` — 竖屏成片
- `jiarenxia-xhs-call-cover.png` — 封面候选
- `jiarenxia-xhs-call-frame-*.png` — 提醒、记录和片尾三张分镜预览

生成过程使用本机 Edge 的 Canvas 与 MediaRecorder，不依赖 FFmpeg。源图为
`docs/images/realdevice_call_reminder.jpg` 与 `docs/images/screenshot_reminder.png`，均为合成
示例数据。

建议发布标题：

> 总想着给爷爷打电话，一忙又忘了

建议正文：

> 不是不惦记，是日子过得太快。于是我做了一个很简单的联系提醒：到日子提醒我，
> 打完电话记一笔，下次重新开始算。它叫家人侠，完全免费、不用注册，家人的资料只
> 存在自己的手机里。目前只有 Android，我想先找一些真正会用它记家里事的人试试。

这条内容使用匿名来源链接：

`https://jiarenxia.com/download?src=xhs-call`

下载服务只按日期、安装包与来源码汇总次数，不记录 IP、设备或用户身份。

## 首轮来源码

| 来源码 | 内容 |
|---|---|
| `xhs-call` | 小红书：联系爷爷 |
| `xhs-kinship` | 小红书：认亲与称谓 |
| `bili-story` | B站：开发故事 |
| `wx-video` | 微信视频号 |

查看最近 30 天的分来源下载次数：

```powershell
jrx.cmd dl-stats
```
