# InGamePPT 数据包版教程(从零到开播)

不装插件,用**数据包 + 资源包**在 Minecraft Java 版里放幻灯片,参会玩家用原版客户端就能看。

两个包各管一件事:

- **资源包**管"图":每一页幻灯片是一张贴图,玩家进服时自动下载
- **数据包**管"屏"和"翻页":屏幕其实是一个 `item_display` 展示实体,翻页就是换它的贴图

服务端用原版就行(Paper、原版服务端、甚至单人存档都可以),不需要任何插件。代价是整副幻灯片
必须**提前**打包好,换内容要让玩家重连一次。

---

## 准备东西

| 需要 | 干什么用 | 备注 |
|---|---|---|
| Minecraft Java 26.1.2 服务端 | 开会用 | Paper 或原版都行 |
| 一个 HTTP 地址 | 让玩家能下载资源包 | 对象存储、nginx、GitHub Release 直链都行 |
| Python + Pillow | 生成两个包(只在你本地用) | `python -m pip install Pillow` |
| 幻灯片图片 | 内容 | 建议从 PPT 导出 1920×1080 |

---

## 第一步:准备幻灯片

建一个文件夹,脚本和幻灯片放在一起:

```
任意文件夹/
  make_packs.py        <- 生成脚本(仓库里的 tools/make_packs.py)
  ppt/                 <- 幻灯片(默认读这个名字的文件夹,可用 --slides 改)
    1.png
    2.png
    3.png
```

规则只有一条:**文件名必须是纯数字**,`1.png`、`2.png`……按数字大小排序播放。其它名字
(`cover.png`、`notes.txt`)一律忽略。

格式支持 `png / jpg / jpeg / gif / bmp / webp`,输出统一转成 PNG,所以源图是 WebP 也行。

---

## 第二步:生成资源包和数据包

在脚本所在文件夹里执行:

```
python make_packs.py
```

脚本会先问清晰度,回车用默认档就行:

```
=== 选择清晰度 ===
  屏幕 7 x 4 格,共 88 页
  1) 省流量     672x384   96 px/格      8k 图集   包约   6 MB
  2) 标准       896x512   128 px/格     16k 图集  包约  12 MB
  3) 清晰      1120x640   160 px/格     16k 图集  包约  18 MB   <- 回车用这个
  4) 高清      1408x806   201 px/格     16k 图集  包约  29 MB
  5) 最高      1728x988   247 px/格,上限 16k 图集  包约  43 MB
请输入序号 1-5,直接回车用默认 3:
```

- **px/格** = 每格屏幕分到多少贴图像素,越高越清楚,也越占体积。觉得字发糊就选更高一档;
  嫌玩家下载慢就选低一档。
- **图集** = 显卡要求。`16k 图集`需要显卡支持 16384 贴图(现代独显基本都行),`8k` 档任何
  机器都能加载。装不下不是变糊,是客户端加载资源包时直接崩,所以脚本不让你选。
- 想跳过询问:`python make_packs.py --preset 3`(也可以写 `--preset 清晰`)或 `--width 1280`。
- 想换成别的长宽比(比如整面墙 16:9):加 `--screen 16x9`,之后屏幕尺寸要按这个比例摆。

跑完得到四个东西:

```
InGamePPT-ResourcePack/       资源包目录
InGamePPT-ResourcePack.zip    资源包(要放到 HTTP 上)
InGamePPT-Datapack/           数据包目录
InGamePPT-Datapack.zip        数据包(丢进世界文件夹)
```

末尾还会打印一行 `resource-pack-sha1=...`,第三步要用,先别关窗口。

---

## 第三步:部署资源包(玩家看到的图)

**1. 把 `InGamePPT-ResourcePack.zip` 放到 HTTP 上**

局域网测试最省事:在 zip 所在目录执行

```
python -m http.server 8000
```

URL 就是 `http://<你的内网IP>:8000/InGamePPT-ResourcePack.zip`。正式开会建议用对象存储、
CDN 或 nginx,把文件放上去拿到一个直链即可。

**2. 改 `server.properties`**,填脚本打印的 URL 和 SHA1:

```
resource-pack=http://你的地址/InGamePPT-ResourcePack.zip
resource-pack-sha1=脚本打印的那串
require-resource-pack=true
```

`require-resource-pack=true` 会挡掉拒绝接受资源包的玩家,这样每个人看到的图都是对的。

**3. 重启服务器**,玩家进服时自动下载资源包。

⚠️ 资源包只在**进服时**应用,所以改了幻灯片以后:重新生成 → 换掉 zip → 更新 sha1 →
重启 → 让玩家重连。不然他们看到的还是旧图。

