# 开发与发布

## 管理应用

需要 JDK 17、Python 3、Android SDK Platform 36、Build Tools 36.0.0、NDK 29.0.14206865。配置 `ANDROID_HOME`，并将 SDK 的 platform-tools 与 build-tools 加入 PATH；也可在本地 `local.properties` 配置 `sdk.dir`。

```sh
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/29.0.14206865"
python3 tools/build_api.py
python3 tools/build_native.py
./gradlew :core:test :app:assembleRelease :app:lintRelease
python3 tools/sign_release.py
```

必须先构建 native 库；否则 APK 构建虽然可能成功，本地 root 服务无法工作。签名工具首次运行生成私有密钥，后续复用。产物位于 `private/releases/qixin-release.apk`。不要上传该目录中的设备资料或密钥。

`vendor/api-102.jar` 是编译期 Vector API，运行时由框架提供。APK 不包含它。固定上游源码与子模块以普通文件快照纳入仓库，来源提交见 `runtime/upstream-lock.json`，克隆后无需初始化 Git submodule。

## 推送运行时

见 [运行时构建与迁移](../runtime/README.md)。其 Gradle / AGP 工具链与管理应用不同，不能把管理应用的 JDK 17 直接用于运行时。上游构建可能需要访问其依赖仓库；本项目 CI 当前验证管理应用，不宣称已在公共 CI 验证运行时全部依赖。

## 设备安装与测试

安装工具是针对单台已连接开发设备的维护工具，会写入 root 配置、Magisk 模块并安装 APK。先阅读脚本、确认 `adb devices` 只有目标设备，再执行。设备迁移、重启和清理不由 CI 自动执行。

```sh
python3 tools/install_observer.py
```

脚本名称保留历史兼容，不会强制把已有管理配置改为观察模式。设备数据与回滚材料保存在 `private/backups/`。测试脚本使用独立 probe 应用；完成后恢复策略并卸载测试 APK。

发布前检查测试、构建、lint、许可证、敏感信息和实际设备验证记录。CI 通过不等于云端推送或长期稳定性通过。第一版以源码发布，不自动分发签名 APK。
