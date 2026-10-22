# 栖信 Qixin

在本机统一查看 MiPush 和 FCM 的连接、注册证据和投递事件。Compose Material 3 界面与 root CLI 共用本地服务，管理功能不依赖互联网。

**已切换到栖信管理与定制 XMSF 运行时，正在完成稳定性验收。** 保留 GMS、栖序推送代理和必要注册桥接。注册缓存不等于业务服务器已采用该通道，连接正常也不代表某条消息已送达。

当前源码版本为 **0.1.0-alpha5**；版本变化见 [CHANGELOG](CHANGELOG.md)。

## 当前实现

- 独立 APK、API 102 Vector 模块和 root 本地 socket 服务。
- 查看 FCM 实际 GcmService 连接状态；MiPush 使用定制运行时的管理 Binder 读取真实连接状态。
- 发现应用 FCM SDK 缓存，仅保存 sender/token 哈希；只读导入 MiPush 注册与事件元数据。
- 七天本地历史，128 MiB 数据库上限，正文默认不记录，按应用开启。
- 系统投递、通知发布、PendingIntent 点击观察；未获得来源证据的通知显示 unknown。
- 配置版本冲突检测，原子写入、root CLI 和签名认证 GUI。
- 温和重连：30/120/300 秒退避，每半小时最多三次，不清空注册、不自动重启整个 GMS。

## 迁移与验收

消息、常驻服务、持续任务分开显示；重复服务通知覆盖状态，不淹没消息历史。Android 原生通知负责横幅、进度、展开、点击目标和操作按钮，栖信不再创建悬浮通知或复制通知。GUI 支持逐应用小米推送配置，通知渠道由 Android 设置管理。

HMS 实验已撤回并卸载。飞书原生 FCM 注册与离线投递已验证，alpha5 修复后台初始化撤销通知的问题；不据此宣称所有应用云端推送或长期稳定性通过。详见 [最新迁移记录](docs/migration-progress-2026-10-22.md) 和 [运行时构建、CLI 与回滚](runtime/README.md)。

## 开发与协议

构建步骤、工具链和安装边界见 [开发说明](docs/development.md)。贡献前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，安全报告见 [SECURITY.md](SECURITY.md)。

本项目采用 **GPL-3.0-only**，第三方代码保留其原许可证，见 [NOTICE](NOTICE)。许可证不授权发布第三方 APK、设备凭据或私有数据。`private/`、构建输出和签名材料不纳入仓库。

## 当前接管进度

连接恢复与栖序的短时推送放行已实现并进行本地对照验证。旧 FCM 连接守护可备份接管/撤回：

```sh
qixin connection takeover --dry-run
qixin connection takeover
qixin connection rollback
```

临时放行需要本仓库对应的栖序配套变更；详见验证记录。其他应用的业务注册、深链及真实离线推送需要分别验收，不应把单个应用的结果视为全部问题已经解决。

## 通知跳转与飞书兼容进展

已加入限定来源和有效期的通知点击修复，以及默认关闭的飞书原生后台连接保活。Bilibili、钉钉、淘宝的既有通知重放进入了对应内容页；飞书稳定后台收信、通知点击和一次锁屏收信通过。飞书官网版 8.3.6 开启原生 FCM 后，已验证完全退出进程后的真实 FCM 投递；alpha5 的后台初始化通知保护不再要求开启保活。飞书 MiPush 离线投递仍未通过，注册 ACK 不代表投递成功。

最新 FCM 清理修复与实机结果见 [官网版 FCM 验证](docs/feishu-official-fcm-2026-10-22.md)；早期 CLI 与保活调查见 [推送调查记录](docs/push-investigation-2026-10-21.md)。

### 动态配色

界面默认跟随系统壁纸色板和深浅色模式，无需单独选择主题。若系统“壁纸与样式”使用固定基础色，应用会遵循该系统色板；选择壁纸颜色后即可随壁纸取色。配色通过系统资源更新，不读取壁纸图片、不访问网络。

### 原生通知展示

0.1.0-alpha3 起移除自绘通知岛及悬浮窗权限。原始通知由 Android System UI 展示，弹出、声音和振动遵循应用通知渠道及勿扰设置；栖信仍记录消息和常驻状态，保留小米原始通知的点击修复。旧 `island` 配置字段仍可导入，但不再控制显示。通知监听组件沿用旧组件名，以保留已授权的通知访问权限。

### 批量注册与应用筛选

应用页只显示具有 MiPush／FCM SDK 组件或已有注册记录的已安装应用，排除推送基础服务自身。仅有 Firebase 分析组件或本地常驻通知不是支持证据。详情中的“从推送应用列表和批量注册中排除”可隐藏不需要管理的应用；应用页开启“显示已排除的应用”可恢复。

“一键注册”创建后台串行任务，不自动打开界面、不清除数据。小米通道只向已允许注册、未停止且有可用接收器的应用发送 SDK 注册请求；其他应用、FCM 需用“手动打开注册”交给自身 SDK 处理。完成后刷新应用与注册记录；“请求已发送”不等于注册完成，也不保证业务服务器会使用此通道。

```sh
qixin registration batch --dry-run
qixin registration batch
qixin registration batch example.app
qixin registration status
qixin registration cancel
qixin app configure example.app '{"pushExcluded":true}'
```

仅管理模式可执行；取消不撤回已经发送的请求。最近任务保留在当前服务内存，服务重启不自动重放。CLI 只发后台请求，手动打开入口位于 GUI。详细结果按应用和通道分别展示。
