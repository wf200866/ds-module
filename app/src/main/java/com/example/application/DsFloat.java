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
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * ds美化 悬浮球 + 全屏控制面板
 * - 悬浮球直接 addView 内置进 App 窗口（无需权限）
 * - 点击悬浮球 → 展开全屏面板
 * - 悬浮球可拖动 + 松手自动贴边
 * - UI 风格参照 Deekseep：品牌蓝 #4D6BFE、跟随系统深色模式、卡片圆角
 */
public final class DsFloat {

    /** 品牌色（参照 Deekseep） */
    private static final int BRAND = 0xFF4D6BFE;
    /** v151 类别辅助色 */
    private static final int C_BLUE   = 0xFF4D6BFE;  // 外观
    private static final int C_PURPLE = 0xFF8A6BFF;  // 功能
    private static final int C_CYAN   = 0xFF3EC9A7;  // 工具
    private static final int C_ORANGE = 0xFFFFA94D;  // 增强
    private static final int C_GRAY   = 0xFF9AA0B5;  // 设置
    private static final int C_RED    = 0xFFFF6B6B;  // 危险

    /** 隐藏所有悬浮球 + 面板（可逆：下次 onResume/切页自动恢复） */
    static void hideAll() {
        for (View b : BALLS.values()) {
            try { if (b.getParent() != null) ((ViewGroup) b.getParent()).removeView(b); } catch (Throwable ignored) {}
        }
        BALLS.clear();
        for (View p : PANELS.values()) {
            try { if (p.getParent() != null) ((ViewGroup) p.getParent()).removeView(p); } catch (Throwable ignored) {}
        }
        PANELS.clear();
    }

    // 悬浮球：按 Activity 内置挂载（满足"非系统悬浮窗"要求，跟随当前页面）
    private static final Map<Integer, View> BALLS = new HashMap<>();
    private static final Map<Integer, View> PANELS = new HashMap<>();
    private static final Map<Integer, android.widget.ScrollView> SCROLLS = new HashMap<>();
    private static final Map<Integer, int[]> CARD_IDS = new HashMap<>();
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static volatile Activity sCurrentActivity;

    /** 配置子页内容构建器（由功能列表点击时设置） */
    private static volatile ConfigBuilder sConfigBuilder = null;
    /** v148：配置页标题（进入配置页时设置） */
    private static volatile String sConfigTitle = "";
    /** v153：面板深色状态（配置页卡片构建用） */
    private static volatile boolean sPanelDark = true;

    /** v452：外部（独立设置页）强制同步面板深浅色，避免浅色背景 + 深色组件 → 灰字 */
    public static void setPanelDark(boolean dark) {
        sPanelDark = dark;
        DsUI.dark = dark;
    }
    /** v150：缓存 App Context（供 log 开关判定） */
    private static volatile Context sAppCtx = null;

    /** 配置子页内容构建接口 */
    interface ConfigBuilder {
        void build(LinearLayout content);
    }

    /** 跟随系统深色模式 */
    static boolean isDark(Context c) {
        try {
            int mode = c.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) { return false; }
    }

    private DsFloat() {}

    /** 当前前台 Activity（供路由 hook 使用） */
    public static Activity currentActivity() {
        return sCurrentActivity;
    }

    /** 供设置页按钮调用：显示全屏面板 */
    public static void showPanel(final Activity activity) {
        showPanelAt(activity, -1);
    }

    /** 打开面板并滚动到指定分类：-1=顶部 0=背景 1=聊天 2=更多 */
    public static void showPanelAt(final Activity activity, final int section) {
        try {
            // v310：点击悬浮球先校验卡密，未激活强制输入
            if (!DsLicense.isActivated(activity)) {
                showLicenseDialog(activity, new Runnable() {
                    public void run() {
                        // 激活成功后重试打开面板
                        showPanelAt(activity, section);
                    }
                });
                return;
            }
            final Integer key = Integer.valueOf(System.identityHashCode(activity));
            final View panel;
            View p0 = PANELS.get(key);
            if (p0 == null) {
                ensure(activity);
                p0 = PANELS.get(key);
            }
            panel = p0;
            if (panel == null) return;
            // 打开面板时隐藏当前 Activity 的悬浮球（避免面板被球遮挡/重叠）
            View ball = BALLS.get(key);
            if (ball != null) ball.setVisibility(View.GONE);
            // 隐藏壁纸层（避免面板背景被壁纸干扰/穿透）
            DsOverlay.setVisible(activity, false);
            // v351：DsPanelV2 截屏模糊
            try {
                Object tag = panel.getTag();
                if (tag instanceof DsPanelV2) {
                    ((DsPanelV2) tag).captureGlass(activity.getWindow().getDecorView());
                }
            } catch (Throwable ignored) {}
            // 面板显示（v149：淡入 + 轻微上滑）
            panel.animate().cancel();
            panel.setAlpha(0f);
            panel.setTranslationY(36f * activity.getResources().getDisplayMetrics().density);
            panel.setVisibility(View.VISIBLE);
            panel.animate().alpha(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            final android.widget.ScrollView scroll = SCROLLS.get(key);
            final int[] ids = CARD_IDS.get(key);
            if (scroll == null || ids == null) return;
            final int off = (int) (20 * activity.getResources().getDisplayMetrics().density);
            // ★ 修复跳转：post 延迟到布局完成后再滚动，否则 getTop()=0 会全部跳顶部
            final int target = section;
            scroll.post(new Runnable() {
                public void run() {
                    try {
                        if (target <= 0) {
                            scroll.smoothScrollTo(0, 0);
                        } else {
                            int idx = -1;
                            if (target == 1 && ids[0] > 0) idx = 0;
                            else if (target == 2 && ids[1] > 0) idx = 1;
                            else if (target == 3 && ids[2] > 0) idx = 2;
                            if (idx >= 0) {
                                View v = panel.findViewById(ids[idx]);
                                if (v != null) scroll.smoothScrollTo(0, v.getTop() - off);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    /** 收起面板 + 恢复灵动岛显示（选图完成/其他场景调用） */
    public static void closePanel(final Activity activity) {
        try {
            final Integer key = Integer.valueOf(System.identityHashCode(activity));
            View panel = PANELS.get(key);
            if (panel != null) panel.setVisibility(View.GONE);
            // 恢复壁纸层（面板关闭后重新显示背景）
            DsOverlay.setVisible(activity, true);
            // 恢复灵动岛（无论是否长按隐藏成小球，球始终存在，只是样式不同）
            View ball = BALLS.get(key);
            if (ball != null) ball.setVisibility(View.VISIBLE);
        } catch (Throwable ignored) {}
    }

    public static void ensure(final Activity activity) {
        try {
            sAppCtx = activity.getApplicationContext();
            log("ensure enter act=" + activity.getClass().getName());
            final Integer key = Integer.valueOf(System.identityHashCode(activity));
            sCurrentActivity = activity;

            // 面板按 Activity 独立重建（球保留，避免选图后消失）
            View oldPanel = PANELS.remove(key);
            if (oldPanel != null && oldPanel.getParent() != null) {
                try { ((ViewGroup) oldPanel.getParent()).removeView(oldPanel); } catch (Throwable ignored) {}
            }

            log("floatOn=" + DsConfig.floatOn(activity));
            final View decor = activity.getWindow().getDecorView();
            log("decor=" + decor.getClass().getName());
            if (!(decor instanceof ViewGroup)) { log("decor not ViewGroup"); return; }
            final ViewGroup root = (ViewGroup) decor;
            // 悬浮球开关：关闭时不显示球/面板（保留配置入口：打开 ds美化 模块 App 可重新开启）
            if (!DsConfig.floatOn(activity)) {
                // 移除已有球，避免残留
                View oldBall = BALLS.get(key);
                if (oldBall != null && oldBall.getParent() != null) {
                    try { ((ViewGroup) oldBall.getParent()).removeView(oldBall); } catch (Throwable ignored) {}
                }
                BALLS.remove(key);
                log("floatOn=false, ball hidden");
                return;
            }

            // ============ 应用内灵动岛（挂 DecorView，固定位置） ============
            final boolean dark = isDark(activity);
            // 隐藏状态（小圆点）由长按切换；进入时按配置恢复对应形态
            if (DsConfig.ballHidden(activity)) {
                View hb = BALLS.get(key);
                if (hb == null || hb.getParent() == null) {
                    // 没有球或球已移除 → 重建为 mini 小圆点
                    final View mini = buildBall(activity, dark, new Runnable() {
                        public void run() {
                            showPanelAt(activity, 0);
                        }
                    }, true);
                    final FrameLayout.LayoutParams mbp = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT);
                    mini.setLayoutParams(mbp);
                    root.addView(mini);
                    BALLS.put(key, mini);
                    positionBall(mini, activity);
                    log("ball mini mode (hidden but tappable)");
                }
            } else if (BALLS.get(key) == null || BALLS.get(key).getParent() == null) {
                // 若该 Activity 没有有效悬浮球则新建（灵动岛胶囊）
                final View ball = buildBall(activity, dark, new Runnable() {
                    public void run() {
                        showPanelAt(activity, 0);
                    }
                }, false);
                final FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
                ball.setLayoutParams(blp);
                root.addView(ball);
                BALLS.put(key, ball);
                positionBall(ball, activity);
                log("ball island added, root.childCount=" + root.getChildCount());
                // v209：动态毛玻璃（截取背后区域模糊作为球背景）
                // 灵动岛显示后不再做动态截屏毛玻璃（截屏会卡顿/异常，导致显示一会然后切换）
                // applyGlassBlur(activity, ball);
            }

            // v212：无论走哪个分支，确保球可见（修复切后台回来球消失/被面板隐藏后不恢复）
            // 注意：面板打开时球应保持隐藏（等 closePanel 恢复），所以先检查面板状态
            try {
                View panelNow = PANELS.get(key);
                boolean panelOpen = panelNow != null && panelNow.getVisibility() == View.VISIBLE;
                View anyBall = BALLS.get(key);
                if (!panelOpen && anyBall != null && anyBall.getParent() != null
                        && anyBall.getVisibility() != View.VISIBLE) {
                    anyBall.setVisibility(View.VISIBLE);
                    anyBall.setAlpha(1f);
                    positionBall(anyBall, activity);
                    log("ball visibility restored");
                }
            } catch (Throwable ignored) {}

            // 同步当前 Activity（供点击球时开面板）
            sCurrentActivity = activity;

            // 自动清理缓存执行器：启动时模式 → 每次启动自动清理一次
            try {
                if (DsConfig.autoClean(activity) == 1) {
                    AutoClean.runIfDue(activity);
                }
            } catch (Throwable ignored) {}

            // 自动备份数据库执行器：开启后每天自动备份一次（纯文件复制，安全）
            try {
                if (DsConfig.autoBackup(activity)) {
                    AutoBackup.runIfDue(activity);
                }
            } catch (Throwable ignored) {}

            // 立即启动界面感知（球挂上就判断，不等面板构建）
            BallScreenWatcher.start(activity);

            // v242：自绘聊天入口小按钮（点击直接打开聊天页，不必走悬浮球菜单）
            try { DsChatButton.ensure(activity); } catch (Throwable ignored) {}

            // ============ 控制面板（由悬浮球触发）v351：全新 DsPanelV2 ============
            final boolean[] shown = {false};
            View fullPanel = null;
            try {
                DsPanelV2 p2 = new DsPanelV2(activity);
                fullPanel = p2.getRoot();
                fullPanel.setTag(p2);
                log("buildPanelV2 OK");
            } catch (Throwable t) {
                log("buildPanelV2 EX " + t);
            }
            if (fullPanel != null) {
                final FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                fullPanel.setLayoutParams(plp);
                fullPanel.setVisibility(View.GONE);
                try {
                    root.addView(fullPanel);
                    PANELS.put(key, fullPanel);
                    log("panelV2 added to root, root.childCount=" + root.getChildCount());
                } catch (Throwable t) {
                    log("addView panelV2 EX " + t);
                }
            }

            // 同步当前 Activity
            sCurrentActivity = activity;
        } catch (Throwable t) {
            log("EX " + t);
        }
    }

    /** 悬浮球：灵动岛药丸 / 小圆点（可拖动+贴边）。长按切换形态 */
    private static View buildBall(final Activity activity, final boolean dark,
                                   final Runnable onOpenPanel, final boolean mini) {
        final TextView ball = new TextView(activity);
        final boolean[] isMini = {mini};
        applyBallStyle(ball, activity, dark, isMini[0]);

        final boolean[] dragging = {false};
        final long[] downTime = {0};
        final Handler h = new Handler(Looper.getMainLooper());

        // 固定位置：不可拖动，只支持 单击开面板 + 长按切换形态
        ball.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                try {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            dragging[0] = false;
                            downTime[0] = System.currentTimeMillis();
                            // 长按 600ms 切换：药丸 <-> 小圆点
                            h.removeCallbacksAndMessages(null);
                            final Runnable toggle = new Runnable() {
                                public void run() {
                                    try {
                                        isMini[0] = !isMini[0];
                                        applyBallStyle(ball, activity, dark, isMini[0]);
                                        DsConfig.setBallHidden(activity, isMini[0]);
                                        // 切换后延迟重新定位（等样式新宽度布局完成，保证居中）
                                        ball.postDelayed(new Runnable() {
                                            public void run() {
                                                positionBall(ball, activity);
                                            }
                                        }, 120);
                                    } catch (Throwable ignored) {}
                                }
                            };
                            h.postDelayed(toggle, 600);
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            return true;
                        case MotionEvent.ACTION_UP:
                            h.removeCallbacksAndMessages(null);
                            if (!dragging[0] && System.currentTimeMillis() - downTime[0] < 400) {
                                onOpenPanel.run();
                            }
                            return true;
                    }
                } catch (Throwable ignored) {}
                return false;
            }
        });

        return ball;
    }

    /** 应用悬浮球样式：false=灵动岛药丸 true=小药丸（隐藏，保持同风格+可点） */
    private static void applyBallStyle(TextView ball, Activity activity, boolean dark, boolean mini) {
        if (mini) {
            // 小药丸：微型液态玻璃（静态），只显示圆点；足够大可长按恢复
            ball.setText("●");
            ball.setTextColor(0xFFFFFFFF);
            ball.setTextSize(10);
            ball.setGravity(Gravity.CENTER);
            ball.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
            int r = dp(activity, 16);
            ball.setBackgroundDrawable(liquidGlassLayers(activity, r, dark, true));
            ball.setAlpha(0.65f);
        } else {
            // 灵动岛药丸（v208：液态玻璃质感，黑色基调，静态光泽）
            ball.setText("● ds美化");
            ball.setTextColor(0xFFFFFFFF);
            ball.setTextSize(14);
            ball.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            ball.setGravity(Gravity.CENTER);
            ball.setPadding(dp(activity, 26), dp(activity, 12), dp(activity, 26), dp(activity, 12));
            int r = dp(activity, 28);
            ball.setBackgroundDrawable(liquidGlassLayers(activity, r, dark, false));
            // 悬浮球透明度（配置生效，默认92%）
            float a = Math.max(0.3f, Math.min(1f, DsConfig.ballAlpha(activity) / 100f));
            ball.setAlpha(a);
        }
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            ball.setElevation(dp(activity, 10));
            ball.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        }
    }

    /** v209：动态毛玻璃 —— 截取悬浮球背后区域并模糊，透出实时背景 */
    private static void applyGlassBlur(final Activity activity, final View ball) {
        // v305：避免重复截屏（启动卡顿优化）——球已有毛玻璃背景就不再重做
        if (ball.getTag() != null && "glass_done".equals(ball.getTag())) return;
        // 延迟到界面稳定后再截屏，且只做一次
        ball.postDelayed(new Runnable() {
            public void run() {
                try {
                    if (ball.getParent() == null) return;
                    final View decor = activity.getWindow().getDecorView();
                    final int sw = decor.getWidth();
                    final int sh = decor.getHeight();
                    if (sw <= 0 || sh <= 0) return;
                    // 球的屏幕区域
                    int[] loc = new int[2];
                    ball.getLocationOnScreen(loc);
                    int bl0 = loc[0], bt0 = loc[1];
                    int bw0 = ball.getWidth(), bh0 = ball.getHeight();
                    // v210：不再扩展 padding，紧贴球体，避免 View 被模糊图撑大
                    if (bw0 <= 0 || bh0 <= 0) return;
                    bl0 = Math.max(0, bl0); bt0 = Math.max(0, bt0);
                    if (bl0 + bw0 > sw) bw0 = sw - bl0;
                    if (bt0 + bh0 > sh) bh0 = sh - bt0;
                    if (bw0 <= 0 || bh0 <= 0) return;
                    final int bl = bl0;
                    final int bt = bt0;
                    final int bw = bw0;
                    final int bh = bh0;
                    // 先临时隐藏球，避免截到自己
                    ball.setVisibility(View.INVISIBLE);
                    ball.postDelayed(new Runnable() {
                        public void run() {
                            try {
                                android.graphics.Bitmap full = android.graphics.Bitmap.createBitmap(
                                        sw, sh, android.graphics.Bitmap.Config.ARGB_8888);
                                android.graphics.Canvas c = new android.graphics.Canvas(full);
                                decor.draw(c);
                                android.graphics.Bitmap crop = android.graphics.Bitmap.createBitmap(full, bl, bt, bw, bh);
                                full.recycle();
                                android.graphics.Bitmap blurred = scaleBlurBall(crop, 8);
if (crop != blurred && !crop.isRecycled()) crop.recycle();
                                int radius = dp(activity, 26);
                                android.graphics.Bitmap rounded = roundBitmapBall(blurred, radius);
                                if (blurred != rounded && !blurred.isRecycled()) blurred.recycle();
                                // 模糊图 + 暗色压层（保证白色文字可读）+ 高光边缘
                                android.graphics.drawable.BitmapDrawable bd =
                                        new android.graphics.drawable.BitmapDrawable(activity.getResources(), rounded);
                                GradientDrawable darkOverlay = new GradientDrawable();
                                darkOverlay.setCornerRadius(radius);
                                darkOverlay.setColor(0x4D000000);
                                GradientDrawable edge = new GradientDrawable();
                                edge.setCornerRadius(radius);
                                edge.setStroke(dp(activity, 1), 0x40FFFFFF);
                                edge.setColor(0x00000000);
                                LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{
                                        bd, darkOverlay, edge});
                                ball.setBackgroundDrawable(layers);
                                ball.setVisibility(View.VISIBLE);
                                ball.animate().alpha(1f).setDuration(150).start();
                                ball.setTag("glass_done");  // v305：标记毛玻璃已完成，避免重复截屏
                            } catch (Throwable ignored) {
                                ball.setVisibility(View.VISIBLE);
                            }
                        }
                    }, 30);
                } catch (Throwable ignored) {}
            }
        }, 200);
    }

    /** v209：缩放模糊（复用 scaleBlur 逻辑，DsFloat 内独立实现） */
    private static android.graphics.Bitmap scaleBlurBall(android.graphics.Bitmap src, int radius) {
        try {
            int w = src.getWidth();
            int h = src.getHeight();
            int scale = Math.max(2, radius / 2);
            android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(
                    src, Math.max(1, w / scale), Math.max(1, h / scale), true);
            android.graphics.Bitmap out = android.graphics.Bitmap.createScaledBitmap(small, w, h, true);
            if (small != src) small.recycle();
            return out;
        } catch (Throwable e) {
            return src;
        }
    }

    /** v209：圆角裁剪 */
    private static android.graphics.Bitmap roundBitmapBall(android.graphics.Bitmap src, int radius) {
        try {
            int w = src.getWidth();
            int h = src.getHeight();
            android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(out);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            android.graphics.RectF rect = new android.graphics.RectF(0, 0, w, h);
            c.drawRoundRect(rect, radius, radius, p);
            p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            c.drawBitmap(src, 0, 0, p);
            return out;
        } catch (Throwable e) {
            return src;
        }
    }

    /** v208：液态玻璃多层 Drawable（黑色基调 + 顶部高光 + 光源光斑 + 底部微反射 + 边缘光） */
    private static LayerDrawable liquidGlassLayers(Activity activity, int radius, boolean dark, boolean mini) {
        int density = (int) activity.getResources().getDisplayMetrics().density;

        // 1) 基础玻璃体：深色对角渐变（左上稍亮模拟受光，右下深邃）
        GradientDrawable body = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                mini ? new int[]{0xF0000000, 0xB3000000}
                     : new int[]{0xF0000000, 0xC0000000, 0x8A000000});
        body.setCornerRadius(radius);

        float glossAlpha = dark ? 0x2E : 0x24;  // 高光强度

        // 2) 顶部高光：上半部白色渐变（玻璃反光带）
        GradientDrawable topGloss = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x52FFFFFF, (0x18 << 24), 0x00FFFFFF});
        topGloss.setCornerRadius(radius);

