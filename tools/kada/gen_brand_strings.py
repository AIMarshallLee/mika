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

# ── 昆仑增长自己的账号与联系方式(白标时换掉原作者的) ──────────────────────────
# 公众号:示例「关注我的公众号」用的名字;改名后重新运行脚本
MP_NAME = "帅厂长"
OWN_GROUP = "老板增长实战群"
SERVICE_WECHAT = "MarshallPD"
SERVICE_EMAIL = "Marshall@kunlungrowth.com"

# 原作者仓库的网址统一换成昆仑增长的 Fork(文档内容相同)
UPSTREAM_URL = "github.com/wangzan101/Mika"
OWN_URL = "github.com/AIMarshallLee/mika"


CUSTOM = {
    "values-zh": {
        "about_tagline": "咔哒 AI 工作助手 · 增长飞轮 AI 出品",
        "about_author": "昆仑增长 出品 · 基于王赞开发的开源项目 Mika(Apache-2.0)二次开发",
        "about_repo": "开源代码",
        "about_repo_sub": "%1$s · 基于 Mika 二次开发",
        "about_guide": "增长飞轮 AI 官网",
        "about_guide_sub": "使用说明、落地案例和联系方式",
        "about_feedback_sub": f"客服微信 {SERVICE_WECHAT} · 复制诊断信息",
        "about_feedback_sheet_sub": f"遇到问题、有想法：加客服微信 {SERVICE_WECHAT}，或发邮件到 {SERVICE_EMAIL}；也可以在 GitHub 提问题",
        "about_feedback_github_sub": "报 Bug、提需求，在昆仑增长的仓库里跟踪进度",
        "diagnostics_copy_sub": f"版本、机型和设置状态，复制后发给客服微信 {SERVICE_WECHAT}",
        "about_check_sub": "演示版不在线更新,新版本由昆仑增长提供",
        "about_data_note": "你发给咔哒的话、屏幕内容和截图，只发给你自己设置的模型服务商。咔哒演示版没有自己的服务器，不收集你的数据，也不自动检查更新。",
        "welcome_title": "你好，我是咔哒",
        "welcome_lead": "咔哒 AI 工作助手，在你的手机上替你办事。\n告诉我要做什么，我在后台把它做完。",
        # 对话页的示例:换成增长飞轮 AI 自己的账号和获客场景
        "chat_example_follow": f"打开微信，搜索公众号「{MP_NAME}」，点击关注",
        "chat_example_group_summary": f"帮我总结一下「{OWN_GROUP}」今天都聊了些啥",
        "chat_example_jobs": "用网页在高德地图搜淮北的家装公司，整理前 5 家的名称、电话和地址",
        "chat_example_multi_step": "打开抖音搜「AI 获客」，看前 5 条视频，总结它们各讲了什么",
    },
    "values-b+zh+Hant": {
        "about_author": "昆侖增長 出品 · 基於王贊開發的開源專案 Mika(Apache-2.0)二次開發",
        "about_repo": "開源程式碼",
        "about_repo_sub": "%1$s · 基於 Mika 二次開發",
        "about_guide": "增長飛輪 AI 官網",
        "about_guide_sub": "使用說明、落地案例和聯絡方式",
        "about_feedback_sub": f"客服微信 {SERVICE_WECHAT} · 複製診斷資訊",
        "about_feedback_sheet_sub": f"遇到問題、有想法：加客服微信 {SERVICE_WECHAT}，或寄信到 {SERVICE_EMAIL}；也可以在 GitHub 提問題",
        "diagnostics_copy_sub": f"版本、機型和設定狀態，複製後發給客服微信 {SERVICE_WECHAT}",
        "chat_example_follow": f"開啟微信，搜尋公眾號「{MP_NAME}」，點擊關注",
        "chat_example_group_summary": f"幫我總結一下「{OWN_GROUP}」今天都聊了些啥",
    },
    "values": {
        # 原版里是不翻译的字符串,只能在默认语言里覆盖:所有语言都显示「演示版」
        "about_beta_label": "演示版",
        "about_tagline": "Kada AI work assistant · by Growth Flywheel AI",
        "about_author": "By Kunlun Growth · built on the open-source Mika by Wang Zan (Apache-2.0)",
        "about_check_sub": "The demo build does not update online",
        "about_repo": "Source code",
        "about_repo_sub": "%1$s · built on Mika",
        "about_guide": "Growth Flywheel AI website",
        "about_guide_sub": "Guide, case studies and contact",
        "about_feedback_sheet_sub": f"Problems or ideas? Message us on WeChat ({SERVICE_WECHAT}), email {SERVICE_EMAIL}, or open a GitHub issue.",
        "diagnostics_copy_sub": f"Version, device and settings — copy and send to {SERVICE_EMAIL}",
        "welcome_title": "Hi, I’m Kada",
        "chat_example_follow": f"Open WeChat, search for the official account {MP_NAME}, and follow it.",
    },
}

# 其他语言(日、韩、西……)里原作者的账号示例和「告诉作者」,统一用英文版覆盖
OTHER_LOCALES = ("chat_example_follow", "about_feedback_sheet_sub", "diagnostics_copy_sub",
                 "about_repo", "about_repo_sub", "about_guide", "about_guide_sub")


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
                key = el.get("name")
                has_url = UPSTREAM_URL in raw
                if not has_url and (not WORD.search(raw) or key in KEEP):
                    continue
                attrs = ' translatable="false"' if el.get("translatable") == "false" else ""
                text = raw if key in KEEP else WORD.sub(name, raw)
                text = text.replace(UPSTREAM_URL, OWN_URL)
                if vdir.name in NAME:
                    text = tidy_cjk(text, name)
                found[key] = (text, attrs)
        custom = dict(CUSTOM.get(vdir.name, {}))
        if vdir.name not in CUSTOM:
            custom.update({k: CUSTOM["values"][k] for k in OTHER_LOCALES if k in CUSTOM["values"]})
        for key, text in custom.items():
            attrs = ' translatable="false"' if key == "about_beta_label" else ""
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
