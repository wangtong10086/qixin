# ADB 调试与重启

本机启用文件级加密（`ro.crypto.type=file`）。栖信的 root 控制器存放于 `/data/adb/qixin`，可在首次解锁前启动；应用自己的凭据加密缓存仍需用户解锁后才能读取。

- 已验证锁屏时 CLI 可连接服务、读取系统钩子状态。
- `tools/debug-session.sh begin` 开启最多 30 分钟 USB 亮屏；`end` 提前恢复。
- 该脚本不解锁已锁定的屏幕、不保存 PIN、不修改锁屏凭据。它只避免已解锁测试过程中自动息屏。
- 当前 `cmd recovery help` 包含 `request-lskf`、`is-lskf-captured`、`reboot-and-apply`。这只证明命令存在，不证明本 ROM 已配置可用的托管后端；本轮未登记托管 token。
- Android Resume-on-Reboot 用于预先准备的重启后恢复 CE 存储，不应当作通用的 ADB 免密解锁。当前流程仍需要重启后的首次人工解锁。

参考：
- https://source.android.com/docs/security/features/encryption/file-based
- https://source.android.com/docs/core/ota/resume-on-reboot

后续调试优先使用 CLI 和本地测试应用，减少需要界面解锁的检查。重启前持久化测试状态，测试结束恢复测试策略与亮屏设置。
