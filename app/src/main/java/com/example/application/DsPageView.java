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

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * v359：设置页根容器 —— 拦截返回键（移植自 DEKK PageView.dispatchKeyEvent）
 *
 * 当页面可见时，返回键 → 隐藏页面（消费事件，不穿透到宿主 Activity）。
 */
public class DsPageView extends FrameLayout {

    private Runnable onBack;

    public DsPageView(Context context) {
        super(context);
    }

    public void setOnBack(Runnable r) {
        this.onBack = r;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        try {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP && getVisibility() == View.VISIBLE) {
                    if (onBack != null) onBack.run();
                    return true;  // 消费返回键
                }
                return true;  // 按下时也消费，防止穿透
            }
        } catch (Throwable ignored) {}
        return super.dispatchKeyEvent(event);
    }
}