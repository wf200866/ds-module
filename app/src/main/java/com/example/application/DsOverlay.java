/*
 * ds模块 — DeepSeek 客户端美化增强模块
 * Copyright (C) 2026 wf200866
 *
 * 本程序是自由软件：你可以根据 GNU 通用公共许可证（GPL-3.0）的条款
 * 重新发布和/或修改它，无论是 GPL-3.0 本身，还是（由你选择）任何更新的版本。
 *
 * 本程序基于 https://github.com/lllucccian/Deekseep (GPL-3.0) 的
 * 部分思路开发，依据 GPL-3.0 传染性条款，本程序同样以 GPL-3.0 发布。
 *
 * 本程序分发时希望它有用，但没有任何担保；甚至没有适销性或特定用途适用性的
 * 隐含担保。详见 GNU 通用公共许可证。
 *
 * 你应该已随本程序收到一份 GNU 通用公共许可证副本；如果没有，请见
 * <https://www.gnu.org/licenses/>。
 *
 * Repository: https://github.com/wf200866/ds-module
 */

package com.example.application;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 背景图方案（独立窗口层）：
 * - TYPE_APPLICATION_ATTACHED_DIALOG + token + FLAG_NOT_TOUCHABLE（触摸穿透）
 * - 支持：壁纸模式（裁剪/适应/拉伸）、模糊、全局色调、暗色滤镜
 * - 悬浮球/面板是独立窗口，添加在背景层之后 → 永远在背景之上
 */
public final class DsOverlay {

    private static final Map<Integer, View> LAYERS = new HashMap<>();

    private DsOverlay() {}