        // 3) 左上方光源光斑：半透明白色，从左上往右淡出
        GradientDrawable lensGlow = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0x2EFFFFFF, 0x08FFFFFF, 0x00FFFFFF});
        lensGlow.setCornerRadius(radius);

        // 4) 底部微反射：底部细白带（玻璃底部反光）
        GradientDrawable bottomReflect = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x1CFFFFFF, 0x00FFFFFF});
        bottomReflect.setCornerRadius(radius);

        // 5) 细描边（玻璃边缘光）
        GradientDrawable edge = new GradientDrawable();
        edge.setCornerRadius(radius);
        edge.setShape(GradientDrawable.RECTANGLE);
        edge.setStroke(dp(activity, 1), dark ? 0x4DFFFFFF : 0x3DFFFFFF);
        edge.setColor(0x00000000);

        // 组装：body 在最底层，glass layers 覆盖其上（保持黑色基底 + 光泽）
        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{
                body, topGloss, lensGlow, bottomReflect, edge});
        return layers;
    }


    /** 定位灵动岛：等布局完成拿到真实宽度后，手算绝对位置（align=0左 1中 2右） */
    private static void positionBall(final View ball, final Activity activity) {
        if (ball == null) return;
        final int align = DsConfig.ballAlign(activity);
        final int offY = DsConfig.ballOffsetY(activity);
        ball.post(new Runnable() {
            public void run() {
                try {
                    if (ball.getParent() == null) return;
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) ball.getLayoutParams();
                    if (lp == null) return;
                    int sw = activity.getResources().getDisplayMetrics().widthPixels;
                    int vw = ball.getWidth();
                    int left;
                    if (align == 0) {                 // 偏左
                        left = dp(activity, 12);
                    } else if (align == 2) {          // 偏右
                        left = sw - vw - dp(activity, 12);
                    } else {                          // 居中：手算（绝对准）
                        left = (sw - vw) / 2;
                    }
                    lp.gravity = Gravity.TOP | Gravity.START;
                    lp.leftMargin = left;
                    lp.topMargin = offY;
                    ball.setLayoutParams(lp);
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void removePanel(Activity activity) {
        try {
            Integer key = Integer.valueOf(System.identityHashCode(activity));
            View p = PANELS.remove(key);
            if (p != null && p.getParent() != null) {
                ((ViewGroup) p.getParent()).removeView(p);
            }
        } catch (Throwable ignored) {}
    }

    // ============ 全屏控制面板（Deekseep 风格：跟随深色模式） ============
    private static View buildFullPanel(final Activity activity, final Runnable refresh,
                                       final Runnable onPanelClosed) {
        final Context ctx = activity;
        final float dm = ctx.getResources().getDisplayMetrics().density;

        // 面板主题：0=跟随系统 1=强制深色 2=强制浅色
        int theme = DsConfig.panelTheme(ctx);
        final boolean dark;
        if (theme == 2) {
            dark = false;          // 强制浅色
        } else if (theme == 0) {
            dark = isDark(ctx);    // 跟随系统
        } else {
            dark = true;           // 强制深色（默认，液态玻璃与白色界面强对比）
        }
        sPanelDark = dark;
        DsUI.dark = dark;  // v350：同步主题到新 UI 系统
        final int bgColor   = dark ? 0xFF0C0C10 : 0xFFF5F6F8;   // 面板背景（v151 更深邃）
        final int barColor  = dark ? 0xCC121218 : 0xCCFFFFFF;   // 导航栏（半透明玻璃）
        final int cardColor = dark ? 0xFF16161C : 0xFFFFFFFF;   // 卡片
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;   // 主文字
        final int subColor  = dark ? 0xFFB0B0B4 : 0xFF333333;   // v450：次要文字改深（浅色模式近黑）
        final int divColor  = dark ? 0xFF2A2A30 : 0xFFEEEEEE;   // 分割线

        // 全屏根容器
        final FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(dark ? 0xE60C0C10 : 0xE6F5F6F8);
        // ★ v347 iOS 26 液态玻璃：玻璃背景层（截屏背后界面 → 模糊 → 作为面板背景）
        final android.widget.ImageView glassBg = new android.widget.ImageView(ctx);
        glassBg.setTag("glass_bg");
        glassBg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        glassBg.setAlpha(0f);  // 初始透明，截屏模糊完成后渐入
        root.addView(glassBg, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // ★ 关键：拦截所有触摸事件，防止穿透到下层 DeepSeek 界面
        root.setClickable(true);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                return true;  // 消费一切触摸，绝不穿透
            }
        });

        LinearLayout page = new LinearLayout(ctx);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ★ 配置子页覆盖层（功能列表 → 点击进入半屏配置页，底部弹出）
        // 全屏遮罩（半透明黑）+ 底部半屏内容（70% 高度，顶部圆角）
        final FrameLayout configLayer = new FrameLayout(ctx);
        configLayer.setBackgroundColor(0x00000000);  // v148：初始透明（渐入动画）
        configLayer.setVisibility(View.GONE);
        configLayer.setClickable(true);
        root.addView(configLayer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        /**
         * ★ 配置子页机制：功能列表 → 点击进入独立配置页（全屏覆盖），返回回列表
         */
        // configLayer 内部结构：底部半屏面板（顶部圆角 + 返回栏 + 内容）
        final LinearLayout configRoot = new LinearLayout(ctx);
        configRoot.setOrientation(LinearLayout.VERTICAL);
        configRoot.setBackgroundColor(0x00000000);
        // 顶部圆角（v173：应用 panelRadius 配置）
        int cfgRadius = Math.max(8, Math.min(48, DsConfig.panelRadius(ctx)));
        GradientDrawable cfgBg = new GradientDrawable();
        cfgBg.setColor(dark ? 0xE6121218 : 0xE6FFFFFF);  // v347：半透明玻璃
        cfgBg.setCornerRadii(new float[]{ dp(ctx,cfgRadius), dp(ctx,cfgRadius), dp(ctx,cfgRadius), dp(ctx,cfgRadius), 0, 0, 0, 0 });
        configRoot.setBackgroundDrawable(cfgBg);
        final FrameLayout.LayoutParams cfgLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (int)(ctx.getResources().getDisplayMetrics().heightPixels * 0.7f));
        cfgLp.gravity = Gravity.BOTTOM;
        configRoot.setClickable(true);  // 阻止点击穿透到遮罩
        configLayer.addView(configRoot, cfgLp);

        // 配置页顶部栏：返回 + 标题
        final LinearLayout configBar = new LinearLayout(ctx);
        configBar.setOrientation(LinearLayout.HORIZONTAL);
        configBar.setGravity(Gravity.CENTER_VERTICAL);
        configBar.setPadding(dp(ctx, 8), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
        configBar.setBackgroundColor(barColor);
        configBar.setElevation(dp(ctx, 4));
        final TextView configBackTv = new TextView(ctx);
        configBackTv.setText("‹ 返回");
        configBackTv.setTextColor(BRAND);
        configBackTv.setTextSize(16);
        configBackTv.setGravity(Gravity.CENTER_VERTICAL);
        configBackTv.setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
        configBar.addView(configBackTv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        final TextView configTitleTv = new TextView(ctx);
        configTitleTv.setTextColor(textColor);
        configTitleTv.setTextSize(16);
        configTitleTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        configBar.addView(configTitleTv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        configRoot.addView(configBar);

        final android.widget.ScrollView configScroll = new android.widget.ScrollView(ctx);
        configScroll.setFillViewport(true);
        final LinearLayout configContent = new LinearLayout(ctx);
        configContent.setOrientation(LinearLayout.VERTICAL);
        configContent.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 24));
        configScroll.addView(configContent);
        configRoot.addView(configScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        configLayer.setVisibility(View.GONE);

        // ★ v148：配置页动画（遮罩渐入/渐出 + 面板底部滑入/滑出）
        final android.animation.ValueAnimator[] maskVaHolder = new android.animation.ValueAnimator[1];
        final Runnable showConfigAnim = new Runnable() {
            public void run() {
                configLayer.setVisibility(View.VISIBLE);
                configRoot.animate().cancel();
                if (maskVaHolder[0] != null) maskVaHolder[0].cancel();
                android.animation.ValueAnimator maskVa = android.animation.ValueAnimator.ofInt(0x00, 0x99);
                maskVaHolder[0] = maskVa;
                maskVa.setDuration(220);
                maskVa.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                    public void onAnimationUpdate(android.animation.ValueAnimator a) {
                        int v = ((Integer) a.getAnimatedValue()).intValue();
                        configLayer.setBackgroundColor(v << 24);
                    }
                });
                maskVa.start();
                int h = configRoot.getHeight() > 0 ? configRoot.getHeight() : cfgLp.height;
                configRoot.setTranslationY(h);
                configRoot.animate().translationY(0f).setDuration(260)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
        };
        final Runnable hideConfigAnim = new Runnable() {
            public void run() {
                configRoot.animate().cancel();
                if (maskVaHolder[0] != null) maskVaHolder[0].cancel();
                int h = configRoot.getHeight() > 0 ? configRoot.getHeight() : cfgLp.height;
                configRoot.animate().translationY(h).setDuration(200)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator())
                        .withEndAction(new Runnable() {
                            public void run() {
                                configLayer.setVisibility(View.GONE);
                                configRoot.setTranslationY(0f);
                            }
                        }).start();
                android.animation.ValueAnimator maskVa = android.animation.ValueAnimator.ofInt(0x99, 0x00);
                maskVaHolder[0] = maskVa;
                maskVa.setDuration(200);
                maskVa.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                    public void onAnimationUpdate(android.animation.ValueAnimator a) {
                        int v = ((Integer) a.getAnimatedValue()).intValue();
                        configLayer.setBackgroundColor(v << 24);
                    }
                });
                maskVa.start();
            }
        };

        // 显示配置页：清空内容 + 填充 builder + 显示（v148：标题 + 动画）
        final Runnable[] showConfig = new Runnable[1];
        showConfig[0] = new Runnable() {
            public void run() {}
        };
        showConfig[0] = new Runnable() {
            public void run() {
                configContent.removeAllViews();
                configScroll.scrollTo(0, 0);  // v149：打开时滚动复位
                configTitleTv.setText(sConfigTitle);
                if (sConfigBuilder != null) {
                    try { sConfigBuilder.build(configContent); } catch (Throwable t) {}
                }
                showConfigAnim.run();
            }
        };
        // 返回（v148：动画关闭）
        configBackTv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                hideConfigAnim.run();
            }
        });
        // 点击遮罩空白处关闭（v148：动画关闭）
        configLayer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { hideConfigAnim.run(); }
        });

        // ===== 顶部导航栏（Deekseep 风格） =====
        LinearLayout navBar = new LinearLayout(ctx);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        navBar.setBackgroundColor(barColor);
        navBar.setElevation(dp(ctx, 4));

        // 关闭按钮（加大点击区域 40dp，好按）
        TextView closeBtn = new TextView(ctx);
        closeBtn.setText("✕");
        closeBtn.setTextColor(BRAND);
        closeBtn.setTextSize(20);
        closeBtn.setGravity(Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(dark ? 0x14FFFFFF : 0x0D000000);
        closeBg.setStroke(dp(ctx, 1), dark ? 0x22FFFFFF : 0x14000000);
        closeBtn.setBackgroundDrawable(closeBg);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // v149：淡出关闭动画
                root.animate().cancel();
                root.setAlpha(1f);
                root.animate().alpha(0f).setDuration(160)
                        .withEndAction(new Runnable() {
                            public void run() {
                                root.setVisibility(View.GONE);
                                root.setAlpha(1f);
                            }
                        }).start();
                // 恢复壁纸层（面板关闭后重新显示背景）
                DsOverlay.setVisible(activity, true);
                // 面板关闭：恢复悬浮球（无论是否长按隐藏成小球，球始终存在）
                final View b = BALLS.get(Integer.valueOf(System.identityHashCode(activity)));
                if (b != null) b.setVisibility(View.VISIBLE);
                if (onPanelClosed != null) onPanelClosed.run();  // 同步 shown 状态
            }
        });
        navBar.addView(closeBtn, new LinearLayout.LayoutParams(
                dp(ctx, 44), dp(ctx, 44)));

        // 标题（v151：主标题 + 副标题）
        LinearLayout titleBox = new LinearLayout(ctx);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER);
        TextView navTitle = new TextView(ctx);
        navTitle.setText("🐋 ds美化");
        navTitle.setTextColor(textColor);
        navTitle.setTextSize(16);
        navTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        navTitle.setGravity(Gravity.CENTER);
        titleBox.addView(navTitle);
        TextView navSubTv = new TextView(ctx);
        navSubTv.setText("控制中心 · v303");
        navSubTv.setTextColor(subColor);
        navSubTv.setTextSize(10);
        navSubTv.setGravity(Gravity.CENTER);
        navSubTv.setAlpha(0.8f);
        titleBox.addView(navSubTv);
        navBar.addView(titleBox, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 占位（保持标题居中）
        TextView placeholder = new TextView(ctx);
        placeholder.setText("   ");
        placeholder.setTextSize(18);
        navBar.addView(placeholder, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        page.addView(navBar, new LinearLayout.LayoutParams(
                 LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ===== 多页面内容区（4 个独立页面，底部 dock 切换显示） =====
        final FrameLayout pageHost = new FrameLayout(ctx);
        pageHost.setBackgroundColor(0x00000000);  // v347：透明，让液态玻璃背景层透出

        // 4 个独立页面（每个 = ScrollView 包 LinearLayout）
        final android.widget.ScrollView[] pages = new android.widget.ScrollView[4];
        final LinearLayout[] pageContents = new LinearLayout[4];
        for (int i = 0; i < 4; i++) {
            android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
            sv.setFillViewport(true);
            sv.setBackgroundColor(bgColor);
            sv.setId(View.generateViewId());
            LinearLayout c = new LinearLayout(ctx);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 84));
            sv.addView(c);
            pages[i] = sv;
            pageContents[i] = c;
            pageHost.addView(sv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            sv.setVisibility(i == 0 ? View.VISIBLE : View.GONE);  // 默认显示主页
        }
        SCROLLS.put(Integer.valueOf(System.identityHashCode(activity)), pages[0]);

        // ===== 第 0 页：主页（v152：Hero 横幅） =====
        final LinearLayout pageHome = pageContents[0];
        LinearLayout hero = new LinearLayout(ctx);
        hero.setOrientation(LinearLayout.VERTICAL);
        // v348 iOS 26：液态玻璃 hero（半透明玻璃 + 顶部高光 + 细描边）
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0x3DFFFFFF, 0x1AFFFFFF, 0x0DFFFFFF}
                     : new int[]{0x99FFFFFF, 0x66FFFFFF, 0x4DFFFFFF});
        heroBg.setCornerRadius(dp(ctx, 24));
        heroBg.setStroke(dp(ctx, 1), dark ? 0x33FFFFFF : 0x33FFFFFF);
        hero.setBackgroundDrawable(heroBg);
        hero.setPadding(dp(ctx, 20), dp(ctx, 18), dp(ctx, 20), dp(ctx, 18));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            hero.setElevation(dp(ctx, 2));
            hero.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        }
        // 标题行：鲸鱼图标 + 标题 + 副标题
        LinearLayout heroTitleRow = new LinearLayout(ctx);
        heroTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        heroTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heroIcon = new TextView(ctx);
        heroIcon.setText("🐋");
        heroIcon.setTextSize(28);
        heroTitleRow.addView(heroIcon);
        LinearLayout heroTexts = new LinearLayout(ctx);
        heroTexts.setOrientation(LinearLayout.VERTICAL);
        heroTexts.setPadding(dp(ctx, 12), 0, 0, 0);
        TextView heroTitle = new TextView(ctx);
        heroTitle.setText("ds美化 控制中心");
        heroTitle.setTextColor(dark ? 0xFFECECEC : 0xFF1A1A1A);
        heroTitle.setTextSize(19);
        heroTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heroTexts.addView(heroTitle);
        TextView heroSub = new TextView(ctx);
        heroSub.setText("DeepSeek 增强 · 完全本地");
        heroSub.setTextColor(dark ? 0xB8FFFFFF : 0xE6000000);
        heroSub.setTextSize(12);
        heroSub.setPadding(0, dp(ctx, 2), 0, 0);
        heroTexts.addView(heroSub);
        heroTitleRow.addView(heroTexts);
        hero.addView(heroTitleRow);
        // 状态小胶囊行
        LinearLayout heroChips = new LinearLayout(ctx);
        heroChips.setOrientation(LinearLayout.HORIZONTAL);
        heroChips.setPadding(0, dp(ctx, 12), 0, 0);
        hero.addView(heroChips);
        String[] chipLabels = {
            DsConfig.bgOn(ctx) ? "壁纸 开" : "壁纸 关",
            DsConfig.ballHidden(ctx) ? "球 隐藏" : "灵动岛",
            DsConfig.removeCensor(ctx) ? "去审查" : "审查 开",
            DsConfig.proxyHost(ctx).length() > 0 ? "反代" : "直连"
        };
        final Runnable[] chipActions = {
            new Runnable() { public void run() { DsConfig.setBgOn(ctx, !DsConfig.bgOn(ctx)); refresh.run(); } },
            new Runnable() { public void run() { DsConfig.setBallHidden(ctx, !DsConfig.ballHidden(ctx)); } },
            new Runnable() { public void run() { DsConfig.setRemoveCensor(ctx, !DsConfig.removeCensor(ctx)); refresh.run(); } },
            new Runnable() { public void run() { } }
        };
        for (int ci = 0; ci < chipLabels.length; ci++) {
            final int cci = ci;
            TextView chip = new TextView(ctx);
            chip.setText(chipLabels[ci]);
            chip.setTextColor(dark ? 0xCCFFFFFF : 0xCC000000);
            chip.setTextSize(10);
            chip.setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4));
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(dark ? 0x1AFFFFFF : 0x14000000);
            chipBg.setCornerRadius(dp(ctx, 12));
            chipBg.setStroke(dp(ctx, 1), dark ? 0x22FFFFFF : 0x11000000);
            chip.setBackgroundDrawable(chipBg);
            if (cci < chipActions.length) {
                chip.setClickable(true);
                chip.setOnClickListener(new android.view.View.OnClickListener() {
                    public void onClick(android.view.View v) {
                        try {
                            chipActions[cci].run();
                            toast(ctx, "已切换");
                        } catch (Throwable ignored) {}
                    }
                });
            }
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipLp.rightMargin = dp(ctx, 6);
            heroChips.addView(chip, chipLp);
        }
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        heroLp.bottomMargin = dp(ctx, 16);
        pageHome.addView(hero, heroLp);

        // ---------- 快捷入口 2x2 网格（v152） ----------
        pageHome.addView(sectionTitle(ctx, "快捷入口"));
        LinearLayout quickGrid1 = new LinearLayout(ctx);
        quickGrid1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout quickGrid2 = new LinearLayout(ctx);
        quickGrid2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams gridLp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridLp1.topMargin = dp(ctx, 4);
        pageHome.addView(quickGrid1, gridLp1);
        LinearLayout.LayoutParams gridLp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridLp2.topMargin = dp(ctx, 8);
        pageHome.addView(quickGrid2, gridLp2);

        // 大图标卡片辅助（返回 LinearLayout 卡片）
        // 卡1 背景壁纸
        quickGrid1.addView(quickCard(ctx, dark, "🖼", "背景壁纸", C_BLUE, new Runnable() {
            public void run() {
                sConfigTitle = "背景壁纸";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildBgConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), quickCardLp(ctx, true));
        // 卡2 自定义头像
        quickGrid1.addView(quickCard(ctx, dark, "👤", "自定义头像", C_PURPLE, new Runnable() {
            public void run() {
                sConfigTitle = "自定义头像";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildAvatarConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), quickCardLp(ctx, false));
        // 卡3 缓存清理
        quickGrid2.addView(quickCard(ctx, dark, "🧹", "缓存清理", C_CYAN, new Runnable() {
            public void run() {
                sConfigTitle = "缓存清理";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildCleanConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), quickCardLp(ctx, true));
        // 卡4 气泡外观
        quickGrid2.addView(quickCard(ctx, dark, "💬", "气泡外观", C_ORANGE, new Runnable() {
            public void run() {
                sConfigTitle = "气泡外观";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildBubbleConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), quickCardLp(ctx, false));

        // ---------- 自绘聊天入口（v241：第三行网格） ----------
        LinearLayout quickGrid3 = new LinearLayout(ctx);
        quickGrid3.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams gridLp3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridLp3.topMargin = dp(ctx, 8);
        pageHome.addView(quickGrid3, gridLp3);
        // 卡5 系统提示词（快捷入口）
        quickGrid3.addView(quickCard(ctx, dark, "📝", "系统提示词", C_PURPLE, new Runnable() {
            public void run() {
                sConfigTitle = "系统提示词";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildSystemPromptConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), quickCardLp(ctx, false));

        // ---------- 欢迎语配置卡（精简版） ----------
        pageHome.addView(sectionTitle(ctx, "主页欢迎语"));

        // ---------- 欢迎语输入（v152 精简） ----------
        final LinearLayout homeBodyL = new LinearLayout(ctx);
        homeBodyL.setOrientation(LinearLayout.VERTICAL);
        pageHome.addView(homeBodyL);
        homeBodyL.addView(iOSLabel(ctx, subColor, "欢迎语（留空=默认）"));
        final android.widget.EditText greetingEt = new android.widget.EditText(ctx);
        greetingEt.setSingleLine(true);
        greetingEt.setText(DsConfig.greeting(ctx));
        greetingEt.setTextColor(textColor);
        greetingEt.setHintTextColor(subColor);
        greetingEt.setHint("输入自定义欢迎语");
        greetingEt.setTextSize(14);
        greetingEt.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8));
        GradientDrawable greetBg = new GradientDrawable();
        greetBg.setColor(0x14FFFFFF);
        greetBg.setCornerRadius(dp(ctx, 10));
        greetingEt.setBackgroundDrawable(greetBg);
        homeBodyL.addView(greetingEt);
        TextView greetOk = new TextView(ctx);
        greetOk.setText("✔ 应用欢迎语");
        greetOk.setTextColor(BRAND);
        greetOk.setTextSize(13);
        greetOk.setGravity(Gravity.CENTER);
        greetOk.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8));
        GradientDrawable greetOkBg = new GradientDrawable();
        greetOkBg.setColor(0x1A4D6BFE);
        greetOkBg.setCornerRadius(dp(ctx, 10));
        greetOk.setBackgroundDrawable(greetOkBg);
        greetOk.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String g = greetingEt.getText().toString().trim();
                DsConfig.setGreeting(ctx, g);
                toast(ctx, g.length() > 0 ? "欢迎语已设置" : "已恢复默认欢迎语");
                refresh.run();
            }
        });
        homeBodyL.addView(greetOk);
        // v318：欢迎语高级设置入口（位置/显隐）
        LinearLayout.LayoutParams navLpGreetAdv = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpGreetAdv.topMargin = dp(ctx, 8);
        homeBodyL.addView(navRow(ctx, "🎯", "欢迎语位置与显隐", "调整偏移、隐藏欢迎语", textColor, subColor, divColor, C_PURPLE, new Runnable() {
            public void run() {
                sConfigTitle = "主页欢迎语";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildGreetConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpGreetAdv);


        // ===== 第 1 页：界面 =====
        final LinearLayout pageFace = pageContents[1];
        // ---------- 功能入口列表（点击进入半屏配置页） ----------
        // v148：分组标题
        pageFace.addView(sectionTitle(ctx, "外观"));
        // ── 背景壁纸 入口 ──
        LinearLayout.LayoutParams navLpBg = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpBg.topMargin = dp(ctx, 4);
        pageFace.addView(navRow(ctx, "🖼", "背景壁纸", "壁纸图、模糊、色调、遮罩", textColor, subColor, divColor, C_BLUE, new Runnable() {
            public void run() {
                sConfigTitle = "背景壁纸";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildBgConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpBg);

        // ── 输入框提示色 入口 ──
        LinearLayout.LayoutParams navLpHue = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpHue.topMargin = dp(ctx, 8);
        pageFace.addView(navRow(ctx, "🎨", "输入框美化", "提示文字、输入文字、光标颜色", textColor, subColor, divColor, C_BLUE, new Runnable() {
            public void run() {
                sConfigTitle = "输入框提示色";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildHintConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpHue);

        // ── 灵动岛设置 入口 ──
        LinearLayout.LayoutParams navLpBall = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpBall.topMargin = dp(ctx, 8);
        pageFace.addView(navRow(ctx, "📍", "灵动岛设置", "位置、透明度", textColor, subColor, divColor, C_BLUE, new Runnable() {
            public void run() {
                sConfigTitle = "灵动岛设置";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildBallConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpBall);

        // ── 面板设置 入口 ──
        LinearLayout.LayoutParams navLpPanel = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpPanel.topMargin = dp(ctx, 8);
        pageFace.addView(navRow(ctx, "⚙️", "面板设置", "主题、圆角", textColor, subColor, divColor, C_BLUE, new Runnable() {
            public void run() {
                sConfigTitle = "面板设置";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildPanelConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpPanel);
        // v148：分组标题
        pageFace.addView(sectionTitle(ctx, "功能"));

        // ── 禁用热更新（开关行）──
        LinearLayout.LayoutParams navLpHot = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpHot.topMargin = dp(ctx, 8);
        pageFace.addView(switchRow(ctx, "🔒", "禁用热更新", "禁止 App 热更新覆盖", textColor, subColor, divColor, C_GRAY, DsConfig.noHotUpdate(ctx), new Runnable() {
            public void run() {
                DsConfig.setNoHotUpdate(ctx, !DsConfig.noHotUpdate(ctx));
                refresh.run();
            }
        }), navLpHot);

        // ── 实时时钟（开关行）──
        LinearLayout.LayoutParams navLpClock = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpClock.topMargin = dp(ctx, 8);
        pageFace.addView(switchRow(ctx, "🕐", "实时时钟", "壁纸上方叠加时间显示", textColor, subColor, divColor, C_PURPLE, DsConfig.showClock(ctx), new Runnable() {
            public void run() {
                DsConfig.setShowClock(ctx, !DsConfig.showClock(ctx));
                refresh.run();
            }
        }), navLpClock);

        // ===== 第 2 页：聊天 =====
        final LinearLayout pageChat = pageContents[2];

        // v148：分组标题
        pageChat.addView(sectionTitle(ctx, "外观"));
        // ── 气泡外观 入口 ──
        LinearLayout.LayoutParams navLpBubble = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpBubble.topMargin = dp(ctx, 4);
        // v170：系统提示词入口
        LinearLayout.LayoutParams navLpSys = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpSys.topMargin = dp(ctx, 4);
        pageChat.addView(navRow(ctx, "📝", "系统提示词", "注入角色/规则，让 AI 按设定回复", textColor, subColor, divColor, C_PURPLE, new Runnable() {
            public void run() {
                sConfigTitle = "系统提示词";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildSystemPromptConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpSys);

        pageChat.addView(navRow(ctx, "💬", "气泡外观", "样式、圆角、透明度、字体", textColor, subColor, divColor, C_BLUE, new Runnable() {
            public void run() {
                sConfigTitle = "气泡外观";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildBubbleConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpBubble);

        // v148：分组标题
        pageChat.addView(sectionTitle(ctx, "功能"));
        // ── 聊天背景独立（开关行 v318 改版：直接开关+选图）──
        LinearLayout.LayoutParams navLpChatBg = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpChatBg.topMargin = dp(ctx, 8);
        pageChat.addView(switchRow(ctx, "🌄", "聊天背景独立", "聊天页使用独立壁纸", textColor, subColor, divColor, C_BLUE, DsConfig.chatBgOn(ctx), new Runnable() {
            public void run() {
                DsConfig.setChatBgOn(ctx, !DsConfig.chatBgOn(ctx));
                refresh.run();
            }
        }), navLpChatBg);
        // ── 聊天壁纸选图按钮（v318：独立行，不占子页）──
        if (DsConfig.chatBgOn(ctx)) {
            LinearLayout.LayoutParams navLpChatBgPick = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            navLpChatBgPick.topMargin = dp(ctx, 4);
            pageChat.addView(navRow(ctx, "🖼", "选择聊天壁纸", "从相册选择聊天页专用壁纸", textColor, subColor, divColor, C_CYAN, new Runnable() {
                public void run() {
                    try {
                        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                        activity.startActivityForResult(intent, 0xB163);
                        toast(ctx, "请选择聊天页壁纸");
                    } catch (Throwable t) {
                        toast(ctx, "无法打开相册");
                    }
                }
            }), navLpChatBgPick);
        }

        // ── 消息时间显示（开关行）──
        LinearLayout.LayoutParams navLpMsgTime = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpMsgTime.topMargin = dp(ctx, 8);
        pageChat.addView(switchRow(ctx, "⏱", "消息时间显示", "消息旁显示时间戳（已上线）", textColor, subColor, divColor, C_BLUE, DsConfig.showMsgTime(ctx), new Runnable() {
            public void run() {
                DsConfig.setShowMsgTime(ctx, !DsConfig.showMsgTime(ctx));
                refresh.run();
            }
        }), navLpMsgTime);
        // ── 隐藏消息下方按钮（开关行，v318）──
        LinearLayout.LayoutParams navLpHideBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpHideBtn.topMargin = dp(ctx, 8);
        pageChat.addView(switchRow(ctx, "🙈", "隐藏消息按钮", "隐藏消息下方操作按钮（复制/朗读/反馈）", textColor, subColor, divColor, C_GRAY, DsConfig.hideBtnOn(ctx), new Runnable() {
            public void run() {
                DsConfig.setHideBtnOn(ctx, !DsConfig.hideBtnOn(ctx));
                refresh.run();
            }
        }), navLpHideBtn);
        // ── 文字波纹（开关行 v318 移入聊天页）──
        LinearLayout.LayoutParams navLpWave2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpWave2.topMargin = dp(ctx, 8);
        pageChat.addView(switchRow(ctx, "🌊", "文字波纹", "消息文字彩色波纹动效", textColor, subColor, divColor, C_PURPLE, DsConfig.textWave(ctx), new Runnable() {
            public void run() {
                DsConfig.setTextWave(ctx, !DsConfig.textWave(ctx));
                refresh.run();
            }
        }), navLpWave2);
        // ── 聊天增强 入口 ──
        LinearLayout.LayoutParams navLpEnhance = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpEnhance.topMargin = dp(ctx, 8);
        pageChat.addView(navRow(ctx, "🧪", "聊天增强", "去审查、多选删除、备份等", textColor, subColor, divColor, C_PURPLE, new Runnable() {
            public void run() {
                sConfigTitle = "聊天增强";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildChatEnhanceConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpEnhance);

        // ===== 第 3 页：更多 =====
        final LinearLayout pageMore = pageContents[3];

        // v148：分组标题
        pageMore.addView(sectionTitle(ctx, "工具"));
        // ── 缓存清理 入口 ──
        LinearLayout.LayoutParams navLpClean = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpClean.topMargin = dp(ctx, 4);
        // v211：反代设置入口
        LinearLayout.LayoutParams navLpProxy = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpProxy.topMargin = dp(ctx, 4);
        pageMore.addView(navRow(ctx, "🌐", "反代设置", "自定义 API 域名（加速/绕限流）", textColor, subColor, divColor, C_CYAN, new Runnable() {
            public void run() {
                sConfigTitle = "反代设置";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildProxyConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpProxy);

        pageMore.addView(navRow(ctx, "🧹", "缓存清理", "自动清理策略、立即清理", textColor, subColor, divColor, C_CYAN, new Runnable() {
            public void run() {
                sConfigTitle = "缓存清理";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildCleanConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpClean);

        // ── 聊天备份 入口 ──
        LinearLayout.LayoutParams navLpBackup = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpBackup.topMargin = dp(ctx, 8);
        pageMore.addView(navRow(ctx, "💾", "聊天备份", "数据库备份、恢复说明", textColor, subColor, divColor, C_CYAN, new Runnable() {
            public void run() {
                sConfigTitle = "聊天备份";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildBackupConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpBackup);

        // ── 设备与进程 入口 ──
        LinearLayout.LayoutParams navLpDevice = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpDevice.topMargin = dp(ctx, 8);
        pageMore.addView(navRow(ctx, "📱", "设备与进程", "设备信息、进程状态", textColor, subColor, divColor, C_CYAN, new Runnable() {
            public void run() {
                sConfigTitle = "设备与进程";
                sConfigBuilder = new ConfigBuilder() {
                    public void build(LinearLayout content) {
                        buildDeviceConfig(ctx, content, activity, textColor, subColor, divColor, refresh);
                    }
                };
                showConfig[0].run();
            }
        }), navLpDevice);

        // ── Deekseep 功能（开发中）──
        pageMore.addView(sectionTitle(ctx, "规划中"));
        addDevNav(ctx, pageMore, textColor, subColor, divColor, "🕐", "历史桥接", "在线历史记录到本地编辑器");
        addDevNav(ctx, pageMore, textColor, subColor, divColor, "🧪", "请求调试", "查看 API 请求详情");
        addDevNav(ctx, pageMore, textColor, subColor, divColor, "✂️", "图片抠图", "消息图片抠图处理");
        addDevNav(ctx, pageMore, textColor, subColor, divColor, "🎵", "本地音频控制", "控制本地音频播放");
        addDevNav(ctx, pageMore, textColor, subColor, divColor, "📱", "QQ音乐控制", "QQ 音乐播放控制");
        addDevNav(ctx, pageMore, textColor, subColor, divColor, "🔑", "账号管理", "多账号切换管理");
        addDevNav(ctx, pageMore, textColor, subColor, divColor, "⚙️", "任务执行", "自动化任务执行器");

        // v148：分组标题
        pageMore.addView(sectionTitle(ctx, "设置"));
        // ── 卡密激活 入口（v318 从外观页移入）──
        LinearLayout.LayoutParams navLpLicense = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpLicense.topMargin = dp(ctx, 8);
        boolean licActivated = DsLicense.isActivated(ctx);
        pageMore.addView(navRow(ctx, "🔑", "卡密激活", licActivated ? "已激活（永久）" : "未激活，输入卡密解锁功能", textColor, subColor, divColor, licActivated ? C_CYAN : C_RED, new Runnable() {
            public void run() {
                showLicenseDialog(ctx, refresh);
            }
        }), navLpLicense);
        // ── 调试日志（开关行）──
        LinearLayout.LayoutParams navLpDebug = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpDebug.topMargin = dp(ctx, 8);
        pageMore.addView(switchRow(ctx, "🐞", "调试日志", "记录模块运行日志", textColor, subColor, divColor, C_GRAY, DsConfig.debugLog(ctx), new Runnable() {
            public void run() {
                DsConfig.setDebugLog(ctx, !DsConfig.debugLog(ctx));
                refresh.run();
            }
        }), navLpDebug);

        // ── 隐藏悬浮窗（开关行）──
        LinearLayout.LayoutParams navLpHide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpHide.topMargin = dp(ctx, 8);
        pageMore.addView(switchRow(ctx, "👻", "隐藏悬浮窗", "切换为小圆点显示", textColor, subColor, divColor, C_GRAY, DsConfig.ballHidden(ctx), new Runnable() {
            public void run() {
                boolean hide = !DsConfig.ballHidden(ctx);
                DsConfig.setBallHidden(ctx, hide);
                // 立即生效：重建球（mini 或 正常胶囊）
                Integer k = Integer.valueOf(System.identityHashCode(activity));
                View old = BALLS.remove(k);
                if (old != null && old.getParent() != null) {
                    try { ((ViewGroup) old.getParent()).removeView(old); } catch (Throwable ignored) {}
                }
                final View nb = buildBall(activity, dark, new Runnable() {
                    public void run() {
                        showPanelAt(activity, 0);
                    }
                }, hide);
                final FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
                nlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                nlp.topMargin = dp(ctx, 30);
                nb.setLayoutParams(nlp);
                View decor = activity.getWindow().getDecorView();
                if (decor instanceof ViewGroup) {
                    ((ViewGroup) decor).addView(nb);
                    BALLS.put(k, nb);
                }
                refresh.run();
            }
        }), navLpHide);

        // ── 恢复默认设置（动作行）──
        LinearLayout.LayoutParams navLpReset = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLpReset.topMargin = dp(ctx, 8);
        pageMore.addView(navRow(ctx, "♻️", "恢复默认设置", "重置所有配置项", textColor, subColor, divColor, C_RED, new Runnable() {
            public void run() {
                DsConfig.setBgOn(ctx, false);  // v318：背景默认关
                DsConfig.setBgAlpha(ctx, 40);
                DsConfig.setDarkFilter(ctx, 0);
                DsConfig.setBgMode(ctx, 0);
                DsConfig.setBgBlur(ctx, 0);
                DsConfig.setTintMode(ctx, 0);
                DsConfig.setHintColor(ctx, 0);
                DsConfig.setBallPos(ctx, 0);
                DsConfig.setBallAlpha(ctx, 90);
                DsConfig.setPanelRadius(ctx, 24);
                DsConfig.setChatBgOn(ctx, false);
                DsConfig.setBubbleRadius(ctx, 18);
                DsConfig.setBubbleAlpha(ctx, 100);
                DsConfig.setAutoClean(ctx, 0);
                DsConfig.setDebugLog(ctx, false);
                DsConfig.setBallHidden(ctx, false);
                // v150：补全其余配置项
                DsConfig.setPanelTheme(ctx, 1);
                DsConfig.setMaskStrength(ctx, 0);
                DsConfig.setBallAlign(ctx, 1);
                DsConfig.setBallOffsetY(ctx, 55);
                DsConfig.setMsgFontSize(ctx, 15);
                DsConfig.setBubbleStyle(ctx, 0);
                DsConfig.setGreeting(ctx, "");
                DsConfig.setGreetX(ctx, 0);
                DsConfig.setGreetY(ctx, 0);
                DsConfig.setGreetHidden(ctx, false);
                DsConfig.setWhaleAnim(ctx, true);
                DsConfig.setTextWave(ctx, false);
                DsConfig.setNoHotUpdate(ctx, false);
                DsConfig.setShowClock(ctx, false);
                DsConfig.setShowMsgTime(ctx, false);
                DsConfig.setAutoContinue(ctx, false);
                // v204：补全缺失配置
                DsConfig.setSystemPrompt(ctx, "");
                DsConfig.setInputTextColor(ctx, 0);
                DsConfig.setCursorColor(ctx, 0);
                DsConfig.setChatBgPath(ctx, "");
                DsConfig.setChatBgOn(ctx, false);
                // v217：补全遗漏项
                DsConfig.setProxyHost(ctx, "");
                DsConfig.setRemoveCensor(ctx, false);
                DsConfig.setDisableTraining(ctx, false);
                // v318：新增开关默认重置
                DsConfig.setBubbleOn(ctx, false);
                DsConfig.setHideBtnOn(ctx, false);
                DsConfig.setChatBtnOn(ctx, false);
                android.widget.Toast.makeText(ctx, "已恢复默认设置", android.widget.Toast.LENGTH_SHORT).show();
                refresh.run();
            }
        }), navLpReset);

        // ── 关于（信息卡）──
        TextView aboutTv = new TextView(ctx);
        aboutTv.setText("ds美化 · DeepSeek 美化模块\n气泡美化 · 系统提示词 · 自定义头像 · 调色盘\n版本 v320 · 2026-09-17");
        aboutTv.setTextColor(subColor);
        aboutTv.setTextSize(12);
        aboutTv.setGravity(Gravity.CENTER);
        aboutTv.setPadding(dp(ctx, 10), dp(ctx, 14), dp(ctx, 10), dp(ctx, 14));
        GradientDrawable aboutBg = new GradientDrawable();
        aboutBg.setColor(0x08FFFFFF);
        aboutBg.setCornerRadius(dp(ctx, 10));
        aboutTv.setBackgroundDrawable(aboutBg);
        LinearLayout.LayoutParams aboutLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        aboutLp.topMargin = dp(ctx, 16);
        pageMore.addView(aboutTv, aboutLp);

        final int pageHomeId = pages[0].getId();
        final int pageFaceId = pages[1].getId();
        final int pageChatId = pages[2].getId();
        final int pageMoreId = pages[3].getId();

        page.addView(pageHost, new LinearLayout.LayoutParams(
                 LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));


        // ===== 底部分类标签栏（大玻璃胶囊容器 + 4 个选中发光标签） =====
        // 这个注入环境里父 LinearLayout 的 padding/gravity/MATCH_PARENT 都不可靠（子视图贴左上角）
        // 所以：大胶囊固定高度 56dp，小胶囊固定高度 + 上下对称 margin 拼满 56dp → 物理居中
        final LinearLayout shell = new LinearLayout(ctx);
        shell.setId(View.generateViewId());
        final int bottomBarId = shell.getId();
        shell.setOrientation(LinearLayout.HORIZONTAL);

        // 大胶囊容器：液态玻璃渐变 + 高光条 + 阴影（图二效果）
        GradientDrawable shellBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0xF226262A, 0xCC1C1C20, 0xA6141418}
                     : new int[]{0xFFFFFFFF, 0xF2FFFFFF, 0xE6EEF1F6});
        shellBg.setShape(GradientDrawable.RECTANGLE);
        shellBg.setCornerRadius(dp(ctx, 28));
        shellBg.setStroke(dp(ctx, 1), dark ? 0x99FFFFFF : 0x66FFFFFF);
        // 高光条：全尺寸 TOP_BOTTOM 渐变（上半亮→下半透明），不用 InsetDrawable
        // ★ 关键：InsetDrawable 的 inset 会变成隐式 padding 挤开内容，导致小胶囊偏上，禁用
        GradientDrawable gloss = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                dark ? new int[]{0x2EFFFFFF, 0x0DFFFFFF, 0x00FFFFFF}
                     : new int[]{0x59FFFFFF, 0x24FFFFFF, 0x00FFFFFF});
        gloss.setShape(GradientDrawable.RECTANGLE);
        gloss.setCornerRadius(dp(ctx, 28));
        LayerDrawable shellLayers = new LayerDrawable(new Drawable[]{shellBg, gloss});
        shell.setBackgroundDrawable(shellLayers);
        shell.setElevation(dp(ctx, 14));
        shell.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        shell.setClipChildren(false);
        shell.setClipToPadding(false);

        final View panelRoot = root;
        // 页面切换辅助：显示第 idx 页，隐藏其他
        final java.lang.Runnable page0 = new java.lang.Runnable() { public void run() { switchPage(pages, 0); }};
        final java.lang.Runnable page1 = new java.lang.Runnable() { public void run() { switchPage(pages, 1); }};
        final java.lang.Runnable page2 = new java.lang.Runnable() { public void run() { switchPage(pages, 2); }};
        final java.lang.Runnable page3 = new java.lang.Runnable() { public void run() { switchPage(pages, 3); }};
        // 标签 1：主页（显示主页页）
        shell.addView(panelTab(ctx, dark, "🏠", "主页", 0, true, new Runnable() {
            public void run() {
                selectTab(shell, dark, "主页");
                page0.run();
            }
        }));
        // 标签 2：界面（切到界面页）
        shell.addView(panelTab(ctx, dark, "🎨", "界面", 1, false, new Runnable() {
            public void run() {
                selectTab(shell, dark, "界面");
                page1.run();
            }
        }));
        // 标签 3：聊天（切到聊天页）
        shell.addView(panelTab(ctx, dark, "💬", "聊天", 2, false, new Runnable() {
            public void run() {
                selectTab(shell, dark, "聊天");
                page2.run();
            }
        }));
        // 标签 4：更多（切到更多页）
        shell.addView(panelTab(ctx, dark, "🧩", "更多", 3, false, new Runnable() {
            public void run() {
                selectTab(shell, dark, "更多");
                page3.run();
            }
        }));

        // v151：宽版 Dock（屏幕宽 - 两侧 24dp），4 个标签等宽均分
        int dockW = ctx.getResources().getDisplayMetrics().widthPixels - dp(ctx, 48);
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(
                 dockW, dp(ctx, 64));
        bottomLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;  // 底部对齐 + 水平居中
        bottomLp.bottomMargin = dp(ctx, 12);  // 贴底
        page.addView(shell, bottomLp);

        // 记录卡片 ID（供 dock 分类跳转定位）
        CARD_IDS.put(Integer.valueOf(System.identityHashCode(activity)),
                new int[]{pageHomeId, pageFaceId, pageChatId, pageMoreId});

        return root;
    }

    /**
     * 构建可折叠分类卡片（Deekseep 风格）：
     * 标题行点击展开/收缩，bodyOut 返回内容容器供填充。
     * 返回 LinearLayout 整卡（child[0]=header, child[1]=body）。
     */
    private static LinearLayout buildCategoryCard(final Context ctx, final String title,
            final int cardColor, final int textColor, final int subColor, final int divColor,
            final LinearLayout[] bodyOut) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setCornerRadius(dp(ctx, 16));
        cbg.setColor(cardColor);
        cbg.setStroke(dp(ctx, 1), divColor);
        card.setBackgroundDrawable(cbg);

        // 标题行
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(textColor);
        t.setTextSize(15);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(t, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView arrow = new TextView(ctx);
        arrow.setText("▾");
        arrow.setTextColor(subColor);
        arrow.setTextSize(14);
        header.addView(arrow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 内容体（默认收缩）
        final LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        body.setPadding(dp(ctx, 14), 0, dp(ctx, 14), dp(ctx, 12));

        card.addView(header);
        card.addView(body);
        if (bodyOut != null) bodyOut[0] = body;
        body.setTag(arrow);  // 箭头存 body tag，供 openCategory 同步状态

        header.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean open = body.getVisibility() == View.VISIBLE;
                if (open) {
                    // v152：收起动画（快速淡出）
                    body.animate().cancel();
                    body.animate().alpha(0f).setDuration(120)
                            .withEndAction(new Runnable() {
                                public void run() {
                                    body.setVisibility(View.GONE);
                                    body.setAlpha(1f);
                                }
                            }).start();
                } else {
                    // v152：展开动画（淡入）
                    body.animate().cancel();
                    body.setAlpha(0f);
                    body.setVisibility(View.VISIBLE);
                    body.animate().alpha(1f).setDuration(180).start();
                }
                arrow.setText(open ? "▾" : "▴");
            }
        });
        return card;
    }

    /**
     * 构建可折叠子项（单个功能折叠组）：
     * 返回一个 LinearLayout，包含可点击标题行 + 内容容器。
     * 点击标题展开/收缩内容。
     */
    private static LinearLayout buildSubCard(final Context ctx, final String title,
            final int textColor, final int subColor, final int divColor) {
        LinearLayout item = new LinearLayout(ctx);
        item.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setCornerRadius(dp(ctx, 12));
        ibg.setColor(0x08FFFFFF);
        ibg.setStroke(dp(ctx, 1), divColor);
        item.setBackgroundDrawable(ibg);

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(textColor);
        t.setTextSize(14);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(t, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView arrow = new TextView(ctx);
        arrow.setText("▾");
        arrow.setTextColor(subColor);
        arrow.setTextSize(13);
        header.addView(arrow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        body.setPadding(dp(ctx, 12), 0, dp(ctx, 12), dp(ctx, 10));

        item.addView(header);
        item.addView(body);
        body.setTag(arrow);

        header.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean open = body.getVisibility() == View.VISIBLE;
                if (open) {
                    // v152：收起动画（快速淡出）
                    body.animate().cancel();
                    body.animate().alpha(0f).setDuration(120)
                            .withEndAction(new Runnable() {
                                public void run() {
                                    body.setVisibility(View.GONE);
                                    body.setAlpha(1f);
                                }
                            }).start();
                } else {
                    // v152：展开动画（淡入）
                    body.animate().cancel();
                    body.setAlpha(0f);
                    body.setVisibility(View.VISIBLE);
                    body.animate().alpha(1f).setDuration(180).start();
                }
                arrow.setText(open ? "▾" : "▴");
            }
        });
        return item;
    }

    /** 获取折叠子项的 body（第2个子视图） */
    private static LinearLayout subCardBody(LinearLayout card) {
        if (card.getChildCount() >= 2 && card.getChildAt(1) instanceof LinearLayout) {
            return (LinearLayout) card.getChildAt(1);
        }
        return card;
    }
    private static LinearLayout avatarCardBody(LinearLayout card) { return subCardBody(card); }
    private static LinearLayout greetCardBody(LinearLayout card) { return subCardBody(card); }

    /**
     * 功能入口行（列表项）：图标+名称+描述+箭头，点击进入配置子页
     * 高级感：图标圆形渐变底、描边、右箭头
     */
    private static View navRow(final Context ctx, final String icon, final String title,
                               final String desc, final int textColor, final int subColor,
                               final int divColor, final Runnable onClick) {
        return navRow(ctx, icon, title, desc, textColor, subColor, divColor, BRAND, onClick);
    }

    /** v151：带类别色的功能入口行 */
    private static View navRow(final Context ctx, final String icon, final String title,
                               final String desc, final int textColor, final int subColor,
                               final int divColor, final int accent, final Runnable onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 10), dp(ctx, 11), dp(ctx, 10), dp(ctx, 11));
        // v348 iOS 26：半透明玻璃行（深色模式浅白，浅色模式淡灰）
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(sPanelDark ? 0x14FFFFFF : 0x0D000000);
        rowBg.setCornerRadius(dp(ctx, 16));
        rowBg.setStroke(dp(ctx, 1), sPanelDark ? 0x14FFFFFF : 0x0A000000);
        row.setBackgroundDrawable(rowBg);
        // 按压反馈（高亮）
        final GradientDrawable rowBgN = rowBg;
        final GradientDrawable rowBgP = new GradientDrawable();
        rowBgP.setColor(sPanelDark ? 0x2EFFFFFF : 0x1A000000);
        rowBgP.setCornerRadius(dp(ctx, 16));
        rowBgP.setStroke(dp(ctx, 1), sPanelDark ? 0x3DFFFFFF : 0x1F000000);
        row.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                int a = e.getActionMasked();
                if (a == android.view.MotionEvent.ACTION_DOWN) {
                    v.setBackgroundDrawable(rowBgP);
                } else if (a == android.view.MotionEvent.ACTION_UP
                        || a == android.view.MotionEvent.ACTION_CANCEL) {
                    v.setBackgroundDrawable(rowBgN);
                }
                return false;
            }
        });
        // 方形圆角图标（iOS 设置风格）
        TextView iconTv = new TextView(ctx);
        iconTv.setText(icon);
        iconTv.setTextSize(16);
        iconTv.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(withAlpha(accent, sPanelDark ? 0x38 : 0x24));
        iconBg.setCornerRadius(dp(ctx, 10));
        iconTv.setBackgroundDrawable(iconBg);
        row.addView(iconTv, new LinearLayout.LayoutParams(dp(ctx, 38), dp(ctx, 38)));
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(ctx, 12), 0, dp(ctx, 8), 0);
        TextView titleTv = new TextView(ctx);
        titleTv.setText(title);
        titleTv.setTextColor(textColor);
        titleTv.setTextSize(15);
        texts.addView(titleTv);
        if (desc != null && desc.length() > 0) {
            TextView descTv = new TextView(ctx);
            descTv.setText(desc);
            descTv.setTextColor(subColor);
            descTv.setTextSize(12);
            descTv.setPadding(0, dp(ctx, 2), 0, 0);
            texts.addView(descTv);
        }
        row.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = new TextView(ctx);
        arrow.setText("›");
        arrow.setTextColor(sPanelDark ? 0x9AFFFFFF : 0xB2000000);
        arrow.setTextSize(22);
        row.addView(arrow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onClick.run(); }
        });
        return row;
    }

    /** 圆角背景辅助 */
    private static android.graphics.drawable.GradientDrawable roundedBg(
            Context ctx, int color, int strokeColor, int radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeColor != 0) g.setStroke(dp(ctx, 1), strokeColor);
        return g;
    }

    /**
     * 开关列表行（纯开关功能，无配置页）：
     * 图标 + 标题 + 描述 + 右侧开关，整行点击切换
     */
    private static View switchRow(final Context ctx, final String icon, final String title,
                                  final String desc, final int textColor, final int subColor,
                                  final int divColor, final boolean checked, final Runnable onToggle) {
        return switchRow(ctx, icon, title, desc, textColor, subColor, divColor, BRAND, checked, onToggle);
    }

    /** v151：带类别色的开关行 */
    private static View switchRow(final Context ctx, final String icon, final String title,
                                  final String desc, final int textColor, final int subColor,
                                  final int divColor, final int accent, final boolean checked, final Runnable onToggle) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 10), dp(ctx, 11), dp(ctx, 10), dp(ctx, 11));
        // v348 iOS 26：半透明玻璃行
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(sPanelDark ? 0x14FFFFFF : 0x0D000000);
        rowBg.setCornerRadius(dp(ctx, 16));
        rowBg.setStroke(dp(ctx, 1), sPanelDark ? 0x14FFFFFF : 0x0A000000);
        row.setBackgroundDrawable(rowBg);
        // 按压反馈（高亮）
        final GradientDrawable swBgN = rowBg;
        final GradientDrawable swBgP = new GradientDrawable();
        swBgP.setColor(sPanelDark ? 0x2EFFFFFF : 0x1A000000);
        swBgP.setCornerRadius(dp(ctx, 16));
        swBgP.setStroke(dp(ctx, 1), sPanelDark ? 0x3DFFFFFF : 0x1F000000);
        row.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                int a = e.getActionMasked();
                if (a == android.view.MotionEvent.ACTION_DOWN) {
                    v.setBackgroundDrawable(swBgP);
                } else if (a == android.view.MotionEvent.ACTION_UP
                        || a == android.view.MotionEvent.ACTION_CANCEL) {
                    v.setBackgroundDrawable(swBgN);
                }
                return false;
            }
        });
        // 方形圆角图标（iOS 风格）
        TextView iconTv = new TextView(ctx);
        iconTv.setText(icon);
        iconTv.setTextSize(16);
        iconTv.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(withAlpha(accent, sPanelDark ? 0x38 : 0x24));
        iconBg.setCornerRadius(dp(ctx, 10));
        iconTv.setBackgroundDrawable(iconBg);
        row.addView(iconTv, new LinearLayout.LayoutParams(dp(ctx, 38), dp(ctx, 38)));
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(ctx, 12), 0, dp(ctx, 8), 0);
        TextView titleTv = new TextView(ctx);
        titleTv.setText(title);
        titleTv.setTextColor(textColor);
        titleTv.setTextSize(15);
        texts.addView(titleTv);
        if (desc != null && desc.length() > 0) {
            TextView descTv = new TextView(ctx);
            descTv.setText(desc);
            descTv.setTextColor(subColor);
            descTv.setTextSize(12);
            descTv.setPadding(0, dp(ctx, 2), 0, 0);
            texts.addView(descTv);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final android.widget.Switch sw = new android.widget.Switch(ctx);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(null);
        sw.setClickable(false);  // 整行点击处理
        row.addView(sw, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onToggle.run();
                sw.setChecked(!sw.isChecked());  // v148：同步开关状态
            }
        });
        return row;
    }

    // ============ v153：配置页精致组件（卡片 / 行 / 按钮） ============

    /** v153：配置卡片容器（圆角卡片，暗色自适应）—— v361：DEKK 风格 */
    private static LinearLayout configCard(Context ctx, LinearLayout content) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        boolean dk = DsUI.dark;
        bg.setColor(dk ? 0xFF292929 : 0xFFFFFFFF);
        bg.setCornerRadius(dp(ctx, 22));
        card.setBackgroundDrawable(bg);
        card.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 12);
        content.addView(card, lp);
        return card;
    }

    /** v344：iOS 26 液态玻璃卡片 —— 半透明磨砂 + 顶部高光 + 边缘发光 */
    private static LinearLayout fxCard(Context ctx, LinearLayout content, final int accent) {
        final LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        // 玻璃拟态：半透明 + 顶部高光线 + 微内阴影感（用 LayerDrawable 叠）
        GradientDrawable glass = new GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                sPanelDark
                        ? new int[]{0xD91C1C2A, 0xB3161620, 0xD920202E}
                        : new int[]{0xE8FFFFFF, 0xD9F0F4FF, 0xE8F5F8FF});
        glass.setCornerRadius(dp(ctx, 20));
        glass.setStroke(dp(ctx, 1), sPanelDark ? 0x33FFFFFF : 0x1A000000);
        // 顶部高光条（玻璃反射感）
        GradientDrawable shine = new GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{sPanelDark ? 0x1FFFFFFF : 0x1A000000, 0x00000000});
        shine.setCornerRadius(dp(ctx, 20));
        shine.setSize(0, dp(ctx, 6));
        LayerDrawable layer = new LayerDrawable(new Drawable[]{glass, shine});
        card.setBackgroundDrawable(layer);
        // 柔和入场
        card.setAlpha(0f);
        card.setTranslationY(dp(ctx, 10));
        card.animate().alpha(1f).translationY(0).setDuration(200).start();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 12);
        content.addView(card, lp);
        return card;
    }

    /** v449：分组标题 —— 浅色模式黑色、深色模式浅色（不再半透明灰） */
    private static View fxSectionTitle(Context ctx, String text, int accent) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.14f);
        tv.setTextColor(sPanelDark ? 0xFFE8E8E8 : 0xFF111111);
        tv.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 4));
        return tv;
    }

    /** v344：iOS 26 列表行 —— 圆角图标 + 标题 + 副标题 + 右箭头 + 点击涟漪 */
    private static View fxListItem(Context ctx, LinearLayout card, String icon, int iconBg,
                                   String title, String sub, final Runnable onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 10), dp(ctx, 12), dp(ctx, 10), dp(ctx, 12));
        // 图标：圆角色块（透明弱化版）
        TextView iconTv = new TextView(ctx);
        iconTv.setText(icon);
        iconTv.setTextSize(16);
        iconTv.setGravity(Gravity.CENTER);
        GradientDrawable iconBgD = new GradientDrawable();
        iconBgD.setColor(withAlpha(iconBg, sPanelDark ? 0x38 : 0x22));
        iconBgD.setCornerRadius(dp(ctx, 12));
        iconTv.setBackgroundDrawable(iconBgD);
        row.addView(iconTv, new LinearLayout.LayoutParams(dp(ctx, 42), dp(ctx, 42)));
        // 文字列
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(ctx, 14), 0, 0, 0);
        TextView titleTv = new TextView(ctx);
        titleTv.setText(title);
        titleTv.setTextSize(15.5f);
        titleTv.setTextColor(sPanelDark ? 0xFFEDEDF2 : 0xFF1A1A1A);
        texts.addView(titleTv);
        if (sub != null && sub.length() > 0) {
            TextView subTv = new TextView(ctx);
            subTv.setText(sub);
            subTv.setTextSize(12.5f);
            subTv.setTextColor(sPanelDark ? 0xB8FFFFFF : 0xE0000000);
            subTv.setPadding(0, dp(ctx, 2), 0, 0);
            texts.addView(subTv);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        // 右箭头（灰色）
        TextView arrow = new TextView(ctx);
        arrow.setText("›");
        arrow.setTextSize(22);
        arrow.setTextColor(sPanelDark ? 0x9AFFFFFF : 0xB2000000);
        arrow.setPadding(dp(ctx, 6), 0, dp(ctx, 4), 0);
        row.addView(arrow);
        // 点击涟漪
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            android.content.res.ColorStateList ripple = android.content.res.ColorStateList.valueOf(
                    withAlpha(iconBg, 0x28));
            android.graphics.drawable.RippleDrawable rd =
                    new android.graphics.drawable.RippleDrawable(ripple, null, null);
            row.setBackground(rd);
        }
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onClick.run(); }
        });
        card.addView(row);
        return row;
    }

    /** v344：iOS 26 行内分隔线（缩进避开图标，半透明） */
    private static void fxDivider(Context ctx, LinearLayout card) {
        View line = new View(ctx);
        line.setBackgroundColor(sPanelDark ? 0x14FFFFFF : 0x0F000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1));
        lp.leftMargin = dp(ctx, 66);  // 图标42 + padding 14 + 间距 ≈ 66
        lp.rightMargin = dp(ctx, 10);
        card.addView(line, lp);
    }

    /** v344：兼容旧调用（fxButton 保留用于其它页面） */
    private static TextView fxButton(Context ctx, LinearLayout card, String text,
                                     final int accent, final Runnable onClick) {
        final TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(accent);
        btn.setTextSize(14);
        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(withAlpha(accent, sPanelDark ? 0x26 : 0x1A));
        bg.setCornerRadius(dp(ctx, 12));
        bg.setStroke(dp(ctx, 1), withAlpha(accent, 0x40));
        btn.setBackgroundDrawable(bg);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onClick.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 6);
        lp.bottomMargin = dp(ctx, 6);
        card.addView(btn, lp);
        return btn;
    }

    /** v342：调整颜色亮度（delta 正=变亮，负=变暗） */
    private static int adjustBrightness(int color, int delta) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r = Math.max(0, Math.min(255, r + delta));
        g = Math.max(0, Math.min(255, g + delta));
        b = Math.max(0, Math.min(255, b + delta));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** v153：卡片内开关行 —— v361：DEKK 风格（16sp 主色，间距 20/14） */
    private static View cardSwitchRow(final Context ctx, LinearLayout card, String title,
                                      boolean checked, final Runnable onToggle) {
        return cardSwitchRow(ctx, card, title, checked, onToggle, null);
    }

    /** v461：卡片内开关行（可返回 Switch 引用，供互斥逻辑联动） */
    private static android.widget.Switch cardSwitchRow(final Context ctx, LinearLayout card, String title,
            boolean checked, final Runnable onToggle, final Void unused) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        boolean dk = DsUI.dark;
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextColor(dk ? 0xFFE8E8E8 : 0xFF1A1A1A);
        tv.setTextSize(16);
        row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.Switch sw = new android.widget.Switch(ctx);
        sw.setChecked(checked);
        sw.setClickable(false);
        row.addView(sw);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sw.setChecked(!sw.isChecked());
                onToggle.run();
            }
        });
        card.addView(row);
        return sw;
    }

    /** v153：卡片内滑杆行（标题 + 实时数值 + 滑杆） */
    /** v153：卡片内滑杆行（标题 + 实时数值 + 滑杆）—— v361：DEKK 风格 */
    private static View cardSeekRow(final Context ctx, LinearLayout card, String title,
                                    int max, int progress, final int displayBase, final String suffix,
                                    final SeekBar.OnSeekBarChangeListener l) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 6));
        boolean dk = DsUI.dark;
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextColor(dk ? 0xFFE8E8E8 : 0xFF1A1A1A);
        tv.setTextSize(16);
        top.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView val = new TextView(ctx);
        val.setText((progress + displayBase) + suffix);
        val.setTextColor(0xFF4D6BFE);
        val.setTextSize(14);
        val.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(val);
        box.addView(top);
        final String sfx = suffix;
        final int base = displayBase;
        SeekBar sb = new SeekBar(ctx);
        sb.setMax(max);
        sb.setProgress(progress);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                val.setText((v + base) + sfx);
                if (l != null) l.onProgressChanged(s, v, f);
            }
            public void onStartTrackingTouch(SeekBar s) { if (l != null) l.onStartTrackingTouch(s); }
            public void onStopTrackingTouch(SeekBar s) { if (l != null) l.onStopTrackingTouch(s); }
        });
        box.addView(sb, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(box);
        return box;
    }

    /** v153：卡片内选择行（标题 + 当前值 + ›），返回右侧值 TextView 供更新 */
    private static TextView cardSelectRow(final Context ctx, LinearLayout card, String title,
                                          String value, final Runnable onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(ctx, 12), 0, dp(ctx, 12));
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextColor(sPanelDark ? 0xFFECECEC : 0xFF1A1A1A);
        tv.setTextSize(14);
        row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView val = new TextView(ctx);
        val.setText(value);
        val.setTextColor(BRAND);
        val.setTextSize(14);
        val.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(val);
        TextView arrow = new TextView(ctx);
        arrow.setText("  ›");
        arrow.setTextColor(sPanelDark ? 0xFFB8B8C0 : 0xFF555555);
        arrow.setTextSize(16);
        row.addView(arrow);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onClick.run(); }
        });
        card.addView(row);
        return val;
    }

