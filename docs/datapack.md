# 数据包 + 资源包方案

同一副幻灯片,换成**展示实体 + 自定义物品模型**来渲染。相比地图方案:

| | 地图方案 | 本方案 |
|---|---|---|
| 颜色 | 原版 62 基色 × 4 明暗,需要抖动 | **全彩,无量化** |
| 分辨率 | 896×512(7×4 格上限) | 只受图集容量限制 |
| 服务端内存 | 88 页 × 28 格 = 2464 张地图常驻 | 一个展示实体 |
| 服务端插件 | 需要 | **不需要** |
| 客户端 | 原版 | 原版 + 资源包 |
| 翻页交互 | 手持钻石斧左键/右键 | `/function` 或 `/trigger` |

代价是资源包必须**提前做好**,而且换包要重连——所以整副幻灯片要在开场前全部打进包里。

## 原理

一页幻灯片 = 一张贴图 = 一个物品模型 = 一个 `item_display` 实体。

- `assets/ppt/textures/page/slide_N.png` — 幻灯片贴图,按屏幕比例加黑边
- `assets/ppt/models/page_N.json` — 一块居中的平面,正反两面都贴这张图
- `assets/ppt/items/page_N.json` — 把物品模型 id `ppt:page_N` 指向上面那个模型
- `item_display` 实体通过 `minecraft:item_model` 组件引用 `ppt:page_N`,由 `transformation.scale` 放大到实际屏幕尺寸

翻页只是改这一个实体的 `item_model` 组件,所以服务端开销是常数,与页数无关。

## 生成

推荐用 Python 脚本,不需要任何编译步骤。把脚本和幻灯片放在同一个文件夹里:

```
任意文件夹/
  make_packs.py        <- 脚本
  ppt/                 <- 幻灯片
    1.png
    2.png
    ...
```

然后在该文件里执行:

```powershell
python make_packs.py
```

脚本会自动读取**自己所在目录**下的 `ppt/`,生成的资源包和数据包也放在同一目录。

### 清晰度

每跑一次都会先把几档清晰度列出来让你选,选完才开始生成:

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

档位是按**每格屏幕分到多少贴图像素**定的,因为画面发糊只跟这一件事有关:1080p 下坐在
3~5 格外看一块 7 格宽的屏,它大约占 1000~1800 个屏幕像素,所以 96 px/格偏省流量、
128 够用、160 舒服、200 是给"字小 + 坐得近"准备的。把屏幕格数做大不会更清楚——同一张
贴图铺得更开,只会更糊。

菜单里的图集档位是硬限制:贴图拼进 items 图集,图集边长取显卡的 max texture size
(桌面卡一般 16384,老卡/集显 8192)。超过不是变糊,而是客户端加载资源包时直接崩
(`StitcherException: Unable to fit: ... Maybe try a lower resolution resourcepack?`)。
页数多到装不下时,先出一档"兼容"(按 8192 图集算),实在不行就拆成两个包分两场加载。

想跳过询问、让脚本无人值守地跑,可选项:

```powershell
python make_packs.py --preset 3          # 直接选第 3 档(也可以写档位名,如 --preset 清晰)
python make_packs.py --width 1280        # 直接指定贴图宽度
python make_packs.py --width auto        # 按 8192 图集容量自动挑宽度
python make_packs.py --screen 10x6       # 屏幕尺寸(默认 7x4)
```

标准输入被重定向时(比如计划任务)不会卡在询问上,会自动用默认档。

脚本依赖 Pillow(`python -m pip install Pillow`)。输入格式由 Pillow 决定,`png / jpg / gif /
bmp / webp` 都行,输出统一重编码成 PNG——所以源图是 WebP 也没问题。

幻灯片文件规则两边一致:纯数字命名,`1.png`、`2.png`……,按数字顺序排序,其它文件一律忽略。

### 关于 `pack.mcmeta` 的格式号

26.1.2 的包格式号已经超过 64,此时**旧的 `pack_format` 字段不再够用**,客户端会直接报:

