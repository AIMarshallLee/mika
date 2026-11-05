#!/usr/bin/env python3
"""咔哒(昆仑增长)白标:从咔哒正式图标(小紫怪母版)生成安卓图标资源,写到 app/src/kada/res。

用法: python3 tools/kada/gen_icons.py <小紫怪透明底母版.png>
母版来自 kadadesk 仓库 assets/brand/kada-mascot-master.png(1254x1254,透明底)。
生成:
  drawable-nodpi/mika_launcher_art.png  App 内 Logo(欢迎页、关于页、侧栏),浅紫底 + 小紫怪
  drawable-nodpi/kada_launcher_fg.png   自适应图标前景(108dp 画布,小紫怪在安全区内)
  drawable-nodpi/kada_launcher_mono.png 主题图标(安卓 13+ 跟随壁纸配色)白色剪影
  drawable/ic_notification.xml          通知小图标(剪影矢量,眼睛镂空)
需要 Pillow、numpy、opencv-python。
"""
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[2] / "app/src/kada/res"
TOP, BOT = (247, 243, 253), (229, 219, 247)  # 浅紫渐变,和图标背景 kada_icon_bg_* 一致


def main(master: str) -> None:
    src = Image.open(master).convert("RGBA")
    a = np.array(src)[..., 3]
    bbox = Image.fromarray(((a > 20) * 255).astype(np.uint8)).getbbox()
    m = src.crop(bbox)

    def place(canvas: Image.Image, scale: float, yoff: float = 0.01) -> Image.Image:
        w = canvas.size[0]
        mw, mh = m.size
        s = scale * w / max(mw, mh)
        mm = m.resize((round(mw * s), round(mh * s)), Image.LANCZOS)
        canvas.alpha_composite(mm, ((w - mm.size[0]) // 2, (w - mm.size[1]) // 2 + round(yoff * w)))
        return canvas

    for d in ("drawable-nodpi", "drawable"):
        (OUT / d).mkdir(parents=True, exist_ok=True)

    n = 1024
    bg = Image.new("RGBA", (n, n))
    dr = ImageDraw.Draw(bg)
    for y in range(n):
        t = y / (n - 1)
        dr.line([(0, y), (n, y)], fill=tuple(round(TOP[i] + (BOT[i] - TOP[i]) * t) for i in range(3)) + (255,))
    place(bg.copy(), 0.70).convert("RGB").save(OUT / "drawable-nodpi/mika_launcher_art.png", optimize=True)

    f = 432
    # 咔哒(昆仑增长):0.50 让小紫怪整个落在自适应图标 66dp 安全圆内(0.58 时圆形图标会切到脚和卡扣)
    fg = place(Image.new("RGBA", (f, f), (0, 0, 0, 0)), 0.50)
    fg.save(OUT / "drawable-nodpi/kada_launcher_fg.png", optimize=True)

    arr = np.array(fg)
    sil = (cv2.GaussianBlur((arr[..., 3] > 20).astype(np.float32), (9, 9), 0) > 0.5).astype(np.uint8)
    sil = cv2.morphologyEx(sil, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    white = ((arr[..., 3] > 128) & (arr[..., :3].astype(int).min(axis=2) > 200)).astype(np.uint8)
    cnt, lab, stats, _ = cv2.connectedComponentsWithStats(white)
    eyes = np.zeros_like(sil)
    for i in sorted(range(1, cnt), key=lambda i: -stats[i, cv2.CC_STAT_AREA])[:2]:  # 两只眼白最大
        pts = cv2.findNonZero((lab == i).astype(np.uint8))
        cv2.fillConvexPoly(eyes, cv2.convexHull(pts), 1)
    eyes = cv2.dilate(eyes, np.ones((5, 5), np.uint8))
    mono = (sil & (1 - eyes)) * 255
    im = np.zeros((f, f, 4), np.uint8)
    im[..., :3] = 255
    im[..., 3] = mono
    Image.fromarray(im).save(OUT / "drawable-nodpi/kada_launcher_mono.png", optimize=True)

    ys, xs = np.where(sil > 0)
    side = max(xs.max() - xs.min(), ys.max() - ys.min()) * 1.08
    cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
    paths = []
    for mask, eps in ((sil, 1.4), (eyes, 0.8)):
        cs, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
        for c in cs:
            if cv2.contourArea(c) < 40:
                continue
            c = cv2.approxPolyDP(c, eps, True)[:, 0, :]
            pts = [((x - cx) / side * 22 + 12, (y - cy) / side * 22 + 12) for x, y in c]
            paths.append("M" + "L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + "Z")
    (OUT / "drawable/ic_notification.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 咔哒(昆仑增长):通知小图标,小紫怪剪影,由 tools/kada/gen_icons.py 从正式图标生成;状态栏只显示形状,颜色由系统决定 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{"".join(paths)}" />
</vector>
''', encoding="utf-8")
    print("icons written to", OUT)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "kada-mascot-master.png")