/** v153：卡片内操作按钮 —— v361：DEKK 风格 */
    private static TextView cardButton(final Context ctx, LinearLayout card, String text,
                                       final int accent, final Runnable onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(15);
        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(ctx, 12), dp(ctx, 13), dp(ctx, 12), dp(ctx, 13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF4D6BFE);
        bg.setCornerRadius(dp(ctx, 14));
        btn.setBackgroundDrawable(bg);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onClick.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(ctx, 12);
        lp.rightMargin = dp(ctx, 12);
        lp.topMargin = dp(ctx, 8);
        lp.bottomMargin = dp(ctx, 8);
        card.addView(btn, lp);
        return btn;
    }
    /** v153：卡片内说明文字 —— v449：浅色模式改黑色（用户要求），深色保持浅灰 */
    private static void cardNote(final Context ctx, LinearLayout card, String text) {
        boolean dk = DsUI.dark;
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(dk ? 0xFFD6D6DA : 0xFF1A1A1A);
        tv.setTextSize(12);
        tv.setLineSpacing(dp(ctx, 3), 1f);
        tv.setPadding(dp(ctx, 16), dp(ctx, 6), dp(ctx, 16), dp(ctx, 12));
        card.addView(tv);
    }
    /** 构建「自定义头像」配置页内容 */
    static void buildAvatarConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        cardButton(ctx, card1, "🖼  选择头像图片", C_BLUE, new Runnable() {
            public void run() {
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                    activity.startActivityForResult(intent, 0xB162);
                    toast(ctx, "请选择头像图片");
                } catch (Throwable t) {
                    toast(ctx, "无法打开相册");
                }
            }
        });
        cardNote(ctx, card1, "主页鲸鱼与聊天页助手头像都会替换（重启生效）");

        LinearLayout card2 = configCard(ctx, content);
        cardSeekRow(ctx, card2, "头像大小", 80, Math.max(0, DsConfig.avatarSize(ctx) - 40), 40, "", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setAvatarSize(ctx, v + 40);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        cardSeekRow(ctx, card2, "水平位置", 100, DsConfig.avatarX(ctx) + 50, -50, "", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setAvatarX(ctx, v - 50);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        cardSeekRow(ctx, card2, "垂直位置", 100, DsConfig.avatarY(ctx) + 50, -50, "", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setAvatarY(ctx, v - 50);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    /** 构建「主页欢迎语」配置页内容 */
    static void buildGreetConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        cardNote(ctx, card1, "欢迎语文案（留空 = 默认）");
        final android.widget.EditText greetEt = new android.widget.EditText(ctx);
        greetEt.setSingleLine(true);
        greetEt.setText(DsConfig.greeting(ctx));
        greetEt.setTextColor(sPanelDark ? 0xFFECECEC : 0xFF1A1A1A);
        greetEt.setHintTextColor(sPanelDark ? 0xFF8A8A92 : 0xFF555555);
        greetEt.setHint("输入自定义欢迎语");
        greetEt.setTextSize(14);
        greetEt.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8));
        GradientDrawable gBg = new GradientDrawable();
        gBg.setColor(sPanelDark ? 0xFF101018 : 0xFFF2F3F6);
        gBg.setCornerRadius(dp(ctx, 10));
        gBg.setStroke(dp(ctx, 1), sPanelDark ? 0xFF26262E : 0xFFE8E8EC);
        greetEt.setBackgroundDrawable(gBg);
        card1.addView(greetEt);
        cardButton(ctx, card1, "✔  应用欢迎语", C_BLUE, new Runnable() {
            public void run() {
                String g = greetEt.getText().toString().trim();
                DsConfig.setGreeting(ctx, g);
                toast(ctx, g.length() > 0 ? "欢迎语已设置（重启生效）" : "已恢复默认欢迎语");
                refresh.run();
            }
        });

        LinearLayout card2 = configCard(ctx, content);
        cardSeekRow(ctx, card2, "水平位置", 100, DsConfig.greetX(ctx) + 50, -50, "", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setGreetX(ctx, v - 50);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        cardSeekRow(ctx, card2, "垂直位置", 100, DsConfig.greetY(ctx) + 50, -50, "", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setGreetY(ctx, v - 50);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        cardSwitchRow(ctx, card2, "隐藏欢迎语", DsConfig.greetHidden(ctx), new Runnable() {
            public void run() {
                DsConfig.setGreetHidden(ctx, !DsConfig.greetHidden(ctx));
                refresh.run();
            }
        });
    }

    /** 构建「背景壁纸」配置页内容 */
    static void buildBgConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        cardSwitchRow(ctx, card1, "启用背景图", DsConfig.bgOn(ctx), new Runnable() {
            public void run() {
                DsConfig.setBgOn(ctx, !DsConfig.bgOn(ctx));
                refresh.run();
            }
        });
        cardButton(ctx, card1, "🖼  修改背景图", C_BLUE, new Runnable() {
            public void run() {
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                    activity.startActivityForResult(intent, 0xB161);
                    toast(ctx, "请选择一张图片作为背景");
                } catch (Throwable t) {
                    toast(ctx, "无法打开相册");
                }
            }
        });
        // v419：壁纸模式（原生绘制开发中，统一走叠加层）
        final String[] wpModeNames = {"叠加层（推荐）", "原生绘制（开发中）"};
        final TextView[] wpModeH = new TextView[1];
        wpModeH[0] = cardSelectRow(ctx, card1, "壁纸模式", wpModeNames[0], new Runnable() {
            public void run() {
                toast(ctx, "原生绘制模式开发中，当前使用叠加层");
            }
        });
        cardNote(ctx, card1, "叠加层：稳定可靠；原生绘制：开发中");

        LinearLayout card2 = configCard(ctx, content);
        cardSeekRow(ctx, card2, "透出强度", 90, DsConfig.bgAlpha(ctx), 0, "%", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setBgAlpha(ctx, v);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        cardSeekRow(ctx, card2, "暗色氛围", 100, DsConfig.darkFilter(ctx), 0, "%", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setDarkFilter(ctx, v);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        cardSeekRow(ctx, card2, "壁纸模糊", 25, DsConfig.bgBlur(ctx), 0, "", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setBgBlur(ctx, v);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        cardSeekRow(ctx, card2, "遮罩强度", 80, DsConfig.maskStrength(ctx), 0, "%", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setMaskStrength(ctx, v);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        LinearLayout card3 = configCard(ctx, content);
        // 背景类型：0=图片壁纸 1=纯黑 2=纯白（配合半透明气泡显玻璃透感）
        final String[] solidNamesF = {"图片壁纸", "纯黑背景", "纯白背景"};
        final TextView[] solidH = new TextView[1];
        solidH[0] = cardSelectRow(ctx, card3, "背景类型", solidNamesF[DsConfig.bgSolid(ctx) % 3], new Runnable() {
            public void run() {
                int s = (DsConfig.bgSolid(ctx) + 1) % 3;
                DsConfig.setBgSolid(ctx, s);
                solidH[0].setText(solidNamesF[s]);
                refresh.run();
            }
        });
        final TextView[] modeH = new TextView[1];
        modeH[0] = cardSelectRow(ctx, card3, "壁纸模式", modeName(DsConfig.bgMode(ctx)), new Runnable() {
            public void run() {
                int m = (DsConfig.bgMode(ctx) + 1) % 3;
                DsConfig.setBgMode(ctx, m);
                modeH[0].setText(modeName(m));
                refresh.run();
            }
        });
        final TextView[] tintH = new TextView[1];
        tintH[0] = cardSelectRow(ctx, card3, "全局色调", tintName(DsConfig.tintMode(ctx)), new Runnable() {
            public void run() {
                int t = (DsConfig.tintMode(ctx) + 1) % 5;
                DsConfig.setTintMode(ctx, t);
                tintH[0].setText(tintName(t));
                refresh.run();
            }
        });
    }

    /** v174：构建「聊天背景独立」配置页内容 */
    static void buildChatBgConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        cardSwitchRow(ctx, card1, "启用聊天独立背景", DsConfig.chatBgOn(ctx), new Runnable() {
            public void run() {
                DsConfig.setChatBgOn(ctx, !DsConfig.chatBgOn(ctx));
                refresh.run();
            }
        });
        cardButton(ctx, card1, "🖼  选择聊天页壁纸", C_BLUE, new Runnable() {
            public void run() {
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                    activity.startActivityForResult(intent, 0xB163);
                    toast(ctx, "请选择聊天页壁纸");
                } catch (Throwable t) {
                    toast(ctx, "无法打开相册");
                }
            }
        });
        cardNote(ctx, card1, "开启后，聊天页使用独立壁纸，其他页面仍用通用壁纸");
    }

    /** v211：构建「反代设置」配置页内容 */
    static void buildProxyConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        cardNote(ctx, card1, "把所有 API 请求的域名 chat.deepseek.com 替换为你的反代服务器，用于加速或绕过限流。留空 = 不使用。");
        final android.widget.EditText proxyEt = new android.widget.EditText(ctx);
        proxyEt.setSingleLine(true);
        proxyEt.setText(DsConfig.proxyHost(ctx));
        proxyEt.setTextColor(sPanelDark ? 0xFFECECEC : 0xFF1A1A1A);
        proxyEt.setHintTextColor(sPanelDark ? 0xFF8A8A92 : 0xFF555555);
        proxyEt.setHint("例如：proxy.example.com");
        proxyEt.setTextSize(14);
        proxyEt.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8));
        GradientDrawable pBg = new GradientDrawable();
        pBg.setColor(sPanelDark ? 0xFF101018 : 0xFFF2F3F6);
        pBg.setCornerRadius(dp(ctx, 10));
        pBg.setStroke(dp(ctx, 1), sPanelDark ? 0xFF26262E : 0xFFE8E8EC);
        proxyEt.setBackgroundDrawable(pBg);
        card1.addView(proxyEt);
        cardButton(ctx, card1, "✔  保存反代地址", C_BLUE, new Runnable() {
            public void run() {
                String v = proxyEt.getText().toString().trim();
                // 去掉可能的 https:// 前缀（只填域名）
                v = v.replace("https://", "").replace("http://", "");
                while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
                DsConfig.setProxyHost(ctx, v);
                toast(ctx, v.length() > 0 ? "反代已设置：" + v + "（重启生效）" : "已关闭反代");
                refresh.run();
            }
        });
        cardNote(ctx, card1, "需自备反代服务器（转发到 chat.deepseek.com）。设置后重启 DeepSeek 生效。");
    }

    /** v170：构建「系统提示词」配置页内容（v290 增加预设管理） */
    static void buildSystemPromptConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        cardNote(ctx, card1, "系统提示词会注入到每次对话请求中，让 AI 按你设定的角色/规则回复。可保存多个预设，下次一键套用。");
        final android.widget.EditText promptEt = new android.widget.EditText(ctx);
        promptEt.setMinLines(4);
        promptEt.setMaxLines(8);
        promptEt.setGravity(android.view.Gravity.START | android.view.Gravity.TOP);
        promptEt.setText(DsConfig.systemPrompt(ctx));
        promptEt.setTextColor(sPanelDark ? 0xFFECECEC : 0xFF1A1A1A);
        promptEt.setHintTextColor(sPanelDark ? 0xFF8A8A92 : 0xFF555555);
        promptEt.setHint("例如：你是一个精通 Python 的编程助手，回答要简洁、给出代码示例。");
        promptEt.setTextSize(14);
        promptEt.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8));
        GradientDrawable pBg = new GradientDrawable();
        pBg.setColor(sPanelDark ? 0xFF101018 : 0xFFF2F3F6);
        pBg.setCornerRadius(dp(ctx, 10));
        pBg.setStroke(dp(ctx, 1), sPanelDark ? 0xFF26262E : 0xFFE8E8EC);
        promptEt.setBackgroundDrawable(pBg);
        card1.addView(promptEt);
        // 保存当前提示词
        cardButton(ctx, card1, "✔  保存系统提示词", C_BLUE, new Runnable() {
            public void run() {
                String v = promptEt.getText().toString().trim();
                DsConfig.setSystemPrompt(ctx, v);
                toast(ctx, v.length() > 0 ? "系统提示词已保存（新对话生效）" : "已清除系统提示词");
                refresh.run();
            }
        });
        // 保存为预设（输入预设名）
        cardButton(ctx, card1, "⭐  保存为预设", C_PURPLE, new Runnable() {
            public void run() {
                final String v = promptEt.getText().toString().trim();
                if (v.length() == 0) { toast(ctx, "请先输入提示词内容"); return; }
                final android.widget.EditText nameEt = new android.widget.EditText(ctx);
                nameEt.setHint("预设名称（如：Python 助手）");
                nameEt.setTextColor(sPanelDark ? 0xFFECECEC : 0xFF1A1A1A);
                LinearLayout nameBox = new LinearLayout(ctx);
                nameBox.setOrientation(LinearLayout.VERTICAL);
                nameBox.addView(nameEt);
                showInputDialog(ctx, "保存为预设", nameBox, new Runnable() {
                    public void run() {
                        String name = nameEt.getText().toString().trim();
                        if (name.length() == 0) name = "预设" + (System.currentTimeMillis() % 1000);
                        addPreset(ctx, name, v);
                        toast(ctx, "预设「" + name + "」已保存");
                        refresh.run();
                    }
                });
            }
        });

        // 预设列表
        LinearLayout card2 = configCard(ctx, content);
        cardNote(ctx, card2, "我的预设（点击应用）");
        String presetsJson = DsConfig.promptPresets(ctx);
        renderPresetList(ctx, card2, presetsJson, promptEt, refresh);
    }

    /** 渲染预设列表 */
    private static void renderPresetList(final Context ctx, final LinearLayout card2,
            final String presetsJson, final android.widget.EditText promptEt, final Runnable refresh) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(presetsJson);
            card2.removeAllViews();
            if (arr.length() == 0) {
                cardNote(ctx, card2, "还没有预设，输入提示词后点「保存为预设」添加。");
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                final org.json.JSONObject obj = arr.getJSONObject(i);
                final String name = obj.optString("name", "未命名");
                final String content = obj.optString("content", "");
                final int idx = i;
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4));
                // 应用按钮
                android.widget.TextView applyTv = new android.widget.TextView(ctx);
                applyTv.setText("▶ " + name);
                applyTv.setTextSize(14);
                applyTv.setTextColor(C_PURPLE);
                applyTv.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
                applyTv.setOnClickListener(new android.view.View.OnClickListener() {
                    public void onClick(android.view.View v) {
                        DsConfig.setSystemPrompt(ctx, content);
                        promptEt.setText(content);
                        toast(ctx, "已应用预设「" + name + "」（新对话生效）");
                    }
                });
                row.addView(applyTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                // 删除按钮
                android.widget.TextView delTv = new android.widget.TextView(ctx);
                delTv.setText("🗑");
                delTv.setTextSize(14);
                delTv.setTextColor(0xFFE53935);
                delTv.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8));
                delTv.setOnClickListener(new android.view.View.OnClickListener() {
                    public void onClick(android.view.View v) {
                        removePreset(ctx, idx);
                        toast(ctx, "已删除预设「" + name + "」");
                        refresh.run();
                    }
                });
                row.addView(delTv);
                card2.addView(row);
            }
        } catch (Throwable t) {
            cardNote(ctx, card2, "预设解析失败");
        }
    }

    /** 新增预设 */
    private static void addPreset(Context ctx, String name, String content) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(DsConfig.promptPresets(ctx));
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("name", name);
            obj.put("content", content);
            arr.put(obj);
            DsConfig.setPromptPresets(ctx, arr.toString());
        } catch (Throwable ignored) {}
    }

    /** 删除预设 */
    private static void removePreset(Context ctx, int idx) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(DsConfig.promptPresets(ctx));
            if (idx >= 0 && idx < arr.length()) {
                arr.remove(idx);
                DsConfig.setPromptPresets(ctx, arr.toString());
            }
        } catch (Throwable ignored) {}
    }

    /** 输入对话框（简易） */
    private static void showInputDialog(final Context ctx, String title, final android.view.View input,
            final Runnable onOk) {
        try {
            final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(ctx).create();
            dlg.setTitle(title);
            dlg.setView(input);
            dlg.setButton(android.app.AlertDialog.BUTTON_POSITIVE, "确定",
                    new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            onOk.run();
                            d.dismiss();
                        }
                    });
            dlg.setButton(android.app.AlertDialog.BUTTON_NEGATIVE, "取消",
                    new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) { d.dismiss(); }
                    });
            dlg.show();
        } catch (Throwable t) {
            toast(ctx, "无法弹出输入框");
        }
    }

    /** v456：绘画软件式专业取色器 —— SV 二维盘(饱和度/亮度) + 竖色相条 + 快捷色进盘微调 */
