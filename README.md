# InGamePPT

这个项目有两个分支: 插件实现方法和数据包实现方法

插件实现方法在 main 分支, 数据包实现方法在 datapack-renderer 分支

个人更推荐数据包实现, 插件实现非常吃服务端各项性能.

本项目的实现初衷是为了学校的 MC 社团在游戏内实现会议. 原方案计划使用 Slideshowpro 模组, Slideshowpro 往游戏内加入了能向所有人展示的屏幕, 但是缺点很明显:

- 所有玩家必须安装此模组才能看到内容

- 此模组支持的 MC 版本与社团现有的原版服是不同版本, 参会者需要下载新版本

- 作为模组必然会导致服务端资源占用上升

而这个数据包的优势非常明显:

- 非常方便, 服务端无须做任何复杂处理, 只需要装一个数据包即可, 只要是 26.1.2 版本的服务端就可以用这个数据包.

- 非常方便, 客户端只需要装一个材质包即可, 无须下载新版本. 对于之前玩过社团原版服的人来说, 省去了大量下载新版本的时间.

- 性能友好, 此数据包几乎不会多占服务端的任何资源, 包括内存, CPU.

- 权限管理友好. 仅有 op 可以执行翻页操作

- 很方便的实行快捷操作: 由于翻页也是一个命令, 你可以**自己做一个**翻页的书, 书的内容写入此命令的点击事件, 实现打开这本书, 点一下就可以翻页. 把这本书给你想要的人, 那么这个人就有了翻页权限. 想要收回这个人的翻页权限, clear 这个人的书即可

- 非常稳定, 通过数据包+材质包加载自定义图片的方法已经被无数地图用过了, 不会导致游戏出现任何不稳定情况

当然, 此数据包也有缺点:

- 设置 ppt 的大小和位置不够精确, 比较麻烦

# 数据包实现的使用教程

## 生成 ppt 对应的数据包和材质包

看见 tools 文件夹下的 make_packs.py 了吗, 这就是生成数据包和材质包的脚本

使用此脚本之前, 你需要先安装一个 Python 库: Pillow

现在, 把脚本丢进一个空文件夹, 然后创建一个 "ppt" 文件夹

把你想要播放的 ppt 转为 PNG 图片, 放到 ppt 文件夹下

图片应该这样命名: "1.png", "2.png", "3.png", ......

图片放好后, 直接打开脚本, 选择清晰度, 一般保持默认选项是最好的

生成完后, 同文件夹下会有一个数据包和材质包

数据包你应该知道怎么加载进世界

每个需要看 ppt 的人应该装对应的材质包

## 游戏内播放 ppt 的具体操作

### 初始化

`/function ppt:build` 在命令执行者的位置生成 ppt , 执行者看向哪, ppt就朝向哪

`/function ppt:mover_egg` 给予自己一个 ppt 移动器, 右键哪个地方就把 ppt 移动到哪个地方

`/function ppt:face_me` 如果你发现 ppt 的朝向偏了, 执行此命令可以让 ppt 朝向你

`/data modify storage ppt:config width set value [NUMBER]` 设置 ppt 宽度

`/data modify storage ppt:config height set value [NUMBER]` 设置 ppt 高度

`/data modify storage ppt:config yaw set value [NUMBER]` 设置 ppt 具体朝向

`/function ppt:apply` 使用 `/data` 改变 ppt 设置后, 你需要执行此命令来应用设置

### 控制

`/function ppt:next` 翻到下一页

`/function ppt:prev` 翻到上一页

### 收尾工作

`/function ppt:clear` 清除所有 ppt

执行此命令后, 你可以安全的卸载数据包, 不会导致世界异常

但是这个世界还会残留一些东西, 这些东西应该不会对世界产生影响. 如果你有洁癖, 想彻底清理数据包所有内容, 请执行以下命令:

```
/function ppt:clear
/kill @e[tag=ppt_mover]
/scoreboard objectives remove ppt.page
/scoreboard objectives remove ppt_next
/scoreboard objectives remove ppt_prev
/scoreboard objectives remove ppt.calc
```