---

## 第四步:部署数据包(负责屏幕和翻页)

1. 把 `InGamePPT-Datapack.zip` 放进世界的 `datapacks` 文件夹,例如
   `paper_server/world/datapacks/`
2. 在服务器控制台执行 `/reload`(换数据包只重载就行,不用重启)
3. 验证:游戏里输 `/function ppt:info`,能看到屏幕坐标就说明装好了

---

## 第五步:把屏幕摆到想要的位置

**随手摆**:人站到"屏幕中心该在的位置",执行

```
/function ppt:build
```

屏幕中心会落在你的视线高度,朝向跟着你的朝向走。

**想挪到别处**:用定位蛋最方便

```
/function ppt:mover_egg
```

拿到"PPT 定位蛋",对着想放屏幕的位置右键——生物出现的瞬间屏幕就挪过去了,生物随即被清除。

**想给精确数值**:先 `/function ppt:info` 看当前值,再改

```
/data modify storage ppt:config x set value 100.5
/data modify storage ppt:config y set value 65
/data modify storage ppt:config z set value 200.5
/function ppt:apply
```

几个要点:

- 坐标是**屏幕中心**,不是左下角
- `width` / `height` 单位是格,而且 `height` 必须跟着 `width` 一起改,否则画面会被拉伸:
  默认 7:4 的屏幕,`height = width × 0.5714`(`/function ppt:info` 会把这个倍数印出来)
- 看到**镜像的文字**:`/function ppt:flip`
- 想让屏幕转向你:`/function ppt:face_me`
- 不想要屏幕了:`/function ppt:clear`

---

## 第六步:播放

```
/function ppt:next       下一页
/function ppt:prev       上一页
```

讲者如果没有 op,用记分板触发器(数据包每刻自动启用,任何人都能用):

```
/trigger ppt_next        下一页
/trigger ppt_prev        上一页
```

当前页码显示在动作栏上,翻到第一页/最后一页时会收到聊天提示。

---

## 常见问题

| 症状 | 原因 | 怎么办 |
|---|---|---|
| 屏幕上全是黑紫格子 | 资源包没生效,或贴图路径不对 | 确认玩家已接受资源包(重连一次);生成器自检会拦住路径问题 |
| 画面发糊、字看不清 | 清晰度档位低了,或屏幕格数太大 | 用更高档重新生成;屏幕做大只会更糊 |
| 进服时游戏崩,日志里有 `Unable to fit` | 贴图总量超过显卡图集上限 | 选低一档清晰度,或把幻灯片拆成两个包分两场 |
| 玩家没下载资源包 | URL 不通 / sha1 不匹配 | 浏览器直接打开那个 URL 试试;sha1 必须和 zip 内容一致 |
| `/function` 提示未知或没反应 | 数据包没加载 | zip 是否在 `world/datapacks/`;控制台 `/reload` 看有没有报错 |
| 换了图片后画面没变 | 资源包只在进服时应用 | 重新生成、更新 sha1、玩家重连 |
| 文字是镜像的 | 屏幕朝向反了 | `/function ppt:flip` |
| 屏幕位置差一点 | 摆放位置是实体中心 | `/function ppt:mover_egg` 重新放,或改 `y` 后 `ppt:apply` |
| 站远了屏幕消失 | 碰撞箱尺寸和实际不符 | `/function ppt:apply` 重建一次 |

---

## 命令速查

| 命令 | 作用 |
|---|---|
| `/function ppt:build` | 把屏幕放到你站的位置(中心在视线高度) |
| `/function ppt:info` | 看屏幕的坐标、朝向、尺寸 |
| `/function ppt:apply` | 改完 storage 数值后应用(保留当前页码) |
| `/function ppt:mover_egg` | 拿定位蛋,右键即可把屏幕挪过去 |
| `/function ppt:face_me` | 屏幕转向你的朝向 |
| `/function ppt:flip` | 正反面掉个个儿(修镜像文字) |
| `/function ppt:clear` | 删除屏幕 |
| `/function ppt:next` / `/trigger ppt_next` | 下一页 |
| `/function ppt:prev` / `/trigger ppt_prev` | 上一页 |

单人存档里测试也可以:资源包 zip 丢进 `.minecraft/resourcepacks/`,数据包 zip 丢进
存档的 `datapacks/` 文件夹,然后 `/reload`。

原理、取舍和实现细节(为什么不用地图、模型为什么要居中、包格式号怎么来的)写在
[docs/datapack.md](docs/datapack.md)。
