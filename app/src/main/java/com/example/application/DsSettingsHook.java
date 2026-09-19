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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * v357：DeepSeek 设置页注入（2.5.1 精准版）
 *
 * 逆向结论（来自 2.5.1 smali）：
 *   ob8.b(c76, hv3, Z, Z, J, wv3, wv3, wv3, F, wv3, ey3, I, I)
 *   参数槽位：
 *     [1] = 点击回调（hv3）
 *     [5] = 图标（Ltia->F）
 *     [6] = 尾部版本号（i18(3)，渲染 "2.5.1(271)"）
 *     [7] = 副标题（Ltia->G）
 *     [9] = 标题（Ltia->H，渲染 "检查更新"）
 *
 * "检查更新"行 = 该 ob8.b 调用中 args[9] == Ltia->H 的那一行（引用比对，精准唯一）。
 *
 * 注入方式：beforeHookedMethod 原地替换（不重渲染、不追加行）：
 *   args[9] → "ds美化" 文本 lambda
 *   args[6] → "v357" 文本 lambda
 *   args[1] → 打开 ds美化 面板的点击 lambda
 */
public class DsSettingsHook {

    private static final String ENTRY_TITLE = "ds美化";
    private static final String ENTRY_VERSION = "v357";

    private static Constructor<?> sComposableLambdaCtor;
    private static Method sTextMethod;

    /** 原"检查更新"行标题 lambda（Ltia->H），用于精准识别 */
    private static Object sUpdateTitle;
    /** 替换用 lambda（预创建） */
    private static Object sReplaceTitle;
    private static Object sReplaceVersion;
    private static Object sReplaceClick;

    private static volatile Activity sCurrentActivity;
    private static volatile boolean sHitLogged = false;

    public static void setCurrentActivity(Activity act) {
        sCurrentActivity = act;
    }

