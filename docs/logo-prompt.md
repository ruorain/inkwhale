# 墨鲸 · 应用图标提示词

复制下面任意一段给 GPT（DALL·E 3 / GPT-4o 图像）。**第一段是主推**，另外两段是不同风格备选。

---

## 主推：墨滴化的鲸鱼

```
Design a flat Android app icon for an app called "Inkwhale" (墨鲸).

Concept: a whale formed from a single ink drop. The whale's body should read as a
drop of ink — a rounded, slightly asymmetric blob — with a small tail fin at one
end and a single minimal eye dot. The silhouette must be recognizable at 48×48 px.

Style: flat vector, solid colors only, absolutely no gradients, no shadows, no
gloss, no 3D, no outline stroke, no text, no letters, no watermark.

Composition: the whale sits centered inside a rounded square, occupying about 62%
of the canvas, with generous even padding on all sides. The rounded square corner
radius is about 22% of the icon width (Android adaptive icon safe zone).

Colors: background #2F6FED (a calm medium blue). The whale is pure white #FFFFFF.
The eye is a single small dot in the background blue, knocked out of the white.

Output: one square image, 1024×1024, the rounded square fills the entire canvas
edge to edge (no transparent margin outside the rounded square).
```

**中文大意**：扁平 Android 图标；鲸鱼由一滴墨形成，圆润水滴状身体 + 小尾巴 + 一个点状眼睛；48px 下仍可辨认；纯色无渐变无阴影；鲸鱼居中占 62%；底色 #2F6FED，鲸鱼纯白；1024×1024 铺满。

---

## 备选 A：留白式（更含蓄，像一枚印章）

```
Design a minimal flat app icon: a solid blue rounded square (#2F6FED) with a whale
tail fin and part of a back curve cut out of it in white negative space. Only two
shapes total — a big blue rounded square and a single white carved form suggesting
a diving whale. No eye, no details, no gradients, no shadows, no text.

The carved shape should feel like a single confident brush stroke, slightly
asymmetric, positioned in the lower-right half. Leave the upper-left area empty.

1024×1024, square, rounded corners about 22% of width, background fills the canvas.
```

---

## 备选 B：墨水瓶（更能说明「存」这个动作）

```
Design a flat app icon: a small ink bottle with a whale tail sticking out of the
neck, drawn with solid shapes only. Ink bottle is white on a #2F6FED rounded
square background. Extremely simplified — the bottle is a rounded rectangle, the
tail is a two-lobe fin, nothing else. No labels, no liquid level, no text.

Thick confident shapes, readable at 48×48 px. No gradients, no shadows, no stroke
outline. 1024×1024, rounded corners 22%, fills the canvas edge to edge.
```

---

## 生成后要做的

1. 存成 PNG，**1024×1024**
2. 丢给我，我会切成 Android 需要的五档尺寸：
   - `mipmap-mdpi` 48×48
   - `mipmap-hdpi` 72×72
   - `mipmap-xhdpi` 96×96
   - `mipmap-xxhdpi` 144×144
   - `mipmap-xxxhdpi` 192×192
3. 顺便生成引导页用的大图（512×512，居中留白）

**注意**：如果 GPT 生成的图带透明边距（圆角外是空的），也没关系，我会自动裁到圆角边界再缩放。

## 不要的效果

- ❌ 渐变、光泽、高光、投影、3D
- ❌ 描边轮廓线
- ❌ 任何文字（不要写 "Inkwhale" 或 "墨鲸"）
- ❌ 细碎纹理（木纹、纸纹、噪点）
- ❌ 鲸鱼画得太写实（要剪影，不要眼睛、鳍肢细节、水花）
- ❌ 深色背景配浅色鲸鱼之外的花哨配色（只用蓝 + 白两色）
