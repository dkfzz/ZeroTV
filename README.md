# ZeroTV（天天影视）

安卓手机 + 电视双端影视 App。Kotlin + Jetpack Compose + Media3(ExoPlayer) 播放。

## 功能
- 聚合 10 个苹果 CMS 采集源（电影天堂/量子/极速/非凡/最大/如意/暴风/360/魔都/iKun）
- 首页分类浏览、全源并行搜索、详情线路+选集、m3u8 播放
- 触屏 + 电视遥控器焦点操作双适配

## 数据源
见 `app/src/main/java/com/tiantian/movie/data/Sources.kt`，均为标准苹果 CMS `provide/vod` 接口。

## 构建（云端编译）
本地无需装 Android SDK，直接推 GitHub 编译：

1. 在 GitHub 新建仓库，把本目录推上去（注意：`local.properties` 已在 .gitignore，本地 SDK 路径不会上传）。
2. 仓库 → **Actions** → 左侧选 **Build APK** → **Run workflow**。
3. 完成后在 workflow 页底部 **Artifacts** 下载 `ZeroTV-debug` 压缩包，解压即得 APK。

或打 tag `v1.2` 触发（会额外产出 release 版 APK）。

## 技术栈版本
- Gradle 8.9 / AGP 8.5.2 / Kotlin 2.0.20
- Compose BOM 2024.06.00 / Media3 1.4.1 / OkHttp 4.12 / Coil 2.6.0
- minSdk 24 / targetSdk 34