    public static void install(final ClassLoader cl) {
        try {
            Class<?> settingItem = XposedHelpers.findClass("ob8", cl);
            Class<?> modifier = XposedHelpers.findClass("c76", cl);
            Class<?> function0 = XposedHelpers.findClass("hv3", cl);
            Class<?> function2 = XposedHelpers.findClass("wv3", cl);
            Class<?> composer = XposedHelpers.findClass("ey3", cl);
            Class<?> composableLambda = XposedHelpers.findClass("t62", cl);

            sComposableLambdaCtor = composableLambda.getDeclaredConstructor(
                    Object.class, Boolean.TYPE, Integer.TYPE);
            sComposableLambdaCtor.setAccessible(true);

            sTextMethod = findTextMethod(XposedHelpers.findClass("z89", cl));
            if (sTextMethod == null) {
                XposedBridge.log("[ds美化] 未找到文本渲染方法，设置页注入取消");
                return;
            }

            // 捕获"检查更新"行标题 lambda（Ltia->H）——可能延迟初始化，先尝试
            try {
                Class<?> tiaCls = XposedHelpers.findClass("tia", cl);
                sUpdateTitle = XposedHelpers.getStaticObjectField(tiaCls, "H");
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] 初次获取 Ltia->H 失败（将懒加载）: " + t);
            }
            if (sUpdateTitle == null) {
                XposedBridge.log("[ds美化] Ltia->H 暂未就绪，将在渲染时懒加载捕获");
            }

            // 预创建替换 lambda
            sReplaceTitle = createTextLambda(cl, ENTRY_TITLE);
            sReplaceVersion = createTextLambda(cl, ENTRY_VERSION);
            sReplaceClick = createClickLambda(cl, function0);
            if (sReplaceTitle == null || sReplaceClick == null) {
                XposedBridge.log("[ds美化] 替换 lambda 创建失败，注入取消");
                return;
            }

            final ClassLoader fcl = cl;
            XposedHelpers.findAndHookMethod(settingItem, "b",
                    modifier, function0, Boolean.TYPE, Boolean.TYPE, Long.TYPE,
                    function2, function2, function2, Float.TYPE, function2,
                    composer, Integer.TYPE, Integer.TYPE,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                // 懒加载：捕获 Ltia->H
                                if (sUpdateTitle == null) {
                                    try {
                                        Class<?> tiaCls = XposedHelpers.findClass("tia", fcl);
                                        sUpdateTitle = XposedHelpers.getStaticObjectField(tiaCls, "H");
                                    } catch (Throwable ignored) {}
                                    if (sUpdateTitle == null) return;
                                }
                                Object[] args = param.args;
                                if (args == null || args.length < 13) return;
                                // 精准识别：仅"检查更新"行（args[9] == Ltia->H）
                                if (args[9] != sUpdateTitle) return;

                                args[9] = sReplaceTitle;          // 标题 → ds美化
                                if (sReplaceVersion != null) {
                                    args[6] = sReplaceVersion;    // 版本 → v357
                                }
                                args[1] = sReplaceClick;          // 点击 → 打开面板

                                if (!sHitLogged) {
                                    sHitLogged = true;
                                    XposedBridge.log("[ds美化] 设置页入口注入成功（检查更新 → ds美化）");
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[ds美化] 设置页注入异常: " + t);
                            }
                        }
                    });

            XposedBridge.log("[ds美化] 设置页 Hook 已安装 (v357)");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] 设置页 Hook 安装失败: " + t);
        }
    }

    /** v359：hook MainActivity.onBackPressed —— 设置页可见时拦截返回键（防穿透） */
    public static void hookBackPressed(ClassLoader cl) {
        try {
            Class<?> mainActivity = XposedHelpers.findClass(DsConfig.hostPackage() + ".MainActivity", cl);
            Method m = null;
            for (Method mm : mainActivity.getDeclaredMethods()) {
                if ("onBackPressed".equals(mm.getName()) && mm.getParameterTypes().length == 0) {
                    m = mm;
                    break;
                }
            }
            if (m == null) {
                XposedBridge.log("[ds美化] 未找到 onBackPressed（返回键由页面自身处理）");
                return;
            }
            m.setAccessible(true);
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object thisObj = param.thisObject;
                        if (!(thisObj instanceof Activity)) return;
                        Activity act = (Activity) thisObj;
                        // 设置页可见 → 消费返回键
                        if (DsSettingsPage.handleBack(act)) {
                            param.setResult(null);
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[ds美化] onBackPressed Hook 已安装");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] onBackPressed Hook 失败: " + t);
        }
    }

    // ============ 文本 lambda ============

    private static Object createTextLambda(ClassLoader cl, final String text) {
        try {
            if (cl == null || sTextMethod == null) return null;
            Class<?> function2 = XposedHelpers.findClass("wv3", cl);
            Object proxy = Proxy.newProxyInstance(cl, new Class<?>[]{function2},
                    new TextHandler(text));
            return wrapComposableLambda(proxy);
        } catch (Throwable t) {
            return null;
        }
    }

    private static class TextHandler implements InvocationHandler {
        private final String text;
        TextHandler(String text) { this.text = text; }

        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("equals".equals(name)) return proxy == (args != null && args.length > 0 ? args[0] : null);
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("toString".equals(name)) return "DsText:" + text;
            if (!"q".equals(name)) return null;
            try {
                Object composer = (args != null && args.length > 0) ? args[0] : null;
                Object[] ta = new Object[18];
                ta[0] = text;          // 文本
                ta[2] = 0L;
                ta[4] = 0L;
                ta[6] = 0L;
                ta[8] = 0L;
                ta[9] = 0;
                ta[10] = Boolean.FALSE;
                ta[11] = 0;
                ta[12] = 0;
                ta[14] = composer;     // Composer
                ta[15] = 0;
                ta[16] = 0;
                ta[17] = 0x3FFFE;
                sTextMethod.invoke(null, ta);
            } catch (Throwable ignored) {}
            return null;
        }
    }

    // ============ 点击 lambda ============

    private static Object createClickLambda(ClassLoader cl, Class<?> function0) {
        try {
            return Proxy.newProxyInstance(cl, new Class<?>[]{function0},
                    new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            String name = method.getName();
                            if ("equals".equals(name)) return proxy == (args != null && args.length > 0 ? args[0] : null);
                            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                            if ("toString".equals(name)) return "DsClick";
                            if ("v".equals(name)) {
                                try {
                                    final Activity act = sCurrentActivity;
                                    if (act != null) {
                                        act.runOnUiThread(new Runnable() {
                                            public void run() {
                                                try {
                                                    // v378：卡密门禁 —— 未激活先弹激活框
                                                    if (!DsLicense.isActivated(act)) {
                                                        DsFloat.showLicenseDialog(act, new Runnable() {
                                                            public void run() { DsSettingsPage.show(act); }
                                                        });
                                                        return;
                                                    }
                                                    DsSettingsPage.show(act);
                                                } catch (Throwable ignored) {}
                                            }
                                        });
                                    }
                                } catch (Throwable ignored) {}
                            }
                            return null;
                        }
                    });
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object wrapComposableLambda(Object lambda) {
        if (sComposableLambdaCtor == null) return lambda;
        try {
            return sComposableLambdaCtor.newInstance(lambda, Boolean.FALSE, Integer.valueOf(0x3F3F3F3F));
        } catch (Throwable t) {
            return lambda;
        }
    }

    private static Method findTextMethod(Class<?> cls) {
        for (Method m : cls.getDeclaredMethods()) {
            if ("b".equals(m.getName()) && m.getParameterTypes().length == 18) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}