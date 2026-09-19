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

/**
 * 授权/激活控制。
 *
 * 本开源版本不含任何卡密校验，{@link #isActivated(Context)} 始终返回 {@code true}，
 * 即所有功能默认可用。如需在自己的分支中接入授权机制，可自行实现本类。
 */
public class DsLicense {

    /** 开源版：始终视为已激活 */
    public static boolean isActivated(Context c) {
        return true;
    }

    /**
     * 校验授权码。
     * 开源版未实现卡密，恒返回 true（保留方法以便调用方兼容）。
     */
    public static boolean verifyCode(String code) {
        return true;
    }

    /**
     * 激活。开源版无需激活，直接返回 true。
     */
    public static boolean activate(Context c, String code) {
        return true;
    }

    /** 当前授权码（开源版为空） */
    public static String currentCode() {
        return "";
    }
}