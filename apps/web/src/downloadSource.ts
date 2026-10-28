/**
 * 宣传内容使用的匿名下载来源码。
 *
 * 来源码只用于下载服务按天汇总次数，不对应用户、设备或 IP。新增来源时需同步
 * 更新 apps/dl-worker/src/index.js 的白名单，避免被归入 direct。
 */
export const CAMPAIGN_DOWNLOAD_SOURCES = [
  "xhs-call",
  "xhs-kinship",
  "bili-story",
  "wx-video",
] as const;

export type CampaignDownloadSource = typeof CAMPAIGN_DOWNLOAD_SOURCES[number];
export type WebDownloadSource = "web" | CampaignDownloadSource;

const CAMPAIGN_SOURCE_SET = new Set<string>(CAMPAIGN_DOWNLOAD_SOURCES);

/** 从下载页查询参数中取已知宣传来源；未知值按普通官网访问处理。 */
export function downloadSourceFromSearch(search: string): WebDownloadSource {
  const source = new URLSearchParams(search).get("src");
  return source && CAMPAIGN_SOURCE_SET.has(source)
    ? source as CampaignDownloadSource
    : "web";
}