private static void showPaletteDialog(final Context ctx, String title, int initial,
        final ColorPickListener2 listener) {
    try {
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(ctx).create();
        dlg.setTitle(title);
        dlg.setButton(android.app.AlertDialog.BUTTON_NEGATIVE, "取消",
                new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { d.dismiss(); }
                });

        final android.widget.LinearLayout root = new android.widget.LinearLayout(ctx);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8));

        final Runnable[] updRef = new Runnable[1];  // v456：前向引用占位（快捷键点击用）

        // ── 当前颜色预览块 ──
        final android.view.View preview = new android.view.View(ctx);
        android.widget.LinearLayout.LayoutParams prevLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 44));
        prevLp.topMargin = dp(ctx, 4);
        prevLp.bottomMargin = dp(ctx, 4);
        GradientDrawable prevBg = new GradientDrawable();
        prevBg.setCornerRadius(dp(ctx, 10));
        prevBg.setColor(initial);
        prevBg.setStroke(dp(ctx, 1), 0x40000000);
        preview.setBackgroundDrawable(prevBg);
        root.addView(preview, prevLp);

        // ── SV 二维盘 + 竖色相条 ──
        final PaletteSVView svView = new PaletteSVView(ctx);
        final PaletteHueView hueView = new PaletteHueView(ctx);
        final android.widget.LinearLayout discRow = new android.widget.LinearLayout(ctx);
        discRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        discRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.LinearLayout.LayoutParams svLp = new android.widget.LinearLayout.LayoutParams(
                0, dp(ctx, 210), 1f);
        discRow.addView(svView, svLp);
        android.widget.LinearLayout.LayoutParams hueLp = new android.widget.LinearLayout.LayoutParams(
                dp(ctx, 30), dp(ctx, 210));
        hueLp.leftMargin = dp(ctx, 10);
        discRow.addView(hueView, hueLp);
        root.addView(discRow, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── 快捷色（点击 → 进入对应色系的盘中微调，不立即应用）──
        final int[] quick = {
                0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
                0xFF00ACC1, 0xFF1E88E5, 0xFF3949AB, 0xFF8E24AA,
                0xFFD81B60, 0xFF6D4C41, 0xFF111111, 0xFF9E9E9E,
                0xFFFFFFFF, 0xFF4D6BFE, 0xFF10B981, 0xFF8EA9FF
        };
        final android.widget.LinearLayout quickRow = new android.widget.LinearLayout(ctx);
        quickRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        quickRow.setPadding(0, dp(ctx, 6), 0, 0);
        for (final int c : quick) {
            final android.view.View sw = new android.view.View(ctx);
            final GradientDrawable qBg = new GradientDrawable();
            qBg.setShape(GradientDrawable.OVAL);
            qBg.setColor(c);
            qBg.setStroke(dp(ctx, 1), 0x33000000);
            sw.setBackgroundDrawable(qBg);
            android.widget.LinearLayout.LayoutParams qLp = new android.widget.LinearLayout.LayoutParams(dp(ctx, 30), dp(ctx, 30));
            qLp.rightMargin = dp(ctx, 5);
            sw.setLayoutParams(qLp);
            sw.setOnClickListener(new android.view.View.OnClickListener() {
                public void onClick(android.view.View v) {
                    // 定位到该颜色：点一下进入盘中对应色系，可继续拖盘微调
                    // （updateSel 在后面定义，用一个数组引用实现前向引用）
                    float[] hsv = new float[3];
                    android.graphics.Color.colorToHSV(c, hsv);
                    hueView.setHue(hsv[0]);
                    svView.setHue(hsv[0]);
                    svView.setSatVal(hsv[1], hsv[2]);
                    updRef[0].run();
                }
            });
            quickRow.addView(sw);
        }
        root.addView(quickRow, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── 当前色值显示 ──
        final android.widget.TextView hexTv = new android.widget.TextView(ctx);
        hexTv.setTextSize(13);
        hexTv.setTextColor(0xFF333333);
        hexTv.setGravity(android.view.Gravity.CENTER);
        hexTv.setPadding(0, dp(ctx, 4), 0, 0);
        root.addView(hexTv, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── 联动更新 ──
        final int[] cur = {initial};
        final Runnable updateSel = new Runnable() {
            public void run() {
                int cc = android.graphics.Color.HSVToColor(new float[]{
                        hueView.getHue(), svView.getSat(), svView.getVal()});
                cur[0] = cc;
                GradientDrawable g = (GradientDrawable) preview.getBackground();
                g.setColor(cc);
                preview.invalidate();
                hexTv.setText(String.format("#%06X", 0xFFFFFF & cc));
            }
        };
        updRef[0] = updateSel;
        svView.setOnChanged(updateSel);
        hueView.setOnChanged(new Runnable() {
            public void run() {
                svView.setHue(hueView.getHue());
                updateSel.run();
            }
        });

        // 初始值
        float[] initHsv = new float[3];
        android.graphics.Color.colorToHSV(initial, initHsv);
        hueView.setHue(initHsv[0]);
        svView.setHue(initHsv[0]);
        svView.setSatVal(initHsv[1], initHsv[2]);

        // ── 完成按钮 ──
        android.widget.TextView confirm = new android.widget.TextView(ctx);
        confirm.setText("✓ 确定使用此颜色");
        confirm.setTextColor(0xFFFFFFFF);
        confirm.setTextSize(15);
        confirm.setGravity(android.view.Gravity.CENTER);
        confirm.setPadding(0, dp(ctx, 12), 0, dp(ctx, 12));
        GradientDrawable cBg = new GradientDrawable();
        cBg.setCornerRadius(dp(ctx, 14));
        cBg.setColor(0xFF4D6BFE);
        confirm.setBackgroundDrawable(cBg);
        android.widget.LinearLayout.LayoutParams cLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 46));
        cLp.topMargin = dp(ctx, 6);
        confirm.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                if (listener != null) listener.onPick(cur[0]);
                dlg.dismiss();
            }
        });
        root.addView(confirm, cLp);

        dlg.setView(root);
        updateSel.run();
        dlg.show();
    } catch (Throwable t) {
        toast(ctx, "无法弹出调色盘");
    }
}

