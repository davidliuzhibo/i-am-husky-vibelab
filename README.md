# 我是哈士奇 / IAmHusky

PICO Vibe Lab 投稿源码。Android Spatial 全沉浸 VR 应用，版本 0.1 / versionCode 1。

## 构建

仓库根目录就是 Gradle 工程。准备 JDK 21 和 Android SDK 35，设置 JAVA_HOME 和 ANDROID_HOME，然后执行：

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease
```

Windows 使用 `gradlew.bat`。没有外部签名配置时，生成 `app/build/outputs/apk/release/app-release-unsigned.apk`，供发布平台签名。
原开发者签名配置及私钥不在仓库中，也不属于构建的必需输入。
Gradle 8.13 使用华为云镜像，分发文件 SHA-256 固定。所有运行时素材已提供，无需调用模型生成服务。

## 玩法

坐在卡通小屋上方，指挥哈士奇拆靠垫、偷吃、躲洗澡、拉雪橇，再与傻狍子合作找路回家。
六关各有主线、挑战和高手任务；观察主人的视线，在回头时装无辜，回家前收拾残局。
捏一下物件或标签派出二哈；点地板移动，另有冲刺按钮与捏住后拉的冲刺方式。无需手柄。
积累星级可获得奖杯、相册奖励，解锁隐藏结局「二哈的梦」。支持保存进度。

包名 `com.vrgames.husky`。本投稿副本保持原工程运行代码与资源；只整理构建入口及说明。
源码许可见 LICENSE，素材说明见 [素材来源](docs/素材来源.md)。
