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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 自绘聊天页面 v2（全屏页面风格，浅色主题）
 * - 头部栏 + 消息区 + 输入栏的正规聊天页布局
 * - 气泡：用户蓝色渐变（右）/ AI 白玻璃（左），带头像与时间
 * - 零网络零 hook；发送 → 假回复占位（v3 再接真实 AI）
 * 入口：DsChatButton 小圆钮 / 悬浮球菜单「自绘聊天」
 */
public final class DsChatView {

    private DsChatView() {}

    // ===== 消息模型 =====
    private static final class Msg {
        final boolean isUser;
        final String text;
        final long time;
        Msg(boolean u, String t) { isUser = u; text = t; time = System.currentTimeMillis(); }
    }

    private static final List<Msg> MSGS = new ArrayList<Msg>();
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static View sRoot = null;

    // ===== 配色（浅色主题） =====
    private static final int WHITE     = 0xFFFFFFFF;
    private static final int PAGE_BG   = 0xFFF5F6F8;
    private static final int DIVIDER   = 0xFFECEDF0;
    private static final int TXT_MAIN  = 0xFF1A1A1A;
    private static final int TXT_SUB   = 0xFF9AA0A6;
    private static final int U_TOP     = 0xFF5B78FF;
    private static final int U_BOT     = 0xFF4D6BFE;
    private static final int A_TOP     = 0xFFFFFFFF;
    private static final int A_BOT     = 0xFFF3F5FA;
    private static final int A_STROKE  = 0xFFE9ECF2;
    private static final int INPUT_BG  = 0xFFF2F3F5;
    private static final int SEND_BG   = 0xFF4D6BFE;
    private static final int AVATAR_AI = 0xFFE9EEFF;
    private static final int AVATAR_U  = 0xFFEFF1F5;

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 打开聊天页（已打开则忽略） */
    public static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (sRoot != null && sRoot.getParent() != null) return;
        final Context ctx = activity;
        H.post(new Runnable() {
            public void run() {
                try { build(activity, ctx); } catch (Throwable t) { log("show EX " + t); }
            }
        });
    }

    private static void build(final Activity activity, final Context ctx) {
        try { activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); } catch (Throwable ignored) {}

        final View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        final ViewGroup rootDecor = (ViewGroup) decor;

        final FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(WHITE);
        root.setClickable(true);
        sRoot = root;

        final LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        int sb = sysDimen(ctx, "status_bar_height", dp(ctx, 24));
        int nav = sysDimen(ctx, "navigation_bar_height", 0);
        column.setPadding(0, sb, 0, nav > 0 ? nav : dp(ctx, 6));
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ===== 头部栏 =====
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(WHITE);
        header.setPadding(dp(ctx, 6), 0, dp(ctx, 12), 0);

        TextView back = new TextView(ctx);
        back.setText("‹");
        back.setTextSize(30);
        back.setTextColor(TXT_MAIN);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(ctx, 42), dp(ctx, 42)));

        TextView title = new TextView(ctx);
        title.setText("自绘对话");
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TXT_MAIN);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(ctx, 8);
        header.addView(title, titleLp);

        TextView tag = new TextView(ctx);
        tag.setText("v2");
        tag.setTextSize(11);
        tag.setTextColor(0xFF8A8F99);
        tag.setGravity(Gravity.CENTER);
        tag.setBackground(roundRect(INPUT_BG, dp(ctx, 9), 0));
        tag.setPadding(dp(ctx, 8), dp(ctx, 2), dp(ctx, 8), dp(ctx, 2));
        header.addView(tag);

        column.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 50)));
        column.addView(divider(ctx));

        // ===== 消息区 =====
        final ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAGE_BG);
        final LinearLayout msgList = new LinearLayout(ctx);
        msgList.setOrientation(LinearLayout.VERTICAL);
        msgList.setPadding(dp(ctx, 12), dp(ctx, 14), dp(ctx, 12), dp(ctx, 20));
        scroll.addView(msgList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ===== 输入栏 =====
        column.addView(divider(ctx));
        LinearLayout inputBar = new LinearLayout(ctx);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setBackgroundColor(WHITE);
        inputBar.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));

        final EditText input = new EditText(ctx);
        input.setHint("输入消息…");
        input.setHintTextColor(TXT_SUB);
        input.setTextColor(TXT_MAIN);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(dp(ctx, 16), 0, dp(ctx, 16), 0);
        input.setBackground(roundRect(INPUT_BG, dp(ctx, 21), 0));
        inputBar.addView(input, new LinearLayout.LayoutParams(0, dp(ctx, 42), 1f));

        final TextView send = new TextView(ctx);
        send.setText("↑");
        send.setTextColor(Color.WHITE);
        send.setTextSize(18);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setGravity(Gravity.CENTER);
        send.setBackground(roundRect(SEND_BG, dp(ctx, 21), 0));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(ctx, 42), dp(ctx, 42));
        sendLp.leftMargin = dp(ctx, 10);
        inputBar.addView(send, sendLp);
        column.addView(inputBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ===== 数据与刷新 =====
        seedIfEmpty();
        refresh(ctx, msgList);
        scroll.post(new Runnable() { public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });

        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String s = input.getText().toString().trim();
                if (s.length() == 0) return;
                MSGS.add(new Msg(true, s));
                input.setText("");
                refresh(ctx, msgList);
                scrollBottom(scroll);
                hideKb(ctx, input);
                H.postDelayed(new Runnable() {
                    public void run() {
                        MSGS.add(new Msg(false, "（v2 假回复）收到：「" + s + "」。接通真实 AI 后这里会显示真实回答。"));
                        refresh(ctx, msgList);
                        scrollBottom(scroll);
                    }
                }, 450);
            }
        });

        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { close(); }
        });

        // ===== 挂载 + 渐入动画 =====
        root.setAlpha(0f);
        rootDecor.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.animate().alpha(1f).setDuration(180).start();
        log("v2 页面已显示, msg=" + MSGS.size());
    }

    /** 关闭聊天页 */
    private static void close() {
        final View r = sRoot;
        if (r == null) return;
        sRoot = null;
        try {
            r.animate().alpha(0f).setDuration(140).withEndAction(new Runnable() {
                public void run() {
                    try {
                        if (r.getParent() != null) ((ViewGroup) r.getParent()).removeView(r);
                    } catch (Throwable ignored) {}
                }
            }).start();
        } catch (Throwable t) {
            try { if (r.getParent() != null) ((ViewGroup) r.getParent()).removeView(r); } catch (Throwable ignored) {}
        }
    }

    private static void seedIfEmpty() {
        if (!MSGS.isEmpty()) return;
        MSGS.add(new Msg(false, "你好，我是 AI 助手。这是自绘聊天 v2：全屏页面样式，气泡、头像、时间全部重绘。"));
        MSGS.add(new Msg(true, "界面清爽多了，测一下发送和滚动～"));
        MSGS.add(new Msg(false, "直接输入试试。屏幕边缘的小圆钮可以拖动调整位置，点击就能回到这里。"));
    }

    private static void refresh(Context ctx, LinearLayout list) {
        list.removeAllViews();
        for (int i = 0; i < MSGS.size(); i++) {
            list.addView(row(ctx, MSGS.get(i)));
        }
    }

    /** 单条消息行 */
    private static View row(Context ctx, Msg m) {
        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams outerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outerLp.bottomMargin = dp(ctx, 16);
        outer.setLayoutParams(outerLp);

        TextView avatar = new TextView(ctx);
        avatar.setText(m.isUser ? "🙂" : "🐋");
        avatar.setTextSize(16);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(roundRect(m.isUser ? AVATAR_U : AVATAR_AI, dp(ctx, 17), 0));
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(m.isUser ? Gravity.END : Gravity.START);

        TextView bubble = new TextView(ctx);
        bubble.setText(m.text);
        bubble.setTextSize(15);
        bubble.setLineSpacing(dp(ctx, 3), 1.0f);
        bubble.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10));
        int maxW = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.66f);
        bubble.setMaxWidth(maxW);
        if (m.isUser) {
            bubble.setTextColor(Color.WHITE);
            bubble.setBackground(bubbleBg(ctx, true));
        } else {
            bubble.setTextColor(TXT_MAIN);
            bubble.setBackground(bubbleBg(ctx, false));
        }
        col.addView(bubble, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView time = new TextView(ctx);
        time.setText(fmt(m.time));
        time.setTextSize(10);
        time.setTextColor(TXT_SUB);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeLp.topMargin = dp(ctx, 4);
        timeLp.leftMargin = dp(ctx, 2);
        timeLp.rightMargin = dp(ctx, 2);
        col.addView(time, timeLp);

        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams flexLp = new LinearLayout.LayoutParams(0, 1, 1f);
        if (m.isUser) {
            outer.addView(new View(ctx), flexLp);
            colLp.rightMargin = dp(ctx, 8);
            outer.addView(col, colLp);
            outer.addView(avatar, avatarLp);
        } else {
            outer.addView(avatar, avatarLp);
            colLp.leftMargin = dp(ctx, 8);
            outer.addView(col, colLp);
            outer.addView(new View(ctx), flexLp);
        }
        return outer;
    }

    /** 气泡背景：用户蓝色渐变 / AI 白玻璃 */
    private static GradientDrawable bubbleBg(Context ctx, boolean user) {
        GradientDrawable d = new GradientDrawable(
                user ? GradientDrawable.Orientation.TL_BR : GradientDrawable.Orientation.TOP_BOTTOM,
                user ? new int[]{U_TOP, U_BOT} : new int[]{A_TOP, A_BOT});
        float r = dp(ctx, 18);
        float near = dp(ctx, 6);
        if (user) {
            d.setCornerRadii(new float[]{r, r, r, r, near, near, r, r});
            d.setStroke(Math.max(1, dp(ctx, 1)), 0x33FFFFFF);
        } else {
            d.setCornerRadii(new float[]{r, r, r, r, r, r, near, near});
            d.setStroke(Math.max(1, dp(ctx, 1)), A_STROKE);
        }
        return d;
    }

    // ===== 工具 =====
    private static View divider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(DIVIDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, (int) (ctx.getResources().getDisplayMetrics().density * 0.5f + 0.5f))));
        return v;
    }

    private static GradientDrawable roundRect(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeColor != 0) d.setStroke(1, strokeColor);
        return d;
    }

    private static int sysDimen(Context ctx, String name, int def) {
        try {
            int id = ctx.getResources().getIdentifier(name, "dimen", "android");
            if (id > 0) return ctx.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {}
        return def;
    }

    private static String fmt(long t) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }

    private static void scrollBottom(final ScrollView s) {
        s.post(new Runnable() { public void run() { s.fullScroll(View.FOCUS_DOWN); } });
    }

    private static void hideKb(Context ctx, View v) {
        try {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Throwable ignored) {}
    }

    private static void log(String s) {
        try { de.robv.android.xposed.XposedBridge.log("[ds美化] 自绘 " + s); } catch (Throwable ignored) {}
    }
}
