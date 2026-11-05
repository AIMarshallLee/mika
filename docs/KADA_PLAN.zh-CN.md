# 咔哒全端 AI Agent 产品体系 · 技术审计与实施报告 V1.0

日期：2026-11-05 ｜ 对应《咔哒全端 AI Agent 产品体系开发总指令 V1.0》
状态标记：✅ 已完成并验证 ｜ 🟡 已实现未验证 ｜ 📋 计划开发 ｜ ⛔ 暂时无法实现

---

## A. 技术架构结论

### A1. 审计到的真实现状（只写读过代码确认的）

| 资产 | 现状 | 证据 |
|---|---|---|
| Mika Fork `AIMarshallLee/mika` | 与上游 v0.1.37 同步，另有 2 篇昆仑试点文档。Kotlin 双模块：`core`（纯 JVM：Agent 主循环、提示词、知识/技能、远程任务协议）+ `app`（安卓：虚拟屏、无障碍、浏览器、沙箱、MCP 服务、定时任务） | 仓库代码 |
| Mika 电脑接口 | **已存在**：HTTP 任务 API `/api/v1` 与 MCP `/mcp` 共用一套任务服务；CLI `tools/mika`（Python，`--device SERIAL` 多机不串台、每次调用自建/自删 adb 临时转发）；`request_id` 幂等；状态 `starting/queued/running/paused/waiting_for_answer/waiting_on_phone/done/failed/cancelled`；电脑交来的任务里付款类动作永不执行 | `docs/cli.md`、`docs/mcp.md`、`core/remote/*` |
| Mika 为「改包名并存安装」的设计 | **已存在**：Provider、助手进程名、无障碍组件、USB 脚本都按 `packageName` 生成；上游自己用 `.dev` 后缀并存安装验证过 | `RecoveryPermissions.kt` 注释、`HelperHandoff.kt`、`docs/dev/remote-device-access-check.md` |
| Mika 写死原包名的地方 | 只有电脑端 CLI（`/sdcard/Android/data/com.wangzan101.mika/files`）和文档；电脑连接默认端口 8765 两个 App 会抢 | `mika_cli.py`、`McpSettings.kt` |
| Mika 更新逻辑 | 每次打开 App 查 `wangzan101/Mika` 的 GitHub 最新发布 → 白标版会把原版当更新 | `MikaLinks.kt`、`UpdateChecker.kt` |
| Mika 技能 | 内置 6 个（含 `info-research`、`documents`）；手机上可「导入技能」（粘贴 SKILL.md，先待确认再收下） | `core/resources/skills`、`SkillImport.kt` |
| Mika 审批 | 询问模式只在花钱前确认；发送、发布、删除不会自动拦；最终核查在开发阶段被关闭 | `docs/dev/roadmap.md` 已知问题 1 |
| 咔哒工作台 `kadadesk` | Electron。**已有**：开放 MCP 服务（知识检索、回复草稿、接待控制、`app_task_request` 带主人审批的应用任务、增长飞轮客户/事件只读接口）；**MCP 客户端**（工具插件，支持 stdio 与 HTTP）；**cli 类插件**（在插件自带 Python 环境里跑命令）；ADB 手机模块（实验：手机接待、线索查找，最多 6 台）；「连接增长飞轮」官方插件 | `desktop/open-mcp.ts`、`desktop/mcp-client.ts`、`shared/plugins.ts`、`desktop/phone/*`、`desktop/flywheel-connect.ts`（分支 `claude/release-0.8.53`） |
| 咔哒品牌资源 | **正式图标存在**：小紫怪透明母版 `assets/brand/kada-mascot-master.png`、`build-assets/icon-1024.png`、10 个变体；主题色昆仑紫 `#4A265F` | kadadesk 仓库 |
| 增长飞轮业务层 | 无独立的「增长飞轮 AI 工作台」工程可审计到；已有经营总控工作台（`kunlun-command-center`）、深圳服务器上的 CRM、飞书多维表格「昆仑增长｜经营工作台」、咔哒的「连接增长飞轮」事件接口 | 项目记录 |

### A2. 保留 / 合并 / 废弃 / 暂缓

