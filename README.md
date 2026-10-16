# 记账助手 (A-Expense-Tracker)

一个本地优先的 Android 记账应用，内置 AI 记账管家（支持任意 OpenAI 兼容 API）。

## 功能总览

### 核心记账
- **记一笔**：计算器键盘（`12.5+3` 表达式实时求值）、分类宫格、备注、成员、日期、关联资产
- **明细**：按日分组、月度收支/结余汇总、点击编辑、长按删除、账本切换
- **图表**：环形占比图、渐变趋势图、星期分布、分类排行、成员排行
- **预算**：月度总预算 + 超支进度条

### 扩展功能
- **🤖 AI 记账管家**：对话式记账（"午饭花了25"→自动分类入账）、月度查询、预算设置、语音输入（需配置 API）
- **多账本**：生活/工作分开记
- **资产账户**：现金/储蓄卡/信用卡/支付宝/微信/负债、余额联动、转账
- **模板记账**：常用账一键复记
- **周期账**：房租/工资等固定收支，启动时自动入账
- **日历视图**：月历上每日收支一览
- **搜索**：备注/分类/金额/成员模糊搜索
- **分类管理**：自定义分类增删改
- **记账提醒**：AlarmManager 定时通知
- **隐私锁**：4 位数字密码门禁
- **打卡统计**：记账天数、连续天数
- **云同步**：账号密码/邮箱验证码登录，数据云端备份，换机不丢
- **备份恢复**：JSON 全量导出/导入
- **CSV 导出**（带 BOM，Excel 友好）
- **桌面小部件**：月度收支概览
- **本地存储**：Room/SQLite

## AI 架构

### 配置任意 OpenAI 兼容 API
设置 → AI 服务配置，填端点/模型/Key（DeepSeek/Kimi/智谱/百炼/自建 vLLM）。

### Agent 协议
App 端 `ai/AiAgent.kt` 用 JSON 动作协议（非 OpenAI function calling）：
模型输出 `{"action":"add_record","amount":25,"category":"餐饮",...}`，App 解析执行后回传结果，循环直到 `{"action":"reply"}`。任何能输出 JSON 的 LLM 都能用。

## 云同步架构

```
App ──HTTP──▶ 云服务器（deploy/deploy-jizhang-cloud.sh 一键部署）
              ├─ 账号密码登录（scrypt 哈希 + 令牌）
              ├─ 邮箱验证码登录（零依赖 SMTP，自动注册）
              └─ 数据同步（全量快照 + 版本冲突检测）
```

## 技术栈

Kotlin 2.2 · Jetpack Compose (Material 3) · Room 2.7 + Flow · MVVM · Navigation Compose · OkHttp

## 构建

首次构建前配置密钥：

```bash
cp secrets.properties.example secrets.properties
# 编辑 secrets.properties 填入你的 AI_API_KEY 和 CLOUD_SERVER
```

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 17+、Android SDK 35。

## 数据模型

```
record      流水：金额(恒正) + 分类 + 账本 + 成员 + 资产 + 冗余 year/month/day/week
category    分类：type(0支出系统/1支出自定义/2收入系统/3收入自定义)
book        账本
asset       资产账户（含转账 asset_transfer）
template    记账模板
cycle_bill  周期账（daily/weekly/monthly/yearly）
reminder    记账提醒
budget      预算（月 + 分类，空串=总预算）
label       标签（record_label 多对多）
```

设计要点：
- 金额恒为正，收支方向由分类决定，避免符号歧义
- 时间维度冗余列是列表/统计查询性能的关键

## Roadmap

- [ ] 图片记账（拍照识别小票）
- [ ] 周视图 / 年度报表
- [ ] 分类预算（细化到每个分类）
- [ ] 数据云同步多设备冲突合并
