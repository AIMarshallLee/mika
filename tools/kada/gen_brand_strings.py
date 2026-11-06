#!/usr/bin/env python3
"""咔哒(昆仑增长)白标:从原版字符串生成咔哒构建用的资源覆盖。

原版 app/src/main/res 里的文字一字不改;本脚本读出其中提到「Mika」的字符串,
把对用户显示的产品名换成咔哒,写到 app/src/kada/res/<语言目录>/strings_kada_brand.xml。
咔哒构建类型(kada)的资源优先于 main,于是只在咔哒安装包里生效。

合并 Mika 上游新版本后重新运行一次即可:
    python3 tools/kada/gen_brand_strings.py
网址里的 Mika(如 github.com/wangzan101/Mika)和小写的 mika 命令名不替换。
少量需要单独措辞的字符串(关于页署名、口号等)写在 CUSTOM 里,优先于自动替换。
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/res"
OUT = ROOT / "app/src/kada/res"
OUT_NAME = "strings_kada_brand.xml"

# 中文(简/繁)用「咔哒」,其他语言用拼写 Kada
NAME = {"values-zh": "咔哒", "values-b+zh+Hant": "咔噠"}
DEFAULT_NAME = "Kada"

# 说的就是 Mika 本身(电脑端命令行工具仍叫 mika),不换名字
KEEP = {"mcp_cli_install_prompt"}

CJK = "\u4e00-\u9fff\u3000-\u303f\uff00-\uffef"


def tidy_cjk(text: str, name: str) -> str:
    """中文里原文在 Mika 两边留了空格;换成汉字后和相邻汉字之间的空格去掉。"""
    text = re.sub(f"(?<=[{CJK}]) {name}", name, text)
    return re.sub(f"{name} (?=[{CJK}])", name, text)


# 不在网址、路径、类名里:前面不是 / . 字母数字,后面不是 / 字母数字
WORD = re.compile(r"(?<![/.A-Za-z0-9_])Mika(?![/A-Za-z0-9_])")

CUSTOM = {
    "values-zh": {
        "about_tagline": "咔哒 AI 工作助手 · 增长飞轮 AI 出品",
        "about_author": "昆仑增长 出品 · © 2026",
        "about_repo": "开源项目 Mika",
        "about_repo_sub": "咔哒手机版基于它开发 · %1$s",
        # 对话页示例:换成咔哒自己的业务场景,都写成只读(只看、只查,不下单不发消息)
        "chat_example_follow": "打开美团，看看附近评分最高的 3 家美甲店，只看不下单",
        "chat_example_group_summary": "打开大众点评，找 3 家评分 4.5 以上的美业门店，记下店名和地址",
        "chat_example_jobs": "在网上查淮北最近在招 AI 相关岗位的 5 家公司，附上来源网址",
        "chat_example_multi_step": "看看我手机里装了哪些办公和客服类 App，列个清单",
        "about_check_sub": "演示版不在线更新,新版本由昆仑增长提供",
        "about_data_note": "你发给咔哒的话、屏幕内容和截图，只发给你自己设置的模型服务商。咔哒演示版没有自己的服务器，不收集你的数据，也不自动检查更新。",
        "welcome_title": "你好，我是咔哒",
        "welcome_lead": "咔哒 AI 工作助手，在你的手机上替你办事。\n告诉我要做什么，我在后台把它做完。",
    },
    "values": {
        # 原版里是不翻译的字符串,只能在默认语言里覆盖:所有语言都显示「演示版」
        "about_beta_label": "演示版",
        "about_tagline": "Kada AI work assistant · by Growth Flywheel AI",
        "about_author": "By Kunlun Growth · © 2026",
        "about_repo_sub": "Kada mobile is built on it · %1$s",
        # 只有中文和英文写了说明;所有语言共用这一份,中英双语
        "kada_upstream_notice": "咔哒手机版由昆仑增长基于开源项目 Mika(github.com/wangzan101/Mika,Copyright 2026 wangzan101)修改而来,遵循 Apache-2.0 许可;修改内容:应用名称、图标、配色、界面文案、安装包标识,关闭了原版的在线更新。随包发布的 PRoot、talloc 未作修改,源码见 Mika 发布页。\n\nKada Mobile by Kunlun Growth is a modified version of the open-source Mika (github.com/wangzan101/Mika, Copyright 2026 wangzan101), licensed under Apache-2.0. Changed: app name, icon, colors, UI text and package id; online updates of the original are turned off.",
        "chat_example_follow": "Open Meituan and look at the 3 top-rated nail salons nearby. Just look, don’t order",
        "chat_example_group_summary": "Open Dianping and find 3 beauty salons rated 4.5+, note their names and addresses",
        "chat_example_jobs": "Find 5 companies in Huaibei hiring for AI roles recently, with source links",
        "chat_example_multi_step": "List the office and customer-service apps installed on my phone",
        "about_check_sub": "The demo build does not update online",
        "about_repo": "Open-source project Mika",
        "welcome_title": "Hi, I’m Kada",
    },
    "values-b+zh+Hant": {
        "about_author": "昆侖增長 出品 · © 2026",
        "about_repo": "開源專案 Mika",
        "about_repo_sub": "咔噠手機版基於它開發 · %1$s",
        "chat_example_follow": "打開美團，看看附近評分最高的 3 家美甲店，只看不下單",
        "chat_example_group_summary": "打開大眾點評，找 3 家評分 4.5 以上的美業門店，記下店名和地址",
        "chat_example_jobs": "在網上查淮北最近在招 AI 相關職缺的 5 家公司，附上來源網址",
        "chat_example_multi_step": "看看我手機裡裝了哪些辦公和客服類 App，列個清單",
    },
}

# 其他语言(阿拉伯、西班牙、日、韩等)没有单独写的,作者行和示例统一用英文版,不再出现原版作者和原版示例
ALL_LOCALES = ("about_author", "about_repo", "about_repo_sub", "chat_example_follow", "chat_example_group_summary",
               "chat_example_jobs", "chat_example_multi_step")


def escape_text(s: str) -> str:
    # 原样写回:ElementTree 读出的文字已反转义,这里只转义 XML 必须转义的字符
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def inner_xml(el: ET.Element) -> str:
    """字符串里可能带 <xliff:g>、<b> 之类标签:保留原样(含子元素)。"""
    parts = [escape_text(el.text or "")]
    for child in el:
        parts.append(ET.tostring(child, encoding="unicode"))
    return "".join(parts)


def main() -> int:
    ET.register_namespace("tools", "http://schemas.android.com/tools")
    ET.register_namespace("xliff", "urn:oasis:names:tc:xliff:document:1.2")
    total = 0
    for vdir in sorted(p for p in MAIN.iterdir() if p.is_dir() and p.name.startswith("values")):
        if vdir.name == "values-night":
            continue
        name = NAME.get(vdir.name, DEFAULT_NAME)
        found: dict[str, str] = {}
        for f in sorted(vdir.glob("strings*.xml")):
            for el in ET.parse(f).getroot().findall("string"):
                raw = inner_xml(el)
                if not WORD.search(raw) or el.get("name") in KEEP:
                    continue
                key = el.get("name")
                attrs = ' translatable="false"' if el.get("translatable") == "false" else ""
                text = WORD.sub(name, raw)
                if vdir.name in NAME:
                    text = tidy_cjk(text, name)
                found[key] = (text, attrs)
        custom = dict(CUSTOM.get(vdir.name, {}))
        for key in ALL_LOCALES:
            if key not in custom and vdir.name != "values":
                custom[key] = CUSTOM["values"][key]
        for key, text in custom.items():
            attrs = ' translatable="false"' if key in ("about_beta_label", "kada_upstream_notice") else ""
            found[key] = (escape_text(text).replace("\n", "\\n").replace("'", "\\'"), attrs)
        if vdir.name == "values":
            found["app_name"] = ("咔哒", ' translatable="false"')
        if not found:
            continue
        out = OUT / vdir.name
        out.mkdir(parents=True, exist_ok=True)
        lines = [
            '<?xml version="1.0" encoding="utf-8"?>',
            "<!-- 由 tools/kada/gen_brand_strings.py 生成,请勿手改;改措辞请改脚本里的 CUSTOM 后重新生成。 -->",
            '<resources xmlns:tools="http://schemas.android.com/tools" xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">',
        ]
        for key in sorted(found):
            text, attrs = found[key]
            lines.append(f'    <string name="{key}"{attrs}>{text}</string>')
        lines.append("</resources>")
        (out / OUT_NAME).write_text("\n".join(lines) + "\n", encoding="utf-8")
        total += len(found)
        print(f"{vdir.name}: {len(found)}")
    print(f"total {total}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