| 决定 | 内容 | 理由 |
|---|---|---|
| **保留** | Mika 作为手机执行端引擎（不重写主循环）；Mika 自带的 CLI/MCP 任务协议 | 能力完整（虚拟屏后台操作、浏览器、沙箱、定时、技能、电脑接口），且上游活跃；重写成本远高于跟随 |
| **合并** | 「最小任务数据契约」直接映射到 Mika 现有任务协议，不另造协议（见 C1）；电脑调手机复用咔哒已有的 **cli 插件 / MCP 客户端**，不新写手机控制协议 | 避免重复开发；`request_id` 已提供幂等 |
| **合并** | 增长飞轮先用现有 CRM + 飞书经营工作台，通过咔哒「连接增长飞轮」插件对接，不新建 CRM | 指令要求，且已有接口 |
| **废弃（不做）** | 全局字符串替换改名；改 Kotlin 包名；自建插件市场；为三个技能建三套 Agent | 破坏上游同步；过度设计 |
| **暂缓** | 多机调度器/设备集群、完整审批状态机、费用统计、私有仓库 `kadadesk-android` 迁移、正式发布签名 | 先跑通单机真机闭环再做；见 D |

### A3. 对原方案的修正

1. **手机控制不用新协议**：Mika 的 CLI/MCP 已经是「任务协议」，电脑端只需带上设备序列号和包名（`MIKA_PACKAGE`）。
2. **咔哒电脑端已有自己的 ADB 手机模块**（操作聊天软件接待）。两者定位不同：咔哒 ADB 模块 = 电脑直接点手机屏幕；Mika = 手机自己当 Agent。**两者不能同时操作同一台手机**，需要在设备上明确分工（P2 设备台账里写清每台手机归谁用）。
3. **`evidence`、`completed_at` 在 Mika 远程状态里不存在**（只有 `started_at_ms`、`answer`、`verified`、`failure_kind`）：证据要靠任务目标里要求「每条带公开 URL」并在电脑端校验；完成时间由电脑端在拿到终态时记录。
4. **私有仓库 `kadadesk-android`**：本会话没有建仓库的权限，P0 先在你的 Fork 分支 `kada/v0.1.0-demo` 上完成（白标改动无密钥、无客户数据）。建议你在 GitHub 新建私有仓库后，由我把分支迁过去（保留上游 remote）。

### A4. 上游升级不破坏咔哒定制的做法

- 品牌只通过**构建类型 `kada` + 资源覆盖层 `app/src/kada/res`**，原版资源一字不改；
- 必改的 Kotlin 只有 10 处、全部带「咔哒(昆仑增长)修改」注释，原版构建行为不变（CI 同时构建原版并跑原版单测把关）；
- 合并上游流程：`git fetch upstream && git merge upstream/main` → `python3 tools/kada/gen_brand_strings.py` → 推送 `kada/**` 分支，Actions 自动构建 + 跑全部原版单测。

---

## B. 咔哒手机版开发结果（P0）

| 项目 | 内容 |
|---|---|
| 代码位置 | `github.com/AIMarshallLee/mika` 分支 `kada/v0.1.0-demo` |
| 说明文档 | `docs/KADA_MOBILE.zh-CN.md`（改动清单、构建、许可证、已知限制） |
| 应用 | 名称「咔哒」；标识 `com.kunlungrowth.kadadesk`（与原版并存）；版本 `0.1.0-demo`，关于页标「演示版」 |
| 图标 | 咔哒正式图标小紫怪：自适应图标 + 安卓 13 主题图标 + 通知小图标 + App 内 Logo；昆仑紫强调色 |
| 构建命令 | 云端：推送 `kada/**` 分支自动构建（`.github/workflows/kada-demo.yml`）；本地：`tools/kada/build-demo.sh` |
| 安装命令 | `tools/kada/install-demo.sh <序列号>`（必带序列号、只装咔哒、不卸载任何 App） |
| 测试结果 | 见 E 节（以实际构建与真机记录为准） |

---

## C. 三端协同设计（P2）

### C1. 任务数据契约 → 直接映射 Mika 现有协议

| 契约字段 | 来源 | 现状 |
|---|---|---|
| `task_id` | Mika `task run` 返回 | 存在 |
| `device_id` | adb 序列号（CLI `--device`） | 存在（电脑端记录） |
| `source` | 电脑端写（`kadadesk` / `flywheel` / `manual`） | 电脑端记录 |
| `goal` | Mika `goal`（≤ 上限字数） | 存在 |
| `status` | 映射：`starting/queued`→`queued`；`running/paused/waiting_for_answer/waiting_on_phone`→`running`（保留子状态）；`done`→`completed`；`failed`；`cancelled` | 存在 |
| `input_refs` | 电脑端记录（Mika 远程 API 不收附件） | 电脑端记录 |
| `result` | Mika `answer` + `verified` | 存在 |
| `evidence` | 任务目标强制「每条附公开 URL」，电脑端解析校验；运行证据用 Mika 任务 ID + 手机上的对话记录 | **Mika 不提供，需电脑端补** |
| `created_at` / `completed_at` | `started_at_ms` / 电脑端拿到终态时记录 | 部分存在 |
| `error` | Mika `reason` + `failure_kind` | 存在 |
| 幂等键（扩展） | Mika `request_id`（小写 UUID，同编号只启动一次） | 存在 |

