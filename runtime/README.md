# 栖信推送运行时

基于 `upstream-lock.json` 固定的 MiPushFramework v1.1.0 及其子模块，保留上游许可证。运行时继续使用 `com.xiaomi.xmsf`，以维持第三方应用的协议兼容；管理界面统一进入栖信。

## 构建

运行时使用独立 JDK 27.0.1、Android SDK 37.2 / Build Tools 37；管理应用使用 JDK 17。工具链放在忽略提交的 `private/`，签名文件由 `tools/build_runtime.py` 读取。

```sh
python3 tools/build_runtime.py --release
```

产物位于 `upstream/xmsf/build/outputs/apk/normal/release/`。此命令不安装软件。安装到现有设备前必须核对签名，签名不同不能直接覆盖。

## 管理与安全边界

GUI 与 root CLI 共用栖信本地服务。管理端复用上游 AIDL 与数据类型，通过 root 专用 Provider 获取 Binder；Provider 同时保留签名权限和 UID 0 校验。普通 shell 和第三方应用不能调用此入口。

```sh
qixin rpc '{"command":"runtime.status"}'
qixin rpc '{"command":"runtime.application","package":"example.app"}'
qixin rpc '{"command":"runtime.channels","package":"example.app"}'
qixin rpc '{"command":"runtime.logs"}'
qixin rpc '{"command":"runtime.observation.start"}'
qixin rpc '{"command":"runtime.observation.status"}'
```

`runtime.application.set` 接受 `package`、上次读取的 `expected` 和新 `settings`；冲突时重新读取，不覆盖。可修改注册允许/询问/拒绝、推送拦截、注册通知和点击回退。`runtime.reconnect` 复用连接恢复节流。日志导出到 root 私有目录。

`runtime.replay` 使用已有事件 ID 在本机重放，用于验证通知展示和跳转；不等于云端投递成功。24 小时采样每五分钟记录一次本地连接状态，不记录消息正文；时间到达仅表示采样窗口结束，不自动判定验收通过。

## 迁移与回滚

先运行 `tools/backup_runtime.py`，备份 APK、CE/DE 数据、数据库、通知设置、Magisk 覆盖和 Vector 配置，并校验哈希。`tools/migrate_runtime.py` 分阶段卸载旧签名、安装新签名、恢复数据并按新 UID 修复属主。每次系统覆盖切换必须重启；不修改系统签名校验或 PackageManager 文件。

```sh
python3 tools/migrate_runtime.py detach --candidate /path/to/runtime.apk
# 开机首次解锁后
python3 tools/migrate_runtime.py install
# 如需恢复原运行时
python3 tools/migrate_runtime.py rollback-detach
# 再次开机首次解锁后
python3 tools/migrate_runtime.py rollback-install
```

Vector 兼容钩子已并入运行时 APK。`tools/switch_runtime_hooks.py integrated` 将原推送模块作用域复制到运行时，并停用原模块；`legacy` 反向切换。此工具只改两个推送模块，之后需要重启。旧模块 APK/数据应先单独备份；卸载后回滚还需运行 `python3 tools/restore_legacy_hook.py` 恢复该 APK、数据和原作用域，再重启。

通知渠道随 UID 变化需要恢复，辅助工具为 `tools/RestoreNotificationChannels.java`。不得仅以“数据库恢复成功”判断迁移完成。

当前实机验收与边界见 [迁移记录](../docs/migration-progress-2026-10-22.md)。
