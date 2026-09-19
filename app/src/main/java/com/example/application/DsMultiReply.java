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
import android.content.SharedPreferences;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * v363：一问多答（移植自 DEKK MultiReplyQueue + MultiReplySettings + MultiReplyStateHook）
 *
 * 原理：
 * 1. Hook 发送入口（nb5.J(jb1, yp1, String, p31)）—— 记录发送的会话/模式
 * 2. Hook 状态类（ao1）的 T 方法（状态变更）—— 检测回复完成
 * 3. Hook J 方法（ao1.J(jn1)）—— 触发续答：new cn1(prompt, 3) → ao1.J(cn1)
 * 4. 队列管理：目标次数（2-3），完成后自动续答直到达到次数
 *
 * 设置存储：ds_multireply prefs（enabled + count）
 */
public class DsMultiReply {

    private static final String PREF = "ds_multireply";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_COUNT = "count";

    // 队列状态
    private static Object sActiveOwner;        // 会话对象（弱引用直接引用）
    private static String sActiveSession;      // 会话 ID
    private static int sTargetCount = 2;       // 目标次数
    private static int sCompletedCount = 0;    // 已完成次数
    private static String sPrompt;             // 原始提问
    private static boolean sAwaitingSession = false;

    private static ClassLoader sLoader;

    // ═══════════ 设置 ═══════════

    public static boolean enabled(Context c) {
        try {
            // v377：卡密门禁
            if (c != null && !DsLicense.isActivated(c)) return false;
            return prefs(c).getBoolean(KEY_ENABLED, false);
        } catch (Throwable t) { return false; }
    }

    public static void setEnabled(Context c, boolean v) {
        try { prefs(c).edit().putBoolean(KEY_ENABLED, v).apply(); } catch (Throwable ignored) {}
    }

    public static int count(Context c) {
        try {
            int v = prefs(c).getInt(KEY_COUNT, 2);
            return Math.max(2, Math.min(3, v));
        } catch (Throwable t) { return 2; }
    }

    public static void setCount(Context c, int v) {
        try { prefs(c).edit().putInt(KEY_COUNT, Math.max(2, Math.min(3, v))).apply(); } catch (Throwable ignored) {}
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ═══════════ 安装 ═══════════

    public static void install(ClassLoader cl) {
        sLoader = cl;
        try {
            installSendEntryHook(cl);
            installTHook(cl);
            installJHook(cl);
            XposedBridge.log("[ds美化][多答] Hook 已安装");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化][多答] Hook 安装失败: " + t);
        }
    }

