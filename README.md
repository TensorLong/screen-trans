# 意群翻译 / Sense Group Translator

意群翻译是一款 Android 悬浮翻译工具。它通过悬浮指针识别屏幕文字，并围绕“意群”这一最小语义片段提供更贴近上下文的翻译结果。

## 核心能力

- 悬浮取词：在任意 App 上方移动指针，快速选择需要理解的文字。
- 屏幕 OCR：使用 MediaProjection 截屏，并通过 Google ML Kit 识别屏幕文本。
- 多粒度识别：支持单词、句子、意群、段落、框选区域与固定区域翻译。
- 意群翻译：当指针指向句中某个单词时，AI 会定位该词所在的语义片段，只翻译这个片段。
- 多翻译引擎：支持 Google、DeepL、Azure、Papago，以及本地 ML Kit 翻译能力。
- 自定义 AI API：内测用户可以配置 API Key、Base URL，并从服务端拉取可用模型后选择使用。
- 语音朗读：支持通过系统 TTS 朗读翻译结果。

## 意群翻译示例

```text
It's an ironic twist-we might all end up as NPCs in this new ecosystem
```

当指针指向 `twist` 时，应用会优先识别 `an ironic twist` 或 `ironic twist` 这样的语义片段，而不是孤立翻译单个词或误切为无意义片段。

## 内测阶段说明

当前版本处于内测阶段，所有功能均为免费开放模式。应用内不展示订阅、付费、广告激励或购买页面。

## 技术栈

- Kotlin
- Jetpack Compose
- Android ViewModel / StateFlow / Coroutines
- Hilt
- Google ML Kit OCR / Language ID / Translate
- Retrofit / Gson / kotlinx.serialization
- Firebase 组件

## 本地开发

需要：

- JDK 17
- Android SDK 35
- Android Gradle Plugin 8.x

常用命令：

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

调试 AI API 时，请只在应用设置页输入自己的测试凭据。不要把 API Key、Base URL、模型凭据或任何密钥写入源码、README、日志或 Git 提交。