### C2. 数据流

```
增长飞轮(CRM/飞书)  ← 人工确认后写入 ─┐
                                    │
咔哒电脑端  ──「咔哒手机」cli 插件(mika CLI, MIKA_PACKAGE=com.kunlungrowth.kadadesk)──▶ adb -s 序列号 转发 ──▶ 咔哒手机版 /api/v1
   ▲  展示结果/证据、审批                                                            │
   └──────────────── task wait 轮询终态（≤50 秒/次）◀────────────────────────────────┘
```

### C3. 代码模块分工

| 模块 | 仓库 | 做什么 | 现状 |
|---|---|---|---|
| 手机执行 | mika `kada` 构建 | 执行任务、返回状态/回答 | ✅ 现有 |
| 设备选择、派单、轮询 | kadadesk：新增「咔哒手机」**cli 插件**（包装 `mika` CLI） | 列手机（`mika devices`）、选手机、`task run/wait/cancel` | 📋 只需插件清单 + 少量界面，复用 cli 插件框架 |
| 结果展示、写台账 | kadadesk 界面 + 「连接增长飞轮」插件 | 展示线索表 → 用户点「录入」才写 CRM | 📋 |
| 审批 | kadadesk 现有主人审批（系统身份认证/验证器） | 高风险动作前批准 | 📋 复用 `app_task_request` 的审批流 |

### C4. 最小集成示例（Mac 上可直接运行，已装 mika CLI）

```bash
export MIKA_PACKAGE=com.kunlungrowth.kadadesk
S=ZY22HPW3FR
mika connect --device $S --json && mika doctor --device $S --json
REQ=$(uuidgen | tr A-Z a-z)
mika --device $S task run --json --goal "只读调研：在公开网页找出淮北 5 家家装企业，每条列企业名称、公开联系方式、业务类型、可能的 AI 需求及依据、来源 URL。不登录、不私信、不提交表单；找不到来源的不写。"
# 取返回的 task_id
mika --device $S task wait TASK_ID --timeout 45 --json   # 循环直到 done/failed/cancelled
```

验收（P2）：同一命令分别指定 2 号、3 号机不串台；拔线时 CLI 返回连接错误而不是假成功；同一 `request_id` 重发不重复执行；输出里不出现令牌（CLI 自带脱敏）。

---

## D. 产品迭代路线

| 版本 | 目标 | 关键工作 | 验收 |
|---|---|---|---|
| **V0.1 演示版**（11/6） | 能装、能演示 | 白标、图标、并存、关闭原版更新、端口隔离 | P0 十项 |
| **V0.2 业务技能** | 一台手机多个岗位 | 三个 SKILL.md（商机调研、内容策划、文档助手）先用手机「导入技能」上线，不改代码；跑够每个 5 次真实测试后再打进 `kada` 构建做内置技能 | 每技能 5 次：完成率、可核对率、意外操作 0、耗时、Token |
| **V0.3 电脑手机协同** | 电脑派单给指定手机 | kadadesk「咔哒手机」cli 插件；设备台账（每台手机归 Mika 执行还是咔哒接待）；结果卡片 + 人工「录入台账」 | C4 验收 + 断网状态明确 |
| **V1 企业交付版** | 能卖、能交付 | 动作层审批（只读/草稿/本地写/对外发送/删除/付款六级）；正式发布签名（GitHub Secrets）；GPL 组件源码随发布；咔哒自己的更新源与反馈渠道；客户配置模板、权限配置、备份迁移、使用手册、售后 | P3 清单 |

V0.2 三个技能的落地要点：
- **商机调研**：基于内置 `info-research`，规定输出表头与「每条必须有公开 URL、无来源不写」；停止条件写「遇验证码/登录墙/付费页即停」。
- **内容策划**：只产出草稿到对话与文件（沙箱生成 Markdown/表格），不调用任何发布动作。
- **文档助手**：基于内置 `documents`；只读源文件，输出另存新文件（沙箱工作区）。
- 技能文件只写「怎么做」（Mika 格式要求），授权与停止规定由审批层负责——这也是 V1 必须补审批层的原因。

---

## E. 当前状态清单

见报告末尾「执行记录」，以实际构建、真机命令回执为准。
