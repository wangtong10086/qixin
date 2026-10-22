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


## 原生通知展示（0.1.0-alpha3）

原始通知直接由 Android System UI 展示，栖信不再发布第二份通知、创建悬浮窗或代理点击。通知监听组件仅上报发布／移除／重连对账，后台服务和持续任务仍由既有分类逻辑单独存储。沿用 `island.IslandListener` 组件名以保留已有通知访问授权，但显示名称为“栖信通知记录”。移除了配置轮询、通知展示定时器和 WindowManager 窗口操作。

系统通知是否弹出遵循原应用渠道和勿扰设置；GUI 提供全局及逐应用系统通知设置入口。旧 `island.enabled`、`island.timeoutSeconds` 和逐应用 `island` 字段仍接受旧备份导入，但没有显示执行路径。原始 PendingIntent 及现有小米系统点击修复不变。

### 推送应用与注册批处理

`Inventory` 保留安装清单供内部查询，对外应用页按 SDK manifest 服务／接收器和已注册记录生成 `pushEvidence`。分析组件、普通通知、本地常驻服务不作为推送支持证据；GMS、XMSF 和管理端本身不作为目标应用。`apps.*.pushExcluded` 默认 false，用户可恢复已排除条目。

`RegistrationPlan` 为每个支持通道生成独立状态。`RegistrationBatch` 使用单线程任务，运行前检查管理模式、排除规则和小米运行时允许策略；停止应用及 FCM 转为手动处理。已启动任务不能并发重复创建，取消只影响未发出的请求。错误保留为逐项失败，不冒充注册完成；本轮任务只存于服务内存，重启不自动执行。

小米新增 `register_background` Binder 操作，与旧 `launch_target_force_register` 分离；后者的前台启动、桥接回退和数据清理均不用于批处理。FCM 不注入假 token、不清除缓存。管理端的“手动打开注册”只在用户点击时启动所选应用。

## 飞书后台通知保护（alpha5）

管理模式下，仅对已匹配的推送配置与消息列表初始化任务设置线程内调用来源标记，在 `clearNotification` 提交异步清理前判断进程是否仍在后台。保护不依赖保活，也不全局拦截 `cancelAll`；其他调用、前台/可见进程、观察模式和未加载配置保持原行为。该兼容修复受管理模式控制，不新增逐项接管开关。

目前适配官网版 8.3.6；内部任务名、混淆接口和调用关系变化可能要求重新适配。钩子注册成功不能替代真实 FCM、通知留存与点击验收。详见 [修复记录](feishu-official-fcm-2026-10-22.md)。
