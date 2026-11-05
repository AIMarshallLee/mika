# 昆仑增长｜Mika 首次部署与验收（Android AI Agent）

> 用途：把本仓库的 Mika 在一台**测试用安卓手机**上跑通，再评估是否与咔哒工作台集成。本文是 Fork 后新增的部署记录，不修改上游代码。
>
> 核对日期：2026-11-05；核对到的上游正式 Release：`v0.1.37`。使用前先核对 [上游最新 Release](https://github.com/wangzan101/Mika/releases/latest)，勿把版本号当作永远最新。

## 1. 当前状态与边界

- [x] 已在 [AIMarshallLee/mika](https://github.com/AIMarshallLee/mika) 建立 Fork，原项目为 [wangzan101/Mika](https://github.com/wangzan101/Mika)。
- [x] 源码包括 `app/`、`core/`、`tools/mika/`、`docs/`、Gradle 配置；许可证为 Apache-2.0，保留上游 `LICENSE`、`NOTICE` 和第三方许可。
- [x] 上游提供现成 APK、电脑端 CLI/Skill、MCP 远程任务接口。**试用不必先编译 APK。**
- [ ] 尚未在真实安卓机安装 APK、授予权限或连接模型。
- [ ] 尚未在用户 Mac 上安装 CLI、连接手机、完成真机任务验收。
- [ ] 尚未验证与咔哒的接口集成；不得宣称已可商用。

## 2. 最短安装路径（优先原版）

1. 准备**测试专用** Android 14+ 手机（操作 App 最低 Android 12，聊天/网页/文件最低 Android 11；Linux 沙箱需要 arm64）。先备份必要数据，避免用正在处理客户业务的主账号。
2. 在安卓手机上打开 [上游最新 Release](https://github.com/wangzan101/Mika/releases/latest)，下载对应 APK；`arm64-v8a` 可选 `-arm64.apk`，不清楚架构时选不带 `-arm64` 的通用 APK。安装后启动一次。
3. Mika「设置 → 模型」添加一个**支持工具调用的主模型**；按应用内「测试并保存」进行实际调用验证。可用服务商 API Key，或依上游说明尝试支持的账号登录。**绝不把 Key、登录凭据、Token 写入 GitHub、文档或截图。**
4. 如需操作其他 App：Mika「设置 → 手机操作」按照[官方手机操作设置指南](setup-guide.md)配对无线调试；该流程通常无需电脑。系统安全授权必须由机主确认，**不 Root、不关闭系统安全保护**。只是聊天、浏览网页可略过。
5. 在 Mika「设置 → 恢复与修复」运行兼容性自检；只有实际检查通过，才记录通过。
6. 保持**询问模式（默认）**，不开启自主模式。注意：官方当前询问模式**只会在花钱的步骤前征求确认**；发消息、发布内容、删除、更改设置等可能直接执行，因此试用阶段必须通过任务指令与测试账号双重限制。

## 3. Mac 电脑连接（在手机完成第 2 节后）

电脑端以 **USB + CLI** 先验收，暂不启用公网连接。需要 macOS 上的 `adb`（Android platform-tools）、Python 3.10+、Git、`pipx`。环境不存在时只安装缺的工具，不覆盖已有配置。

```bash
adb version
adb devices
# 确认设备已通过手机屏幕上的 USB 调试授权，且 adb devices 显示 device
pipx install 'git+https://github.com/wangzan101/Mika.git@v0.1.37#subdirectory=tools/mika'
mika --version
mika devices --json
# 多台设备时明确选择正确的 SERIAL
mika connect --device SERIAL --json
mika doctor --device SERIAL --json
mika ui --device SERIAL
```

说明：

- 用**上游发布版的固定 tag**安装 CLI，是为了让电脑端和已装 APK 对齐；之后如更新 APK，同步检查 CLI 版本。若要研究自己 Fork 的代码再单独 Clone，本步无需二次下载整个源码。
- `mika connect` / `doctor` 通过**仅说明连接正常**，不能当作任务执行成功。
- `mika ui` 是本机浏览器任务台，可查看、提交、暂停任务；不要分享带访问密钥的页面链接。
- 原项目也提供 [Skill](computer-install.md) 和 [MCP](mcp.md) 两种接入；**先用 CLI 跑通，再评估咔哒或其他客户端是否需要 MCP**，不必两个都装。

## 4. 首轮只读验收

先在手机上直接执行以下测试，不要用有真实客户数据的账号：

| 编号 | 对 Mika 说的话 | 合格标准 |
| --- | --- | --- |
| T01 | 「只告诉我这台手机的安卓版本，不要修改任何设置。」 | 回答与系统版本一致；无写操作 |
| T02 | 「在浏览器阅读 GitHub 上的 Mika 项目 README，用中文概括三项核心能力；不要登录、上传、发布或点击购买。」 | 能打开网页、内容可核对 |
| T03 | 「打开系统设置，查看系统语言后向我汇报；不要修改任何设置。」 | 能完成一次实际只读 App 操作，且没有改动 |

从 Mac 进行额外验证时，只发送一项只读任务：

```bash
mika --device SERIAL task run --goal "只告诉我这台手机的安卓版本，不修改任何设置、账号或文件。" --json
# 从上一条结果取 TASK_ID
mika --device SERIAL task wait TASK_ID --timeout 45 --json
```

验收必须同时有 **任务最终状态** 和 **人工核对结果**。任务返回 `done` 不代表输出必然正确；`doctor` 不验证模型调用。每项记录：设备型号 / Android 版本 / Mika 版本 / 模型服务商与型号（不记密钥）/ 是否成功 / 耗时 / 失败截图或日志（先脱敏）。

**禁止首轮测试**：真实账号群发、自动私信、批量招聘招呼、自动购买、朋友圈或短视频发布、删除文件、改系统设置、任何客户隐私读取。第三方 App 自动化还可能违反平台条款，需单独审核。

## 5. 给电脑端 AI 助手的交接提示词（直接复制）

> 目标：使用我的 Fork `https://github.com/AIMarshallLee/mika`，把 Mika 官方稳定版本部署到 USB 连接的安卓**测试机**，并执行只读验收。先阅读本仓库 `docs/KUNLUN_MIKA_QUICKSTART.zh-CN.md`、上游 `docs/install-with-agent.md`、`docs/computer-install.md`，再执行。仅在我实际连接并授权手机后继续。逐项检查 `adb`、设备数量与状态、Android 版本、CPU 架构；从上游 Releases 选匹配 APK，绝不猜版本、绝不替我确认手机安全授权。不能因安装失败而直接卸载原应用（可能丢数据），不得自动 Root、绕过安全限制、打印令牌/密钥。安卓安装和模型配置成功后，优先 USB + CLI，`connect/doctor` 后实际运行 T01–T03 并核对结果。执行期间不要操作真实业务或客户账号，不要修改本仓库源码，不要自行开启网络远程服务。任何关键报错先停并报告错误原文、已完成步骤与最小修复建议；最后给我明确的通过/未通过清单。 

## 6. 以后准备二次开发时

1. 先基于 T01–T03 的实际测试数据确认原版能力；
2. 按 [电脑连接文档](computer.md) / [MCP 文档](mcp.md) 对接，先小范围测试，不直接并入咔哒核心；
3. 如需修改源码，先创建功能分支、保留上游同步路径、运行原项目测试；
4. 构建要求见 [贡献指南](../.github/CONTRIBUTING.md)：JDK 17 + Android SDK；单测与 APK Debug 构建均需实际运行记录，不可将“代码已 Fork”视为“构建已通过”。
