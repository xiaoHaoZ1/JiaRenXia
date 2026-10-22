import { SITE } from "../site";

export default function Terms() {
  return (
    <>
      <h1>用户协议</h1>
      <p className="note">草稿，尚未经过法律或合规审核。</p>
      <div className="card">
        <h3>软件性质</h3>
        <ul>
          <li>家人侠是个人作品，免费提供给个人使用，全部功能不收取任何费用。</li>
          <li>通过「帮助 → 支持作者」的打赏是自愿的，不影响任何功能。</li>
          <li>家人侠<a href={SITE.sourceUrl} target="_blank" rel="noopener">源代码</a>以 GPL-3.0 开源；你可以按该许可使用、修改和分发，但不得冒用「家人侠」名称和图标发布改版。</li>
        </ul>
        <h3>数据责任</h3>
        <p>
          家庭资料只保存在你自己的手机里，请自行做好备份；卸载或清除数据后无法找回。
        </p>
        <h3>其他</h3>
        <p>
          本软件按现状提供，作者会持续改进但不作任何担保。如有问题请写信到{" "}
          <a href="mailto:2107290326@qq.com">2107290326@qq.com</a>。
        </p>
      </div>
    </>
  );
}
