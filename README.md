# NebulaStrike（星云突袭）

一款完全**离线**的 Android 街机太空射击游戏。零第三方依赖 —— 纯 Java 基于 Android
框架（`SurfaceView` + `Canvas`）实现，任何能跑 Android Studio 的环境都能构建，
APK 体积仅 ~300 KB。

## 玩法
- **竖屏 + 浮动摇杆**：按住屏幕**任意位置**滑动，飞船按手指相对位移移动
  （MOBA/王者式操控，手指不遮挡飞机，松手即停，暂停自动冻结），右下角炸弹圆键，自动开火
- 迎击无人机（drone）、织机（weaver）、猎手（hunter）、坦克（tank）四种敌机，会排成 V 字编队来袭
- **九种掉落道具**（旋转六边形 + 脉冲光环 + 闪烁描边，与敌机明确区分；接近时磁吸捕获，
  光环加速闪烁提示可收集）：
  - **武器类**：散射 W（3/5/7 向扇形）/ 追踪导弹 M / **穿透激光 L**（高射速窄束，命中后穿透继续飞行）
  - **火力等级 P**：雷电式成长——弹道数量随等级 1→3 扩展，永久生效，坠机降 1 级，满级折算 300 分
  - **功能类**：护盾 S（挡一次伤害）/ 炸弹 B / 速射 R（8 秒射速翻倍）/ 双倍分数 ×2 / 生命 +（上限 4）
- **Boss 战**：DREADNOUGHT 级母舰，三阶段血量，扇形 / 瞄准 / 螺旋弹幕，还会召唤僚机
- 连击倍率奖励连续击杀；屏幕震动、粒子特效、Python 纯合成 8-bit 音效
- **双模式**：
  - **战役模式**：150 秒难度爬坡 + 后期缓慢加压，每 4 波一个 Boss
  - **无尽模式**：90 秒即达满压，难度无上限攀升，每 3 波一个 Boss，敌种 1.6 倍速登场
- 双榜独立：两模式各自维护本地 Top-5 排行榜（SharedPreferences 持久化），
  菜单并排展示，结算画面高亮本次排名
- 完整 UI 流程：主菜单 / 暂停 / 设置（音效、粒子、震动、低特效均可开关）/ 结算
- **应用内升级**：主菜单底部「检查更新」——查询 GitHub Releases，若有新版本
  直接在应用内下载（带进度条），完成后自动唤起系统安装器（**全程不跳转浏览器**）；
  已是最新时同步显示当前版本号；升级安装完成或用户取消后，APK 残留自动清理。
  游戏本体离线可玩，仅此功能按需联网

## 项目结构
```
app/src/main/java/com/nebulastrike/game/
  core/     纯 Java 模拟层（不依赖 android.*）：实体、刷怪、碰撞、
            Boss AI、难度曲线、自适应画质调节器
  render/   Canvas 渲染器（900x1600 竖屏虚拟坐标系，任意屏幕自动留边适配），
            矢量图形零分配（共享 Path、缓存渐变）
  AudioEngine  SoundPool 音效（res/raw 内为合成 WAV）   Haptics  震动反馈
  GameView  游戏线程 + 触摸输入      MainActivity  入口
  UpdateManager  应用内升级（检查/下载/唤起系统安装器）  UpdateFileProvider  APK 只读提供器
  core/UpdateCheck  版本比较与发布 JSON 解析（纯逻辑，可单测）
app/src/test/   模拟层 JUnit 单元测试（JVM 直接运行）
scripts/gen_sfx.py  纯 Python 合成全部音效，无需外部素材
scripts/verify.sh   无 SDK 验证：类型检查 + 单元测试 + 模拟基准
```

## 性能
- 固定虚拟坐标系；所有实体走对象池；dt 钳位（后台恢复不会炸物理）
- 帧率调节器：游戏中 60 fps、菜单 30 fps；按帧耗时反馈自适应粒子发射量；
  低内存设备自动降特效
- `scripts/verify.sh` 第 4 步运行 60 秒最坏情况模拟基准
  （CI CPU 上约 5 微秒/帧 —— 相对 60 fps 预算有约 18 倍余量）

## 下载

到 [GitHub Releases 页面](https://github.com/cedarln/NebulaStrike/releases) 下载最新
`NebulaStrike-vX.Y.Z.apk`（已签名，~270 KB），传到手机安装即可，无需联网运行。

当前版本 **v1.5.0**：
- 竖屏 + 浮动摇杆（相对滑动操控 + 按下锁定动画）
- 九种掉落道具：穿透激光、火力等级（雷电式 1→3 级弹道成长）+ 七种经典道具
- 战役模式 + 无尽模式（双榜独立）
- 三阶段 Boss 战、连击系统
- 8-bit 合成音效与震动反馈、应用内升级

## 构建
```bash
./gradlew :app:assembleDebug     # debug APK 输出在 app/build/outputs/apk/debug/
./gradlew :app:assembleRelease   # 签名 release APK 输出在 app/build/outputs/apk/release/
```

release 使用仓库自带的密钥库 `release.keystore` 签名。密码**不写入仓库**，请放在
`local.properties`（已 gitignore）或环境变量里：

```properties
RELEASE_STORE_PASSWORD=你的密码
RELEASE_KEY_PASSWORD=你的密码
```

缺少密码时 `assembleRelease` 会直接失败，避免产出未签名 APK。仅供学习与自娱；
正式上架请更换为自己的密钥。

## 验证（无需 Android SDK）
```bash
scripts/verify.sh                # 基于官方 android.jar 的类型检查 + JUnit 测试 + 基准
```

许可证：MIT。
