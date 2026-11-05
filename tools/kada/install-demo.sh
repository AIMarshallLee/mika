#!/usr/bin/env bash
# 咔哒(昆仑增长):把演示包装到「指定」的一台安卓手机。
# 用法:tools/kada/install-demo.sh <ADB序列号> [安装包路径]
# 安全约束:
#  - 必须给出序列号,所有 adb 命令都带 -s,不会误装到别的手机;
#  - 只安装/覆盖咔哒(com.kunlungrowth.kadadesk),不卸载、不清除任何 App(包括原版 Mika 和微信);
#  - 原版 Mika 与咔哒应用标识、签名都不同,装上后两者并存,数据互不迁移。
set -euo pipefail
SERIAL="${1:-}"
[ -n "$SERIAL" ] || { echo "用法:$0 <ADB序列号> [安装包]   先用 adb devices 查序列号" >&2; exit 2; }
cd "$(dirname "$0")/../.."
APK="${2:-dist/咔哒手机版-v0.1.0-demo.apk}"
[ -f "$APK" ] || { echo "没有找到安装包 $APK,先运行 tools/kada/build-demo.sh" >&2; exit 1; }
STATE="$(adb -s "$SERIAL" get-state 2>/dev/null || true)"
[ "$STATE" = "device" ] || { echo "手机 $SERIAL 未连接或未授权 USB 调试(状态:${STATE:-无})" >&2; exit 1; }
echo "手机:$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r') 安卓 $(adb -s "$SERIAL" shell getprop ro.build.version.release | tr -d '\r')"
echo "安装前 原版 Mika:$(adb -s "$SERIAL" shell pm list packages com.wangzan101.mika | tr -d '\r' | grep -x 'package:com.wangzan101.mika' >/dev/null && echo 已安装 || echo 未安装)"
adb -s "$SERIAL" install -r "$APK"
echo "安装后 咔哒:$(adb -s "$SERIAL" shell pm list packages com.kunlungrowth.kadadesk | tr -d '\r')"
echo "安装后 原版 Mika:$(adb -s "$SERIAL" shell pm list packages com.wangzan101.mika | tr -d '\r' | grep -x 'package:com.wangzan101.mika' >/dev/null && echo 仍在 || echo 未安装)"
echo "版本:$(adb -s "$SERIAL" shell dumpsys package com.kunlungrowth.kadadesk | grep -m1 versionName | tr -d '\r ')"
