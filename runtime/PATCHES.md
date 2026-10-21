# 相对固定上游的改动

基线与子模块提交见 `upstream-lock.json`。保留全部来源与许可证，原始 APK、设备数据及密钥只在私有备份中保存。

- `xmsf` 移除 `manager:ui` 依赖，宿主不再启动旧管理界面依赖。保留协议、数据库、通知及管理接口。
- `ManagerUiEntryPoints` 的入口统一转向栖信，保留应用包名参数。
- `MiPushFrameworkApp` 在所有进程关闭遥测和启动时远程开关请求；关闭旧通知岛/浮窗/焦点通知，由栖信通知监听器展示通知岛。
- `xmsf` 集成 `xposed` 库，以同一个 APK 提供现有系统兼容钩子；模块 API 102 和原作用域保持一致。钩子进程遥测同样关闭。宿主显式声明 libxposed compileOnly 依赖，并保留 XposedRuntime 回调，防止发布混淆产生 AbstractMethodError。
- `ManagerRuntimeService` 分离可复用的 `ManagerRuntimeEndpoint`。新增 root 专用 `QixinRuntimeProvider`，解决 root app_process 没有 AMS 应用记录、无法普通 bindService 的问题。保留 AIDL 协议及签名权限；Provider 只允许 UID 0，endpoint 保留调用者检查。
- `ManagerRuntimeCallerPolicy` 支持 root 和同签名栖信，错误签名不能因包名相同而放行。
- 管理端引用相同源码中的 AIDL/类型，通过 expected 快照校验避免覆盖新配置；迁移后不再直接扫描运行时数据库进行常规同步。
- 增加实际旧数据库迁移测试，核对应用配置、注册材料和事件负载的指纹。测试输入来自本机私有备份，不提交到仓库。

没有改变第三方业务服务器选择推送通道的逻辑；模块连接正常不能保证某个应用会采用小米推送。