```
Pack declares support for version newer than 64, but is missing mandatory fields min_format and max_format
```

必须写成 `min_format` + `max_format`。生成器用的数值来自两个权威来源:

- `resource_major` / `resource_minor` / `data_major` / `data_minor` 取自服务端 jar 里的 `version.json`
- 字段写法对照原版自带的 `data/minecraft/datapacks/*/pack.mcmeta`

当前是资源包 `min_format: [84, 0]` / `max_format: 84`,数据包 `min_format: [101, 1]` /
`max_format: 101`。以后游戏版本更新时,改 `make_packs.py` 顶部的 `RESOURCE_PACK_FORMAT` 与
`DATA_PACK_FORMAT` 即可。

输出:

```
dist/InGamePPT-ResourcePack/      资源包目录
dist/InGamePPT-ResourcePack.zip   资源包(server.properties 用这个)
dist/InGamePPT-Datapack/          数据包目录
dist/InGamePPT-Datapack.zip       数据包(丢进世界 datapacks/)
```

生成完会打印贴图尺寸、每格像素密度、各级图集能否装下、以及资源包的 SHA1。

## 部署

**资源包**:把 zip 放到任意 HTTP 服务上,然后在 `server.properties` 里填:

```
resource-pack=<zip 的 URL>
resource-pack-sha1=<生成器打印的值>
require-resource-pack=true
```

`require-resource-pack=true` 会踢掉拒绝接受资源包的玩家,正好保证每名参会者都拿到贴图。
注意 SHA1 必须和文件实际内容一致,否则客户端每次进服都会重新下载。

**数据包**:把 `InGamePPT-Datapack.zip` 放进 `<世界目录>/datapacks/`,然后 `/reload`。

## 使用

### 摆放和调整大小

位置和尺寸都存在 `storage ppt:config` 里,所以既可以"站着摆",也可以给精确数值。

**随手摆**——站到屏幕中心该在的位置,执行:

```
/function ppt:build     屏幕中心落在你的视线高度,朝向沿用你的朝向
/function ppt:info      查看当前数值
/function ppt:next      下一页
/function ppt:prev      上一页
/function ppt:clear     删除屏幕
```

**精确控制**——先 `/function ppt:info` 看当前数值,再直接改:

```
/data modify storage ppt:config x set value 100.5
/data modify storage ppt:config y set value 65
/data modify storage ppt:config z set value 200.5
/data modify storage ppt:config yaw set value 180
/data modify storage ppt:config width set value 10
/data modify storage ppt:config height set value 5.714
/function ppt:apply                                  应用(会保留当前页码)
```

几个要点:

- **实体的位置就是屏幕中心**,不是左下角,所以摆放时按中心去算。这一点依赖模型面片相对
  原点居中,客户端不会替模型做居中(见下面"屏幕总是偏半格")
- 屏幕**可读的那一面朝向实体的朝向**,也就是你执行 `build` 时的朝向
- `yaw` 决定朝向;如果看到文字是镜像的,把它加 180 即可(模型正反两面都贴了图)
- `width` / `height` 单位是**格**,同时决定实体碰撞箱,所以站远时也不会被错误裁剪
- **`height` 必须跟着 `width` 走,否则画面会被拉伸**:`height = width × (纹理高 / 纹理宽)`。
  用 `--screen 7x4` 生成时比例是 4/7,所以 `height = width × 0.5714`。`/function ppt:info` 会把
  这个倍数直接印出来
- 想换一个完全不同的比例(比如整面 16:9 的墙),要重新生成资源包:`--screen 16x9`

### 用定位蛋快速移动

不想记坐标的话,用定位蛋:

```
/function ppt:mover_egg
```

拿到一颗写着"PPT 定位蛋"的蛋,对着想放屏幕的位置右键——生物出现的瞬间屏幕就挪过去,
生物随即被清除。**屏幕中心 = 刷怪的位置**,想再精确一点就对照 `/function ppt:info` 微调 y。

