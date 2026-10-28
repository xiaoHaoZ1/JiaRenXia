// 站内线性图标：24×24 画布、圆头描边、颜色跟随 currentColor。全部为本站自绘，不引第三方图标库。

import type { ReactNode } from "react";

const PATHS = {
  // 家人档案：证件卡片
  profile: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="3" />
      <circle cx="9" cy="10.8" r="2.2" />
      <path d="M5.8 16.2c.6-1.6 1.8-2.4 3.2-2.4s2.6.8 3.2 2.4" />
      <path d="M14.5 10h3.5M14.5 13.5h3.5" />
    </>
  ),
  // 称谓：对话气泡
  title: (
    <>
      <path d="M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v7.5a2.5 2.5 0 0 1-2.5 2.5H11l-4.5 3.5v-3.5A2.5 2.5 0 0 1 4 14z" />
      <path d="M8 9h8M8 12.3h5" />
    </>
  ),
  // 关系图：三代节点
  tree: (
    <>
      <rect x="9" y="3" width="6" height="5" rx="1.6" />
      <rect x="3" y="16" width="6" height="5" rx="1.6" />
      <rect x="15" y="16" width="6" height="5" rx="1.6" />
      <path d="M12 8v4M6 16v-2a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v2" />
    </>
  ),
  // 生日：蛋糕
  cake: (
    <>
      <path d="M4 21h16" />
      <path d="M5 21v-7a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v7" />
      <path d="M5 16.2c1.2 1 2.3 1 3.5 0s2.3-1 3.5 0 2.3 1 3.5 0 2.3-1 3.5 0" />
      <path d="M12 12V8.8" />
      <path d="M12 3.6c.9 1 1.3 1.8 1.3 2.5a1.3 1.3 0 0 1-2.6 0c0-.7.4-1.5 1.3-2.5z" />
    </>
  ),
  // 守护：盾牌加对勾
  shield: (
    <>
      <path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.3-7.5 9.5-4.3-1.2-7.5-4.9-7.5-9.5V6z" />
      <path d="M8.8 12.2l2.2 2.2 4.4-4.6" />
    </>
  ),
  // 家事：清单
  tasks: (
    <>
      <rect x="5" y="4.5" width="14" height="16.5" rx="2.5" />
      <path d="M9 4.5v-.7A1.3 1.3 0 0 1 10.3 2.5h3.4A1.3 1.3 0 0 1 15 3.8v.7" />
      <path d="M8.5 11l1.8 1.8 3.2-3.3" />
      <path d="M8.5 16.5h7" />
    </>
  ),
  // 家人共享：分享
  share: (
    <>
      <circle cx="18" cy="5.5" r="2.5" />
      <circle cx="6" cy="12" r="2.5" />
      <circle cx="18" cy="18.5" r="2.5" />
      <path d="M8.2 10.8l7.6-4M8.2 13.2l7.6 4" />
    </>
  ),
  // 隐私：锁
  lock: (
    <>
      <rect x="4.5" y="10.5" width="15" height="10.5" rx="2.5" />
      <path d="M8 10.5v-3a4 4 0 0 1 8 0v3" />
      <path d="M12 14.6v2.4" />
    </>
  ),
  // 不上传：云加斜线
  cloudOff: (
    <>
      <path d="M8.5 7.4A5.8 5.8 0 0 1 17.6 10.6 3.9 3.9 0 0 1 19.8 17.3M15.5 18.5H7a4.5 4.5 0 0 1-1.6-8.7" />
      <path d="M3.5 3.5l17 17" />
    </>
  ),
  // 不用注册：人像加对勾
  userCheck: (
    <>
      <circle cx="10" cy="8" r="3.5" />
      <path d="M3.5 20c0-3.6 2.9-6.5 6.5-6.5 1.6 0 3 .5 4.2 1.4" />
      <path d="M15.5 18.5l2 2 4-4" />
    </>
  ),
  // 没有后台、无处上传：盒子加斜线
  inboxOff: (
    <>
      <path d="M4 13.5l2.2-7A2 2 0 0 1 8.1 5h7.8a2 2 0 0 1 1.9 1.5l2.2 7V18a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z" />
      <path d="M4 13.5h4.5l1.2 2h4.6l1.2-2H20" />
      <path d="M3.5 3.5l17 17" />
    </>
  ),
  // 饭桶侠：转盘（上方指针）
  wheel: (
    <>
      <circle cx="12" cy="13.5" r="7.5" />
      <path d="M12 6.5v7M12 13.5l-5.4 5.2M12 13.5l5.4 5.2" />
      <path d="M10.3 2.6h3.4L12 5.4z" />
    </>
  ),
  // 朋友侠：两个人并肩
  friends: (
    <>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3 19.5c0-3.5 2.7-5.8 6-5.8s6 2.3 6 5.8" />
      <circle cx="17" cy="9" r="2.4" />
      <path d="M16 14.2c2.4.4 4.2 2.1 4.7 4.6" />
    </>
  ),
  heart: (
    <path d="M12 20.3s-7.5-4.6-7.5-10.1A4.2 4.2 0 0 1 12 7.6a4.2 4.2 0 0 1 7.5 2.6c0 5.5-7.5 10.1-7.5 10.1z" />
  ),
  arrowRight: <path d="M5 12h14M13 6l6 6-6 6" />,
  check: <path d="M5 12.5l4.2 4.2L19 7" />,
  // 提醒：铃铛
  bell: (
    <>
      <path d="M6 16v-5a6 6 0 0 1 12 0v5l1.5 2.4a.8.8 0 0 1-.68 1.1H5.18a.8.8 0 0 1-.68-1.1z" />
      <path d="M10 20.5a2.2 2.2 0 0 0 4 0" />
    </>
  ),
  // 桌面插件：四宫格
  grid: (
    <>
      <rect x="4" y="4" width="7" height="7" rx="1.8" />
      <rect x="13" y="4" width="7" height="7" rx="1.8" />
      <rect x="4" y="13" width="7" height="7" rx="1.8" />
      <rect x="13" y="13" width="7" height="7" rx="1.8" />
    </>
  ),
} satisfies Record<string, ReactNode>;

export type IconName = keyof typeof PATHS;

/** 线性图标；默认装饰性（aria-hidden），需要朗读的含义请写在相邻文字里。 */
export function Icon({ name, size = 24, className }: {
  name: IconName; size?: number; className?: string;
}) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {PATHS[name]}
    </svg>
  );
}
