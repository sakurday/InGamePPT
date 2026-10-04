# InGamePPT

在 Minecraft 游戏内播放幻灯片, 适合会议等场景.

> 推荐用**数据包 + 资源包**版本, 不需要装插件: 见 [datapack.md](datapack.md).
> 下面写的是最早的服务端插件版本.

这是一款服务端插件, 版本要求 Minecraft 26.1.2 Paper 服务端.

## 使用方法

首次启动插件会创建文件夹 <paper_server>\plugins\InGamePPT\ppt

你需要将已经组织好, 待播放的 png 图片文件放入此文件夹下

图片命名规则应该这样: "1.png", "2.png", "3.png", ...

然后, 进入游戏, 你需要搭建一片矩形的物品展示框区域

输入 /ppt select

然后, 使用金斧头, 左键点击展示框区域的左上角和右下角, 这片区域是你要展示的图片的区域.

最后, 输入 /ppt play 即可播放 ppt, 会从 "1.png" 开始播放

通过 /ppt + 或 /ppt - , 你可以翻页

播放完毕后, 使用 /ppt clear 来结束播放
