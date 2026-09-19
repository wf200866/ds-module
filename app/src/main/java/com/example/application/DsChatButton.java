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
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * 自绘聊天入口按钮（v243）
 * - 内嵌样式：固定贴在 DeepSeek 顶部工具栏，与原生按钮同排
 * - 位置：右上角"自动朗读"按钮左边（x=662, y=113, 132x132px @1080x2412）
 * - 样式：纯图标无背景（与原生按钮一致），点击直接打开聊天页
 * - 不闪退：纯 View addView，无 hook
 */
public final class DsChatButton {

    private DsChatButton() {}

    // 实测坐标（1080x2412）：自动朗读 [805,113][937,245]，开启新对话 [948,113][1080,245]
    // 插到"自动朗读"左边，保持同样大小和间距
    private static final int BTN_W = 132;      // 与原生按钮同宽
    private static final int BTN_H = 132;      // 与原生按钮同高
    private static final int BTN_Y = 113;      // 与原生按钮顶边对齐
    private static final int BTN_X = 805 - BTN_W - 11;  // = 662，自动朗读左边留同样间距

    private static final Map<Integer, View> BTNS = new HashMap<Integer, View>();

    /** 每个 Activity onResume 调用；重复调用安全 */
    public static void ensure(final Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) return;
            // 开关：默认关闭（用户需在面板手动开启聊天入口按钮）
            if (!DsConfig.chatBtnOn(activity)) return;
            final Integer key = Integer.valueOf(System.identityHashCode(activity));
            View cur = BTNS.get(key);
            if (cur != null && cur.getParent() != null) return;

            final View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            final ViewGroup root = (ViewGroup) decor;
            final Context ctx = activity;

            // 屏幕尺寸适配：以 1080x2412 为基准等比缩放
            final int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
            final float scale = screenW / 1080.0f;
            final int bw = (int) (BTN_W * scale + 0.5f);
            final int bh = (int) (BTN_H * scale + 0.5f);
            final int bx = (int) (BTN_X * scale + 0.5f);
            final int by = (int) (BTN_Y * scale + 0.5f);

            final TextView btn = new TextView(ctx);
            btn.setText("💬");
            btn.setTextSize(24);
            btn.setGravity(android.view.Gravity.CENTER);
            // 纯图标无背景（与原生工具栏按钮一致），不要圆圈底色
            btn.setBackgroundColor(0x00000000);
            btn.setTextColor(0xFF1A1A1A);  // 深色图标（匹配浅色主题）
            try { btn.setElevation(dp(ctx, 2)); } catch (Throwable ignored) {}

            // 点击 → 打开聊天页；不要拖动（固定内嵌）
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try { DsChatView.show(activity); } catch (Throwable ignored) {}
                }
            });

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bw, bh);
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
            lp.leftMargin = bx;
            lp.topMargin = by;

            root.addView(btn, lp);
            BTNS.put(key, btn);
            log("内嵌按钮已添加 x=" + bx + " y=" + by + " " + bw + "x" + bh);
        } catch (Throwable t) {
            log("ensure EX " + t);
        }
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void log(String s) {
        try { de.robv.android.xposed.XposedBridge.log("[ds美化] 聊天钮 " + s); } catch (Throwable ignored) {}
    }
}