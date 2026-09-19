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
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * v350：全新 UI 系统 —— 从零构建（iOS 26 液态玻璃风格）
 *
 * 设计原则：
 * 1. 液态玻璃：半透明材质 + 顶部高光 + 细描边 + 大圆角，透出背后模糊内容
 * 2. 克制配色：单一强调色（蓝），不用彩虹色堆砌，不用大 emoji
 * 3. 精细排版：明确的字号/字重/字距层级，灰色辅助文字
 * 4. 轻动画：按压缩放 + 淡入，不做花哨呼吸/霓虹
 *
 * 架构：
 * - DsTokens      设计令牌（颜色/尺寸）
 * - 组件库        glassCard / glassRow / glassSwitch / glassButton / sectionHeader
 * - buildPanel()  面板入口（顶部栏 + 页面容器 + 底部导航）
 * - 页面构建      主页 / 外观 / 聊天 / 更多 / 配置子页
 */
public class DsUI {

    // ═══════════════════════════════ 设计令牌 ═══════════════════════════════

    public static final int BRAND = 0xFF4D6BFE;          // 品牌蓝
    public static final int ACCENT_GREEN = 0xFF34C77B;   // 成功绿
    public static final int ACCENT_RED = 0xFFFF5D5D;     // 危险红
    public static final int ACCENT_ORANGE = 0xFFFF9F43;  // 提示橙
    public static final int ACCENT_PURPLE = 0xFF9B6BFF;  // 紫
    public static final int ACCENT_CYAN = 0xFF3EC9A7;    // 青
    public static final int ACCENT_GRAY = 0xFF8E8E93;    // 灰（iOS 系统灰）

    /** 当前面板是否深色（由 buildPanel 设置） */
    public static volatile boolean dark = true;

    // ── 深色模式色板 ──
    private static int darkGlassCard()      { return 0x12FFFFFF; }   // 玻璃卡片 7%
    private static int darkGlassCardHi()    { return 0x1FFFFFFF; }   // 按压高亮 12%
    private static int darkStroke()         { return 0x1AFFFFFF; }   // 细描边 10%
    private static int darkText()           { return 0xFFF2F2F7; }   // 主文字（iOS 白）
    private static int darkTextSub()        { return 0x99EBEBF5; }   // 次级 60%
    private static int darkTextFaint()      { return 0x4DEBEBF5; }   // 弱 30%
    private static int darkRowBg()          { return 0x0FFFFFFF; }   // 行背景 6%
    private static int darkSeparator()      { return 0x14EBEBF5; }   // 分隔线

    // ── 浅色模式色板 ──
    private static int lightGlassCard()     { return 0xD9FFFFFF; }   // 玻璃卡片 85%
    private static int lightGlassCardHi()   { return 0xFFFFFFFF; }   // 按压高亮
    private static int lightStroke()        { return 0x14000000; }   // 细描边
    private static int lightText()          { return 0xFF000000; }   // 主文字
    private static int lightTextSub()       { return 0xFF3C3C43; }   // 次级 60%
    private static int lightTextFaint()     { return 0xFF555555; }   // 弱 30%
    private static int lightRowBg()         { return 0xB3FFFFFF; }   // 行背景
    private static int lightSeparator()     { return 0x0F3C3C43; }   // 分隔线

    // 语义色（自动适配）
    public static int cText()        { return dark ? darkText() : lightText(); }
    public static int cTextSub()     { return dark ? darkTextSub() : lightTextSub(); }
    public static int cTextFaint()   { return dark ? darkTextFaint() : lightTextFaint(); }
    public static int cGlassCard()   { return dark ? darkGlassCard() : lightGlassCard(); }
    public static int cGlassCardHi() { return dark ? darkGlassCardHi() : lightGlassCardHi(); }
    public static int cStroke()      { return dark ? darkStroke() : lightStroke(); }
    public static int cRowBg()       { return dark ? darkRowBg() : lightRowBg(); }
    public static int cSeparator()   { return dark ? darkSeparator() : lightSeparator(); }
    public static int cPanelBg()     { return dark ? 0xE60A0A0E : 0xE6F2F2F6; }

    /** 面板主背景（画在玻璃层之下） */
    public static int panelBg()      { return dark ? 0xFF0A0A0E : 0xFFF2F2F6; }

    // ── 尺寸 ──
    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
    public static final float RADIUS_CARD = 20f;
    public static final float RADIUS_ROW = 14f;
    public static final float RADIUS_ICON = 10f;

