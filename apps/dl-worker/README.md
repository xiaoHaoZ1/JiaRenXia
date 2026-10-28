# jrx-dl — 下载服务

家人侠 APK 的下载计数与分发服务：Cloudflare Worker + R2 + D1。

## 结构
- `src/index.js` — Worker：GET 按 `?src=` 计数（D1 `daily` 表），从 R2 取对象；支持 Range（仅客户端带 Range 头时 206，否则 200）。
- `schema.sql` — D1 表结构。
- `wrangler.toml` — 绑定配置（需自行填写）。
- `test/` — `node --test` 单元测试。

## 自行部署
1. `npm i -g wrangler && wrangler login`
2. 建 R2 桶：`wrangler r2 bucket create jrx-download`，上传 APK：`wrangler r2 object put jrx-download/jiarenxia-x.y.z.apk --file <apk>`
3. 建 D1：`wrangler d1 create jrx-stats`，把返回的 `database_id` 填进 `wrangler.toml`；建表：`wrangler d1 execute jrx-stats --remote --file schema.sql`
4. 改 `wrangler.toml` 的 `routes`（自定义域或 pages.dev/workers.dev 域名）、桶名、库名
5. `wrangler deploy`

## 匿名来源统计

宣传链接使用下载页查询参数，例如：

- `https://jiarenxia.com/download?src=xhs-call`
- `https://jiarenxia.com/download?src=xhs-kinship`
- `https://jiarenxia.com/download?src=bili-story`
- `https://jiarenxia.com/download?src=wx-video`

下载页只把白名单内的来源码带到 APK 地址；Worker 按「中国时区日期 + 文件 + 来源」
累计次数，不读取或保存 IP、UA、设备及用户信息。未知来源统一记为 `direct`。运行
产品根目录的 `jrx.cmd dl-stats` 可查看最近 30 天明细和分来源汇总。