    public static void apply(final Activity activity) {
        try {
            // v377：卡密门禁 —— 未激活不应用壁纸
            if (!DsLicense.isActivated(activity)) {
                remove(activity);
                return;
            }
            // v179 诊断：确认 apply 执行
            log("apply enter, bgOn=" + DsConfig.bgOn(activity));
            // v420：原生壁纸模式（诊断阶段）—— 修复：原生模式未实现时，先清旧层再回退叠加层，避免双层壁纸+错位
            if (DsConfig.wallpaperMode(activity) == 1) {
                // 先移除旧叠加层，防止残留
                remove(activity);
                // 原生绘制模式尚未实现（DsChatBgHook 为空实现），回退到叠加层模式
                // 仅当原生 hook 生效时才走原生；否则继续走叠加层
                log("native wallpaper mode requested, falling back to overlay");
            }
            // 先移除旧的，保证每次重建
            remove(activity);
            final Integer key = Integer.valueOf(System.identityHashCode(activity));
            boolean on = DsConfig.enabled(activity) && DsConfig.bgOn(activity);
            log("apply on=" + on);
            if (!on) return;

            final View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            final ViewGroup decorGroup = (ViewGroup) decor;
            FrameLayout root = new FrameLayout(activity);
            // 背景图
            String bgPath = DsConfig.bgPath(activity);
            // 加载背景图（图片壁纸）
            Bitmap bmp = load(bgPath);
            if (bmp != null) {
                // 模糊处理
                int blur = DsConfig.bgBlur(activity);
                if (blur > 0 && blur <= 25) {
                    bmp = blur(activity, bmp, blur);
                }

                ImageView iv = new ImageView(activity);
                // 壁纸模式
                int mode = DsConfig.bgMode(activity);
                if (mode == 1) iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                else if (mode == 2) iv.setScaleType(ImageView.ScaleType.FIT_XY);
                else iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(bmp);

                // 全局色调
                int tint = DsConfig.tintMode(activity);
                if (tint != 0) {
                    iv.setColorFilter(new ColorMatrixColorFilter(tintMatrix(tint)));
                }

                // 透明度：壁纸整体可见（亮一点，文字靠遮罩保证清晰）
                int alpha = DsConfig.bgAlpha(activity);
                if (alpha < 0) alpha = 0;
                if (alpha > 90) alpha = 90;
                float bgAlpha = alpha == 0 ? 0f : (0.25f + alpha / 100f * 0.45f);
                iv.setAlpha(bgAlpha);

                root.addView(iv, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

                // 文字对比层：默认极轻（8%），浅色壁纸自动增强到 18%（不压暗壁纸，只提升文字可读性）
                View readabilityMask = new View(activity);
                readabilityMask.setBackgroundColor(0xFF000000);
                int mask = DsConfig.maskStrength(activity);
                if (mask < 0) mask = 0;
                if (mask > 80) mask = 80;
                boolean lightWall = isLightWallpaper(bmp);
                float maskAlpha;
                if (mask == 0) {
                    // 用户未手动设遮罩：浅色壁纸自动加 18%，普通壁纸 8%（几乎不影响观感）
                    maskAlpha = lightWall ? 0.18f : 0.08f;
                } else {
                    maskAlpha = 0.18f + mask / 100f * 0.55f;
                }
                readabilityMask.setAlpha(Math.min(0.68f, maskAlpha));
                root.addView(readabilityMask, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }

            // 暗色滤镜层
            int dark = DsConfig.darkFilter(activity);
            if (dark > 0) {
                View darkLayer = new View(activity);
                darkLayer.setBackgroundColor(Color.BLACK);
                darkLayer.setAlpha(Math.min(0.85f, dark / 100f * 0.6f));
                root.addView(darkLayer, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }

            // 实时时钟层（v203：日期+星期+毛玻璃）
            if (DsConfig.showClock(activity)) {
                final LinearLayout clockBox = new LinearLayout(activity);
                clockBox.setOrientation(LinearLayout.VERTICAL);
                clockBox.setGravity(Gravity.CENTER);
                clockBox.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));
                // 毛玻璃背景（半透明黑 + 高光渐变）
                GradientDrawable clockBg = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{0x66000000, 0x44000000});
                clockBg.setCornerRadius(dp(activity, 18));
                clockBg.setStroke(dp(activity, 1), 0x2AFFFFFF);
                clockBox.setBackgroundDrawable(clockBg);
                clockBox.setElevation(dp(activity, 6));
                clockBox.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);

                final TextView timeTv = new TextView(activity);
                timeTv.setTextColor(0xFFFFFFFF);
                timeTv.setTextSize(24);
                timeTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                timeTv.setGravity(Gravity.CENTER);
                timeTv.setIncludeFontPadding(false);
                clockBox.addView(timeTv);

                final TextView dateTv = new TextView(activity);
                dateTv.setTextColor(0xBFFFFFFF);
                dateTv.setTextSize(11);
                dateTv.setGravity(Gravity.CENTER);
                dateTv.setPadding(0, dp(activity, 2), 0, 0);
                clockBox.addView(dateTv);

                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
                clp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                clp.topMargin = dp(activity, 64);
                root.addView(clockBox, clp);
                // 每秒刷新
                final java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
                final java.text.SimpleDateFormat sdfd = new java.text.SimpleDateFormat("M月d日 EEEE");
                final Runnable[] tick = new Runnable[1];
                tick[0] = new Runnable() {
                    public void run() {
                        try {
                            java.util.Date now = new java.util.Date();
                            timeTv.setText(sdf.format(now));
                            dateTv.setText(sdfd.format(now));
                            clockBox.postDelayed(tick[0], 1000);
                        } catch (Throwable ignored) {}
                    }
                };
                clockBox.postDelayed(tick[0], 0);
            }

            log("apply root children=" + root.getChildCount());
            if (root.getChildCount() == 0) return;

            // 壁纸层：固定插到 index 2（DeepSeek 内容 index0 + EdgeToEdge index1 之后）
            // 这样壁纸在内容之上可见，但在我们的球(index3)/面板(index4)之下不遮挡
            root.setClickable(false);
            root.setFocusable(false);
            int insertIdx = 2;
            if (insertIdx > decorGroup.getChildCount()) insertIdx = decorGroup.getChildCount();
            decorGroup.addView(root, insertIdx, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            LAYERS.put(key, root);
            log("bg added to decor fixed index=" + insertIdx + ", children=" + decorGroup.getChildCount());
            // v187：背景稳定显示（面板打开时由 setVisible 隐藏）

            // ★ 关键修复：DecorView 窗口背景是白色的（windowBackground），盖住底层壁纸
            // 把它设为透明，让壁纸从窗口底层透出
            try {
                decorGroup.setBackgroundColor(0x00000000);
                decorGroup.getRootView().setBackgroundColor(0x00000000);
                log("decor background -> transparent");
            } catch (Throwable ignored) {}

            // 递归透明化 DeepSeek 内容层（只处理 index 0/1 的 DeepSeek 内容，不碰我们的球/面板/壁纸）
            try {
                for (int i = 0; i < decorGroup.getChildCount(); i++) {
                    View ch = decorGroup.getChildAt(i);
                    if (ch == root) continue;  // 跳过壁纸层
                    // 只透明化前 2 个（DeepSeek 内容 + EdgeToEdge），后面的球/面板保持不透明
                    if (i >= 2) {
                        log("skip layer " + i + " (" + ch.getClass().getSimpleName() + ") keep opaque");
                        continue;
                    }
                    if (ch instanceof ViewGroup) {
                        makeTransparent((ViewGroup) ch);
                        log("content layer " + i + " transparent");
                    }
                }
            } catch (Throwable ignored) {}

            // 透明化系统导航栏背景（navigationBarBackground 是白色，盖在壁纸上）
            try {
                for (int i = 0; i < decorGroup.getChildCount(); i++) {
                    View ch = decorGroup.getChildAt(i);
                    String cls = ch.getClass().getSimpleName();
                    String idName = "";
                    try { idName = ch.getResources().getResourceName(ch.getId()); } catch (Throwable ignored) {}
                    log("decor child " + i + ": " + cls + " id=" + idName);
                    if (ch.getId() == android.R.id.navigationBarBackground
                            || "navigationBarBackground".equals(cls)) {
                        ch.setBackgroundColor(0x00000000);
                        ch.setAlpha(0f);
                        log("navigationBarBackground transparent");
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            log("EX " + t);
        }
    }

    public static void remove(Activity activity) {
        try {
            Integer key = Integer.valueOf(System.identityHashCode(activity));
            View v = LAYERS.remove(key);
            if (v != null && v.getParent() != null) {
                ((ViewGroup) v.getParent()).removeView(v);
            }
        } catch (Throwable ignored) {}
    }

    /** 控制壁纸层可见性（打开面板时隐藏，避免面板背景被壁纸干扰） */
    public static void setVisible(Activity activity, boolean show) {
        try {
            Integer key = Integer.valueOf(System.identityHashCode(activity));
            View v = LAYERS.get(key);
            if (v != null) {
                v.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable ignored) {}
    }
    /** 检测壁纸是否偏亮（浅色壁纸需增强文字对比） */
    private static boolean isLightWallpaper(Bitmap bmp) {
        try {
            if (bmp == null) return false;
            int w = bmp.getWidth(), h = bmp.getHeight();
            if (w <= 0 || h <= 0) return false;
            // 采样 100 点算平均亮度
            long sum = 0; int n = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    int x = w * i / 10 + w / 20;
                    int y = h * j / 10 + h / 20;
                    if (x >= w) x = w - 1; if (y >= h) y = h - 1;
                    int px = bmp.getPixel(x, y);
                    int r = (px >> 16) & 0xFF, g = (px >> 8) & 0xFF, b = px & 0xFF;
                    sum += (r * 299 + g * 587 + b * 114) / 1000;  // 亮度
                    n++;
                }
            }
            float avg = n > 0 ? (float) sum / n : 0;
            return avg > 160;  // 平均亮度 >160 视为浅色壁纸
        } catch (Throwable t) {
            return false;
        }
    }
    /** 高清加载：inSampleSize=1（不压缩） */
    private static Bitmap load(String path) {
        try {
            if (path == null || path.length() == 0) return null;
            File f = new File(path);
            if (!f.exists()) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 1;
            return BitmapFactory.decodeFile(path, o);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 圆形裁剪头像 */
    private static Bitmap toCircle(Bitmap src) {
        try {
            int size = Math.min(src.getWidth(), src.getHeight());
            Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(src, 0, 0, paint);
            return output;
        } catch (Throwable e) {
            return src;
        }
    }

    /** 高斯模糊（RenderScript，API 31+ 用 setRenderEffect 兜底） */
    private static Bitmap blur(Context ctx, Bitmap src, int radius) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                // 高版本直接用 RenderScript 不可用，用缩放模糊近似
                return scaleBlur(src, radius);
            }
            RenderScript rs = RenderScript.create(ctx);
            Allocation input = Allocation.createFromBitmap(rs, src);
            Allocation output = Allocation.createTyped(rs, input.getType());
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            script.setRadius(Math.min(25f, radius));
            script.setInput(input);
            script.forEach(output);
            output.copyTo(src);
            rs.destroy();
            return src;
        } catch (Throwable e) {
            return scaleBlur(src, radius);
        }
    }

    /** 缩放模糊近似（所有版本可用） */
    private static Bitmap scaleBlur(Bitmap src, int radius) {
        try {
            int w = src.getWidth();
            int h = src.getHeight();
            int scale = Math.max(2, radius / 2);
            Bitmap small = Bitmap.createScaledBitmap(src, Math.max(1, w / scale), Math.max(1, h / scale), true);
            Bitmap out = Bitmap.createScaledBitmap(small, w, h, true);
            if (small != src) small.recycle();
            return out;
        } catch (Throwable e) {
            return src;
        }
    }

    /** 全局色调 ColorMatrix */
    private static ColorMatrix tintMatrix(int mode) {
        ColorMatrix m = new ColorMatrix();
        switch (mode) {
            case 1: // 暖色
                m.set(new float[]{
                        1.1f, 0, 0, 0, 20,
                        0, 1.0f, 0, 0, 5,
                        0, 0, 0.9f, 0, -10,
                        0, 0, 0, 1, 0});
                break;
            case 2: // 冷色
                m.set(new float[]{
                        0.9f, 0, 0, 0, -10,
                        0, 1.0f, 0, 0, 5,
                        0, 0, 1.15f, 0, 20,
                        0, 0, 0, 1, 0});
                break;
            case 3: // 青绿
                m.set(new float[]{
                        0.9f, 0, 0, 0, -15,
                        0, 1.1f, 0, 0, 10,
                        0, 0, 1.05f, 0, 15,
                        0, 0, 0, 1, 0});
                break;
            case 4: // 粉紫
                m.set(new float[]{
                        1.15f, 0, 0, 0, 25,
                        0, 0.9f, 0, 0, -5,
                        0, 0, 1.2f, 0, 30,
                        0, 0, 0, 1, 0});
                break;
        }
        return m;
    }

    private static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 递归把 ViewGroup 及子 View 的背景设为透明（让底层壁纸透出） */
    private static void makeTransparent(ViewGroup group) {
        try {
            group.setBackgroundColor(0x00000000);
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof ViewGroup) {
                    makeTransparent((ViewGroup) child);
                } else if (child != null) {
                    child.setBackgroundColor(0x00000000);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 模块版本号 */
    /** v174：判断当前是否聊天页（View 树含"深度思考"/"智能搜索"或输入框） */
    private static boolean isChatScreen(Activity activity) {
        try {
            View decor = activity.getWindow().getDecorView();
            if (decor instanceof ViewGroup) {
                if (findChatView((ViewGroup) decor)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
    private static boolean findChatView(ViewGroup g) {
        try {
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c instanceof android.widget.EditText) return true;  // 输入框=聊天页
                if (c instanceof ViewGroup) {
                    if (findChatView((ViewGroup) c)) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static String moduleVersion(Context ctx) {
        try {
            String v = ctx.getPackageManager().getPackageInfo("com.example.application", 0).versionName;
            return v != null ? v : "1.0";
        } catch (Throwable t) { return "1.0"; }
    }

    private static void log(String msg) {
        android.util.Log.i("ds美化", "[bg] " + msg);
    }
}