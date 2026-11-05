#!/usr/bin/env bash
# 咔哒(昆仑增长):构建咔哒手机版演示包。需要 JDK 17 + Android SDK(与原版 Mika 构建要求相同)。
# 产物:dist/咔哒手机版-v<版本>.apk(dist/ 已在 .gitignore 里,安装包不进仓库)。
# 签名:演示阶段用本机调试签名;正式发布前改用自己保管的发布密钥,密钥绝不提交到 GitHub。
set -euo pipefail
cd "$(dirname "$0")/../.."
VERSION="0.1.0-demo"
./gradlew :app:assembleKada --stacktrace
APK="app/build/outputs/apk/kada/app-kada.apk"
[ -f "$APK" ] || { echo "没有找到构建产物 $APK" >&2; exit 1; }
mkdir -p dist
OUT="dist/咔哒手机版-v${VERSION}.apk"
cp "$APK" "$OUT"
echo "构建完成:$OUT"
if command -v aapt2 >/dev/null 2>&1; then aapt2 dump badging "$OUT" | head -3; fi
shasum -a 256 "$OUT" 2>/dev/null || sha256sum "$OUT"
