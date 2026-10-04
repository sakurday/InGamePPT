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

```powershell
# 1. 编译生成器(依赖地图方案里那套无 Bukkit 依赖的图像代码)
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
javac -encoding UTF-8 -cp out/plugin -d out/probe tools/PackBuilder.java

# 2. 生成
java -cp "out/plugin;out/probe" PackBuilder <幻灯片目录> dist <贴图宽度> <屏幕宽格> <屏幕高格>
# 例:
java -cp "out/plugin;out/probe" PackBuilder paper_server/plugins/InGamePPT/ppt dist 640 7 4
```

幻灯片文件规则和之前一致:纯数字命名,`1.png`、`2.png`……,按数字顺序排序。

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

讲者站到屏幕应该在的位置,朝向墙面:

```
/function ppt:build     生成屏幕并显示第 1 页
/function ppt:next      下一页
/function ppt:prev      上一页
/function ppt:clear     删除屏幕
```

讲者如果没有 op,可以用记分板触发器(数据包每刻自动重新启用):

```
/trigger ppt_next       下一页
/trigger ppt_prev       上一页
```

当前页码显示在动作栏上。翻到最后一页再往后、或第一页再往前,会收到聊天提示。

## 待实测确认

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
