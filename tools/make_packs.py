#!/usr/bin/env python3
"""把脚本所在文件夹里的 ppt/ 生成成资源包和数据包。

用法(脚本和 ppt 文件夹放在一起):

    python make_packs.py
    python make_packs.py --width 800
    python make_packs.py --width auto --screen 7x4

幻灯片文件名必须是纯数字: 1.png、2.png、3.png ... 按数字顺序排序。
可读格式由 Pillow 决定,包含 png/jpg/gif/bmp/webp,输出统一重编码为 PNG。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - 只做友好提示
    sys.exit("需要 Pillow:\n    python -m pip install Pillow")


# 取自 26.1.2 服务端 jar 里的 version.json 的 pack_version:
#   resource_major 84 / resource_minor 0
#   data_major 101 / data_minor 1
# 新版格式号超过 64 之后 pack_format 不够用了,必须写 min_format 与 max_format,
# 写法对照原版自带的 data/minecraft/datapacks/*/pack.mcmeta。
RESOURCE_PACK_FORMAT = (84, 0)
DATA_PACK_FORMAT = (101, 1)

NAMESPACE = "ppt"
PACK_NAME = "InGamePPT"
SLIDE_EXTENSIONS = {"png", "jpg", "jpeg", "gif", "bmp", "webp"}

# 图集要给打包留余量,mipmap 还要再多三分之一
ATLAS_SIZES = (2048, 4096, 8192, 16384)
PACKING_HEADROOM = 0.75
MIPMAP_FACTOR = 1.33


def scan_slides(folder: Path) -> tuple[list[tuple[int, Path]], int]:
    """返回按页码排序的幻灯片,以及被忽略的重复页码数量。"""
    found: dict[int, Path] = {}
    duplicates = 0
    for entry in sorted(folder.iterdir()):
        if not entry.is_file():
            continue
        extension = entry.suffix.lower().lstrip(".")
        if extension not in SLIDE_EXTENSIONS:
            continue
        if not entry.stem.isdigit():
            continue
        number = int(entry.stem)
        if number < 1:
            continue
        if number in found:
            duplicates += 1
            continue
        found[number] = entry
    return sorted(found.items()), duplicates


def fit_to_screen(path: Path, width: int, height: int) -> Image.Image:
    """保持比例缩放到贴边,其余用黑色填充(居中)。"""
    with Image.open(path) as opened:
        image = opened.convert("RGBA")
        scale = min(width / image.width, height / image.height)
        scaled_width = max(1, round(image.width * scale))
        scaled_height = max(1, round(image.height * scale))
        resized = image.resize((scaled_width, scaled_height), Image.LANCZOS)

    canvas = Image.new("RGB", (width, height), (0, 0, 0))
    canvas.paste(resized, ((width - scaled_width) // 2, (height - scaled_height) // 2), resized)
    return canvas


def auto_width(pages: int, aspect: float, atlas: int = 8192) -> int:
    """挑一个刚好能装进目标图集的最大宽度。"""
    budget = atlas * atlas * PACKING_HEADROOM / MIPMAP_FACTOR
    per_slide = budget / max(1, pages)
    # per_slide = w * (w / aspect)
    width = int((per_slide * aspect) ** 0.5)
    return max(128, width // 16 * 16)


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    # 显式写 \n,否则 Windows 上会变成 CRLF,和别的实现产生无意义的差异
    path.write_text(content, encoding="utf-8", newline="\n")


def compact(value) -> str:
    """命令里内嵌的 JSON 用紧凑写法,和 Java 版本的模板保持一致。"""
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def pack_meta(version: tuple[int, int], description: str) -> str:
    major, minor = version
    return json.dumps({
        "pack": {
            "description": description,
            "min_format": [major, minor],
            "max_format": major,
        }
    }, ensure_ascii=False, indent=2) + "\n"


def make_resource_pack(root: Path, slides: list[tuple[int, Path]],
                       width: int, height: int) -> None:
    write(root / "pack.mcmeta", pack_meta(RESOURCE_PACK_FORMAT, f"{PACK_NAME} 幻灯片"))

    total = len(slides)
    for index, (_, path) in enumerate(slides):
        page = index + 1
        # 必须放在 textures/item/ 下:图集是按目录声明的(assets/minecraft/atlases/items.json
        # 只收录 item/ 目录),放在别处的贴图不会被打进图集,模型就渲染成黑紫格子。
        texture = root / f"assets/{NAMESPACE}/textures/item/page/slide_{page}.png"
        texture.parent.mkdir(parents=True, exist_ok=True)
        fit_to_screen(path, width, height).save(texture, "PNG", optimize=True)
        if page % 20 == 0 or page == total:
            print(f"  已处理 {page}/{total}")

        write(root / f"assets/{NAMESPACE}/items/page_{page}.json", json.dumps({
            "model": {
                "type": "minecraft:model",
                "model": f"{NAMESPACE}:page_{page}",
            }
        }, indent=2) + "\n")

        # 必须相对模型原点居中:ItemDisplayRenderer 只做了一次绕 Y 轴 180° 旋转,
        # 没有任何居中平移,所以模型坐标系原点就是实体位置。
        # 面片原先照抄 item/generated 放在 z 7.5~8.5,中心在 z=8 单位(半格),
        # 会让整块屏幕偏离实体位置半格。这里改成 z -0.5~0.5,中心落在原点。
        # 正反两面都贴图,其中一面是镜像的,看到反字就把 yaw 加 180。
        write(root / f"assets/{NAMESPACE}/models/page_{page}.json", json.dumps({
            "textures": {"0": f"{NAMESPACE}:item/page/slide_{page}"},
            "elements": [{
                "from": [-8, -8, -0.5],
                "to": [8, 8, 0.5],
                "faces": {
                    "north": {"uv": [0, 0, 16, 16], "texture": "#0"},
                    "south": {"uv": [16, 16, 0, 0], "texture": "#0"},
                },
            }],
        }, indent=2) + "\n")


def make_data_pack(root: Path, slides: list[tuple[int, Path]],
                   screen_width: float, screen_height: float) -> None:
    pages = len(slides)
    functions = root / f"data/{NAMESPACE}/function"
    config = f"storage {NAMESPACE}:config"
    width_text = f"{screen_width:g}"
    height_text = f"{screen_height:g}"
    ratio = screen_height / screen_width

    write(root / "pack.mcmeta", pack_meta(DATA_PACK_FORMAT, f"{PACK_NAME} 幻灯片控制"))

    write(root / "data/minecraft/tags/function/load.json",
          json.dumps({"values": [f"{NAMESPACE}:init"]}, indent=2) + "\n")
    write(root / "data/minecraft/tags/function/tick.json",
          json.dumps({"values": [f"{NAMESPACE}:tick"]}, indent=2) + "\n")

    write(functions / "init.mcfunction", "\n".join([
        "# 只在第一次载入时创建记分板,重复 /reload 时这几行会报 \"already exists\",无害",
        "scoreboard objectives add ppt.page dummy",
        "scoreboard objectives add ppt_next trigger",
        "scoreboard objectives add ppt_prev trigger",
        "scoreboard objectives add ppt.calc dummy",
        "scoreboard players set #page ppt.page 1",
        "# 屏幕的位置与尺寸,只在缺失时写入,不会覆盖你改过的值",
        f"execute unless data {config} x run data modify {config} x set value 0.0",
        f"execute unless data {config} y run data modify {config} y set value 64.0",
        f"execute unless data {config} z run data modify {config} z set value 0.0",
        f"execute unless data {config} yaw run data modify {config} yaw set value 0.0",
        f"execute unless data {config} width run data modify {config} width set value {width_text}",
        f"execute unless data {config} height run data modify {config} height set value {height_text}",
    ]) + "\n")

    # 讲者可能没有 op,所以翻页也开放成触发器;触发器用过一次会被禁用,因此每刻重新启用
    write(functions / "tick.mcfunction", (
        "scoreboard players enable @a ppt_next\n"
        "scoreboard players enable @a ppt_prev\n"
        f"execute as @a[scores={{ppt_next=1..}}] run function {NAMESPACE}:next\n"
        "scoreboard players reset @a[scores={ppt_next=1..}] ppt_next\n"
        f"execute as @a[scores={{ppt_prev=1..}}] run function {NAMESPACE}:prev\n"
        "scoreboard players reset @a[scores={ppt_prev=1..}] ppt_prev\n"
        "# 定位蛋刷出的生物:把屏幕挪过去再把它清掉\n"
        f"execute as @e[tag=ppt_mover] at @s run function {NAMESPACE}:move_here\n"
    ))

    # 位置和尺寸都由 storage 里的数值决定,所以可以用宏函数做精确控制。
    # 实体位置就是屏幕中心,scale 直接取宽高(模型是 1 格见方)。
    write(functions / "place.mcfunction", (
        "# 内部函数: 按 storage 的数值重建屏幕,由 build / apply 调用\n"
        f'kill @e[type=minecraft:item_display, tag=ppt_screen]\n'
        f'$summon minecraft:item_display $(x) $(y) $(z) '
        '{Tags:["ppt_screen"],Rotation:[$(yaw)f,0f],'
        'width:$(width)f,height:$(height)f,view_range:2.0f,'
        'brightness:{sky:15,block:15},item_display:"none",'
        f'item:{{id:"minecraft:paper",count:1,components:{{"minecraft:item_model":"{NAMESPACE}:page_1"}}}},'
        'transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],'
        'scale:[$(width)f,$(height)f,1f],right_rotation:[0f,0f,0f,1f]}}\n'
    ))

    # 把屏幕摆到讲者现在站的位置,中心在视线高度,朝向沿用讲者朝向。
    # 数值统一经记分板转成 double:直接存 Rotation 会留下浮点标签,宏替换出来就是
    # "180.0ff" 这种畸形字面量。
    def store_double(path: str, key: str) -> str:
        return (
            f"execute store result score #v ppt.calc run data get entity @s {path} 100\n"
            f"execute store result {config} {key} double 0.01 run scoreboard players get #v ppt.calc"
        )

    write(functions / "build.mcfunction", (
        "# 站到屏幕中心该在的位置,执行本函数:屏幕中心落在视线高度,朝向沿用你的朝向\n"
        + store_double("Pos[0]", "x") + "\n"
        + "execute store result score #v ppt.calc run data get entity @s Pos[1] 100\n"
        "# +150 即抬高 1.5 格到视线高度\n"
        "scoreboard players add #v ppt.calc 150\n"
        + f"execute store result {config} y double 0.01 run scoreboard players get #v ppt.calc\n"
        + store_double("Pos[2]", "z") + "\n"
        + store_double("Rotation[0]", "yaw") + "\n"
        + f"function {NAMESPACE}:apply\n"
        + f"tellraw @s {compact({'text': '屏幕已放到你的位置,用 /function ' + NAMESPACE + ':info 查看数值', 'color': 'green'})}\n"
    ))

    # 改完 storage 里的数值后调它:重建屏幕并保持当前页码
    write(functions / "apply.mcfunction", (
        f"function {NAMESPACE}:place with {config}\n"
        f"function {NAMESPACE}:refresh\n"
    ))

    # 定位蛋:用刷怪蛋当"把屏幕搬到这"的工具。
    # 蛋里带 entity_data,给刷出的生物打上 ppt_mover 标签;tick 里看到这个标签就
    # 把屏幕挪到生物位置并把它清掉——这样不需要插件,也不需要玩家会输坐标。
    write(functions / "mover_egg.mcfunction", (
        "give @s minecraft:pig_spawn_egg["
        'minecraft:item_name="PPT 定位蛋",'
        # 借用原版某个物品的外观,省得再打包一张贴图
        'minecraft:item_model="minecraft:ender_eye",'
        # 26.1.2 的 entity_data 组件要求带 id 字段(实体类型),裸 NBT 复合会解析失败:
        #   Malformed 'minecraft:entity_data' component: Expected 'id' field
        'minecraft:entity_data={id:"minecraft:pig",Tags:["ppt_mover"],NoAI:1b,Silent:1b,NoGravity:1b,'
        'Invulnerable:1b,PersistenceRequired:1b,Fire:-1s,'
        'DeathLootTable:"minecraft:empty"}]\n'
        f"tellraw @s {compact({'text': '拿好定位蛋:对着想放屏幕的位置右键,屏幕中心就挪过去', 'color': 'green'})}\n"
    ))

    write(functions / "move_here.mcfunction", (
        "# 内部函数:由 tick 对每个带 ppt_mover 标签的生物执行(@s 是那只生物)\n"
        f"data modify {config} x set from entity @s Pos[0]\n"
        f"data modify {config} y set from entity @s Pos[1]\n"
        f"data modify {config} z set from entity @s Pos[2]\n"
        f"tellraw @a[distance=..16] {compact({'text': 'PPT 已移动到此处', 'color': 'green'})}\n"
        "kill @s\n"
        f"function {NAMESPACE}:apply\n"
    ))

    # 朝向的两个正交操作:转向自己 / 掉个面。
    # 哪个面是"正"取决于模型的 UV 约定,与其猜,不如让这两个命令组合出正确朝向。
    write(functions / "face_me.mcfunction", (
        "# 站在屏幕前面执行:让屏幕转向你当前的朝向\n"
        "execute store result score #v ppt.calc run data get entity @s Rotation[0] 100\n"
        f"execute store result {config} yaw double 0.01 run scoreboard players get #v ppt.calc\n"
        f"function {NAMESPACE}:apply\n"
        f"tellraw @s {compact({'text': '屏幕已转向你的朝向;若看到反字,再用 /function ' + NAMESPACE + ':flip', 'color': 'green'})}\n"
    ))

    write(functions / "flip.mcfunction", (
        "# 屏幕正反面掉个个儿:看到镜像的文字时用\n"
        f"execute store result score #v ppt.calc run data get {config} yaw 100\n"
        "scoreboard players add #v ppt.calc 18000\n"
        f"execute store result {config} yaw double 0.01 run scoreboard players get #v ppt.calc\n"
        f"function {NAMESPACE}:apply\n"
    ))

    # 数值查询:宏函数把 storage 的值直接打进聊天栏,顺便把修改方式也印出来
    write(functions / "info.mcfunction", f"function {NAMESPACE}:info_print with {config}\n")
    write(functions / "info_print.mcfunction", (
        f'$tellraw @s [{{"text":"屏幕中心 ","color":"gray"}},{{"text":"$(x) $(y) $(z)","color":"white"}}]\n'
        f'$tellraw @s [{{"text":"朝向 ","color":"gray"}},{{"text":"yaw=$(yaw)","color":"white"}},'
        '{"text":"   尺寸 ","color":"gray"},{"text":"$(width) x $(height) 格","color":"white"}]\n'
        f'tellraw @s {compact({"text": f"改数值: /data modify {config} <x|y|z|yaw|width|height> set value <值>",
                              "color": "dark_gray"})}\n'
        f'tellraw @s {compact({"text": f"然后 /function {NAMESPACE}:apply 生效", "color": "dark_gray"})}\n'
        f'tellraw @s {compact({"text": f"纹理比例 {screen_width:g}:{screen_height:g},高度应为宽度的 {ratio:.4f} 倍",
                              "color": "dark_gray"})}\n'
        f'tellraw @s {compact({"text": f"快速移动: /function {NAMESPACE}:mover_egg 拿定位蛋,右键就能把屏幕挪过去",
                              "color": "dark_gray"})}\n'
    ))

    write(functions / "clear.mcfunction",
          "kill @e[type=minecraft:item_display, tag=ppt_screen]\n")

    last_message = compact({"text": f"已经是最后一页了 ({pages}/{pages})", "color": "yellow"})
    write(functions / "next.mcfunction", (
        f"execute if score #page ppt.page matches {pages + 1}.. "
        f"if entity @s[type=minecraft:player] run tellraw @s {last_message}\n"
        f"execute unless score #page ppt.page matches {pages + 1}.. "
        "run scoreboard players add #page ppt.page 1\n"
        f"function {NAMESPACE}:refresh\n"
    ))

    first_message = compact({"text": f"已经是第一页了 (1/{pages})", "color": "yellow"})
    write(functions / "prev.mcfunction", (
        "execute if score #page ppt.page matches ..1 "
        f"if entity @s[type=minecraft:player] run tellraw @s {first_message}\n"
        "execute unless score #page ppt.page matches ..1 "
        "run scoreboard players remove #page ppt.page 1\n"
        f"function {NAMESPACE}:refresh\n"
    ))

    refresh = ["# 按当前页码刷新屏幕"]
    for page in range(1, pages + 1):
        refresh.append(f"execute if score #page ppt.page matches {page} "
                       f"run function {NAMESPACE}:show_{page}")
    write(functions / "refresh.mcfunction", "\n".join(refresh) + "\n")

    for index, (_, path) in enumerate(slides):
        page = index + 1
        actionbar = compact({"text": f"第 {page}/{pages} 页  {path.name}", "color": "aqua"})
        write(functions / f"show_{page}.mcfunction", (
            "data merge entity @e[tag=ppt_screen,limit=1] "
            f'{{item:{{id:"minecraft:paper",count:1,components:{{"minecraft:item_model":"{NAMESPACE}:page_{page}"}}}}}}\n'
            f"execute if entity @s[type=minecraft:player] run title @s actionbar {actionbar}\n"
        ))


def zip_folder(source: Path, target: Path) -> None:
    files = sorted(path for path in source.rglob("*") if path.is_file())
    # 条目时间戳固定,否则内容没变也会因为 mtime 不同而产生不同的 SHA1,
    # 客户端就会白白重下一遍资源包。
    fixed_time = (1980, 1, 1, 0, 0, 0)
    with zipfile.ZipFile(target, "w") as archive:
        for path in files:
            info = zipfile.ZipInfo(path.relative_to(source).as_posix(), date_time=fixed_time)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def verify(resource_pack: Path, pages: int) -> list[str]:
    """生成后的自检:路径、引用、图集目录三件事必须成立,否则游戏里就是黑紫格子。"""
    problems: list[str] = []
    for page in range(1, pages + 1):
        item_file = resource_pack / f"assets/{NAMESPACE}/items/page_{page}.json"
        model_file = resource_pack / f"assets/{NAMESPACE}/models/page_{page}.json"
        if not item_file.is_file():
            problems.append(f"第 {page} 页缺少物品模型定义")
            continue
        if not model_file.is_file():
            problems.append(f"第 {page} 页缺少平面模型")
            continue

        target = json.loads(item_file.read_text(encoding="utf-8"))["model"]["model"]
        namespace, _, path = target.partition(":")
        if not (resource_pack / f"assets/{namespace}/models/{path}.json").is_file():
            problems.append(f"第 {page} 页物品模型指向的模型不存在: {target}")

        for texture in json.loads(model_file.read_text(encoding="utf-8"))["textures"].values():
            namespace, _, path = texture.partition(":")
            if not (resource_pack / f"assets/{namespace}/textures/{path}.png").is_file():
                problems.append(f"第 {page} 页模型引用的贴图不存在: {texture}")
            elif not path.startswith(("item/", "block/")):
                # 图集只收录 textures/item 与 textures/block,别处的贴图永远不会被打包
                problems.append(f"第 {page} 页贴图不在图集收录的目录里: {texture}")
    return problems


def sha1(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def report(slides, resource_pack, data_pack, resource_zip, data_zip, width, height) -> None:
    pages = len(slides)
    total_pixels = pages * width * height
    print(f"贴图尺寸 : {width}x{height},共 {pages} 页")
    print(f"贴图总量 : {total_pixels / 1_000_000:.1f} M 像素")
    print("  图集需要有约 25% 打包余量,加上 mipmap 再乘 1.33:")
    for size in ATLAS_SIZES:
        capacity = size * size
        fits = "可以" if capacity * PACKING_HEADROOM >= total_pixels * MIPMAP_FACTOR else "装不下"
        print(f"    {size:>5} x {size:<5} = {capacity / 1_000_000:>6.1f} M 像素  {fits}")

    print(f"\n资源包: {resource_pack}")
    print(f"  assets/{NAMESPACE}/items/     {pages} 个物品模型定义")
    print(f"  assets/{NAMESPACE}/models/    {pages} 个平面模型")
    print(f"  assets/{NAMESPACE}/textures/  {pages} 张贴图")
    print(f"  格式 {RESOURCE_PACK_FORMAT[0]}.{RESOURCE_PACK_FORMAT[1]},"
          f"zip {resource_zip.stat().st_size / 1048576:.1f} MB")

    print(f"\n数据包: {data_pack}")
    print(f"  格式 {DATA_PACK_FORMAT[0]}.{DATA_PACK_FORMAT[1]},"
          f"zip {data_zip.stat().st_size / 1048576:.2f} MB")

    print("\n=== 把这行填进 server.properties ===")
    print(f"resource-pack=<{resource_zip.name} 放到 HTTP 服务上后的 URL>")
    print(f"resource-pack-sha1={sha1(resource_zip)}")
    print("require-resource-pack=true")
    print(f"\n=== 数据包 zip 放进 <世界目录>/datapacks/ 后 /reload ===")
    print(f"然后进游戏执行: /function {NAMESPACE}:build")


def main() -> None:
    script_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description="把 ppt/ 里的幻灯片生成资源包与数据包",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="默认读取脚本同目录下的 ppt/,输出也放在脚本同目录。")
    parser.add_argument("--slides", type=Path, default=script_dir / "ppt",
                        help="幻灯片目录(默认: 脚本同目录下的 ppt/)")
    parser.add_argument("--out", type=Path, default=script_dir,
                        help="输出目录(默认: 脚本同目录)")
    parser.add_argument("--width", default="640",
                        help="贴图宽度,或 auto 按图集容量自动选(默认 640)")
    parser.add_argument("--screen", default="7x4",
                        help="屏幕尺寸,格为单位,例如 7x4 或 10x6(默认 7x4)")
    args = parser.parse_args()

    try:
        screen_width, screen_height = (float(v) for v in args.screen.lower().split("x"))
    except ValueError:
        sys.exit("--screen 格式应为 宽x高,例如 7x4")

    slides_folder: Path = args.slides
    if not slides_folder.is_dir():
        sys.exit(f"找不到幻灯片目录: {slides_folder}")

    slides, duplicates = scan_slides(slides_folder)
    if not slides:
        sys.exit(f"{slides_folder} 里没有幻灯片。文件名必须是纯数字,例如 1.png、2.png。")

    if args.width == "auto":
        width = auto_width(len(slides), screen_width / screen_height)
        print(f"自动选择贴图宽度: {width}")
    else:
        width = int(args.width)
    height = round(width * screen_height / screen_width)
    height += height % 2

    print(f"幻灯片   : {len(slides)} 页 ({slides_folder})")
    if duplicates:
        print(f"注意     : 有 {duplicates} 个重复页码被忽略")

    out_root: Path = args.out
    resource_pack = out_root / f"{PACK_NAME}-ResourcePack"
    data_pack = out_root / f"{PACK_NAME}-Datapack"

    make_resource_pack(resource_pack, slides, width, height)
    make_data_pack(data_pack, slides, screen_width, screen_height)

    problems = verify(resource_pack, len(slides))
    if problems:
        for problem in problems[:10]:
            print(f"自检失败: {problem}")
        sys.exit(f"生成结果有问题,共 {len(problems)} 处,先不要部署。")
    print(f"自检通过: {len(slides)} 页的模型、贴图与图集目录都正确")

    resource_zip = out_root / f"{PACK_NAME}-ResourcePack.zip"
    data_zip = out_root / f"{PACK_NAME}-Datapack.zip"
    zip_folder(resource_pack, resource_zip)
    zip_folder(data_pack, data_zip)

    report(slides, resource_pack, data_pack, resource_zip, data_zip, width, height)


if __name__ == "__main__":
    main()
