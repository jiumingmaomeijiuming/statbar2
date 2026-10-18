# 状态栏时间电池调整 v1.0

Xposed / LSPosed 模块，用于调整状态栏**时间**和**电池**的左右位置与上下偏移。

作者：九命猫

---

## 一、功能

### 时间（clock）
| 项目 | 可调范围 |
|---|---|
| 位置 | 默认 / 居左 / 居中 / 居右 |
| 左右微调 | -200 ~ +200 dp |
| 上下偏移 | -20 ~ +20 dp |

### 电池（battery）
| 项目 | 可调范围 |
|---|---|
| 位置 | 默认 / 移到左侧 / 移到最右 |
| 左右微调 | -200 ~ +200 dp |
| 上下偏移 | -20 ~ +20 dp |

### 其他
- 总开关：一键关闭所有调整
- 热更新：改完点「应用」，多数情况立即生效，无需重启
- 一键重启系统界面（需要 Root）
- 恢复默认：所有参数清零

---

## 二、编译（Android Studio）

### 前置
- 安装 **Android Studio**（Giraffe / Hedgehog / Iguana 均可）
- 装好 **Android SDK Platform 34**（SDK Manager 里勾选）
- 装好 **JDK 17**

### 步骤
1. 打开 Android Studio → `File → Open` → 选中本文件夹 `StatusBarTuner`
2. 首次打开会提示下载 Gradle，**允许联网下载**（约 100MB）
3. 等待 Sync 完成（本工程**无任何第三方远程依赖**，只需要本地 Android SDK）
4. 菜单 `Build → Build Bundle(s) / APK(s) → Build APK(s)`
5. 编译完成后点右下角 `locate`，得到 `app-debug.apk`

### 关于 Xposed API
工程自带 `xposedstub` 模块，是**编译期 stub**，运行时由 LSPosed 提供真实实现。
所以**不需要联网下载 Xposed API**，也不会把它打包进 APK。这点已经处理好了。

---

## 三、安装与使用

### 1. 装 APK
把 `app-debug.apk` 装到手机。

### 2. 在 LSPosed 里启用
打开 LSPosed（通知栏点进去）→ 模块 → 勾选「状态栏时间电池调整」

### 3. 勾作用域（关键）
进入模块详情 → 作用域 → **只勾选「系统界面」**
包名是 `com.android.systemui`

> ⚠️ 不要勾「系统框架」，可能导致不稳定。

### 4. 重启系统界面
- 方法A：APP 里点「重启系统界面」（需要 Root）
- 方法B：命令行 `su -c killall com.android.systemui`
- 方法C：直接重启手机

### 5. 打开 APP 调参数
选位置 → 拖 SeekBar 调偏移 → 点「应用」

---

## 四、工作原理

1. Hook SystemUI 的状态栏创建流程（`CollapsedStatusBarFragment.onViewCreated` 等 4 个候选 hook 点，自动逐个尝试）
2. 通过**资源 id** 定位 clock 和 battery 视图（内置 6+6 个候选 id，覆盖 AOSP 与 MIUI 命名）
3. 用 `translationX / translationY` 做位移

**为什么用位移而不是换父容器（reparent）？**
reparent 会让 SystemUI 布局链断裂，极易崩溃。位移只是视觉移动，不改动布局树，
跨 ROM 兼容性高、不会崩。代价是原位置会留空（不会挤压其他图标）。

---

## 五、故障排查

### 装了没反应
按顺序排查：

1. **LSPosed 里模块是否勾选？作用域是否勾了系统界面？**
2. **模块是否真的被加载？**
   跑一下旁边的 `dump_systemui.sh`，看【6】的日志：
   - 看到 `StatBarTuner: hook 成功: xxx` → hook 正常
   - 看到 `所有 hook 候选均失败` → 需要补类名（见下）
   - 看到 `clock=false battery=false` → 资源 id 没匹配上

3. **Hook 点不对**（最可能）
   你的 SystemUI 类名可能不在候选列表里。
   跑 `dump_systemui.sh`，把【2】【3】输出的类名发给我，
   我把它们加进 `MainHook.java` 的 `candidates` 数组重新编译。

4. **clock / battery 找不到**
   跑 `dump_systemui.sh` 看【4】，
   把显示 ✓ 的资源名补进 `MainHook.java` 的 `CLOCK_IDS` / `BATTERY_IDS` 数组。

### 状态栏错乱 / 图标重叠
去 APP 点「恢复默认」，或者在 LSPosed 里取消勾选模块后重启系统界面。

### SystemUI 崩溃循环
开机时连按音量键进安全模式（LSPosed 会禁用所有模块），
进系统后到 LSPosed 里取消本模块，重启即可。

---

## 六、版本规则

- 小改（修 bug、补参数）：`v1.0` → `v1.1` → `v1.2`
- 大改（功能架构变化）：`v1.1` → `v2.1` → `v3.1`

文件名与 `versionName` 同步带版本号。

---

## 七、文件结构

```
StatusBarTuner/
├── app/                         主模块
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml         含 xposedmodule 声明
│       ├── assets/xposed_init          入口：MainHook
│       ├── java/com/jiumingmao/statbar/
│       │   ├── MainHook.java           Hook 逻辑（改这里调 hook 点）
│       │   └── MainActivity.java       设置界面
│       └── res/...
├── xposedstub/                  Xposed API 编译期 stub（不用改）
├── dump_systemui.sh             诊断脚本（放手机里跑）
└── README.md
```

---

作者：九命猫