    /** Hook 发送入口：nb5.J(jb1, yp1, String, p31) —— 记录会话和提问 */
    private static void installSendEntryHook(ClassLoader cl) {
        try {
            Class<?> nb5 = XposedHelpers.findClass("nb5", cl);
            Class<?> jb1 = XposedHelpers.findClass("jb1", cl);
            Class<?> yp1 = XposedHelpers.findClass("yp1", cl);
            Class<?> p31 = XposedHelpers.findClass("p31", cl);

            java.lang.reflect.Method m = nb5.getDeclaredMethod("J",
                    jb1, yp1, String.class, p31);
            m.setAccessible(true);
            XposedBridge.hookMethod(m, new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object[] args = param.args;
                        if (args == null || args.length != 4) return;
                        // args[1] = 会话对象, args[2] = 提问文本
                        Object session = args[1];
                        String text = (args[2] instanceof String) ? (String) args[2] : null;
                        XposedBridge.log("[ds美化][多答] 发送入口触发 text=" + (text == null ? "null" : text.substring(0, Math.min(20, text.length()))));
                        if (text == null || text.length() == 0) return;
                        if (!enabled(currentApp())) return;

                        // 新会话：开始队列
                        synchronized (DsMultiReply.class) {
                            if (sActiveSession == null || sAwaitingSession) {
                                sActiveOwner = session;
                                sPrompt = text;
                                sTargetCount = count(currentApp());
                                sCompletedCount = 0;
                                sAwaitingSession = false;
                                XposedBridge.log("[ds美化][多答] 队列启动 session=" + session + " target=" + sTargetCount);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[ds美化][多答] 发送入口 Hook 已安装");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化][多答] 发送入口 Hook 失败: " + t);
        }
    }

    /** Hook 状态类 T 方法（ao1.T(ao1, d21) 静态方法）—— 检测完成 */
    private static void installTHook(ClassLoader cl) {
        try {
            Class<?> ao1 = XposedHelpers.findClass("ao1", cl);
            Class<?> d21 = XposedHelpers.findClass("d21", cl);

            for (java.lang.reflect.Method m : ao1.getDeclaredMethods()) {
                if (!"T".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 2) continue;
                // T(ao1, d21)：第二个参数是状态对象
                if (!d21.isAssignableFrom(pts[1])) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new de.robv.android.xposed.XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object[] args = param.args;
                            if (args == null || args.length < 2) return;
                            Object state = args[1];
                            if (state == null) return;
                            String cls = state.getClass().getName();
                            // 调试日志：打印所有状态事件
                            try {
                                boolean b = XposedHelpers.getBooleanField(state, "b");
                                boolean d = XposedHelpers.getBooleanField(state, "d");
                                Object a = XposedHelpers.getObjectField(state, "a");
                                String aCls = a == null ? "null" : a.getClass().getName();
                                XposedBridge.log("[ds美化][多答] 状态事件 " + cls + " b=" + b + " d=" + d + " a=" + aCls);
                            } catch (Throwable ignored) {}
                            // 只处理"正常完成"状态
                            if (!"c21".equals(cls)) return;
                            try {
                                boolean b = XposedHelpers.getBooleanField(state, "b");
                                boolean d = XposedHelpers.getBooleanField(state, "d");
                                if (b || d) return;
                                Object a = XposedHelpers.getObjectField(state, "a");
                                if (!firstIsSuccess(a)) return;
                                XposedBridge.log("[ds美化][多答] 检测到正常完成状态");
                                tryTriggerContinue(args[0]);
                            } catch (Throwable ignored) {}
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log("[ds美化][多答] T Hook 已安装");
                return;
            }
            XposedBridge.log("[ds美化][多答] T 方法未找到（签名不匹配）");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化][多答] T Hook 失败: " + t);
        }
    }

    /** 判断完成对象是否成功（照 DEKK：eg6.a 是 l01 且 l01.a == 0） */
    private static boolean firstIsSuccess(Object a) {
        try {
            if (a == null) return false;
            if (a instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) a;
                if (list.isEmpty()) return false;
                a = list.get(0);
            }
            if (a == null) return false;
            // a 应该是 eg6（或子类）
            if (!"eg6".equals(a.getClass().getName())) return false;
            Object inner = XposedHelpers.getObjectField(a, "a");
            if (inner == null) return false;
            if (!"l01".equals(inner.getClass().getName())) return false;
            int code = XposedHelpers.getIntField(inner, "a");
            return code == 0;  // 0 = 成功
        } catch (Throwable t) {
            return false;
        }
    }

