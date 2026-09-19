# 第三方声明 / Third-Party Notices

本模块使用了或参考了以下第三方项目与资源。相关权利归各自所有者所有。

## Deekseep

- **项目**：Deekseep（DeepSeek LSPosed 模块）
- **作者**：lllucccian
- **来源**：https://github.com/lllucccian/Deekseep
- **许可**：GNU General Public License v3.0 (GPL-3.0)

说明：本项目的功能架构、设置页信息层级、部分 hook 思路（如气泡背景注入、设置页入口注入等）
在开发过程中参考了 Deekseep 项目。本项目为独立实现，包名与 Deekseep（`com.dsmod.probe`）不同，
未直接复制其源码文件。依据 GPL-3.0 的传染性要求，本项目同样以 GPL-3.0 发布并提供源码。

## LSPosed / Xposed

- **来源**：https://github.com/LSPosed/LSPosed 、 https://github.com/rovo89/XposedBridge
- **许可**：Apache License 2.0 / 各自许可

说明：本项目通过传统 Xposed / LSPosed 运行时框架加载，使用其公开 API。相关 API 类仅作为
`compileOnly` 依赖参与编译，不随本模块 APK 分发。

## AndroidX / Google Material

- **来源**：https://developer.android.com/jetpack/androidx
- **许可**：Apache License 2.0

说明：参考 Material Design 的视觉规范进行界面设计。

## DeepSeek

- DeepSeek 是深度求索（High-Flyer）的商标/产品，本项目与官方无任何隶属关系。
- 本项目仅对官方客户端进行本地增强，不修改、不重分发官方应用本体。

---

若您是上述任一项目的作者，认为本项目的引用方式不妥，请通过仓库 Issue 联系，我们会及时调整。
