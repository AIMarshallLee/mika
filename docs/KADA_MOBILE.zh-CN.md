# 咔哒手机版（Android）· 白标说明与开发记录

> 咔哒手机版 = 原版 Mika 的完整能力 + 咔哒品牌。由昆仑增长基于 [Mika](https://github.com/wangzan101/Mika)（作者王赞，Apache-2.0）二次开发。
> 当前版本：**v0.1.0 演示版（Demo）**，不代表已达到正式商业交付标准。

## 1. 设计原则

- **不改 Agent 主循环、不改原版资源文件。** 白标通过新增构建类型 `kada` 实现：应用标识、名字、图标、主题色、少量文字由构建配置和 `app/src/kada/res` 资源覆盖层提供。原版构建（`assembleDebug` / `assembleRelease`）的产物和行为不变。
- **与原版 Mika 并存。** 应用标识 `com.kunlungrowth.kadadesk`（原版 `com.wangzan101.mika`），签名也不同；同一台手机可以同时装两个，数据互不迁移——咔哒需要单独配置模型、手机操作权限。
- **合并上游容易。** 改动集中在少数几行，并都带「咔哒(昆仑增长)修改」注释；资源覆盖由脚本生成，合并 Mika 新版后重跑脚本即可。

## 2. 改动清单（相对上游 Mika v0.1.37）

| 位置 | 改动 | 原因 |
|---|---|---|
| `app/build.gradle.kts` | 新增构建类型 `kada`（等同发布版、不可调试，演示阶段用本机调试签名）；用 `androidComponents` 把应用标识设为 `com.kunlungrowth.kadadesk`、版本 `0.1.0-demo`；新增 BuildConfig：`BRAND_NAME`、`MCP_DEFAULT_PORT`、`UPDATE_CHECK`、`ACCENT_LIGHT/DARK` | 独立应用标识与品牌配置 |
| `app/src/kada/res/**` | 应用名「咔哒」；桌面图标（咔哒正式图标小紫怪，自适应图标 + 安卓 13 主题图标）；App 内 Logo；通知小图标；约 190 条中文界面文字把 Mika 换成咔哒；关于页口号、署名、数据说明 | 品牌白标 |
| `MikaApp.kt` / `UpdateChecker.kt` | 咔哒构建**不检查 Mika 原版发布页** | 防止把原版安装包当成咔哒的更新 |
| `McpSettings.kt` | 电脑连接默认端口：原版 8765，咔哒 18765 | 两个 App 同机开启电脑连接时不抢端口 |
| `SystemPrompt.kt`（core） | 智能体对用户自称咔哒；原版提示词一字不变 | 演示时 AI 不自称 Mika |
| `ChatCopy` / `ChatDrawer` / `AboutScreen` / `TaskService` / 悬浮胶囊 | 写死的「Mika」改读 `BuildConfig.BRAND_NAME` | 顶栏、侧栏、通知、悬浮胶囊显示咔哒 |
| `AboutCopy` + `strings_about.xml` | 版本号后的「Beta」标记改为资源，咔哒显示「演示版」 | 明确标注 Demo |
| `ui/theme/Colors.kt` | 强调色读构建配置：咔哒用昆仑紫 `#4A265F` | 与咔哒电脑端视觉统一 |
| `tools/mika/scripts/mika_cli.py` | 环境变量 `MIKA_PACKAGE` 指定手机上的包名 | 电脑端 CLI 能连咔哒（原版写死了 Mika 的目录） |
| `tools/kada/*` | 资源生成脚本、构建脚本、安装脚本 | 可重复构建 |

**没有改的（有意保留）**：Kotlin 包名/命名空间 `com.wangzan101.mika`、类名、Intent Action 名（都是显式组件，不冲突）、原版许可证与署名、内部日志标签、文件导出目录 `下载/Mika`（按 App 区分，不串数据）、原版英文提示词里内部称呼的 Mika。

## 3. 构建与安装

环境：JDK 17、Android SDK 36（与原版相同）。

```bash
tools/kada/build-demo.sh                       # 产物 dist/咔哒手机版-v0.1.0-demo.apk
adb devices                                    # 查序列号
tools/kada/install-demo.sh ZY22HPW3FR          # 只装到指定手机，不卸载任何 App
```

改了 `CUSTOM` 措辞或合并上游后：`python3 tools/kada/gen_brand_strings.py`。
换图标母版：`python3 tools/kada/gen_icons.py <小紫怪透明底母版.png>`（母版在 kadadesk 仓库 `assets/brand/kada-mascot-master.png`）。

电脑端 CLI 连接咔哒：

```bash
export MIKA_PACKAGE=com.kunlungrowth.kadadesk
mika connect --device SERIAL --json
```

## 4. 许可证与商业使用

- Mika 主项目 Apache-2.0：允许商业二次开发。保留了 `LICENSE`、`NOTICE`、`licenses/THIRD_PARTY_NOTICES.md` 和关于页「开源许可」入口；关于页署名写明「基于王赞开发的开源项目 Mika 二次开发」。
- 修改过的文件都带「咔哒(昆仑增长)修改」说明（Apache-2.0 第 4(b) 条）。
- 第三方 GPL/LGPL 组件：PRoot（GPL-2.0+）、talloc（LGPL-3.0+）以独立程序随安装包分发。**正式对外分发咔哒时，须同样提供这些组件的对应源码**（上游做法是在发布页附 `mika-<版本>-gpl-sources.tar.gz`），咔哒发布页需照做。关于页的说明文字仍指向 Mika 发布页，正式版前要改成咔哒自己的发布地址。
- 签名：演示包用本机调试签名；正式发布换独立发布密钥，安全保存，**不进 GitHub**。API Key、客户资料同样不进仓库、不打进安装包。

## 5. 已知限制（演示版）

- 「反馈问题」仍打开上游 Mika 的 GitHub Issues；「开源仓库」指向上游（作为署名保留）。正式版需换成咔哒自己的反馈渠道。
- 「检查更新」在咔哒里会显示连不上（已关闭联网检查，属预期）。
- 部分英文内部提示词仍称呼 Mika，智能体已被告知对用户一律自称咔哒，偶尔仍可能说出 Mika。
- Mika 原版的「询问模式」只在花钱前确认；发消息、发布、删除不会自动拦。商用前必须在动作执行层加审批（见总指令第八章）。