原理:数据包没法凭空造物品,所以这颗蛋是原版刷怪蛋加上 `minecraft:entity_data`,
刷出的生物自带 `ppt_mover` 标签;`tick` 里发现这个标签就把 storage 的 x/y/z 换成生物位置,
然后 `kill` 掉它。外观借用了原版末影之眼的模型,不用额外打包贴图。

### 调整朝向

```
/function ppt:face_me     让屏幕转向你当前的朝向
/function ppt:flip        正反面掉个个儿(看到镜像文字时用)
```

哪一面算"正"由模型的 UV 约定决定,与其猜,不如让这两条命令组合出正确朝向。

讲者如果没有 op,可以用记分板触发器(数据包每刻自动重新启用):

```
/trigger ppt_next       下一页
/trigger ppt_prev       上一页
```

当前页码显示在动作栏上。翻到最后一页再往后、或第一页再往前,会收到聊天提示。

## 验证记录

下面这些原本是"没实测过、可能翻车"的点,现在都已经在 26.1.2 的本地测试服(Paper)上跑通了:

- `item_display:"none"` 是合法取值,`/function ppt:build` 正常出屏幕
- 屏幕尺寸/缩放:模型 16 单位靠 `transformation.scale` 放大到 7×4 格,同时写进实体碰撞箱,
  站远也不会被裁剪
- 朝向:正反两面都贴图,其中一面镜像,`ppt:face_me` + `ppt:flip` 能凑出正确朝向
- 图集容量:见生成器打印的对照表,超出会在客户端加载资源包时崩(`Unable to fit`)
- `mover_egg` 与 `move_here` 都已实测;手动执行 `move_here` 有守卫,不会把玩家自己 kill 掉

## 常见问题

**屏幕上出现黑紫格子(缺失贴图)**

两个原因,生成器的自检会把它们拦下来:

1. **贴图没有放在图集收录的目录里。** 图集是按目录声明的——
   `assets/minecraft/atlases/items.json` 只收录 `textures/item/`,
   `assets/minecraft/atlases/blocks.json` 只收录 `textures/block/`。
   放在别处(比如 `textures/page/`)的贴图**永远不会被打进图集**,模型就渲染成黑紫格子。
   所以生成器把贴图写在 `assets/ppt/textures/item/page/` 下,模型里引用 `ppt:item/page/slide_N`。
2. 模型引用的贴图或模型文件不存在(路径写错、少了一页)。

**画面被拉伸了**

`width` / `height` 的比例和贴图比例不一致。贴图比例由生成时的 `--screen` 决定,改尺寸时要按
比例缩放两个值,具体倍数用 `/function ppt:info` 查。

**站远了屏幕就消失**

实体碰撞箱(`width` / `height` 字段)和实际尺寸不符。用 `/function ppt:apply` 重建即可,它会
按 storage 里的宽高同时设置缩放与碰撞箱。

**屏幕总是偏半格**

模型面片没有相对原点居中。客户端渲染展示实体物品时只做一次绕 Y 轴 180° 旋转,**不会**替模型
做居中平移(`DisplayRenderer$ItemDisplayRenderer.submitInner` 里只有 `mulPose` + `submit`),
所以模型坐标系的原点就是实体位置。

原先照抄 `item/generated`,面片放在 `z: 7.5 ~ 8.5`,中心在 z = 8 单位(半格),整块屏幕就偏了
半格。正确的写法是 `z: -0.5 ~ 0.5`(同时保留 1 单位的厚度避免同一平面上两个面重合)。
生成器已按此修正,以后再改模型时注意保持居中。

这几处原先列成了"待实测",现在都已在测试服上确认,清单见上面的「验证记录」。

## 和地图方案的取舍

这个方案拿到全彩和无上限分辨率,换来三个约束:

- 资源包必须提前生成,换内容要重连
- 客户端需要下载资源包(你这份 88 页约 7 MB)
- 翻页交互从"斧头左右键"退化成命令/触发器,因为纯原版没有左键点击事件

如果这三条可以接受,它就是更合适的方案。
