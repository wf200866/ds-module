# ds模块 — DeepSeek 客户端美化增强模块

一个基于 LSPosed/Xposed 的 DeepSeek 官方 Android 客户端（`com.deepseek.chat`）美化与增强模块。
在本机 DeepSeek 进程内运行，提供界面美化、气泡外观、壁纸、输入框美化、聊天增强等可选功能。

> 本项目与 DeepSeek（深度求索）、High-Flyer、LSPosed、Xposed 均无隶属关系，也未经其官方认可或提供支持。
> 产品名称与商标归各自所有者所有。

## ✨ 功能

- 🎨 **气泡外观**：渐变气泡 / 液态玻璃 / 自定义配色 / 圆角 / 透明度
- 🖼 **背景壁纸**：图片壁纸叠加层（含整体模糊、色调、遮罩）
- 💬 **聊天增强**：消息编辑、批量删除会话、聊天备份、搜索
- ⌨️ **输入框美化**：提示文字色、输入文字色、光标色、背景玻璃化
- 📝 **系统提示词**：注入角色/规则，让 AI 按设定回复
- 🎯 **欢迎语自定义**、**灵动岛**、**自定义头像**
- 🧹 **缓存清理**、**设备状态注入（可选）**
- 📱 设置入口注入 DeepSeek 设置页（检查更新 → ds模块）

## 📦 环境要求

- Android 7.0 及以上
- 已 Root 的 LSPosed / Xposed 环境（支持传统 Xposed 入口）
- 官方 DeepSeek 客户端 `com.deepseek.chat`

## 🚀 构建

```bash
# 需 Android SDK + JDK 8+
./gradlew assembleRelease
```

或使用 Android Studio 打开本项目直接构建。

> 编译所需 jar（Xposed API 等）位于 `app/libs/`，为 `compileOnly` 依赖，不会打包进 APK。

## 🧩 使用

1. 安装 APK
2. 在 LSPosed 中启用本模块，作用域勾选 **DeepSeek**
3. 重启 DeepSeek
4. 进入 DeepSeek 的 **设置 → 检查更新** 位置，即可看到 **ds模块** 入口

## 📂 源码结构（开源部分）

```
app/src/main/java/com/example/application/
├── HookInit.java          # Xposed 入口，所有 hook 注册
├── DsConfig.java          # 配置读写
├── DsFloat.java           # 悬浮球 / 设置面板 UI
├── DsSettingsPage.java    # 设置页
├── DsOverlay.java         # 背景壁纸叠加层
├── DsChatBgHook.java      # 原生壁纸 hook（开发中）
├── DsBubbleGradient.java  # 气泡渐变
├── DsChatEditor.java      # 聊天编辑 / 批量删除
├── DsChatSearch.java      # 聊天搜索
├── DsChatView.java        # 聊天视图
├── DsChatButton.java      # 聊天入口按钮
├── DsMultiReply.java      # 一问多答
├── DsTextWave.java        # 文字波纹
├── DsLicense.java         # 授权
├── DsUI.java / DsPageView.java / DsPanelV2.java   # UI 组件
├── DsSettingsHook.java    # 设置页入口注入
├── DsDeviceContext.java   # 设备状态注入
├── DsLocalMessageText.java
├── MainActivity.java
└── GlobalApplication.java
```

## 🙏 致谢 / 第三方

- **[Deekseep](https://github.com/lllucccian/Deekseep)** (lllucccian) — 本项目在功能架构、部分 hook 思路上参考了该项目，其以 **GPL-3.0** 许可发布。详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
- **LSPosed / Xposed** — 运行环境与 API。

## ⚖️ 许可

本项目以 **GNU General Public License v3.0** 发布，详见 [LICENSE](LICENSE)。

因参考了以 GPL-3.0 发布的 Deekseep 项目，依据 GPL 的传染性条款，本项目同样以 GPL-3.0 开源。
你可以自由使用、修改、分发，但**分发衍生作品时须同样以 GPL-3.0 开源并提供源码**。

## ⚠️ 免责声明

请务必阅读 [DISCLAIMER.md](DISCLAIMER.md)。使用本模块可能修改本地聊天数据，请提前备份。
因使用本模块产生的任何后果由使用者自行承担。

## 🔗 仓库

- GitHub: https://github.com/wf200866/ds-module
