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
 * v421：本地消息显示层剥离（移植自 DEKK LocalMessageText）
 *
 * 发送时注入了设备状态等上下文，但用户看到的应该是原始消息。
 * 本类负责把注入内容剥掉，返回用户可见文本。
 */
public class DsLocalMessageText {

    /** 剥离 start...end 块 */
    public static String stripBlock(String text, String start, String end) {
        if (text == null) return null;
        int s = text.indexOf(start);
        if (s < 0) return text;
        int e = text.indexOf(end, s + start.length());
        if (e < 0) {
            // 只有开始标记 → 从那里截掉
            return text.substring(0, s);
        }
        String head = text.substring(0, s);
        String tail = text.substring(e + end.length());
        return head + tail;
    }

    /**
     * 返回用户可见文本：
     * 1. 剥离设备状态块
     * 2. 找 [用户消息] 标记，只保留其后内容
     * 3. 若剥离后为空 → 返回 null（表示不显示）
     */
    public static String visible(String text) {
        if (text == null) return null;
        String t = text;

        // 1. 剥离设备状态块
        t = stripBlock(t, DsDeviceContext.HDR_START, DsDeviceContext.HDR_END);

        // 2. 找 [用户消息]，只保留其后内容
        int idx = t.indexOf(DsDeviceContext.USER_MARK);
        if (idx >= 0) {
            int p = idx + DsDeviceContext.USER_MARK.length();
            // 跳过空白
            while (p < t.length()) {
                char c = t.charAt(p);
                if (c != ' ' && c != '\r' && c != '\n') break;
                p++;
            }
            t = t.substring(p);
        }

        // 3. 去除可能残留的 \\n 转义
        t = t.replace("\\n", "\n").trim();

        if (t.length() == 0) return null;
        return t;
    }
}