    /** Hook J 方法（ao1.J(jn1)）—— 真正的发送入口（照 DEKK 逻辑） */
    private static void installJHook(ClassLoader cl) {
        try {
            Class<?> ao1 = XposedHelpers.findClass("ao1", cl);
            Class<?> jn1 = XposedHelpers.findClass("jn1", cl);
            java.lang.reflect.Method m = ao1.getMethod("J", jn1);
            m.setAccessible(true);
            XposedBridge.hookMethod(m, new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (sAutoDispatch) return;  // 自动续答时不处理
                        Object[] args = param.args;
                        if (args == null || args.length < 1) return;
                        Object input = args[0];
                        if (input == null) return;
                        String cls = input.getClass().getName();
                        XposedBridge.log("[ds美化][多答] J事件=" + cls);
                        if (!"cn1".equals(cls)) return;
                        // 从输入状态读取正文：thisObject.Q().l().b()
                        String text = null;
                        try {
                            Object q = XposedHelpers.callMethod(param.thisObject, "Q");
                            Object l = XposedHelpers.callMethod(q, "l");
                            text = (String) XposedHelpers.callMethod(l, "b");
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化][多答] 输入状态读取失败: " + t);
                        }
                        XposedBridge.log("[ds美化][多答] 从输入状态读取正文 present=" + (text != null && text.length() > 0));
                        if (text == null || text.length() == 0) return;
                        if (!enabled(currentApp())) return;

                        synchronized (DsMultiReply.class) {
                            // 新会话启动（覆盖旧队列）
                            sActiveOwner = param.thisObject;
                            sPrompt = text;
                            sTargetCount = count(currentApp());
                            sCompletedCount = 0;
                            sActiveSession = null;  // 等首个完成事件绑定
                            sAwaitingSession = true;
                            XposedBridge.log("[ds美化][多答] 队列启动 prompt=" + text.substring(0, Math.min(16, text.length())) + " target=" + sTargetCount);
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[ds美化][多答] J Hook 已安装");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化][多答] J Hook 失败: " + t);
        }
    }

    private static volatile boolean sAutoDispatch = false;

    // ═══════════ 核心逻辑 ═══════════

    /** 检测完成并触发续答（照 DEKK：传入 ao1 实例） */
    private static void tryTriggerContinue(Object ao1Instance) {
        try {
            // 取会话 ID：ao1.M().a
            Object mResult = XposedHelpers.callMethod(ao1Instance, "M");
            Object sessionObj = XposedHelpers.getObjectField(mResult, "a");
            String session = (sessionObj instanceof String) ? (String) sessionObj : null;
            XposedBridge.log("[ds美化][多答] 完成匹配：" + session);

            synchronized (DsMultiReply.class) {
                // 未激活 → 忽略
                if (sActiveOwner == null && sActiveSession == null) return;

                // 首次绑定会话
                if (sActiveSession == null) {
                    sActiveSession = session;
                    XposedBridge.log("[ds美化][多答] 会话绑定通过 " + session);
                } else if (!sActiveSession.equals(session)) {
                    XposedBridge.log("[ds美化][多答] 完成忽略：会话不匹配");
                    return;
                }

                // 计数
                sCompletedCount++;
                XposedBridge.log("[ds美化][多答] 完成 " + sCompletedCount + "/" + sTargetCount);

                if (sCompletedCount >= sTargetCount) {
                    cancel();
                    XposedBridge.log("[ds美化][多答] 全部完成");
                    return;
                }

                // 触发续答
                Context app = currentApp();
                if (app == null || !enabled(app)) {
                    cancel();
                    return;
                }
                triggerContinue(ao1Instance);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化][多答] 续答触发失败: " + t);
        }
    }

    /** 触发续答：new cn1(prompt, 3) → ao1.J(cn1) */
    private static void triggerContinue(Object ao1Instance) {
        try {
            sAutoDispatch = true;  // 标记：自动发送中（J hook 会跳过）
            Class<?> cn1 = XposedHelpers.findClass("cn1", sLoader);
            Object input = XposedHelpers.newInstance(cn1, sPrompt, Integer.valueOf(3));
            XposedHelpers.callMethod(ao1Instance, "J", input);
            XposedBridge.log("[ds美化][多答] 已调用续答入口");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化][多答] 续答调用失败: " + t);
            cancel();
        } finally {
            sAutoDispatch = false;
        }
    }

    public static synchronized void cancel() {
        sActiveOwner = null;
        sActiveSession = null;
        sCompletedCount = 0;
        sAwaitingSession = false;
        sPrompt = null;
    }

    private static Context currentApp() {
        try {
            android.app.Application app = android.app.AndroidAppHelper.currentApplication();
            return app;
        } catch (Throwable t) {
            return null;
        }
    }
}