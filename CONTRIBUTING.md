# 参与贡献

欢迎提 issue、发 PR。这个项目刻意保持极小：**没有 Gradle、没有 AndroidX、没有第三方依赖**，APK 只有 177 KB。

## 环境

- JDK 17
- Android build-tools 34.0.0
- Android platform android-32

## 构建

不需要 Android Studio。手搓工具链：

`ash
aapt2 compile --dir app/res -o build/res
aapt2 link -o build/base.apk --manifest app/AndroidManifest.xml \\
  -I \/platforms/android-32/android.jar \\
  -R build/res/*.flat --java build/gen
javac -source 8 -target 8 -encoding UTF-8 \\
  -bootclasspath \/platforms/android-32/android.jar \\
  -d build/classes build/gen/**/*.java app/src/**/*.java
d8 --lib \/platforms/android-32/android.jar --output build/dex build/classes
# 把 classes.dex 塞进 base.apk，zipalign，apksigner 签名
`

Windows 上可用 ~/.dsh/skills/android-apk/scripts/android.ps1 build app。

## 代码约定

- **UI 全部用 Java 写**，没有一个 XML 布局。想加界面就写 View，不要引入资源布局。
- **不引入第三方库**。网络用 HttpURLConnection，JSON 用 org.json，Markdown 自己解析。
- **不用 emoji、不用渐变**。图标来自 Lucide（ISC），加新图标走 	ools/convert_icons.py。
- 弹层只用两种：Sheet.showOptions 和 Sheet.showForm，别自己写 Dialog。
- 提示用 Banner，不要用 Toast。

## PR 前自查

- [ ] 能在本机构建通过
- [ ] 没有引入新依赖
- [ ] 新文案尽量短（目标用户注意力有限）