/** v456：SV 二维色盘 —— 显示当前色调下所有饱和度(横)/亮度(竖)组合，拖动取色 */
private static class PaletteSVView extends android.view.View {
    private float hue = 0f, sat = 0.5f, val = 0.8f;
    private java.lang.Runnable onChanged;
    public PaletteSVView(android.content.Context c) { super(c); }
    public void setHue(float h) { hue = h; invalidate(); }
    public void setSatVal(float s, float v) { sat = s; val = v; invalidate(); }
    public float getSat() { return sat; }
    public float getVal() { return val; }
    public void setOnChanged(java.lang.Runnable r) { onChanged = r; }
    @Override
    protected void onDraw(android.graphics.Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        // 1) 纯色调底色
        android.graphics.Paint p0 = new android.graphics.Paint();
        p0.setColor(android.graphics.Color.HSVToColor(new float[]{hue, 1f, 1f}));
        cv.drawRect(0, 0, w, h, p0);
        // 2) 水平白→透明（饱和度从 0 到 1）
        android.graphics.Paint p1 = new android.graphics.Paint();
        p1.setShader(new android.graphics.LinearGradient(0, 0, w, 0,
                0xFFFFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p1);
        // 3) 垂直透明→黑（亮度从 1 到 0）
        android.graphics.Paint p2 = new android.graphics.Paint();
        p2.setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                0x00000000, 0xFF000000, android.graphics.Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p2);
        // 4) 边框
        android.graphics.Paint pf = new android.graphics.Paint();
        pf.setStyle(android.graphics.Paint.Style.STROKE);
        pf.setStrokeWidth(dp(getContext(), 1f));
        pf.setColor(0x66000000);
        cv.drawRect(0, 0, w, h, pf);
        // 5) 指示点（当前 S/V）
        float px = sat * w, py = (1f - val) * h;
        android.graphics.Paint dot = new android.graphics.Paint();
        dot.setStyle(android.graphics.Paint.Style.STROKE);
        dot.setStrokeWidth(dp(getContext(), 3f));
        dot.setColor(0xFF000000);
        cv.drawCircle(px, py, dp(getContext(), 8f), dot);
        android.graphics.Paint dot2 = new android.graphics.Paint();
        dot2.setStyle(android.graphics.Paint.Style.STROKE);
        dot2.setStrokeWidth(dp(getContext(), 1.5f));
        dot2.setColor(0xFFFFFFFF);
        cv.drawCircle(px, py, dp(getContext(), 8f), dot2);
    }
    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return true;
        float x = Math.max(0, Math.min(w, e.getX()));
        float y = Math.max(0, Math.min(h, e.getY()));
        sat = x / w;
        val = 1f - y / h;
        invalidate();
        if (onChanged != null) onChanged.run();
        return true;
    }
}

