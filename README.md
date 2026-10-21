# 栖信 Qixin

在本机统一查看 MiPush 和 FCM 的连接、注册证据和投递事件。Compose Material 3 界面与 root CLI 共用本地服务，管理功能不依赖互联网。

**已切换到栖信管理与定制 XMSF 运行时，正在完成稳定性验收。** 保留 GMS、栖序推送代理和必要注册桥接。注册缓存不等于业务服务器已采用该通道，连接正常也不代表某条消息已送达。

## 当前实现

- 独立 APK、API 102 Vector 模块和 root 本地 socket 服务。
- 查看 FCM 实际 GcmService 连接状态；MiPush 使用定制运行时的管理 Binder 读取真实连接状态。
- 发现应用 FCM SDK 缓存，仅保存 sender/token 哈希；只读导入 MiPush 注册与事件元数据。
- 七天本地历史，128 MiB 数据库上限，正文默认不记录，按应用开启。
- 系统投递、通知发布、PendingIntent 点击观察；未获得来源证据的通知显示 unknown。
- 配置版本冲突检测，原子写入、root CLI 和签名认证 GUI。
- 温和重连：30/120/300 秒退避，每半小时最多三次，不清空注册、不自动重启整个 GMS。

## 迁移与验收

消息、常驻服务、持续任务分开显示；重复服务通知覆盖状态，不淹没消息历史。通知岛提供进度、展开、原始点击目标和操作按钮。GUI 支持逐应用小米推送配置，通知渠道由 Android 设置管理。

HMS 实验已撤回并卸载，飞书离线推送研究暂停。24 小时采样正在进行，尚未宣称长期稳定性或所有应用云端推送通过。详见 [最新迁移记录](docs/migration-progress-2026-10-22.md) 和 [运行时构建、CLI 与回滚](runtime/README.md)。

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

临时放行需要本仓库对应的栖序配套变更；详见验证记录。业务注册迁移、焦点卡片深链与钉钉/飞书真实离线推送仍在验证，不应把当前观察记录视为全部问题已经解决。

## 通知跳转与飞书兼容进展

已加入限定来源和有效期的通知点击修复，以及默认关闭的飞书原生后台连接保活。Bilibili、钉钉、淘宝的既有通知重放进入了对应内容页；飞书稳定后台收信、通知点击和一次锁屏收信通过。飞书完全退出后的 MiPush／FCM 离线投递仍未通过，注册 ACK 不代表投递成功。

详细原因、CLI 操作、启动通知清理修复及验收边界见 [推送调查记录](docs/push-investigation-2026-10-21.md)。