    // ═══════════════════════════════ 工具方法 ═══════════════════════════════

    public static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    /** 调整亮度（delta 正=亮，负=暗） */
    public static int brighten(int color, int delta) {
        int r = Math.max(0, Math.min(255, ((color >> 16) & 0xFF) + delta));
        int g = Math.max(0, Math.min(255, ((color >> 8) & 0xFF) + delta));
        int b = Math.max(0, Math.min(255, (color & 0xFF) + delta));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 点按缩放反馈（iOS 风格） */
    public static void pressFeedback(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, android.view.MotionEvent e) {
                int a = e.getActionMasked();
                if (a == android.view.MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                    view.setAlpha(0.75f);
                } else if (a == android.view.MotionEvent.ACTION_UP
                        || a == android.view.MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).alpha(1f)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
                }
                return false;
            }
        });
    }

    /** 淡入上浮入场 */
    public static void enterAnim(View v, int delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 12));
        v.animate().alpha(1f).translationY(0).setStartDelay(delayMs).setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    // ═══════════════════════════════ 组件库 ═══════════════════════════════

    /**
     * 玻璃卡片 —— 液态玻璃的基础容器
     * 半透明材质 + 大圆角 + 细描边 + 极轻的顶部高光
     */
    public static LinearLayout glassCard(Context ctx, LinearLayout parent) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4));

        // v352：高不透明度卡片（干脆利落，不发灰）
        GradientDrawable body = new GradientDrawable();
        body.setColor(dark ? 0xFA1C1C1E : 0xFAFFFFFF);
        body.setCornerRadius(dp(ctx, 16));
        body.setStroke(dp(ctx, 1), dark ? 0x1AFFFFFF : 0x0F000000);

        card.setBackgroundDrawable(body);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            card.setElevation(dp(ctx, 1));
            card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 10);
        parent.addView(card, lp);
        return card;
    }

    /** 分组标题 —— iOS 风格灰色小字 */
    public static TextView sectionHeader(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(12.5f);
        tv.setTextColor(cTextSub());
        tv.setLetterSpacing(0.02f);
        tv.setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 7));
        return tv;
    }

    /** 行内分隔线（缩进对齐文字） */
    public static void separator(Context ctx, LinearLayout card) {
        View line = new View(ctx);
        line.setBackgroundColor(cSeparator());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(ctx, 0.66f)));
        lp.leftMargin = dp(ctx, 63);
        lp.rightMargin = dp(ctx, 14);
        card.addView(line, lp);
    }

    /**
     * 列表行（返回 View，不自动添加）—— 供 navRow 等旧调用桥接
     */
    public static View rowView(Context ctx, String icon, int iconColor,
                               String title, String sub, final Runnable onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 14), dp(ctx, 11), dp(ctx, 14), dp(ctx, 11));

        // 图标块（v353：统一品牌色单字，去 emoji 化）
        if (icon != null && icon.length() > 0) {
            TextView iconTv = new TextView(ctx);
            iconTv.setText(icon);
            iconTv.setTextSize(15);
            iconTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            iconTv.setGravity(Gravity.CENTER);
            GradientDrawable ibg = new GradientDrawable();
            ibg.setColor(alpha(BRAND, dark ? 0x2E : 0x1A));
            ibg.setCornerRadius(dp(ctx, 9));
            iconTv.setBackgroundDrawable(ibg);
            iconTv.setTextColor(BRAND);
            row.addView(iconTv, new LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34)));
        }

        // 文字
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        if (icon != null && icon.length() > 0) texts.setPadding(dp(ctx, 13), 0, 0, 0);
        TextView titleTv = new TextView(ctx);
        titleTv.setText(title);
        titleTv.setTextSize(15.5f);
        titleTv.setTextColor(cText());
        texts.addView(titleTv);
        if (sub != null && sub.length() > 0) {
            TextView subTv = new TextView(ctx);
            subTv.setText(sub);
            subTv.setTextSize(12.5f);
            subTv.setTextColor(cTextSub());
            subTv.setPadding(0, dp(ctx, 2), 0, 0);
            texts.addView(subTv);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 右箭头
        if (onClick != null) {
            TextView arrow = new TextView(ctx);
            arrow.setText("›");
            arrow.setTextSize(20);
            arrow.setTextColor(cTextFaint());
            arrow.setPadding(dp(ctx, 6), 0, 0, 0);
            row.addView(arrow);
        }

        if (onClick != null) {
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { onClick.run(); }
            });
            pressFeedback(row);
        }
        return row;
    }

    /**
     * 列表行（添加到卡片）
     * 方形色块图标 + 标题 + 副标题 + 右箭头
     */
    public static View row(Context ctx, LinearLayout card, String icon, int iconColor,
                           String title, String sub, final Runnable onClick) {
        View row = rowView(ctx, icon, iconColor, title, sub, onClick);
        card.addView(row);
        return row;
    }

    /**
     * 开关行（返回 View）—— 供 switchRow 桥接
     */
    public static View switchRowView(Context ctx, String icon, int iconColor,
                                     String title, String sub,
                                     boolean checked, final Runnable onToggle) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10));

        if (icon != null && icon.length() > 0) {
            TextView iconTv = new TextView(ctx);
            iconTv.setText(icon);
            iconTv.setTextSize(15);
            iconTv.setGravity(Gravity.CENTER);
            GradientDrawable ibg = new GradientDrawable();
            ibg.setColor(alpha(iconColor, dark ? 0x3D : 0x26));
            ibg.setCornerRadius(dp(ctx, RADIUS_ICON));
            iconTv.setBackgroundDrawable(ibg);
            row.addView(iconTv, new LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)));
        }
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        if (icon != null && icon.length() > 0) texts.setPadding(dp(ctx, 13), 0, 0, 0);
        TextView titleTv = new TextView(ctx);
        titleTv.setText(title);
        titleTv.setTextSize(15.5f);
        titleTv.setTextColor(cText());
        texts.addView(titleTv);
        if (sub != null && sub.length() > 0) {
            TextView subTv = new TextView(ctx);
            subTv.setText(sub);
            subTv.setTextSize(12.5f);
            subTv.setTextColor(cTextSub());
            subTv.setPadding(0, dp(ctx, 2), 0, 0);
            texts.addView(subTv);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final android.widget.Switch sw = new android.widget.Switch(ctx);
        sw.setChecked(checked);
        sw.setClickable(false);
        row.addView(sw);

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onToggle.run();
                sw.setChecked(!sw.isChecked());
            }
        });
        pressFeedback(row);
        return row;
    }

    /** 开关行（添加到卡片），返回 Switch 供状态同步 */
    public static android.widget.Switch switchRow(Context ctx, LinearLayout card,
                                                  String icon, int iconColor,
                                                  String title, String sub,
                                                  boolean checked, final Runnable onToggle) {
        View row = switchRowView(ctx, icon, iconColor, title, sub, checked, onToggle);
        // 找出行里的 Switch 返回
        android.widget.Switch sw = null;
        if (row instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) row;
            for (int i = 0; i < ll.getChildCount(); i++) {
                View c = ll.getChildAt(i);
                if (c instanceof android.widget.Switch) { sw = (android.widget.Switch) c; break; }
            }
        }
        card.addView(row);
        return sw;
    }

    /**
     * 强调按钮 —— v352：收敛风格
     * primary：品牌色填充（仅用于主要动作）；secondary：浅灰底深色字（iOS 默认）
     */
    public static TextView button(Context ctx, LinearLayout card, String text,
                                  int color, boolean secondary, final Runnable onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextSize(15);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));

        GradientDrawable bg = new GradientDrawable();
        if (secondary) {
            bg.setColor(dark ? 0x1FFFFFFF : 0x0F000000);
            btn.setTextColor(dark ? 0xFFF2F2F7 : 0xFF000000);
        } else {
            // v352：品牌色降低饱和（柔和的蓝）
            bg.setColor(color);
            btn.setTextColor(0xFFFFFFFF);
        }
        bg.setCornerRadius(dp(ctx, 12));
        btn.setBackgroundDrawable(bg);

        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onClick.run(); }
        });
        pressFeedback(btn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(ctx, 12);
        lp.rightMargin = dp(ctx, 12);
        lp.topMargin = dp(ctx, 6);
        lp.bottomMargin = dp(ctx, 10);
        card.addView(btn, lp);
        return btn;
    }

    /** 说明文字（卡片内） */
    public static TextView note(Context ctx, LinearLayout card, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(cTextFaint());
        tv.setLineSpacing(dp(ctx, 2), 1f);
        tv.setPadding(dp(ctx, 14), dp(ctx, 4), dp(ctx, 14), dp(ctx, 10));
        card.addView(tv);
        return tv;
    }
}