/** v456：竖色相条 —— 彩虹 0-360°，点击/拖动选色系，盘中立即刷新 */
private static class PaletteHueView extends android.view.View {
    private float hue = 0f;
    private java.lang.Runnable onChanged;
    public PaletteHueView(android.content.Context c) { super(c); }
    public void setHue(float h) { hue = h; invalidate(); }
    public float getHue() { return hue; }
    public void setOnChanged(java.lang.Runnable r) { onChanged = r; }
    @Override
    protected void onDraw(android.graphics.Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int[] cols = new int[7];
        for (int i = 0; i < 7; i++) cols[i] = android.graphics.Color.HSVToColor(new float[]{i * 60f, 1f, 1f});
        float[] pos = {0f, 0.1667f, 0.3333f, 0.5f, 0.6667f, 0.8333f, 1f};
        android.graphics.Paint p = new android.graphics.Paint();
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, h, cols, pos, android.graphics.Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        android.graphics.Paint pf = new android.graphics.Paint();
        pf.setStyle(android.graphics.Paint.Style.STROKE);
        pf.setStrokeWidth(dp(getContext(), 1f));
        pf.setColor(0x66000000);
        cv.drawRect(0, 0, w, h, pf);
        // 指示线
        float y = hue / 360f * h;
        android.graphics.Paint dl = new android.graphics.Paint();
        dl.setColor(0xFF000000);
        dl.setStrokeWidth(dp(getContext(), 3f));
        cv.drawLine(0, y, w, y, dl);
        android.graphics.Paint dl2 = new android.graphics.Paint();
        dl2.setColor(0xFFFFFFFF);
        dl2.setStrokeWidth(dp(getContext(), 1f));
        cv.drawLine(0, y, w, y, dl2);
    }
    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        int h = getHeight();
        if (h <= 0) return true;
        float y = Math.max(0, Math.min(h, e.getY()));
        hue = y / h * 360f;
        invalidate();
        if (onChanged != null) onChanged.run();
        return true;
    }
}

