# 架构与边界

Compose GUI → 经 UID 和证书校验的本地 Unix socket → root 控制器 → 配置、只读注册扫描、SQLite 历史。

Vector 主要作用于 system_server、XMSF 及显式配置的应用兼容作用域。系统锁内不做 IPC/磁盘访问，事件进入 512 条有界队列，由后台线程投递。GMS 主进程同时承载认证业务，本版不注入 GMS；FCM 使用服务诊断、系统投递证据和应用 SDK 注册缓存。

观察模式不修改通知或应用行为。管理模式仍须逐项接管；当前接管开关默认均关闭。任何局部修复必须匹配实际方法签名。MiPush 的人工合成 mipush-action URI 仅在原 Intent 无法解析且去除后能够解析时处理，保留 identifier；该分支尚待实机消息验证。

## 证据模型

- cached-registration：本地 SDK 留有注册缓存；不证明 token 仍有效。
- registration-callback：系统观察到注册回调；不证明业务服务器上传成功。
- dispatch：框架收到投递请求；不等于应用已处理。
- notification：通知服务接受或拒绝发布；不等于用户已看到。
- click：PendingIntent 发起结果；不等于业务页面已加载。

标题/正文仅按应用明确开启后保存。token、原始 Bundle、完整 deep link 参数不进入历史。迁移后通过运行时 Binder 同步注册及事件元数据，不把 payload、dev_info、reg_sec 导入栖信历史；本地通知重放由运行时内部处理原始负载。数据保留七天，配置和历史不上传。

## 撤回

停用 Vector 中的栖信模块，停用 Magisk `qixin_controller`，运行 `/data/adb/qixin/stop.sh`，重启即可卸载运行中的观察钩子。保留 `/data/adb/qixin` 和备份，便于恢复。已迁移设备还需按 runtime/README.md 恢复原运行时与兼容模块，不能只停用栖信后删除数据。

## 与栖序配合

栖序公布 `qixu.push.lease.api=1` 的 JVM 属性。栖信验证投递来源 UID，并在优先级 10000 的投递拦截器中为目标包写入 `qixin.push.lease.<package>` 的 elapsedRealtime 到期时间。该属性不是 Android 系统属性，仅存在于 system_server，应用进程不能通过自身 JVM 属性伪造。栖序后台启动、服务、任务判断在租约有效期内暂时放行，30 秒后恢复原策略。它不替代业务注册，也不会永久授予自启动。

控制器安装脚本使用 `/data/adb/local-controller-policy.lock` 串行更新 SELinux 策略，避免同时读取或覆盖策略。运行阶段不反复修改策略。
