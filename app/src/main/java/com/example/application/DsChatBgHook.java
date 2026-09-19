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

/**
 * 聊天背景原生模式（占位，开发中）
 *
 * 说明：原生绘制模式尚未攻克（2.5.1 渲染架构限制），
 * 当前壁纸统一走 DsOverlay 叠加层。本类仅保留接口以兼容调用。
 */
public class DsChatBgHook {

    /** 不注册任何 hook */
    public static void install(final ClassLoader cl) {
        // no-op
    }

    /** 占位 */
    public static void refresh(String path) {
        // no-op
    }
}