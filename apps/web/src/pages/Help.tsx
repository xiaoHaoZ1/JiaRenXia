// 帮助页：给家里老人看的图文教程。每一节配一张 App 截图 + 「跟着点」的步骤。
// 截图与首页同源——真机实拍（英文名合成数据）存在 docs/images/，复制到 assets/shots/；
// App 界面有明显变化时同步更新。

import { SITE } from "../site";
import KinshipDemo from "../KinshipDemo";
import homeShot from "../assets/shots/home.png";
import detailShot from "../assets/shots/detail.png";
import affairsShot from "../assets/shots/affairs.png";
import graphShot from "../assets/shots/graph.png";
import "./help.css";

/** 教程配图：带手机外框的截图（外框样式在 styles.css，与首页共用）。 */
function Shot({ src, alt }: { src: string; alt: string }) {
  return (
    <div className="phone" aria-hidden="false">
      <img src={src} alt={alt} width={540} height={1199} loading="lazy" decoding="async" />
    </div>
  );
}

export default function Help() {
  return (
    <>
      <h1>帮助</h1>
      <p className="muted">
        给家里老人看的图文教程：每一屏都配了 App 里的样子，跟着点就行。还有搞不懂的地方，写信给作者。
      </p>

      <div className="card help-sec">
        <Shot src={homeShot}
              alt="家庭页截图：顶部是“家庭”标题和“添加家人”按钮，下面是写着姓氏的圆形家人头像与称谓" />
        <div>
          <h3>一打开：家人都在这里</h3>
          <p>
            App 底下三个标签：家庭、家事、我的。打开就是「家庭」，家里人都在这一屏，头像旁的小字写着 TA 和你的关系。
          </p>
          <ol className="help-steps">
            <li>右上角「添加家人」，填上姓名就把人加进来</li>
            <li>点圆圈头像或名字，看 TA 的资料</li>
            <li>旁边的放大镜是找人，人多时直接搜</li>
          </ol>
        </div>
      </div>

      <div className="card help-sec">
        <Shot src={detailShot}
              alt="家人资料页截图：大头像和名字，下面是称谓、打电话按钮、重要日期与生日提醒开关" />
        <div>
          <h3>看一个人的资料</h3>
          <p>点开任何一位家人，就是 TA 的资料页：大头像、名字、该叫什么，一目了然。</p>
          <ol className="help-steps">
            <li>「打电话」直接拨号</li>
            <li>「编辑」改姓名、生日这些信息</li>
            <li>「添加关系」把 TA 和其他家人连起来（比如：是某某的儿子）</li>
            <li>生日提醒打开，还能选当天或提前 1、3、7 天提醒</li>
          </ol>
        </div>
      </div>

      <div className="card help-sec">
        <Shot src={affairsShot}
              alt="家事页截图：顶部是标题和小铃铛，下面列着今天要办的事和未来 30 天" />
        <div>
          <h3>今天要办的事、快到的生日</h3>
          <p>
            底下点「家事」：今天是几号、谁过生日、什么事到期，下面列出未来 30 天。
          </p>
          <ol className="help-steps">
            <li>事办完了，点右边的 ✓</li>
            <li>顶栏小铃铛设置每天几点提醒、哪类要不要提醒</li>
            <li>右下角「新建事项」记缴费、医保等到点要办的事</li>
          </ol>
        </div>
      </div>

      <div className="card help-sec">
        <Shot src={graphShot}
              alt="家庭关系图截图：一家人画在一张图上，红框标出“我”，连线上写着称谓，顶部胶囊可选只看父系或母系" />
        <div>
          <h3>谁该叫什么，看图就明白</h3>
          <p>家庭页里的「家庭关系图」入口，把一家人自动画在一张图上。</p>
          <ol className="help-steps">
            <li>红框是「我」，连线上写着称谓</li>
            <li>顶部胶囊可以只看父系、母系或配偶方</li>
            <li>两个手指放大缩小，按住拖动</li>
          </ol>
        </div>
      </div>

      <div className="card" id="kinship">
        <KinshipDemo />
      </div>

      <div className="card">
        <h3>字太小怎么办</h3>
        <p>「我的 → 外观与字号」里把字调大，整个 App 的字都跟着变大。</p>
        <h3>换手机怎么办</h3>
        <ol className="help-steps">
          <li>旧手机「我的 → 备份全部数据」，设个密码记好</li>
          <li>把备份文件发到新手机（微信、数据线都行）</li>
          <li>新手机装好家人侠，「从备份恢复」选这个文件、输入密码</li>
        </ol>
        <p>注意：卸载会清空资料，换机、卸载之前都先备份。</p>
      </div>

      <div className="card">
        <h3>家人侠收费吗？</h3>
        <p>
          完全免费。家人档案、提醒、导出表格、高清关系图等所有功能都开放使用，没有任何付费门槛。如果它帮到了你，可以点顶部的「支持作者」扫码请作者喝杯咖啡。
        </p>
        <h3>卸载重装数据还在吗？</h3>
        <p>
          不在。家庭资料只保存在手机本地，卸载或清除数据前请先导出备份。
        </p>
        <h3>联系</h3>
        <p>
          有任何问题请写信到{" "}
          <a href={`mailto:${SITE.contactEmail}`}>{SITE.contactEmail}</a>。
        </p>
      </div>
    </>
  );
}
