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

脚本会自动读取**自己所在目录**下的 `ppt/`,生成的资源包和数据包也放在同一目录。可选项:

```powershell
python make_packs.py --width auto        # 按图集容量自动挑最大宽度
python make_packs.py --width 800         # 指定贴图宽度(默认 640)
python make_packs.py --screen 10x6       # 屏幕尺寸(默认 7x4)
```

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
`max_format: 101`。以后游戏版本更新时,改 `make_packs.py` 和 `PackBuilder.java` 顶部的两个常量即可。

输出:

```
dist/InGamePPT-ResourcePack/      资源包目录
dist/InGamePPT-ResourcePack.zip   资源包(server.properties 用这个)
dist/InGamePPT-Datapack/          数据包目录
dist/InGamePPT-Datapack.zip       数据包(丢进世界 datapacks/)
```

生成完会打印每张图的像素总量、各级图集能否装下、以及资源包的 SHA1。

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

- **实体的位置就是屏幕中心**,不是左下角,所以摆放时按中心去算
- `yaw` 是朝向;如果看到文字是镜像的,把它加 180 即可(模型正反两面都贴了图)
- `width` / `height` 单位是**格**,同时决定实体碰撞箱,所以站远时也不会被错误裁剪
- **`height` 必须跟着 `width` 走,否则画面会被拉伸**:`height = width × (纹理高 / 纹理宽)`。
  用 `--screen 7x4` 生成时比例是 4/7,所以 `height = width × 0.5714`。`/function ppt:info` 会把
  这个倍数直接印出来
- 想换一个完全不同的比例(比如整面 16:9 的墙),要重新生成资源包:`--screen 16x9`

讲者如果没有 op,可以用记分板触发器(数据包每刻自动重新启用):

```
/trigger ppt_next       下一页
/trigger ppt_prev       上一页
```

当前页码显示在动作栏上。翻到最后一页再往后、或第一页再往前,会收到聊天提示。

## 待实测确认

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

这些我无法在本地验证,第一次跑的时候重点看这几处:

1. **`item_display:"none"` 是否是合法取值。** 如果 `/function ppt:build` 报错、屏幕没出现,多半是这里;改成 `"fixed"` 再试。
2. **屏幕尺寸与缩放上限。** 模型元素是 16 单位(1 格),靠 `scale:[7,4,1]` 放大到 7×4 格。如果尺寸不对或元素被裁剪,调整 `transformation.scale`。
3. **朝向。** 模型正反面都贴了图,但其中一面是镜像的。如果看到文字反了,把实体转 180 度(`/tp @e[tag=ppt_screen] ~ ~ ~ ~180 ~`)。
4. **图集容量。** 640 宽的贴图 × 88 页 = 2060 万像素,需要 8192×8192 的图集。客户端加载资源包时如果日志出现 `Unable to fit`,就降低贴图宽度重新生成。
5. **`width` / `height` 的视锥裁剪。** 已按屏幕实际尺寸设置,如果站远或侧看时屏幕消失,把 `view_range` 调大。

## 和地图方案的取舍

这个方案拿到全彩和无上限分辨率,换来三个约束:

- 资源包必须提前生成,换内容要重连
- 客户端需要下载资源包(你这份 88 页约 7 MB)
- 翻页交互从"斧头左右键"退化成命令/触发器,因为纯原版没有左键点击事件

如果这三条可以接受,它就是更合适的方案。
