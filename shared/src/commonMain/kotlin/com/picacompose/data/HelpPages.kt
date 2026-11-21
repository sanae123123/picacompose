package com.picacompose.data

/**
 * 应用内的使用帮助：一整页 HTML，由 [LocalHttpServer] 在本机端口上喂给内置浏览器。
 *
 * 为什么不直接拼 Compose 界面：帮助内容会长、会改、要有锚点跳转和折叠，写成网页最省事，
 * 而且这一页不依赖网络 —— 断网、图床挂了也照样能翻。
 *
 * 只有一个入口：点设置里的「使用帮助」。页面内部的跳转全走 `#锚点`，不需要别的路由。
 */
object HelpPages {

    /** 交给 [LocalHttpServer] 的处理器。 */
    val handler: LocalHttpHandler = { path ->
        when (path) {
            "/", "/index.html", "" -> LocalHttpResponse.html(HTML)
            else -> null
        }
    }

    private val HTML: String = """
<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="color-scheme" content="dark light">
<title>使用帮助</title>
<style>
:root{
  --bg:#0d0f12; --fg:#e9eaee; --muted:#9aa1ab; --card:#16191e;
  --line:#252a32; --accent:#8ab4f8; --accent-weak:rgba(138,180,248,.12);
}
@media (prefers-color-scheme: light){
  :root{
    --bg:#f6f7f9; --fg:#1a1c20; --muted:#5d6470; --card:#ffffff;
    --line:#e4e7ec; --accent:#2f6fdb; --accent-weak:rgba(47,111,219,.08);
  }
}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{
  margin:0; background:var(--bg); color:var(--fg);
  font:16px/1.75 -apple-system,BlinkMacSystemFont,"PingFang SC","Noto Sans CJK SC","Microsoft YaHei",system-ui,sans-serif;
  padding:0 0 calc(40px + env(safe-area-inset-bottom));
  -webkit-font-smoothing:antialiased;
}
.wrap{max-width:720px; margin:0 auto; padding:26px 18px 0}
.head .kicker{
  margin:0 0 6px; font-size:12px; letter-spacing:.14em; text-transform:uppercase;
  color:var(--accent); font-weight:600;
}
h1{margin:0; font-size:27px; line-height:1.3; letter-spacing:-.01em}
.lede{margin:12px 0 0; color:var(--muted); font-size:15px}
.meta{margin:10px 0 0; color:var(--muted); font-size:13px}
.toc{display:flex; flex-wrap:wrap; gap:8px; margin:22px 0 6px}
.toc a{
  display:inline-block; padding:7px 13px; border-radius:999px;
  background:var(--card); border:1px solid var(--line); color:var(--fg);
  text-decoration:none; font-size:14px;
}
.toc a:active{background:var(--accent-weak)}
section{margin-top:34px; scroll-margin-top:14px}
h2{
  margin:0 0 4px; font-size:20px; line-height:1.4; letter-spacing:-.01em;
  display:flex; align-items:center; gap:10px;
}
h2 .n{
  flex:none; width:26px; height:26px; border-radius:8px; background:var(--accent-weak);
  color:var(--accent); font-size:14px; font-weight:600;
  display:inline-flex; align-items:center; justify-content:center;
}
h3{margin:0 0 8px; font-size:16px; line-height:1.5}
p{margin:10px 0}
p:first-child, h3 + p{margin-top:0}
p:last-child{margin-bottom:0}
.card{
  background:var(--card); border:1px solid var(--line); border-radius:14px;
  padding:15px 16px; margin:12px 0;
}
.card + .card{margin-top:10px}
ul.plain{margin:12px 0 0; padding-left:0; list-style:none}
ul.plain li{
  position:relative; padding-left:18px; margin:9px 0; color:var(--fg);
}
ul.plain li::before{
  content:""; position:absolute; left:2px; top:.72em;
  width:6px; height:6px; border-radius:50%; background:var(--accent); opacity:.65;
}
ul.plain li b{font-weight:600}
ol.steps{margin:10px 0 0; padding-left:22px}
ol.steps li{margin:8px 0}
.tip{
  margin:12px 0 0; padding:2px 0 2px 12px; border-left:3px solid var(--accent);
  color:var(--muted); font-size:14.5px;
}
.kbd{
  display:inline-block; padding:1px 7px; border-radius:6px; border:1px solid var(--line);
  background:var(--bg); font-size:13.5px; font-family:ui-monospace,SFMono-Regular,Menlo,monospace;
  vertical-align:1px;
}
table{width:100%; border-collapse:collapse; margin:10px 0 0}
th,td{
  text-align:left; vertical-align:top; padding:10px 6px;
  border-bottom:1px solid var(--line); font-size:14.5px; line-height:1.65;
}
th{color:var(--muted); font-weight:500; white-space:nowrap; width:34%}
tr:last-child th, tr:last-child td{border-bottom:0}
details{
  border-bottom:1px solid var(--line); padding:12px 0;
}
details:last-of-type{border-bottom:0}
summary{cursor:pointer; font-weight:500; list-style:none; display:flex; gap:10px; align-items:flex-start}
summary::-webkit-details-marker{display:none}
summary::before{content:"＋"; color:var(--accent); flex:none; width:18px}
details[open] summary::before{content:"－"}
details .answer{margin:8px 0 0; padding-left:28px; color:var(--muted); font-size:14.5px}
.foot{
  margin-top:40px; padding-top:18px; border-top:1px solid var(--line);
  color:var(--muted); font-size:13px; line-height:1.9;
}
.foot a{color:var(--accent); text-decoration:none}
</style>
</head>
<body>
<main class="wrap">

<header class="head">
  <p class="kicker">PicaCompose</p>
  <h1>使用帮助</h1>
  <p class="lede">这一页跟着安装包走，不联网也能看。下面的目录可以点，跳到你关心的那一段。</p>
  <p class="meta">版本 ${APP_VERSION}</p>
</header>

<nav class="toc">
  <a href="#first">第一次打开</a>
  <a href="#tabs">五个标签</a>
  <a href="#find">找本子</a>
  <a href="#read">看本子</a>
  <a href="#offline">下载和导出</a>
  <a href="#net">线路和图片</a>
  <a href="#settings">设置逐项</a>
  <a href="#faq">常见问题</a>
</nav>

<section id="first">
<h2><span class="n">1</span>第一次打开</h2>

<div class="card">
<h3>先挑一条线路</h3>
<p>数据都要从服务器拿，而这些域名在有些网络里会被解析到错的地方。所以第一次打开会先停在线路页，把候选线路挨个连一遍，能连上的按快慢排好。</p>
<p>点延迟低的那条进去就行。选过的线路会记住，以后启动不再让你选。</p>
<p class="tip">一条都连不上，多半是这个网络到那边不通，换个网再进来；进来了也一直是白页的话，去 设置 → 测速 重新挑一条。</p>
</div>

<div class="card">
<h3>登录</h3>
<p>用哔咔账号登录。没有账号在登录页点注册；忘了密码走邮箱找回。</p>
<p>「记住账号」默认是开的，只存登录令牌和账号名，不存密码，下次打开直接进主页。关掉会把已经存的令牌一起清掉，下次得重新输。</p>
</div>

<div class="card">
<h3>第一次登录会问一次内容过滤</h3>
<p>问的是要不要「只看无 H」。当时选什么都行，之后在 设置 → 只看无 H 里随时能改。</p>
</div>
</section>

<section id="tabs">
<h2><span class="n">2</span>五个标签</h2>
<ul class="plain">
  <li><b>主页</b>：上面是资料卡，接着是公告和消息两张卡，再往下是本子妹推荐和官方合集，可以横向滑。</li>
  <li><b>分类</b>：上半是「热门分类」那一排磁贴（支持哔咔、排行榜、游戏推荐、留言板、最新更新、随机本子……），下半是「全部分类」。大部分点进去是站内列表，少数是网页，用应用里的浏览器打开。</li>
  <li><b>游戏</b>：官方小游戏，双列排下来，滚到底自动接着拉。</li>
  <li><b>我的</b>：签到、我的收藏、历史评论、浏览历史、下载管理；右上角那支铅笔是编辑资料（头像、昵称、签名）。</li>
  <li><b>设置</b>：后面有一整段逐项说明。</li>
</ul>
<p class="tip">切标签可以点底部栏，也可以左右滑。</p>
</section>

<section id="find">
<h2><span class="n">3</span>找本子</h2>

<div class="card">
<h3>列表里筛</h3>
<p>分类磁贴进去就是列表。顶栏能换排序：默认、最新、最早、最多爱心、最多浏览；还能只留「已完结」或「连载中」。</p>
<p>标签也能筛：加进去的标签要求同时满足，填到「排除」里的标签则一本都不会出现。</p>
<p>列表右上角那三条横线里还有个「跳页」，页数多的时候直接跳过去。</p>
</div>

<div class="card">
<h3>搜</h3>
<p>分类页右上角那颗放大镜。按标题和作者搜，下面是大家常搜的词，点一个就直接搜。</p>
<p>搜索结果带简介，所以「只看无 H」在搜索页判断得最准；别的列表接口不带简介，只看得到标签。</p>
</div>

<div class="card">
<h3>别的地方</h3>
<p>排行榜、随机本子、最新更新、留言板都在分类页顶部那排磁贴里。排行榜分「最热门」和「骑士榜」两页，最热门里还能切今天 / 这周 / 这个月。</p>
<p>随机本子那页滑到底再往上推一把，就换一批。</p>
<p>详情页上的标签点一下，就顺着这个标签搜同类；作者名、汉化组也能点，只看这一家。</p>
</div>
</section>

<section id="read">
<h2><span class="n">4</span>看本子</h2>

<div class="card">
<h3>详情页</h3>
<p>封面、标签、简介、章节列表都在这一页，封面点开能看大图，点一下关掉。</p>
<p>右上角三个动作：赞、收藏、下载（下载按钮里也能导出）。</p>
<p>「开始阅读」从头看，「继续阅读」接着上次的位置；章节列表里点某一话直接跳过去，点的正好是上次那一话，就接着上次那一页。</p>
<p>评论区里点一条评论能展开回复，长按是举报。</p>
</div>

<div class="card">
<h3>阅读器</h3>
<ul class="plain">
  <li>往下滑就是往下翻，一页接一页。</li>
  <li>点一下屏幕：顶栏和底栏一起出来或者收起来。</li>
  <li>底部那条进度可以点、可以横着拖，直接跳到那一页。</li>
  <li>两根手指放大之后可以拖着看，松手还有点惯性；放大状态下拖不会误翻页。</li>
  <li>音量键：<span class="kbd">音量加</span> 上一页、<span class="kbd">音量减</span> 下一页，不会真的调音量。不想要就去 设置 → 音量键翻页 关掉。</li>
  <li>顶栏上有上一话 / 下一话，换话会回到第 1 页。</li>
</ul>
<p class="tip">图超过 6 秒还没下来，会自动换一条线路重新拉；要是这一张彻底失败了，点一下那张图单独重试。</p>
</div>
</section>

<section id="offline">
<h2><span class="n">5</span>下载和导出</h2>

<div class="card">
<h3>缓存到本地</h3>
<p>详情页右上角那个下载按钮，点了就开始把整本存到手机上，可以继续去逛别的，进度在 我的 → 下载管理 里看。</p>
<p>下完的本子离线也能看，不耗流量。下载管理里能取消、重试（已经下好的页不会重下）、删掉整本腾地方。</p>
<p class="tip">缓存和导出目前只有 Android 版有，别的平台上不显示这两个入口。</p>
</div>

<div class="card">
<h3>导出成文件</h3>
<p>同一个下载按钮里能选导出，三种格式：</p>
<ul class="plain">
  <li><b>压缩包</b>：原样打包，换台设备还能导回来。</li>
  <li><b>PDF</b>：每页一张图，什么阅读器都能开。</li>
  <li><b>电子书</b>：带目录的 epub，丢进阅读器就能看。</li>
</ul>
<p>页数多的时候要跑一会儿，可以选「后台执行」，进度去通知栏看。选完格式会弹系统的保存框，存在哪你说了算。</p>
</div>
</section>

<section id="net">
<h2><span class="n">6</span>线路和图片</h2>

<div class="card">
<h3>线路相关的几个开关</h3>
<p><b>自动测速</b>：启动时在后台把线路测一遍，慢的自动换掉。关掉就不再自动换，网络变了得自己去 设置 → 线路 重新选。</p>
<p><b>快速进入并后台测速</b>：先按上次的线路进界面，测速丢到后台跑；关掉就老老实实停在线路页等测速完。</p>
<p><b>线路 / 测速</b>：这两行是给你看的，不能点。想换线路只能重新打开应用 —— 自动测速开着的话，后台测完会自己换到能用的那条。</p>
</div>

<div class="card">
<h3>DoH 解析</h3>
<p>接口域名改走加密 DNS 解析，某些网络下能绕开污染。一个解析端点不通会自动换下一个；全都不行会自己关掉，退回系统 DNS，设置里的开关也跟着变回关。</p>
</div>

<div class="card">
<h3>图片 IP 优选</h3>
<p>图床域名经常被解析到别的公司的 IP 上，所以图片请求单独钉了一组 Cloudflare 地址，启动后在后台挨个测，挑最快的几台用，之后每 30 分钟重测一次。</p>
<p>关掉就不再后台测速，只用服务器下发的地址加内置的备用地址。图片老是转圈的时候，先确认这个开关是开的。</p>
</div>

<div class="card">
<h3>图片质量</h3>
<p>按官方那套档位来：原图最清楚也最费流量，往下依次是高、中、低。默认中，和官方一致。换一档下一张图就生效。</p>
</div>
</section>

<section id="settings">
<h2><span class="n">7</span>设置逐项</h2>
<p>按设置页从上到下的顺序。</p>

<table>
<tr><th>皮肤</th><td>MIUI 风格、Material You、鸿蒙风格、iOS 风格，换完立刻变，不用重启。</td></tr>
<tr><th>只看无 H</th><td>列表里只留标签或简介写了无 H 的作品，公共场合翻着放心。默认关。</td></tr>
<tr><th>不看 AI</th><td>列表里去掉标签带 AI（像「AI作畫」）或者标题里写着 AI 的作品。只看标题和标签，简介里提到 AI 的不算。默认关。</td></tr>
<tr><th>记住账号</th><td>开着下次打开直接恢复登录；关掉会清掉已存的令牌。</td></tr>
<tr><th>保存的账号 / 切换账号</th><td>登过的号都记在这里，点一下直接切过去，不用重新输密码。</td></tr>
<tr><th>自动测速</th><td>启动时后台测线路，慢了自动换。改完立即生效。</td></tr>
<tr><th>快速进入并后台测速</th><td>先按上次的线路进界面，测速放后台。</td></tr>
<tr><th>线路 / 测速 / 各线路延迟</th><td>这几行都只看不改：当前走哪条、哪个通道、延迟多少，以及每条线路通不通。</td></tr>
<tr><th>DoH 解析</th><td>接口走加密 DNS；全挂了会自动退回系统 DNS。</td></tr>
<tr><th>图片 IP 优选</th><td>后台测 Cloudflare 地址、挑最快的给图片用。</td></tr>
<tr><th>图片质量</th><td>原图 / 高 / 中 / 低。</td></tr>
<tr><th>缓存并发</th><td>一本同时下几页，同时也当全局网络上限用。可选 4 / 8 / 16 / 32 / 64，默认 32。</td></tr>
<tr><th>清除图片缓存</th><td>只清图片，不动登录、线路和阅读进度。</td></tr>
<tr><th>优选状态 / 地址池 / 上次完成</th><td>后台测速现在到哪一步、手上有多少可用地址、上一次什么时候测完。</td></tr>
<tr><th>音量键翻页</th><td>阅读器里音量加翻上一页、音量减翻下一页。关掉恢复成调音量。默认开。</td></tr>
<tr><th>检查更新</th><td>去 GitHub 看有没有新版本，有就问你装不装。</td></tr>
<tr><th>使用帮助</th><td>就是这一页。</td></tr>
<tr><th>关于 / GitHub / 接口</th><td>版本和项目地址；「接口」这一行说的是数据来自哪个模块。</td></tr>
<tr><th>退出登录</th><td>清掉令牌，账号名留着，下次登录还能点上。</td></tr>
</table>
</section>

<section id="faq">
<h2><span class="n">8</span>常见问题</h2>

<details>
<summary>一直在转圈，图和列表都出不来</summary>
<div class="answer">先把应用关掉重开，让线路重新测一遍；再去 设置 看「图片 IP 优选」是不是开着。只有某一张图转圈的话，是那一张的问题，点它单独重试就行。</div>
</details>

<details>
<summary>换了网络之后什么都拉不动了</summary>
<div class="answer">线路跟网络走，从 Wi-Fi 换到流量（或者反过来）之后可能整条不通。把应用关掉重开：自动测速开着就会重新测一遍换到能用的那条；如果第一条请求就失败，应用会直接把你送到线路页重选。</div>
</details>

<details>
<summary>首页推荐或者封面是空白的</summary>
<div class="answer">多半是图片那边没连上。去 设置 看看「图片 IP 优选」和「图片质量」，再滑到底看「优选状态」是不是「这轮没测出可用地址」；是的话过一会儿再进来看，或者手动去 设置 → 测速 换条线。</div>
</details>

<details>
<summary>手机空间不够</summary>
<div class="answer">设置 → 清除图片缓存 只清图片，能腾出不少；下好的本子在 我的 → 下载管理 里删。阅读进度、收藏、登录状态都不会动。</div>
</details>

<details>
<summary>音量键不翻页</summary>
<div class="answer">去 设置 → 音量键翻页 看看是不是关着。开着也只在这个应用的阅读器里生效，别的地方照常调音量。</div>
</details>

<details>
<summary>想回到最开始那本怎么找</summary>
<div class="answer">我的 → 浏览历史，按时间排，点一条直接回那一本；开着的本子在详情页会显示「继续阅读」，接着上次那页。</div>
</details>

<details>
<summary>这一页怎么是网页</summary>
<div class="answer">帮助内容写成网页，好处是断网也能看、内容长了也排得开、还能跳锚点。它由应用自己在本机开的一个小端口提供，不出手机，也不占流量。</div>
</details>
</section>

<p class="foot">
  PicaCompose ${APP_VERSION}<br>
  项目地址 <a href="https://github.com/sanae123123/picacompose">github.com/sanae123123/picacompose</a>
</p>

</main>
</body>
</html>
    """.trimIndent()
}
