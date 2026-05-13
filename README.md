# Screen Translate

Screen Translate 是一个 Android 悬浮翻译工具：在任意 App 上方显示可拖动光标，实时 OCR 屏幕文字，并把光标指向的内容翻译成目标语言。

## 基本功能

- 悬浮窗取词：通过系统悬浮窗在当前屏幕上选择需要翻译的文字。
- 实时屏幕 OCR：使用 Android `MediaProjection` 截屏，并通过 Google ML Kit 识别屏幕文字。
- 多种识别模式：支持单词、句子、段落、框选区域、固定区域翻译。
- 多翻译引擎：支持 Google、DeepL、Azure、Papago，以及本地 ML Kit 翻译能力。
- AI 辅助：可使用兼容 OpenAI 的接口进行 OCR 文本纠错和语义处理。
- 语音朗读：支持 TTS 朗读翻译结果。
- 大屏/折叠屏适配：项目正在持续改进折叠屏、平板和横竖屏切换体验。

## 当前正在增加的功能：意群翻译

意群翻译会把当前 OCR 识别出的完整句子交给 AI，由 AI 找出光标所指单词所在的最小语义片段，再只翻译这个片段。

目标示例：

```text
It's an ironic twist-we might all end up as NPCs in this new ecosystem
```

当光标指向 `twist` 时，意群应识别为 `ironic twist` 或 `an ironic twist`，而不是没有语义的 `twist-we`。

## 技术栈

- Kotlin
- Jetpack Compose
- Android ViewModel / StateFlow / Coroutines
- Hilt
- Google ML Kit OCR / Language ID / Translate
- Retrofit / Gson / kotlinx.serialization
- Firebase 相关组件

## 本地开发

需要：

- JDK 17
- Android SDK 35
- Android Gradle Plugin 8.x

常用命令：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

调试 AI 功能时，请在 App 设置页填写自己的兼容 OpenAI API 配置。不要把 API key 写入源码、README、日志或 Git 提交。
