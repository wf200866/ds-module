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
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * v421：设备上下文注入（移植自 DEKK DeviceContextHook）
 *
 * 功能：发送消息时，在消息前注入设备状态（时间/时区/电量/充电状态），
 * 让 AI 知道当前用户的真实环境。显示时由 DsLocalMessageText 剥离，用户无感。
 *
 * Hook 点：pn9.N(s78, int, String)
 *   - args[2] = 消息文本
 *   - 注入格式见 build()
 */
public class DsDeviceContext {

    private static volatile boolean sInstalled = false;
    private static volatile int sDiagCount = 0;

    /** 注入标记（供显示层剥离） */
    public static final String HDR_START = "[DS美化 系统提示词：设备状态（客户端附加上下文，非 system 角色）]";
    public static final String HDR_END = "[/DS美化 设备状态]";
    public static final String USER_MARK = "[用户消息]";

    public static void install(final ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;
        try {
            Class<?> pn9 = XposedHelpers.findClass("pn9", cl);
            Class<?> s78 = XposedHelpers.findClass("s78", cl);
            XposedHelpers.findAndHookMethod(pn9, "N", s78, int.class, String.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                android.content.Context ctx = appCtx();
                                if (ctx == null) return;
                                if (!DsConfig.deviceContextOn(ctx)) return;

                                Object srcObj = param.args[2];
                                if (!(srcObj instanceof String)) return;
                                String src = (String) srcObj;
                                if (src == null || src.length() == 0) return;

                                // v422：防止重复注入（已含标记 → 跳过）
                                if (src.contains(USER_MARK) || src.contains(HDR_END)) return;

                                // v422：只看用户主动发送的短文本（避免渲染已有消息时误改）
                                // 若文本过长（>2000）或含大量换行 → 疑似渲染内容，跳过
                                if (src.length() > 2000) return;

                                String state = build(ctx);
                                StringBuilder sb = new StringBuilder();
                                sb.append(HDR_START).append("\n").append(state)
                                        .append("\n").append(HDR_END).append("\n\n");
                                sb.append(USER_MARK).append("\n").append(src);

                                param.args[2] = sb.toString();

                                if (sDiagCount < 5) {
                                    sDiagCount++;
                                    XposedBridge.log("[ds美化][设备上下文] 已注入："
                                            + state.replace("\n", " | "));
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化][设备上下文] pn9.N Hook 已安装 (v421)");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化][设备上下文] Hook 失败: " + t);
        }
    }

    /** 生成设备状态文本 */
    public static String build(Context ctx) {
        StringBuilder sb = new StringBuilder();

        // 时间 + 时区
        try {
            TimeZone tz = TimeZone.getDefault();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX", Locale.US);
            sdf.setTimeZone(tz);
            sb.append("本地时间：").append(sdf.format(new Date())).append("\n");
            sb.append("时区：").append(tz.getID()).append("\n");
        } catch (Throwable ignored) {}

        // 电量 + 充电状态
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent bat = ctx.registerReceiver(null, filter);
            if (bat != null) {
                int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    int pct = Math.min(100, Math.round(level * 100f / scale));
                    sb.append("电量：").append(pct).append("%\n");
                }
                int status = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                String cs;
                switch (status) {
                    case BatteryManager.BATTERY_STATUS_CHARGING: cs = "正在充电"; break;
                    case BatteryManager.BATTERY_STATUS_FULL: cs = "已充满"; break;
                    case BatteryManager.BATTERY_STATUS_DISCHARGING:
                    case BatteryManager.BATTERY_STATUS_NOT_CHARGING: cs = "未充电"; break;
                    default: cs = "未知"; break;
                }
                sb.append("充电状态：").append(cs).append("\n");
            }
        } catch (Throwable ignored) {}

        return sb.toString();
    }

    private static android.content.Context appCtx() {
        try {
            return android.app.AndroidAppHelper.currentApplication();
        } catch (Throwable t) {
            return null;
        }
    }
}