/** 调色盘回调 */
    interface ColorPickListener2 {
        void onPick(int color);
    }

    /** 开发中功能入口（点击提示"开发中"） */
    private static void addDevNav(final Context ctx, final LinearLayout parent,
            final int textColor, final int subColor, final int divColor,
            final String icon, final String name, final String desc) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 8);
        parent.addView(navRow(ctx, icon, name, desc + "（开发中）", textColor, subColor, divColor, C_GRAY, new Runnable() {
            public void run() {
                toast(ctx, "「" + name + "」开发中，敬请期待");
            }
        }), lp);
    }

    /** 卡密激活对话框（v310） */
    static void showLicenseDialog(final Context ctx, final Runnable refresh) {
        try {
            if (DsLicense.isActivated(ctx)) {
                new android.app.AlertDialog.Builder(ctx)
                        .setTitle("卡密激活")
                        .setMessage("已激活（永久）\n卡密：" + DsLicense.currentCode())
                        .setNegativeButton("关闭", null)
                        .show();
                return;
            }
            final android.widget.EditText et = new android.widget.EditText(ctx);
            et.setHint("输入卡密");
            et.setSingleLine(true);
            new android.app.AlertDialog.Builder(ctx)
                    .setTitle("卡密激活")
                    .setMessage("请输入卡密解锁全部功能")
                    .setView(et)
                    .setPositiveButton("激活", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            String code = et.getText().toString();
                            if (DsLicense.activate(ctx, code)) {
                                toast(ctx, "激活成功！功能已解锁");
                                if (refresh != null) refresh.run();
                            } else {
                                toast(ctx, "卡密错误，请重试");
                            }
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Throwable t) {
            toast(ctx, "无法打开激活窗口: " + t);
        }
    }

    /** v174：处理聊天壁纸选图回调 */
    static void handleChatBgResult(final Activity activity, final int resultCode, final android.content.Intent data) {
        try {
            if (resultCode != Activity.RESULT_OK || data == null) return;
            android.net.Uri uri = data.getData();
            if (uri == null) return;
            java.io.InputStream in = activity.getContentResolver().openInputStream(uri);
            if (in == null) return;
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            in.close();
            java.io.File dir = new java.io.File(DsConfig.dataPath("files"));
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, "ds_chatbg.png");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(out.toByteArray());
            fos.close();
            DsConfig.setChatBgPath(activity, f.getAbsolutePath());
            DsConfig.setChatBgOn(activity, true);
            DsOverlay.apply(activity);
            toast(activity, "聊天壁纸已设置");
        } catch (Throwable t) {
            toast(activity, "设置失败");
        }
    }

    /** 构建「输入框提示色」配置页内容 */
    static void buildHintConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        final TextView[] hueH = new TextView[1];
        hueH[0] = cardSelectRow(ctx, card1, "提示文字颜色", colorName(DsConfig.hintColor(ctx)), new Runnable() {
            public void run() {
                int c = DsConfig.hintColor(ctx);
                int[] seq = {0, 0xFF4D6BFE, 0xFF55CCAA, 0xFFFF8A8A, 0xFFFFCC66};
                int next = 0;
                for (int i = 0; i < seq.length; i++) {
                    if (seq[i] == c) next = (i + 1) % seq.length;
                }
                DsConfig.setHintColor(ctx, seq[next]);
                hueH[0].setText(colorName(seq[next]));
                refresh.run();
            }
        });
        cardNote(ctx, card1, "提示文字：输入框占位文字的颜色");

        // v166：输入文字颜色
        LinearLayout card2 = configCard(ctx, content);
        final TextView[] inputH = new TextView[1];
        inputH[0] = cardSelectRow(ctx, card2, "输入文字颜色", colorName(DsConfig.inputTextColor(ctx)), new Runnable() {
            public void run() {
                int c = DsConfig.inputTextColor(ctx);
                int[] seq = {0, 0xFF4D6BFE, 0xFF55CCAA, 0xFFFF8A8A, 0xFFFFCC66, 0xFFFFFFFF};
                int next = 0;
                for (int i = 0; i < seq.length; i++) {
                    if (seq[i] == c) next = (i + 1) % seq.length;
                }
                DsConfig.setInputTextColor(ctx, seq[next]);
                inputH[0].setText(colorName(seq[next]));
                refresh.run();
            }
        });

        // v166：光标颜色
        LinearLayout card3 = configCard(ctx, content);
        final TextView[] cursorH = new TextView[1];
        cursorH[0] = cardSelectRow(ctx, card3, "光标颜色", colorName(DsConfig.cursorColor(ctx)), new Runnable() {
            public void run() {
                int c = DsConfig.cursorColor(ctx);
                int[] seq = {0, 0xFF4D6BFE, 0xFF55CCAA, 0xFFFF8A8A, 0xFFFFCC66, 0xFFFFFFFF};
                int next = 0;
                for (int i = 0; i < seq.length; i++) {
                    if (seq[i] == c) next = (i + 1) % seq.length;
                }
                DsConfig.setCursorColor(ctx, seq[next]);
                cursorH[0].setText(colorName(seq[next]));
                refresh.run();
            }
        });
        cardNote(ctx, card3, "文字/光标颜色：修改后重新进入输入框生效");
    }

    /** 构建「灵动岛设置」配置页内容 */
    /** v375：构建「AI 一问多答」配置页内容 */
    static void buildMultiReplyConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        cardSwitchRow(ctx, card1, "开启一问多答", DsMultiReply.enabled(ctx), new Runnable() {
            public void run() {
                DsMultiReply.setEnabled(ctx, !DsMultiReply.enabled(ctx));
                refresh.run();
            }
        });
        cardNote(ctx, card1, "开启后，每次提问 AI 会自动回答 2-3 次（自动重发原问题），适合需要多版本答案的场景。");
        LinearLayout card2 = configCard(ctx, content);
        final String[] countNames = {"2 次", "3 次"};
        final TextView[] countH = new TextView[1];
        countH[0] = cardSelectRow(ctx, card2, "总回答次数", countNames[DsMultiReply.count(ctx) - 2], new Runnable() {
            public void run() {
                int next = DsMultiReply.count(ctx) >= 3 ? 2 : DsMultiReply.count(ctx) + 1;
                DsMultiReply.setCount(ctx, next);
                countH[0].setText(countNames[next - 2]);
            }
        });
        cardNote(ctx, card2, "设置 AI 自动回答的总次数（含首次回答）。");
    }
    static void buildBallConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        // v365：悬浮窗总开关（默认关闭）
        LinearLayout card0 = configCard(ctx, content);
        cardSwitchRow(ctx, card0, "启用悬浮窗", DsConfig.floatOn(ctx), new Runnable() {
            public void run() {
                DsConfig.setFloatOn(ctx, !DsConfig.floatOn(ctx));
                refresh.run();
            }
        });
        cardNote(ctx, card0, "开启后显示悬浮球，可随时打开面板；关闭后隐藏。");
        LinearLayout card1 = configCard(ctx, content);
        final String[] alignNamesF = {"偏左", "居中", "偏右"};
        final TextView[] alignH = new TextView[1];
        alignH[0] = cardSelectRow(ctx, card1, "水平位置", alignNamesF[DsConfig.ballAlign(ctx) % 3], new Runnable() {
            public void run() {
                int next = (DsConfig.ballAlign(ctx) + 1) % 3;
                DsConfig.setBallAlign(ctx, next);
                alignH[0].setText(alignNamesF[next]);
                refresh.run();
            }
        });
        LinearLayout card2 = configCard(ctx, content);
        cardSeekRow(ctx, card2, "垂直位置", 380, Math.max(0, DsConfig.ballOffsetY(ctx) - 20), 20, "", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setBallOffsetY(ctx, v + 20);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        cardSeekRow(ctx, card2, "透明度", 80, DsConfig.ballAlpha(ctx) - 20, 20, "%", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setBallAlpha(ctx, v + 20);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        // 聊天入口按钮开关（v243 自绘按钮）
        LinearLayout card3 = configCard(ctx, content);
        cardSwitchRow(ctx, card3, "聊天入口按钮", DsConfig.chatBtnOn(ctx), new Runnable() {
            public void run() {
                DsConfig.setChatBtnOn(ctx, !DsConfig.chatBtnOn(ctx));
                refresh.run();
            }
        });
        cardNote(ctx, card3, "在 DeepSeek 顶部工具栏添加 💬 按钮，点击直接打开聊天页");
    }

    /** 构建「面板设置」配置页内容 */
    static void buildPanelConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        final String[] themeNamesF = {"跟随系统", "深色", "浅色"};
        final TextView[] themeH = new TextView[1];
        themeH[0] = cardSelectRow(ctx, card1, "面板主题", themeNamesF[DsConfig.panelTheme(ctx) % 3], new Runnable() {
            public void run() {
                int next = (DsConfig.panelTheme(ctx) + 1) % 3;
                DsConfig.setPanelTheme(ctx, next);
                themeH[0].setText(themeNamesF[next]);
                refresh.run();
            }
        });
        cardNote(ctx, card1, "主题切换需重新打开面板生效");
        LinearLayout card2 = configCard(ctx, content);
        cardSeekRow(ctx, card2, "面板圆角", 32, DsConfig.panelRadius(ctx), 0, "", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setPanelRadius(ctx, v);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    /** 构建「气泡外观」配置页内容 —— v459：三段式排版 + 效果互斥 */
    static void buildBubbleConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {

        // ═══ 第一段：总开关 ═══
        LinearLayout card0 = configCard(ctx, content);
        cardSwitchRow(ctx, card0, "气泡美化", DsConfig.bubbleOn(ctx), new Runnable() {
            public void run() {
                DsConfig.setBubbleOn(ctx, !DsConfig.bubbleOn(ctx));
                refresh.run();
            }
        });
        cardNote(ctx, card0, "控制 AI/用户消息气泡的样式、颜色与圆角（关闭后全部气泡美化失效）");
        cardSwitchRow(ctx, card0, "样式统一", DsConfig.unifyBubble(ctx), new Runnable() {
            public void run() { DsConfig.setUnifyBubble(ctx, !DsConfig.unifyBubble(ctx)); }
        });
        cardNote(ctx, card0, "AI 与用户气泡强制使用同一种颜色样式");

        // ═══ 第二段：气泡效果（互斥单选）═══
        // 渐变 / 液态玻璃 —— 互斥，开一个自动关另一个（只更新 Switch 状态，不重建页面）
        LinearLayout cardFx = configCard(ctx, content);
        content.addView(fxSectionTitle(ctx, "气泡效果（三选一）", C_BLUE));
        // 用于互斥联动的引用（先声明后赋值）
        final android.widget.Switch[] gradSw = new android.widget.Switch[1];
        final android.widget.Switch[] glassSw = new android.widget.Switch[1];
        // ② 渐变
        gradSw[0] = cardSwitchRow(ctx, cardFx, "✦ 渐变气泡", DsConfig.gradientBubbleOn(ctx), new Runnable() {
            public void run() {
                boolean next = !DsConfig.gradientBubbleOn(ctx);
                if (next) {  // 开启渐变 → 关掉玻璃
                    DsConfig.setGlassBubbleOn(ctx, false);
                    DsConfig.setBubbleStyle(ctx, 0);
                    if (glassSw[0] != null) glassSw[0].setChecked(false);   // 只改玻璃开关状态
                }
                DsConfig.setGradientBubbleOn(ctx, next);
            }
        }, null);
        final String[] gradStyleNames = {"左上→右下", "上下", "左右", "径向"};
        final TextView[] gradStyleH = new TextView[1];
        gradStyleH[0] = cardSelectRow(ctx, cardFx, "渐变方向", gradStyleNames[DsConfig.gradientStyle(ctx) % 4], new Runnable() {
            public void run() {
                int next = (DsConfig.gradientStyle(ctx) + 1) % 4;
                DsConfig.setGradientStyle(ctx, next);
                gradStyleH[0].setText(gradStyleNames[next]);
            }
        });
        final String[] gradPalNames = {"品牌蓝", "紫色", "青绿", "炫彩", "粉紫", "暖橙", "自定义"};
        final TextView[] gradPalH = new TextView[1];
        gradPalH[0] = cardSelectRow(ctx, cardFx, "渐变配色", gradPalNames[DsConfig.gradientPalette(ctx) % 7], new Runnable() {
            public void run() {
                int next = (DsConfig.gradientPalette(ctx) + 1) % 7;
                DsConfig.setGradientPalette(ctx, next);
                gradPalH[0].setText(gradPalNames[next]);
                if (next == 6) {
                    showPaletteDialog(ctx, "渐变起始色", 0xFF4D6BFE, new ColorPickListener2() {
                        public void onPick(int color) {
                            DsConfig.setGradientCustom1(ctx, color);
                        }
                    });
                }
            }
        });
        // ③ 液态玻璃
        glassSw[0] = cardSwitchRow(ctx, cardFx, "◈ 液态玻璃", DsConfig.glassBubbleOn(ctx) || DsConfig.bubbleStyle(ctx) == 3, new Runnable() {
            public void run() {
                boolean next = !(DsConfig.glassBubbleOn(ctx) || DsConfig.bubbleStyle(ctx) == 3);
                if (next) {  // 开启玻璃 → 关掉渐变
                    DsConfig.setGradientBubbleOn(ctx, false);
                    DsConfig.setGlassBubbleOn(ctx, true);
                    DsConfig.setBubbleStyle(ctx, 3);
                    if (gradSw[0] != null) gradSw[0].setChecked(false);  // 只改渐变开关状态
                } else {
                    DsConfig.setGlassBubbleOn(ctx, false);
                    DsConfig.setBubbleStyle(ctx, 0);
                }
            }
        }, null);
        cardNote(ctx, cardFx, "互斥：开启渐变或玻璃，另一个自动关闭");

        // ═══ 第三段：气泡颜色 ═══
        LinearLayout cardColor = configCard(ctx, content);
        content.addView(fxSectionTitle(ctx, "气泡颜色", C_BLUE));
        // 用户气泡颜色
        final String[] colorNamesF = {"跟随样式", "品牌蓝", "深蓝", "青绿", "紫色", "粉色", "橙色", "自定义"};
        final TextView[] colorH = new TextView[1];
        colorH[0] = cardSelectRow(ctx, cardColor, "用户气泡", colorNamesF[DsConfig.bubbleColor(ctx) % 8], new Runnable() {
            public void run() {
                int next = (DsConfig.bubbleColor(ctx) + 1) % 8;
                DsConfig.setBubbleColor(ctx, next);
                colorH[0].setText(colorNamesF[next]);
                if (next == 7) {
                    showPaletteDialog(ctx, "用户气泡颜色", DsConfig.bubbleCustomColor(ctx), new ColorPickListener2() {
                        public void onPick(int color) {
                            DsConfig.setBubbleCustomColor(ctx, color);
                            toast(ctx, "气泡颜色已自定义");
                            refresh.run();
                        }
                    });
                }
                refresh.run();
            }
        });
        // AI 气泡颜色
        final String[] aiColorNamesF = {"浅灰", "品牌蓝", "深蓝", "青绿", "紫色", "粉色", "橙色", "自定义"};
        final TextView[] aiColorH = new TextView[1];
        aiColorH[0] = cardSelectRow(ctx, cardColor, "AI气泡", aiColorNamesF[DsConfig.aiBubbleColor(ctx) % 8], new Runnable() {
            public void run() {
                int next = (DsConfig.aiBubbleColor(ctx) + 1) % 8;
                DsConfig.setAiBubbleColor(ctx, next);
                aiColorH[0].setText(aiColorNamesF[next]);
                if (next == 7) {
                    showPaletteDialog(ctx, "AI气泡颜色", DsConfig.aiBubbleCustomColor(ctx), new ColorPickListener2() {
                        public void onPick(int color) {
                            DsConfig.setAiBubbleCustomColor(ctx, color);
                            toast(ctx, "AI气泡颜色已自定义");
                            refresh.run();
                        }
                    });
                }
                refresh.run();
            }
        });
        // 消息文字颜色
        final String[] textColorNamesF = {"自动", "黑色", "白色", "品牌蓝", "深灰", "红色", "绿色", "自定义"};
        final TextView[] textColorH = new TextView[1];
        textColorH[0] = cardSelectRow(ctx, cardColor, "文字颜色", textColorNamesF[DsConfig.msgTextColor(ctx) % 8], new Runnable() {
            public void run() {
                int next = (DsConfig.msgTextColor(ctx) + 1) % 8;
                DsConfig.setMsgTextColor(ctx, next);
                textColorH[0].setText(textColorNamesF[next]);
                if (next == 7) {
                    showPaletteDialog(ctx, "消息文字颜色", DsConfig.msgCustomColor(ctx), new ColorPickListener2() {
                        public void onPick(int color) {
                            DsConfig.setMsgCustomColor(ctx, color);
                            toast(ctx, "文字颜色已自定义");
                            refresh.run();
                        }
                    });
                }
                refresh.run();
            }
        });

        // ═══ 第四段：尺寸与透明度 ═══
        LinearLayout cardSize = configCard(ctx, content);
        content.addView(fxSectionTitle(ctx, "尺寸与透明度", C_BLUE));
        cardSeekRow(ctx, cardSize, "气泡圆角", 32, DsConfig.bubbleRadius(ctx), 0, "", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setBubbleRadius(ctx, v);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        cardSeekRow(ctx, cardSize, "气泡透明度", 70, DsConfig.bubbleAlpha(ctx) - 30, 30, "%", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setBubbleAlpha(ctx, v + 30);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        cardSeekRow(ctx, cardSize, "消息字体大小", 12, DsConfig.msgFontSize(ctx) - 12, 12, "", new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setMsgFontSize(ctx, v + 12);
                refresh.run();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    /** 构建「聊天增强」配置页内容 */
    static void buildChatEnhanceConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        // ── 记录管理 ──
        content.addView(fxSectionTitle(ctx, "记录管理", C_BLUE));
        LinearLayout cardData = fxCard(ctx, content, C_BLUE);
        fxListItem(ctx, cardData, "✏️", C_BLUE, "编辑消息内容", "修改已发送的聊天文字", new Runnable() {
            public void run() {
                try { DsChatEditor.show(activity); } catch (Throwable t) { toast(ctx, "编辑器异常: " + t); }
            }
        });
        fxDivider(ctx, cardData);
        fxListItem(ctx, cardData, "🗑", C_RED, "批量删除会话", "一次性移除多个会话", new Runnable() {
            public void run() {
                try { DsChatEditor.deleteSessions(activity); } catch (Throwable t) { toast(ctx, "删除异常: " + t); }
            }
        });
        fxDivider(ctx, cardData);
        fxListItem(ctx, cardData, "🔍", C_CYAN, "搜索聊天记录", "按关键词查找消息", new Runnable() {
            public void run() {
                try { DsChatSearch.show(activity); } catch (Throwable t) { toast(ctx, "搜索异常: " + t); }
            }
        });
        // ── 备份 ──
        content.addView(fxSectionTitle(ctx, "备份", C_CYAN));
        LinearLayout cardBackup = fxCard(ctx, content, C_CYAN);
        fxListItem(ctx, cardBackup, "💾", C_CYAN, "备份选中的会话", "手动导出指定会话", new Runnable() {
            public void run() {
                try { DsChatEditor.backupSessions(activity); } catch (Throwable t) { toast(ctx, "备份异常: " + t); }
            }
        });
        fxDivider(ctx, cardBackup);
        cardSwitchRow(ctx, cardBackup, "自动备份聊天记录", DsConfig.autoBackup(ctx), new Runnable() {
            public void run() {
                DsConfig.setAutoBackup(ctx, !DsConfig.autoBackup(ctx));
                refresh.run();
            }
        });
        // ── 隐私 ──
        content.addView(fxSectionTitle(ctx, "隐私", C_PURPLE));
        LinearLayout cardPrivacy = fxCard(ctx, content, C_PURPLE);
        cardSwitchRow(ctx, cardPrivacy, "去除安全审查", DsConfig.removeCensor(ctx), new Runnable() {
            public void run() {
                DsConfig.setRemoveCensor(ctx, !DsConfig.removeCensor(ctx));
                refresh.run();
            }
        });
        fxDivider(ctx, cardPrivacy);
        cardSwitchRow(ctx, cardPrivacy, "不把数据用于优化", DsConfig.disableTraining(ctx), new Runnable() {
            public void run() {
                DsConfig.setDisableTraining(ctx, !DsConfig.disableTraining(ctx));
                refresh.run();
            }
        });
        // ── 实验 ──
        content.addView(fxSectionTitle(ctx, "实验", C_GRAY));
        LinearLayout cardExp = fxCard(ctx, content, C_GRAY);
        cardSwitchRow(ctx, cardExp, "AI 心跳（开发中）", DsConfig.aiHeartbeat(ctx), new Runnable() {
            public void run() {
                DsConfig.setAiHeartbeat(ctx, !DsConfig.aiHeartbeat(ctx));
                refresh.run();
            }
        });
        fxDivider(ctx, cardExp);
        cardSwitchRow(ctx, cardExp, "本地禁言（开发中）", DsConfig.localMute(ctx), new Runnable() {
            public void run() {
                DsConfig.setLocalMute(ctx, !DsConfig.localMute(ctx));
                refresh.run();
            }
        });
    }

    /** 构建「缓存清理」配置页内容 */
    static void buildCleanConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        final String[] cleanNamesF = {"关闭", "启动时", "每天"};
        final TextView[] cleanH = new TextView[1];
        cleanH[0] = cardSelectRow(ctx, card1, "自动清理策略", cleanNamesF[DsConfig.autoClean(ctx) % 3], new Runnable() {
            public void run() {
                int next = (DsConfig.autoClean(ctx) + 1) % 3;
                DsConfig.setAutoClean(ctx, next);
                cleanH[0].setText(cleanNamesF[next]);
                refresh.run();
            }
        });
        LinearLayout card2 = configCard(ctx, content);
        cardButton(ctx, card2, "🧹  立即清理缓存", C_CYAN, new Runnable() {
            public void run() {
                try {
                    long freed = 0;
                    File cacheDir = new File(DsConfig.dataPath("cache"));
                    if (cacheDir.exists()) {
                        freed += deleteDir(cacheDir);
                    }
                    File filesDir = new File(DsConfig.dataPath("files/coil"));
                    if (filesDir.exists()) {
                        freed += deleteDir(filesDir);
                    }
                    String msg;
                    if (freed > 0) {
                        String size;
                        if (freed > 1024 * 1024) size = String.format("%.1f MB", freed / 1024f / 1024f);
                        else if (freed > 1024) size = String.format("%.0f KB", freed / 1024f);
                        else size = freed + " B";
                        msg = "已清理 " + size;
                    } else {
                        msg = "缓存已很干净";
                    }
                    toast(ctx, msg);
                    refresh.run();
                } catch (Throwable t) {
                    toast(ctx, "清理失败");
                }
            }
        });
        cardNote(ctx, card2, "清理缓存与图片缓存，不影响聊天记录");
    }

    /** 构建「聊天备份」配置页内容 */
    static void buildBackupConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        cardSwitchRow(ctx, card1, "自动备份聊天数据库", DsConfig.autoBackup(ctx), new Runnable() {
            public void run() {
                DsConfig.setAutoBackup(ctx, !DsConfig.autoBackup(ctx));
                refresh.run();
            }
        });
        cardButton(ctx, card1, "💾  立即备份聊天数据库", C_BLUE, new Runnable() {
            public void run() {
                try {
                    File dbDir = new File(DsConfig.dataPath("databases"));
                    File backupDir = new File(DsConfig.dataPath("files/ds_backup"));
                    if (!backupDir.exists()) backupDir.mkdirs();
                    int count = 0;
                    File[] dbs = dbDir.listFiles();
                    if (dbs != null) {
                        for (File db : dbs) {
                            if (db.getName().endsWith(".db")) {
                                try {
                                    java.io.FileInputStream in = new java.io.FileInputStream(db);
                                    java.io.FileOutputStream out = new java.io.FileOutputStream(new File(backupDir, db.getName()));
                                    byte[] buf = new byte[8192];
                                    int len;
                                    while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                                    out.flush(); out.close(); in.close();
                                    count++;
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                    toast(ctx, count > 0 ? "已备份 " + count + " 个数据库" : "没有可备份的数据库");
                } catch (Throwable t) {
                    toast(ctx, "备份失败");
                }
            }
        });
        cardNote(ctx, card1, "备份保存在 files/ds_backup/，纯文件复制，安全无副作用");
    }

    /** 构建「设备与进程」配置页内容 */
    static void buildDeviceConfig(final Context ctx, final LinearLayout content,
            final Activity activity, final int textColor, final int subColor,
            final int divColor, final Runnable refresh) {
        LinearLayout card1 = configCard(ctx, content);
        TextView devTv = new TextView(ctx);
        try {
            android.os.StatFs sf = new android.os.StatFs("/data");
            long totalBytes = sf.getTotalBytes();
            long availBytes = sf.getAvailableBytes();
            java.lang.Runtime rt = Runtime.getRuntime();
            long memMb = rt.totalMemory() / 1024 / 1024;
            long freeMb = rt.freeMemory() / 1024 / 1024;
            java.text.DecimalFormat df = new java.text.DecimalFormat("0.0");
            devTv.setText(
                "型号: " + android.os.Build.MODEL + "\n" +
                "系统: Android " + android.os.Build.VERSION.RELEASE + "\n" +
                "运行内存: " + memMb + " MB / 空闲 " + freeMb + " MB\n" +
                "存储: 总 " + df.format(totalBytes / 1073741824.0) + " GB / 可用 " + df.format(availBytes / 1073741824.0) + " GB");
        } catch (Throwable t) {
            devTv.setText("设备信息获取失败");
        }
        devTv.setTextColor(sPanelDark ? 0xFFC8C8CC : 0xFF444444);
        devTv.setTextSize(12);
        devTv.setLineSpacing(dp(ctx, 3), 1f);
        devTv.setGravity(Gravity.START);
        devTv.setPadding(0, dp(ctx, 10), 0, dp(ctx, 10));
        card1.addView(devTv);

        LinearLayout card2 = configCard(ctx, content);
        final TextView procTv = new TextView(ctx);
        procTv.setText("正在获取...");
        procTv.setTextColor(sPanelDark ? 0xFFC8C8CC : 0xFF444444);
        procTv.setTextSize(13);
        procTv.setGravity(Gravity.START);
        procTv.setPadding(0, dp(ctx, 10), 0, dp(ctx, 4));
        card2.addView(procTv);
        cardButton(ctx, card2, "🔄  刷新进程信息", C_CYAN, new Runnable() {
            public void run() {
                try {
                    java.util.List<String> lines = new java.util.ArrayList<String>();
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "ps -A | grep deepseek"});
                        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                        String line;
                        while ((line = r.readLine()) != null) lines.add(line);
                        p.waitFor();
                    } catch (Throwable ignored) {}
                    if (lines.isEmpty()) {
                        procTv.setText("DeepSeek 进程：未运行");
                    } else {
                        StringBuilder sb = new StringBuilder("DeepSeek 进程运行中\n");
                        for (String l : lines) {
                            String[] parts = l.trim().split("\\s+");
                            if (parts.length >= 5) {
                                sb.append("PID ").append(parts[1]).append(" · RSS ").append(parts[4]).append("K\n");
                            }
                        }
                        procTv.setText(sb.toString().trim());
                    }
                } catch (Throwable t) {
                    procTv.setText("获取失败");
                }
            }
        });
        // 首次自动刷新
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "ps -A | grep deepseek"});
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            java.util.List<String> lines = new java.util.ArrayList<String>();
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
            if (lines.isEmpty()) procTv.setText("DeepSeek 进程：未运行");
            else procTv.setText("DeepSeek 进程运行中 (" + lines.size() + " 个)");
        } catch (Throwable ignored) {}
    }

    /** 展开指定分类卡片并滚动过去 */
    private static void openCategory(final LinearLayout body, final TextView arrowHolder,
            final android.widget.ScrollView scroll, final View card) {
        try {
            if (body != null && body.getVisibility() != View.VISIBLE) {
                body.setVisibility(View.VISIBLE);
                if (arrowHolder != null) arrowHolder.setText("▴");
            }
            if (scroll != null && card != null) {
                scroll.post(new Runnable() {
                    public void run() {
                        try { scroll.smoothScrollTo(0, card.getTop() - dp(scroll.getContext(), 16)); }
                        catch (Throwable ignored) {}
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    /** 底部控制栏按钮：液态玻璃胶囊小按钮（带图标文字 + 开关态高亮） */
    private static TextView bottomBtn(Context ctx, boolean dark, String label, boolean active,
                                      final Runnable onClick) {
        TextView b = new TextView(ctx);
        b.setText(label);
        b.setTextSize(12);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
        // 液态玻璃胶囊底
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                active
                        ? (dark ? new int[]{0x664D6BFE, 0x334D6BFE} : new int[]{0xCC4D6BFE, 0x994D6BFE})
                        : (dark ? new int[]{0x333A3A3E, 0x222A2A2E} : new int[]{0x22FFFFFF, 0x11EEF0F3}));
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(ctx, 16));
        g.setStroke(dp(ctx, 1), dark ? 0x33FFFFFF : 0x44FFFFFF);
        b.setBackgroundDrawable(g);
        b.setTextColor(active ? 0xFFFFFFFF : (dark ? 0xFFB0B0B6 : 0xFF6A6A70));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onClick.run(); }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(ctx, 4);
        lp.rightMargin = dp(ctx, 4);
        b.setLayoutParams(lp);
        return b;
    }

    private static TextView sectionTitle(Context ctx, String t) {
        // v449：分组标题 —— 浅色模式黑色、深色模式浅色（不再半透明灰）
        TextView v = new TextView(ctx);
        v.setText(t);
        v.setTextColor(sPanelDark ? 0xFFE8E8E8 : 0xFF111111);
        v.setTextSize(11);
        v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        v.setLetterSpacing(0.12f);
        v.setPadding(dp(ctx, 4), dp(ctx, 14), 0, dp(ctx, 6));
        return v;
    }

    /** v149：标签页切换（目标页淡入动画） */
    private static void switchPage(View[] pages, int idx) {
        for (int i = 0; i < pages.length; i++) {
            View p = pages[i];
            if (i == idx) {
                if (p.getVisibility() != View.VISIBLE) {
                    p.animate().cancel();
                    p.setAlpha(0f);
                    p.setVisibility(View.VISIBLE);
                    p.animate().alpha(1f).setDuration(150).start();
                } else {
                    p.setAlpha(1f);
                }
            } else {
                p.animate().cancel();
                p.setAlpha(1f);
                p.setVisibility(View.GONE);
            }
        }
    }

    private static View iOSSwitch(Context ctx, int textColor, String label, boolean checked, final Runnable onToggle) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(ctx, 8), 0, dp(ctx, 8));

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(textColor);
        tv.setTextSize(16);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv);

        Switch sw = new Switch(ctx);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                onToggle.run();
            }
        });
        row.addView(sw);
        return row;
    }

    private static TextView iOSLabel(Context ctx, int color, String t) {
        TextView v = new TextView(ctx);
        v.setText(t);
        v.setTextColor(color);
        v.setTextSize(13);
        v.setPadding(0, dp(ctx, 12), 0, dp(ctx, 2));
        return v;
    }

    private static SeekBar iOSSeek(Context ctx, int max, int prog, SeekBar.OnSeekBarChangeListener l) {
        SeekBar s = new SeekBar(ctx);
        s.setMax(max);
        s.setProgress(prog);
        s.setOnSeekBarChangeListener(l);
        return s;
    }

    private static String colorName(int c) {
        if (c == 0) return "默认";
        if (c == 0xFF4D6BFE) return "蓝";
        if (c == 0xFF55CCAA) return "青";
        if (c == 0xFFFF8A8A) return "红";
        if (c == 0xFFFFCC66) return "黄";
        return "自定义";
    }

    private static String modeName(int m) {
        if (m == 1) return "适应";
        if (m == 2) return "拉伸";
        return "裁剪";
    }

    private static String tintName(int t) {
        if (t == 1) return "暖色";
        if (t == 2) return "冷色";
        if (t == 3) return "青绿";
        if (t == 4) return "粉紫";
        return "默认";
    }

    /** 构建底部独立玻璃 dock（5 个快捷按钮横排） */
    private static View buildDock(final Context ctx, final boolean dark,
                                   final Runnable onHomeClick) {
        // 玻璃容器
        final LinearLayout dock = new LinearLayout(ctx);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6));
        final int dockRadius = dp(ctx, 24);
        // 玻璃背景：半透明渐变 + 高光边
        GradientDrawable glassBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0xE6333338, 0xB3222226, 0x991A1A1E}
                     : new int[]{0xF2FFFFFF, 0xD9FFFFFF, 0xCCF0F2F5});
        glassBg.setShape(GradientDrawable.RECTANGLE);
        glassBg.setCornerRadius(dockRadius);
        glassBg.setStroke(dp(ctx, 1), dark ? 0x66FFFFFF : 0x44FFFFFF);
        dock.setBackgroundDrawable(glassBg);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            dock.setElevation(dp(ctx, 10));
            dock.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        }

        // dock = 分类导航（点击 → 打开面板 + 滚到对应分类）
        // section: -1=顶部 0=背景 1=聊天 2=更多
        dock.addView(dockBtn(ctx, dark, "主页", 0, true, onHomeClick));
        dock.addView(dockBtn(ctx, dark, "背景", 0, false, new Runnable() {
            public void run() {
                showPanelAt((Activity) ctx, 1);
            }
        }));
        dock.addView(dockBtn(ctx, dark, "聊天", 1, false, new Runnable() {
            public void run() {
                showPanelAt((Activity) ctx, 2);
            }
        }));
        dock.addView(dockBtn(ctx, dark, "更多", 2, false, new Runnable() {
            public void run() {
                showPanelAt((Activity) ctx, 3);
            }
        }));

        return dock;
    }

    /** dock 按钮：纯文字分类标签（液态玻璃胶囊） */
    private static View dockBtn(final Context ctx, final boolean dark,
                                 final String label, final int section,
                                 final boolean highlighted, final Runnable onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(label);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(13);
        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btn.setTextColor(highlighted ? 0xFF4D6BFE : (dark ? 0xFFE6E6E6 : 0xFF333333));
        btn.setPadding(dp(ctx, 14), dp(ctx, 9), dp(ctx, 14), dp(ctx, 9));

        // 液态玻璃胶囊背景
        GradientDrawable b = new GradientDrawable();
        if (highlighted) {
            b.setShape(GradientDrawable.RECTANGLE);
            b.setCornerRadius(dp(ctx, 18));
            b.setColor(dark ? 0x884D6BFE : 0x334D6BFE);
            b.setStroke(dp(ctx, 1), 0x884D6BFE);
        } else {
            b.setShape(GradientDrawable.RECTANGLE);
            b.setCornerRadius(dp(ctx, 18));
            b.setColor(dark ? 0x40FFFFFF : 0x33000000);
        }
        btn.setBackgroundDrawable(b);

        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onClick.run(); }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(ctx, 3);
        lp.rightMargin = dp(ctx, 3);
        btn.setLayoutParams(lp);
        return btn;
    }

    private static View panelTab(final Context ctx, final boolean dark,
                                 final String icon, final String label, final int section,
                                 final boolean highlighted, final Runnable onClick) {
        final LinearLayout tab = new LinearLayout(ctx);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setTag(label);  // 用 tag 存标签名，供 selectTab 切换选中态

        TextView iconTv = new TextView(ctx);
        iconTv.setText(icon);
        iconTv.setTextSize(16);
        iconTv.setGravity(Gravity.CENTER);
        iconTv.setIncludeFontPadding(false);
        tab.addView(iconTv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView labelTv = new TextView(ctx);
        labelTv.setText(label);
        labelTv.setTextSize(10);
        labelTv.setGravity(Gravity.CENTER);
        labelTv.setIncludeFontPadding(false);
        labelTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(ctx, 1);
        tab.addView(labelTv, llp);

        applyTabStyle(tab, dark, highlighted);

        tab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // v151：点击回弹动效
                final View tv = v;
                tv.animate().cancel();
                tv.setScaleX(1f); tv.setScaleY(1f);
                tv.animate().scaleX(1.1f).scaleY(1.1f).setDuration(90)
                        .withEndAction(new Runnable() {
                            public void run() {
                                tv.animate().scaleX(1f).scaleY(1f).setDuration(150)
                                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                        .start();
                            }
                        }).start();
                onClick.run();
            }
        });

        // v151：等宽均分（weight=1），高度 48dp + 上下 margin 8dp = 64dp 拼满大胶囊
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, dp(ctx, 48), 1f);
        lp.topMargin = dp(ctx, 8);
        lp.bottomMargin = dp(ctx, 8);
        lp.leftMargin = dp(ctx, 2);
        lp.rightMargin = dp(ctx, 2);
        tab.setLayoutParams(lp);
        return tab;
    }

    /** 选中指定标签，其他标签取消高亮 */
    private static void selectTab(LinearLayout bar, boolean dark, String label) {
        try {
            for (int i = 0; i < bar.getChildCount(); i++) {
                View v = bar.getChildAt(i);
                Object tag = v.getTag();
                if (tag instanceof String) {
                    boolean sel = label.equals(tag);
                    applyTabStyle(v, dark, sel);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 应用标签样式（v151）：选中=品牌蓝渐变胶囊+发光；未选中=透明弱化 */
    private static void applyTabStyle(View tab, boolean dark, boolean selected) {
        try {
            final Context ctx = tab.getContext();
            if (!(tab instanceof LinearLayout)) return;
            LinearLayout ll = (LinearLayout) tab;
            if (ll.getChildCount() < 2) return;
            View iconV = ll.getChildAt(0);
            View labelV = ll.getChildAt(1);

            if (selected) {
                GradientDrawable core = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFF6A85FF, 0xFF4D6BFE, 0xFF3A5AF0});
                core.setShape(GradientDrawable.RECTANGLE);
                core.setCornerRadius(dp(ctx, 16));
                core.setStroke(dp(ctx, 1), 0xE6FFFFFF);
                GradientDrawable glow = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        dark ? new int[]{0x664D6BFE, 0x334D6BFE, 0x1A4D6BFE}
                             : new int[]{0x804D6BFE, 0x404D6BFE, 0x204D6BFE});
                glow.setShape(GradientDrawable.RECTANGLE);
                glow.setCornerRadius(dp(ctx, 18));
                LayerDrawable layers = new LayerDrawable(new Drawable[]{glow, core});
                layers.setLayerInset(0, -dp(ctx, 2), -dp(ctx, 2), -dp(ctx, 2), -dp(ctx, 2));
                ll.setBackgroundDrawable(layers);
                ll.setElevation(dp(ctx, 10));
                ll.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
                ll.setTranslationZ(dp(ctx, 6));
                if (iconV instanceof TextView) { iconV.setAlpha(1f); }
                if (labelV instanceof TextView) {
                    ((TextView) labelV).setTextColor(0xFFFFFFFF);
                    labelV.setAlpha(1f);
                }
            } else {
                ll.setBackgroundDrawable(null);
                ll.setElevation(0);
                ll.setTranslationZ(0f);
                if (iconV instanceof TextView) { iconV.setAlpha(0.6f); }
                if (labelV instanceof TextView) {
                    ((TextView) labelV).setTextColor(dark ? 0xFFC8C8CC : 0xFF444444);
                    labelV.setAlpha(0.9f);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 递归删除目录，返回释放的字节数 */
    private static long deleteDir(File dir) {
        long freed = 0;
        try {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory()) {
                        freed += deleteDir(child);
                    } else {
                        freed += child.length();
                        child.delete();
                    }
                }
            }
            dir.delete();
        } catch (Throwable ignored) {}
        return freed;
    }

    static void toast(Context ctx, String msg) {
        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** v152：快捷入口大图标卡片 */
    private static View quickCard(final Context ctx, final boolean dark, final String icon,
                                  final String label, final int accent, final Runnable onClick) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        // v348 iOS 26：液态玻璃卡片（半透明 + 顶部高光 + 细描边）
        GradientDrawable cbg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0x24FFFFFF, 0x0FFFFFFF}
                     : new int[]{0xB3FFFFFF, 0x66FFFFFF});
        cbg.setCornerRadius(dp(ctx, 20));
        cbg.setStroke(dp(ctx, 1), dark ? 0x1FFFFFFF : 0x14000000);
        card.setBackgroundDrawable(cbg);
        card.setPadding(dp(ctx, 12), dp(ctx, 18), dp(ctx, 12), dp(ctx, 18));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            card.setElevation(dp(ctx, 1));
            card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        }
        // 方形圆角图标（iOS 设置风格）
        TextView iconTv = new TextView(ctx);
        iconTv.setText(icon);
        iconTv.setTextSize(20);
        iconTv.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(withAlpha(accent, dark ? 0x33 : 0x22));
        iconBg.setCornerRadius(dp(ctx, 12));
        iconTv.setBackgroundDrawable(iconBg);
        card.addView(iconTv, new LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44)));

        TextView labelTv = new TextView(ctx);
        labelTv.setText(label);
        labelTv.setTextColor(dark ? 0xFFECECEC : 0xFF1A1A1A);
        labelTv.setTextSize(13);
        labelTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        labelTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(ctx, 10);
        card.addView(labelTv, llp);
        // 按压反馈：缩放（iOS 风格轻反馈）
        card.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                int a = e.getActionMasked();
                if (a == android.view.MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
                    v.setAlpha(0.85f);
                } else if (a == android.view.MotionEvent.ACTION_UP
                        || a == android.view.MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).alpha(1f)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f)).start();
                }
                return false;
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onClick.run(); }
        });
        return card;
    }

    /** v152：快捷卡片布局参数 */
    private static LinearLayout.LayoutParams quickCardLp(Context ctx, boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (left) lp.rightMargin = dp(ctx, 5);
        else lp.leftMargin = dp(ctx, 5);
        return lp;
    }

    /** v151：替换颜色 alpha 通道 */
    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static void log(String msg) {
        try {
            // v150：调试日志开关控制（默认关闭，不再刷日志）
            Context c = sAppCtx;
            if (c == null || !DsConfig.debugLog(c)) return;
        } catch (Throwable ignored) {
            return;
        }
        android.util.Log.i("ds美化", "[float] " + msg);
    }

    /** 界面感知：主页隐藏灵动岛，聊天页显示；键盘弹起（输入）隐藏，不挡输入。
     *  主页特征：真实 View 树的 TextView 文本 == "深度思考"/"智能搜索"（已验证是原生 TextView）。
     *  状态按 Activity 隔离，避免多页面切换串扰。 */
    static final class BallScreenWatcher {
        private static volatile boolean running = false;
        private static volatile boolean sImeShown = false;

        // 按 Activity 存状态，避免页面切换串扰
        private static final java.util.Map<Integer, Boolean> LAST_SHOW = new HashMap<>();
        private static final java.util.Map<Integer, Boolean> HOME_STATE = new HashMap<>();
        private static final java.util.Map<Integer, Integer> HOME_HIT = new HashMap<>();

        static void start(final Activity activity) {
            if (running) return;
            running = true;
            final Integer key = Integer.valueOf(System.identityHashCode(activity));
            HOME_STATE.put(key, Boolean.FALSE);   // 默认非主页（隐藏在其他判定后）
            HOME_HIT.put(key, 0);
            LAST_SHOW.put(key, Boolean.FALSE);
            try {
                final View decor = activity.getWindow().getDecorView();
                decor.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    private boolean prevIme = false;
                    public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                        try {
                            int ime = 0;
                            if (android.os.Build.VERSION.SDK_INT >= 30) {
                                ime = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                            } else if (android.os.Build.VERSION.SDK_INT >= 21) {
                                ime = insets.getSystemWindowInsetBottom();
                            }
                            sImeShown = ime > 0;
                            // 键盘收起瞬间立即复查（响应灵敏）
                            if (!sImeShown && prevIme) {
                                H.post(sRunnable);
                            }
                            prevIme = sImeShown;
                        } catch (Throwable ignored) {}
                        return insets;
                    }
                });
            } catch (Throwable ignored) {}
            H.post(sRunnable);
        }

        private static final Runnable sRunnable = new Runnable() {
            public void run() {
                try {
                    Activity act = sCurrentActivity;
                    if (act != null && !act.isFinishing()) {
                        updateBall(act);
                    }
                } catch (Throwable ignored) {}
                H.postDelayed(sRunnable, 300);
            }
        };

        /** 按当前 Activity 计算并更新球可见性：主页特特征黑名单 + 键盘状态（稳定方案） */
        private static void updateBall(Activity act) {
            try {
                Integer key = Integer.valueOf(System.identityHashCode(act));
                View ball = BALLS.get(key);
                if (ball == null) return;
                if (DsConfig.ballHidden(act)) return;

                boolean home = isHomeScreen(act.getWindow().getDecorView());
                int hit = HOME_HIT.containsKey(key) ? HOME_HIT.get(key) : 0;
                if (home) {
                    hit++;
                    if (hit >= 2) HOME_STATE.put(key, Boolean.TRUE);
                } else {
                    hit = 0;
                    HOME_STATE.put(key, Boolean.FALSE);
                }
                HOME_HIT.put(key, hit);
                // v156：主页不再隐藏球（用户需要随时点开面板），仅键盘弹出时隐藏避免挡输入
                boolean show = !sImeShown;
                Boolean last = LAST_SHOW.get(key);
                if (last != null && last == show) return;
                LAST_SHOW.put(key, show);

                ball.animate().cancel();
                ball.setClickable(true);
                ball.setEnabled(true);
                ball.animate().alpha(show ? 1f : 0f).setDuration(120).start();
            } catch (Throwable ignored) {}
        }

        /** 主页特征：真实 View 树递归找 TextView 文本 == "深度思考"/"智能搜索" */
        private static boolean isHomeScreen(View v) {
            try {
                if (v instanceof android.widget.TextView) {
                    String t = ((android.widget.TextView) v).getText().toString();
                    if ("深度思考".equals(t) || "智能搜索".equals(t)) return true;
                }
                if (v instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) v;
                    for (int i = 0; i < g.getChildCount(); i++) {
                        if (isHomeScreen(g.getChildAt(i))) return true;
                    }
                }
            } catch (Throwable ignored) {}
            return false;
        }
    }   // end BallScreenWatcher

    /** 自动清理缓存执行器 */
    static final class AutoClean {
        private static final String PREFS = "ds_autoclean";
        private static android.content.SharedPreferences prefs(Context c) {
            return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        /** 按配置执行清理：autoClean==1 启动时，==2 每天 */
        static void runIfDue(Context ctx) {
            try {
                int mode = DsConfig.autoClean(ctx);
                if (mode <= 0) return;  // 关闭
                long last = prefs(ctx).getLong("last_clean_day", 0);
                java.util.Calendar cal = java.util.Calendar.getInstance();
                long today = cal.get(cal.YEAR) * 10000L + (cal.get(cal.MONTH) + 1) * 100L + cal.get(cal.DAY_OF_MONTH);
                if (mode == 1) {
                    // 启动时：每次启动清（1分钟内去重）
                    long lastMs = prefs(ctx).getLong("last_clean_ms", 0);
                    if (System.currentTimeMillis() - lastMs < 1000 * 60) return;
                    prefs(ctx).edit().putLong("last_clean_ms", System.currentTimeMillis()).commit();
                } else if (mode == 2) {
                    // 每天：同一天不重复
                    if (last == today) return;
                    prefs(ctx).edit().putLong("last_clean_day", today).commit();
                }
                long freed = cleanDeepSeekCache();
                log("AutoClean done, freed=" + freed);
            } catch (Throwable ignored) {}
        }

        /** 清理 DeepSeek 缓存目录 */
        static long cleanDeepSeekCache() {
            long freed = 0;
            try {
                File cacheDir = new File(DsConfig.dataPath("cache"));
                if (cacheDir.exists()) freed += deleteDir(cacheDir);
                File coilDir = new File(DsConfig.dataPath("files/coil"));
                if (coilDir.exists()) freed += deleteDir(coilDir);
            } catch (Throwable ignored) {}
            return freed;
        }

        private static long deleteDir(File dir) {
            long freed = 0;
            try {
                File[] children = dir.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (child.isDirectory()) freed += deleteDir(child);
                        else { freed += child.length(); child.delete(); }
                    }
                }
                dir.delete();
            } catch (Throwable ignored) {}
            return freed;
        }
    }

    /** 自动备份聊天数据库执行器（每天一次，纯文件复制安全） */
    static final class AutoBackup {
        private static final String PREFS = "ds_autobackup";
        private static android.content.SharedPreferences prefs(Context c) {
            return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        /** 每天自动备份一次（同一天不重复） */
        static void runIfDue(Context ctx) {
            try {
                long last = prefs(ctx).getLong("last_backup_day", 0);
                java.util.Calendar cal = java.util.Calendar.getInstance();
                long today = cal.get(cal.YEAR) * 10000L + (cal.get(cal.MONTH) + 1) * 100L + cal.get(cal.DAY_OF_MONTH);
                if (last == today) return;  // 今天已备份
                prefs(ctx).edit().putLong("last_backup_day", today).commit();
                int count = backupDatabases();
                log("AutoBackup done, backed up " + count + " dbs");
            } catch (Throwable ignored) {}
        }

        /** 复制 DeepSeek 所有 .db 到备份目录 */
        static int backupDatabases() {
            int count = 0;
            try {
                File dbDir = new File(DsConfig.dataPath("databases"));
                File backupDir = new File(DsConfig.dataPath("files/ds_backup"));
                if (!backupDir.exists()) backupDir.mkdirs();
                File[] dbs = dbDir.listFiles();
                if (dbs != null) {
                    for (File db : dbs) {
                        if (db.getName().endsWith(".db")) {
                            try {
                                java.io.FileInputStream in = new java.io.FileInputStream(db);
                                java.io.FileOutputStream out = new java.io.FileOutputStream(new File(backupDir, db.getName()));
                                byte[] buf = new byte[8192];
                                int len;
                                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                                out.flush(); out.close(); in.close();
                                count++;
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (Throwable ignored) {}
            return count;
        }
    }
}