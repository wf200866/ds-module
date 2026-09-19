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

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * ds美化 v1（全新项目，纯悬浮球 + 背景方案）
 * 入口：按包名 hook 目标 App 的所有 Activity.onResume，
 * 注入背景层（DsOverlay）和悬浮球（DsFloat）。
 *
 * 已废弃：z89.b / z89.a / wv3 / k72.q 等 Compose 设置行 hook
 * （Compose slot 硬编码，Xposed 无法在设置页插入原生行，见旧项目记录）
 */
public class HookInit implements IXposedHookLoadPackage {

    /** DeepSeek 应用 Context（由 onResume 时缓存，供 T2 欢迎语读取配置） */
    private static volatile android.content.Context sAppCtx = null;
    /** v334：自动备份定时器是否已启动 */
    private static volatile boolean sAutoBackupStarted = false;
    /** v354：设置页 Hook 是否已安装 */
    private static volatile boolean sSettingsHookInstalled = false;

    /** T2 欢迎语 hook 是否已注册（避免重复注册） */
    private static volatile boolean sWelcomeHooked = false;
    private static volatile boolean sLicenseLogged = false;

    /** T3 鲸鱼→头像 hook 是否已注册 */
    private static volatile boolean sWhaleHooked = false;
    /** v154 去审查 hook 是否已注册 */
    private static volatile boolean sCensorHooked = false;
    /** v155 禁用热更新 hook 是否已注册 */
    private static volatile boolean sHotUpdateHooked = false;
    /** v157 气泡外观 hook 是否已注册 */
    private static volatile boolean sBubbleHooked = false;
    /** v169 禁用数据优化 hook 是否已注册 */
    private static volatile boolean sTrainingHooked = false;
    /** v170 系统提示词 hook 是否已注册 */
    private static volatile boolean sPromptHooked = false;
    /** v211 反代 hook 是否已注册 */
    private static volatile boolean sProxyHooked = false;

    /** 当前鲸鱼替换出来的 painter（供 Image 渲染去着色判断） */
    private static volatile Object sWhalePainter = null;

    /** 当前线程是否正在渲染鲸鱼头像（供 vk0 ColorFilter 构造器去着色） */
    private static volatile boolean sRenderingWhale = false;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        // 自己的 App：让主界面显示"已激活"状态
        if (BuildConfig.APPLICATION_ID.equals(lpparam.packageName)) {
            XposedHelpers.findAndHookMethod(
                    MainActivity.class.getName(),
                    lpparam.classLoader,
                    "isModuleActivated",
                    XC_MethodReplacement.returnConstant(true));
            return;
        }

        if (!"com.deepseek.chat".equals(lpparam.packageName)
                && !"com.deepseek.mod".equals(lpparam.packageName)
                && !lpparam.packageName.startsWith("com.deepseek")) {
            return;
        }

        XposedBridge.log("[ds美化] 注入成功");
        // v354：设置页注入（移植自 DEKK）—— 延迟到 onResume（类加载后）
        // DsSettingsHook.install(lpparam.classLoader);  // 见 onResume
        // T1：输入框提示色真正生效
        hookHintColor();
        hookInputBeauty();  // v166：输入框美化（文字色+光标色）

        // T3：主页鲸鱼图标 → 自定义头像（hook kf5.K painterResource 替换 painter）
        // 注意：kf5 是 DeepSeek 混淆类，handleLoadPackage 阶段可能未加载 → 在 onResume 延迟注册（同 T2 成功经验）
        // hookWhaleToAvatar();  // 改为 onResume 里调用

        // T2 核心第一层：hook 框架层 Resources.getString（立即可用，不受 DeepSeek 类加载时序限制）
        // 主页欢迎语无论走 vc5.d0 还是任何封装，最终都经过 Resources.getString
        hookResourcesGetString();

        // T2 核心第二层：主页大标题真正走 v56.d 字段（模型欢迎语，不走资源）！
        // 用 ClassLoader.loadClass 钩子等 v56 类加载后，立即 hook 其构造器，before 替换 d 参数
        hookWelcomeV56(lpparam.classLoader);

        // T2 核心第三层：主页欢迎语真正来源 = WelcomeMsgConfig.messages 的 k5a.b 字段（服务端下发）
        // hook k5a 构造器 before，把第三个参数（b=欢迎语文本）替换成用户配置
        hookWelcomeK5a(lpparam.classLoader);

        // T2：主页欢迎语 —— 不能在这里 hook！
        // vc5 是 DeepSeek 自己的混淆类，handleLoadPackage 阶段类尚未加载，
        // loadClass("vc5") 会 ClassNotFoundException。改为在 onResume（类已加载）时延迟 hook。
        // hookWelcome(lpparam.classLoader);

        // 所有 Activity onResume 后：授予悬浮窗权限 + 添加真悬浮球（独立窗口）
        XposedHelpers.findAndHookMethod(Activity.class, "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject instanceof Activity) {
                            final Activity act = (Activity) param.thisObject;
                            sAppCtx = act.getApplicationContext();  // 缓存 context 供 T2 用
                            DsConfig.initPaths(sAppCtx);  // v379：初始化动态路径（支持改包名）
                            // v354：设置页注入（延迟到 onResume，类已加载）
                            try {
                                DsSettingsHook.setCurrentActivity(act);
                                if (!sSettingsHookInstalled) {
                                    sSettingsHookInstalled = true;
                                    DsSettingsHook.install(act.getClassLoader());
                                    // v359：hook 返回键（设置页防穿透）
                                    DsSettingsHook.hookBackPressed(act.getClassLoader());
                                    // v363：一问多答
                                    try { DsMultiReply.install(act.getClassLoader()); } catch (Throwable ignored) {}
                                    // v368：原生壁纸模式
                                    try { DsChatBgHook.install(act.getClassLoader()); } catch (Throwable ignored) {}
                                    // v422：设备上下文注入（已回退，仅保留类占位）
                                    // try { DsDeviceContext.install(act.getClassLoader()); } catch (Throwable ignored) {}
                                    // v423：液态玻璃气泡（暂停，先优化气泡）
                                    // try { DsGlassBubble.install(act.getClassLoader()); } catch (Throwable ignored) {}
                                }
                            } catch (Throwable ignored) {}
                            // v334：自动备份聊天数据库（开启后每 6 小时检查一次，距上次备份超 24 小时则备份）
                            try {
                                if (!sAutoBackupStarted) {
                                    sAutoBackupStarted = true;
                                    startAutoBackupTimer(sAppCtx);
                                }
                            } catch (Throwable ignored) {}

                            // v310：卡密激活状态缓存（未激活时禁用气泡美化等核心功能）
                            final boolean licensed = DsLicense.isActivated(sAppCtx);
                            if (!licensed && !sLicenseLogged) {
                                sLicenseLogged = true;
                                XposedBridge.log("[ds美化] 未激活卡密，气泡美化禁用（悬浮球/激活入口保留）");
                            }

                            // T2 延迟注册：此时 DeepSeek 自己的类（vc5）必然已加载
                            if (!sWelcomeHooked) {
                                sWelcomeHooked = true;
                                hookWelcome(act.getClass().getClassLoader());
                            }

                            // T3 延迟注册：kf5（painterResource）此时必然已加载
                            if (!sWhaleHooked) {
                                sWhaleHooked = true;
                                try {
                                    hookWhaleToAvatar(act.getClass().getClassLoader());
                                    // 同时 hook fh6.N（Image 核心渲染）去着色（v133 验证有效）
                                    try {
                                        Class<?> clsFh6 = act.getClass().getClassLoader().loadClass("fh6");
                                        hookImageColorFilter(clsFh6);
                                    } catch (Throwable t) {
                                        XposedBridge.log("[ds美化] T3 fh6 hook EX " + t);
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log("[ds美化] T3 setup EX " + t);
                                }
                            }

                            // v154 去审查延迟注册：d98（ServerMessageHint）构造器 hook
                            if (!sCensorHooked) {
                                sCensorHooked = true;
                                hookContentFilter(act.getClass().getClassLoader());
                            }

                            // v155 禁用热更新延迟注册：sq9（UpdateInfo）构造器 hook
                            if (!sHotUpdateHooked) {
                                sHotUpdateHooked = true;
                                hookHotUpdate(act.getClass().getClassLoader());
                            }

                            // v157 气泡外观延迟注册：暂时禁用（入口 Modifier 含滚动容器，叠加会闪退）
                            // if (!sBubbleHooked) {
                            //     sBubbleHooked = true;
                            //     hookBubble(act.getClass().getClassLoader());
                            // }

                            // v169 禁用数据优化延迟注册：dr9（UpdateUserSettingsRequest 数据）构造器 hook
                            if (!sTrainingHooked) {
                                sTrainingHooked = true;
                                hookTrainingOptOut(act.getClass().getClassLoader());
                            }

                            // v170 系统提示词延迟注册：e41（ChatFullCompletionRequest）构造器 hook
                            if (!sPromptHooked) {
                                sPromptHooked = true;
                                hookSystemPrompt(act.getClass().getClassLoader());
                            }

                            // v211 反代延迟注册：hook vc5.U/V（URL 组装），替换 host
                            if (!sProxyHooked) {
                                sProxyHooked = true;
                                hookProxy(act.getClass().getClassLoader());
                            }

                            // v223diag：诊断 UserMessageHint 渲染链（w95.g 收到的 hint 是否为 null）
                            try {
                                hookHintDiag(act.getClass().getClassLoader());
                            } catch (Throwable ignored) {}

                            // v224：消息时间显示 —— hook ts9.f（最接近渲染器的注入点）
                            try {
                                hookMsgTime224(act.getClass().getClassLoader());
                            } catch (Throwable ignored) {}

                            // v225：AI 消息时间 —— hook px ctor（已被 v226 替代，保留代码但不注册）
                            // try {
                            //     hookMsgTime225(act.getClass().getClassLoader());
                            // } catch (Throwable ignored) {}

                            // v233：AI 消息时间 —— hook uk8.e（拿 vq）+ e30 构造器（blocks 列表原地 add 时间 tip）
                            try {
                                hookMsgTime233(act.getClass().getClassLoader());
                            } catch (Throwable ignored) {}

                            // 气泡hook —— v262 用户气泡（tp2.a换色）+ v276 AI文本贴合气泡
                            // v262：用户气泡 换色+样式+透明度+文字色（tp2.a/b 字段）
                            if (licensed) {
                            try {
                                hookBubble262(act.getClass().getClassLoader());
                            } catch (Throwable ignored) {}
                            // v276：AI文本贴合气泡（每个文字段落独立小气泡，贴合宽度）
                            try {
                                hookBubble276(act.getClass().getClassLoader());
                            } catch (Throwable t) {
                                XposedBridge.log("[ds美化] v276 注册EX " + t);
                            }
                            // v427【方案C探针】：画布层气泡识别（只打日志）
                            try {
                                hookBubbleCanvas(act.getClass().getClassLoader());
                            } catch (Throwable t) {
                                XposedBridge.log("[ds美化][C探针] 注册EX " + t);
                            }

                            // v300：主题色板诊断（找文字颜色字段）
                            try {
                                hookBubble300(act.getClass().getClassLoader());
                            } catch (Throwable ignored) {}
                            // v309：文字波纹动效（hook TextView.onDraw）
                            try {
                                hookTextWave(act.getClass().getClassLoader());
                            } catch (Throwable ignored) {}
                            } // end licensed

                            // v227：隐藏消息下方按钮 —— hook i95.d（用户消息操作视图）+ zl9.T（AI 反馈按钮）
                            try {
                                hookHideButtons(act.getClass().getClassLoader());
                            } catch (Throwable ignored) {}


                            act.getWindow().getDecorView().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    // v177：grantOverlay 独立 try-catch，不阻断 apply/ensure
                                    try {
                                        grantOverlay(act.getApplicationContext());
                                    } catch (Throwable t) {
                                        XposedBridge.log("[ds美化] grantOverlay EX " + t);
                                    }
                                    try {
                                        DsOverlay.apply(act);
                                    } catch (Throwable t) {
                                        XposedBridge.log("[ds美化] apply EX " + t);
                                    }
                                    try {
                                        DsFloat.ensure(act);
                                    } catch (Throwable t) {
                                        XposedBridge.log("[ds美化] ensure EX " + t);
                                    }
                                }
                            }, 400L);
                        }
                    }
                });

        // 处理"修改背景图"相册选图回调
        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult",
                int.class, int.class, android.content.Intent.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            int reqCode = (Integer) param.args[0];
                            int resultCode = (Integer) param.args[1];
                            if (reqCode == 0xB161 && resultCode == Activity.RESULT_OK) {
                                Activity act = (Activity) param.thisObject;
                                android.content.Intent data = (android.content.Intent) param.args[2];
                                android.net.Uri uri = data != null ? data.getData() : null;
                                if (uri == null) return;
                                // 读图 → 复制到 DeepSeek 私有目录
                                java.io.InputStream in = act.getContentResolver().openInputStream(uri);
                                if (in == null) return;
                                java.io.File outFile = new java.io.File(DsConfig.dataPath("files/ds_bg.png"));
                                java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                                byte[] buf = new byte[8192];
                                int len;
                                while ((len = in.read(buf)) != -1) fos.write(buf, 0, len);
                                fos.flush(); fos.close(); in.close();
                                DsConfig.setBgPath(act, outFile.getAbsolutePath());
                                android.widget.Toast.makeText(act, "背景图已更新", android.widget.Toast.LENGTH_SHORT).show();
                                // 只刷新背景层，不重建球/面板（否则悬浮窗会消失）
                                DsOverlay.apply(act);
                                // 选完图后面板自动收起，并恢复显示灵动岛
                                DsFloat.closePanel(act);
                            } else if (reqCode == 0xB162 && resultCode == Activity.RESULT_OK) {
                                Activity act = (Activity) param.thisObject;
                                android.content.Intent data = (android.content.Intent) param.args[2];
                                android.net.Uri uri = data != null ? data.getData() : null;
                                if (uri == null) return;
                                java.io.InputStream in = act.getContentResolver().openInputStream(uri);
                                if (in == null) return;
                                java.io.File outFile = new java.io.File(DsConfig.dataPath("files/ds_avatar.png"));
                                java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                                byte[] buf = new byte[8192];
                                int len;
                                while ((len = in.read(buf)) != -1) fos.write(buf, 0, len);
                                fos.flush(); fos.close(); in.close();
                                DsConfig.setAvatarPath(act, outFile.getAbsolutePath());
                                android.widget.Toast.makeText(act, "头像已保存", android.widget.Toast.LENGTH_SHORT).show();
                                DsFloat.closePanel(act);
                            } else if (reqCode == 0xB163 && resultCode == Activity.RESULT_OK) {
                                // v174：聊天壁纸选图
                                Activity act = (Activity) param.thisObject;
                                android.content.Intent data = (android.content.Intent) param.args[2];
                                DsFloat.handleChatBgResult(act, resultCode, data);
                                DsFloat.closePanel(act);
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] onActivityResult EX " + t);
                        }
                    }
                });
    }

    /**
     * 强制授予 DeepSeek 悬浮窗权限（SYSTEM_ALERT_WINDOW / AppOps OP 24）。
     * Xposed 有系统级权限，通过反射直接改 AppOps，无需用户手动去设置里开。
     */
    public static void grantOverlay(android.content.Context ctx) {
        try {
            if (android.provider.Settings.canDrawOverlays(ctx)) return;
            Object appOps = ctx.getSystemService(android.content.Context.APP_OPS_SERVICE);
            if (appOps == null) return;
            java.lang.reflect.Method m = appOps.getClass().getMethod(
                    "setMode", int.class, int.class, String.class, int.class);
            m.invoke(appOps, 24, android.os.Process.myUid(), ctx.getPackageName(), 0); // 0=MODE_ALLOWED
            XposedBridge.log("[ds美化] overlay permission granted");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] grantOverlay EX " + t);
        }
    }

    /**
     * T1：让"输入框提示色"真正生效。
     *
     * 关键结论（a82.smali 反编译确认）：
     * - DeepSeek 聊天输入框是 CustomEditText(extends AppCompatEditText)，创建流程：
     *   1304: setHint(CharSequence)  → 设置提示文字
     *   1317: setHintTextColor(I)    → 紧跟其后设置它的默认提示色
     * - 早期方案 hook setHint 后改色，会被 DeepSeek 自己的 setHintTextColor 覆盖 → 白改。
     * - 正确方案：hook setHintTextColor(int)，在 before 里替换颜色参数为配置色。
     *   这样 DeepSeek 无论调用多少次、设置什么默认色，最终颜色都被替换成配置色。
     *
     * 只对 EditText 生效（避免影响普通 TextView 的提示色）。
     * 若用户未配置(0=默认)，不干预。
     */
    /** v166：输入框美化 —— 输入文字色 + 光标色 */
    private static void hookInputBeauty() {
        try {
            // 输入文字颜色：hook TextView.setTextColor(int)
            XposedHelpers.findAndHookMethod(
                    android.widget.TextView.class,
                    "setTextColor",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                if (param.thisObject instanceof android.widget.EditText) {
                                    android.content.Context c =
                                            ((android.widget.EditText) param.thisObject).getContext();
                                    int color = DsConfig.inputTextColor(c);
                                    if (color != 0) {
                                        param.args[0] = Integer.valueOf(color);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化] v166 hook setTextColor OK");

            // 光标颜色：hook setTextCursorDrawable（API29+），转成纯色 drawable
            try {
                XposedHelpers.findAndHookMethod(
                        android.widget.TextView.class,
                        "setTextCursorDrawable",
                        android.graphics.drawable.Drawable.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    if (!(param.thisObject instanceof android.widget.EditText)) return;
                                    android.content.Context c =
                                            ((android.widget.EditText) param.thisObject).getContext();
                                    int color = DsConfig.cursorColor(c);
                                    if (color != 0) {
                                        android.graphics.drawable.GradientDrawable gd =
                                                new android.graphics.drawable.GradientDrawable();
                                        gd.setColor(color);
                                        gd.setCornerRadius(c.getResources().getDisplayMetrics().density * 2f);
                                        param.args[0] = gd;
                                    }
                                } catch (Throwable ignored) {}
                            }
                        });
                XposedBridge.log("[ds美化] v166 hook setTextCursorDrawable OK");
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v166 hook cursor EX " + t);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v166 hookInputBeauty EX " + t);
        }
    }

    private static void hookHintColor() {
        try {
            XposedHelpers.findAndHookMethod(
                    android.widget.TextView.class,
                    "setHintTextColor",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                if (param.thisObject instanceof android.widget.EditText) {
                                    android.content.Context c =
                                            ((android.widget.EditText) param.thisObject).getContext();
                                    int color = DsConfig.hintColor(c);
                                    if (color != 0) {
                                        param.args[0] = Integer.valueOf(color);  // 替换颜色参数
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化] T1 hookHintTextColor OK");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T1 hookHintTextColor EX " + t);
        }
    }

    /**
     * T3：主页鲸鱼图标（chat_welcome_logo 0x7f070063）→ 自定义头像。
     *
     * 原理（2.5.1 反编译确认）：
     * - 主页鲸鱼图标 = drawable chat_welcome_logo (0x7f070063)，vector 蓝色鲸鱼
     * - 渲染链：b17.smali 调 kf5.K(II,Composer)=painterResource(0x7f070063) → me4.a(painter,...)=Image
     * - kf5.K 对 vector 走 getXml 解析（不走 getDrawable！所以 hook getDrawable 无效，v129 失败）
     * - 正确方案：hook kf5.K 本身，当 p0==0x7f070063 且用户有头像时，
     *   反射构造 DrawablePainter(y23(Drawable)) 返回 → 鲸鱼图标位置渲染头像
     */
    private static void hookWhaleToAvatar(final ClassLoader cl) {
        try {
            Class<?> clsKf5 = cl.loadClass("kf5");
            hookKf5Painter(clsKf5);
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T3 hookWhaleToAvatar EX " + t);
        }
    }

    /** hook kf5.K（painterResource）替换鲸鱼图标为头像 + 隐藏按钮图标 */
    private static void hookKf5Painter(final Class<?> clsKf5) {
        try {
            final Class<?> clsY23 = clsKf5.getClassLoader().loadClass("y23");
            for (java.lang.reflect.Method m : clsKf5.getDeclaredMethods()) {
                if (!"K".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 3 || pts[0] != int.class || pts[1] != int.class) continue;
                final java.lang.reflect.Method fm = m;
                XposedBridge.hookMethod(fm, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            int resId = ((Integer) param.args[0]).intValue();
                            // v228：隐藏消息下方按钮图标（复制/朗读/分享/重新生成）→ 返回透明 painter
                            // v329 修复：必须检查 hideBtnOn 开关！未开启时不得隐藏按钮（否则按钮图标消失）
                            if (resId == 0x7f0700b0   // ic_copy_outline
                                    || resId == 0x7f07010c   // ic_play_media_outline_24
                                    || resId == 0x7f070128   // ic_stop_speech_outline_20
                                    || resId == 0x7f070123   // ic_share_outline_20
                                    || resId == 0x7f0700af   // ic_color_wechat (分享备用)
                                    || resId == 0x7f070115   // ic_refresh_outline_20 (重新生成)
                                    || resId == 0x7f070112   // ic_refresh_circle_fill
                                    || resId == 0x7f070113   // ic_refresh_outline
                                    || resId == 0x7f07012a) { // ic_think_outline_16
                                android.content.Context ctx = appCtx();
                                // v329：开关检查 —— 未开启隐藏按钮功能时，正常返回原图标（不透明化）
                                if (ctx == null || !DsConfig.hideBtnOn(ctx)) {
                                    return XposedBridge.invokeOriginalMethod(fm, param.thisObject, param.args);
                                }
                                if (ctx != null) {
                                    android.graphics.Bitmap trans = android.graphics.Bitmap.createBitmap(
                                            1, 1, android.graphics.Bitmap.Config.ARGB_8888);
                                    android.graphics.drawable.BitmapDrawable bd =
                                            new android.graphics.drawable.BitmapDrawable(ctx.getResources(), trans);
                                    java.lang.reflect.Constructor<?> ctor = clsY23.getDeclaredConstructor(
                                            android.graphics.drawable.Drawable.class);
                                    ctor.setAccessible(true);
                                    if (sHideBtnLogged++ < 10) {
                                        XposedBridge.log("[ds美化] v228 隐藏按钮图标 0x" + Integer.toHexString(resId));
                                    }
                                    return ctor.newInstance(bd);
                                }
                            }
                            if (resId == 0x7f070063 || resId == 0x7f070059) {  // 鲸鱼图标 / 助手头像
                                android.content.Context ctx = appCtx();
                                java.io.File avatar = new java.io.File(
                                        DsConfig.dataPath("files/ds_avatar.png"));
                                if (ctx != null && avatar.exists()) {
                                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(
                                            avatar.getAbsolutePath());
                                    if (bmp != null) {
                                        android.graphics.Bitmap round;
                                        if (resId == 0x7f070063) {
                                            // 主页鲸鱼：43:32 宽扁画布 + 正中圆形（原逻辑）
                                            round = makeRoundAvatar(bmp);
                                        } else {
                                            // 助手头像：方形圆角头像（助手头像一般是 32x32 方块）
                                            round = makeSquareAvatar(bmp);
                                        }
                                        android.graphics.drawable.BitmapDrawable bd =
                                                new android.graphics.drawable.BitmapDrawable(ctx.getResources(), round);
                                        java.lang.reflect.Constructor<?> ctor = clsY23.getDeclaredConstructor(
                                                android.graphics.drawable.Drawable.class);
                                        ctor.setAccessible(true);
                                        Object painter = ctor.newInstance(bd);
                                        if (resId == 0x7f070063) sWhalePainter = painter;
                                        XposedBridge.log("[ds美化] T3 avatar replace resId=0x" +
                                                Integer.toHexString(resId) + " OK");
                                        return painter;
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] T3 replace EX " + t);
                        }
                        // 其它资源或异常：调用原方法
                        return XposedBridge.invokeOriginalMethod(fm, param.thisObject, param.args);
                    }
                });
                XposedBridge.log("[ds美化] T3 hookKf5Painter OK");
                return;
            }
            XposedBridge.log("[ds美化] T3 kf5.K not found");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T3 hookKf5Painter EX " + t);
        }
    }

    /** v168：制作方形圆角头像（助手头像用，128x128 画布 + 圆角裁剪） */
    private static android.graphics.Bitmap makeSquareAvatar(android.graphics.Bitmap src) {
        try {
            int size = 128;
            android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(out);
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
            // 圆角矩形裁剪
            android.graphics.Bitmap circle = toCircleBitmap(src);
            float radius = size * 0.22f;
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setAntiAlias(true);
            android.graphics.RectF rect = new android.graphics.RectF(2, 2, size - 2, size - 2);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            android.graphics.Rect srcRect = new android.graphics.Rect(0, 0, circle.getWidth(), circle.getHeight());
            android.graphics.Rect dstRect = new android.graphics.Rect(2, 2, size - 2, size - 2);
            canvas.drawBitmap(circle, srcRect, dstRect, paint);
            return out;
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T3 makeSquareAvatar EX " + t);
            return src;
        }
    }

    /** 制作 43:32 透明画布 + 圆形头像（固定画布，保证显示正常） */
    private static android.graphics.Bitmap makeRoundAvatar(android.graphics.Bitmap src) {
        try {
            // 鲸鱼图标是 43x32 的宽扁形状，固定画布
            int w = 430, h = 320;
            android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(out);
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
            // 圆形裁剪头像，占画布高度 92%（正圆，稍留边）
            android.graphics.Bitmap circle = toCircleBitmap(src);
            int diameter = (int) (h * 0.92f);
            int left = (w - diameter) / 2;
            int top = (h - diameter) / 2;
            android.graphics.Rect srcRect = new android.graphics.Rect(0, 0, circle.getWidth(), circle.getHeight());
            android.graphics.Rect dstRect = new android.graphics.Rect(left, top, left + diameter, top + diameter);
            canvas.drawBitmap(circle, srcRect, dstRect, null);
            return out;
        } catch (Throwable t) {
            return src;
        }
    }

    /** 圆形裁剪 */
    private static android.graphics.Bitmap toCircleBitmap(android.graphics.Bitmap src) {
        try {
            int size = Math.min(src.getWidth(), src.getHeight());
            android.graphics.Bitmap output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(output);
            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(src, 0, 0, paint);
            return output;
        } catch (Throwable t) {
            return src;
        }
    }

    /** hook fh6.N（Image 核心渲染）去掉鲸鱼头像的 tint 着色（v133 验证有效） */
    private static void hookImageColorFilter(final Class<?> clsFh6) {
        try {
            for (java.lang.reflect.Method m : clsFh6.getDeclaredMethods()) {
                if (!"N".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 7 || pts[1] != clsFh6.getClassLoader().loadClass("ku6")) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object painter = param.args[1];  // 参数2 = painter
                            if (painter != null && sWhalePainter != null && painter == sWhalePainter) {
                                param.args[5] = null;  // 参数6 = colorFilter(vk0) → null 去着色
                                XposedBridge.log("[ds美化] T3 clear colorFilter OK");
                            }
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log("[ds美化] T3 hookImageColorFilter OK");
                return;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T3 hookImageColorFilter EX " + t);
        }
    }

    /**
     * T2 核心第一层：hook 框架层 Resources.getString(int)。
     * - Resources 是 Android 框架类，handleLoadPackage 阶段必然已加载，可立即注册，无时序问题
     * - Compose stringResource 无论经过 vc5.d0 / vc5.e0 多少封装，最终都调用 Resources.getString
     * - 只拦截主页欢迎语相关资源 ID，若设置了 greeting 则替换返回；其余资源原样放行
     */
    private static void hookResourcesGetString() {
        // 单参重载 getString(int)
        try {
            XposedHelpers.findAndHookMethod(
                    android.content.res.Resources.class,
                    "getString",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                int resId = ((Integer) param.args[0]).intValue();
                                boolean hit = resId == 0x7f0f0393      // welcome_message_title_unified
                                        || resId == 0x7f0f01eb       // model_welcome_message
                                        || resId == 0x7f0f0083       // chat_welcome_title
                                        || resId == 0x7f0f00c8       // default 模型
                                        || resId == 0x7f0f037e       // vision 模型
                                        || resId == 0x7f0f00df;      // expert 模型
                                if (!hit) return;
                                android.content.Context ctx = appCtx();
                                if (ctx == null) return;
                                String g = DsConfig.greeting(ctx);
                                if (g != null && g.length() > 0) {
                                    param.setResult(g);
                                    XposedBridge.log("[ds美化] T2 RS.getString(int) 0x"
                                            + Integer.toHexString(resId) + " -> '" + g + "'");
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化] T2 hookResourcesGetString(int) OK");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T2 hookResourcesGetString(int) EX " + t);
        }
        // 带参重载 getString(int, Object[]) —— model_welcome_message(0x7f0f01eb) 走这个！
        try {
            XposedHelpers.findAndHookMethod(
                    android.content.res.Resources.class,
                    "getString",
                    int.class, Object[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                int resId = ((Integer) param.args[0]).intValue();
                                boolean hit = resId == 0x7f0f0393
                                        || resId == 0x7f0f01eb
                                        || resId == 0x7f0f0083
                                        || resId == 0x7f0f00c8
                                        || resId == 0x7f0f037e
                                        || resId == 0x7f0f00df;
                                if (!hit) return;
                                android.content.Context ctx = appCtx();
                                if (ctx == null) return;
                                String g = DsConfig.greeting(ctx);
                                if (g != null && g.length() > 0) {
                                    param.setResult(g);
                                    XposedBridge.log("[ds美化] T2 RS.getString(int,Object[]) 0x"
                                            + Integer.toHexString(resId) + " -> '" + g + "'");
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化] T2 hookResourcesGetString(int,Object[]) OK");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T2 hookResourcesGetString(int,Object[]) EX " + t);
        }
    }

    /**
     * T2 核心第二层：hook v56 构造器，从源头把模型欢迎语字段 d 替换成用户配置。
     *
     * 背景（qo2.q 反编译确认）：
     * 主页大标题文本优先级：
     *   1. v56.d 非空 → 用 v56.d（模型欢迎语，来自模型配置，不走 Android 资源！）
     *   2. v56.s == false → 用 0x7f0f01eb 模板（带参数，format 后）
     *   3. 兜底 → 0x7f0f0393
     * 之前 hook 资源层/Compose stringResource 都只命中兜底分支 → 主页走 v56.d 时永远覆盖不到。
     *
     * 方案：v56 构造器 before 阶段，把欢迎语参数（d 字段对应 p5）替换成用户 greeting。
     * v56 是延迟加载的混淆类 → 用 ClassLoader.loadClass 钩子，等它一加载就 hook 构造器。
     */
    private static void hookWelcomeV56(final ClassLoader cl) {
        try {
            // 1) 先尝试直接 loadClass（可能已加载）
            try {
                Class<?> v56 = cl.loadClass("v56");
                tryHookV56Ctor(v56);
                XposedBridge.log("[ds美化] T2 v56 already loaded, hooked ctor");
                return;
            } catch (Throwable t) {
                // 类还没加载，走 loadClass 钩子
            }

            // 2) ClassLoader.loadClass 钩子：等 v56 加载时注册构造器 hook
            XposedHelpers.findAndHookMethod(
                    ClassLoader.class, "loadClass", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object cls = param.getResult();
                                if (cls == null) return;
                                if ("v56".equals(param.args[0])) {
                                    tryHookV56Ctor((Class<?>) cls);
                                    param.setResult(cls);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化] T2 loadClass hook armed for v56");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T2 hookWelcomeV56 EX " + t);
        }
    }

    /** hook v56 构造器，before 阶段替换欢迎语参数（p5 -> user greeting） */
    private static void tryHookV56Ctor(Class<?> v56) {
        try {
            for (java.lang.reflect.Constructor<?> c : v56.getDeclaredConstructors()) {
                Class<?>[] pts = c.getParameterTypes();
                if (pts.length >= 5 && pts[0] == int.class
                        && pts[1] == String.class
                        && pts[2] == String.class
                        && pts[3] == String.class
                        && pts[4] == String.class) {
                    // p2=a, p3=b, p4=c, p5=d(欢迎语)
                    XposedBridge.hookMethod(c, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                android.content.Context ctx = appCtx();
                                if (ctx == null) return;
                                String g = DsConfig.greeting(ctx);
                                if (g != null && g.length() > 0) {
                                    param.args[4] = g;  // 替换 d 字段（欢迎语）
                                    XposedBridge.log("[ds美化] T2 v56 ctor d -> '" + g + "'");
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                    XposedBridge.log("[ds美化] T2 v56 ctor hooked OK");
                    return;
                }
            }
            XposedBridge.log("[ds美化] T2 v56 ctor signature not matched");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T2 tryHookV56Ctor EX " + t);
        }
    }

    /**
     * T2 核心第三层：hook k5a 构造器替换欢迎语文本。
     *
     * 背景（2.5.1 反编译确认）：
     * - 主页欢迎语真正来源 = WelcomeMsgConfig（o5a）的 messages 列表，服务端下发，存 MMKV。
     * - 每个消息元素 = k5a，构造器 (IILjava/lang/String;)V：
     *     p1 mask, p2 = a（时段）, p3 = b（欢迎语文本，就是主页那行字）
     * - 渲染：zc.t() 读 o5a.a 列表 → k5a.b → 显示
     * - 之前 hook 资源层/Compose/v56 都不对：资源层是兜底、v56.d 是模型配置，主页走的是 k5a.b！
     *
     * hook k5a 构造器 before，把 p3（b=欢迎语文本）替换成用户 greeting。
     * k5a 是延迟加载混淆类 → 先尝试直接 loadClass，不行则挂 ClassLoader.loadClass 钩子。
     */
    private static void hookWelcomeK5a(final ClassLoader cl) {
        try {
            // 1) 先尝试直接 loadClass
            try {
                Class<?> k5a = cl.loadClass("k5a");
                tryHookK5aCtor(k5a);
                XposedBridge.log("[ds美化] T2 k5a already loaded, hooked ctor");
                return;
            } catch (Throwable t) {
                // 未加载，走钩子
            }

            // 2) ClassLoader.loadClass 钩子：等 k5a 加载
            XposedHelpers.findAndHookMethod(
                    ClassLoader.class, "loadClass", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object cls = param.getResult();
                                if (cls == null) return;
                                if ("k5a".equals(param.args[0])) {
                                    tryHookK5aCtor((Class<?>) cls);
                                    param.setResult(cls);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化] T2 loadClass hook armed for k5a");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T2 hookWelcomeK5a EX " + t);
        }
    }

    /** hook k5a 构造器 (II,String)，before 替换 p3（b=欢迎语文本） */
    private static void tryHookK5aCtor(Class<?> k5a) {
        try {
            boolean hooked = false;
            for (java.lang.reflect.Constructor<?> c : k5a.getDeclaredConstructors()) {
                Class<?>[] pts = c.getParameterTypes();
                if (pts.length == 3 && pts[0] == int.class && pts[1] == int.class && pts[2] == String.class) {
                    XposedBridge.hookMethod(c, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                android.content.Context ctx = appCtx();
                                if (ctx == null) return;
                                String g = DsConfig.greeting(ctx);
                                if (g != null && g.length() > 0) {
                                    param.args[2] = g;  // p3 = b = 欢迎语文本
                                    XposedBridge.log("[ds美化] T2 k5a ctor b -> '" + g + "'");
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                    hooked = true;
                    XposedBridge.log("[ds美化] T2 k5a ctor hooked OK");
                }
            }
            if (!hooked) XposedBridge.log("[ds美化] T2 k5a ctor signature not matched");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T2 tryHookK5aCtor EX " + t);
        }
    }

    /**
     * T2：主页欢迎语修改（升级为诊断+多资源拦截版）。
     *
     * 背景（2.5.1 反编译确认）：
     * - 主页大标题真正逻辑在 qo2.q() 中：
     *   1. v56.d 非空 → 用自定义欢迎语
     *   2. v56.s == false → 用 model_welcome_message (0x7f0f01eb) 模板（"使用%s开始对话"），经由 vc5.e0
     *   3. 否则才用 uiWelcomeMessage (0x7f0f0393)（兜底）
     * - 上一版只 hook 0x7f0f0393 → 是设置里的模型欢迎语默认值，不是主页真正用的分支 → 白改。
     *
     * 本版策略：
     * A. hook vc5.d0 / vc5.e0，把"所有被调用的资源 ID"打日志（去重），确认主页到底走哪个资源
     * B. 对候选资源（0x7f0f0393 / 0x7f0f01eb / 0x7f0f00c8 / 0x7f0f037e / 0x7f0f00df）
     *    若设置了 greeting，直接替换返回值 → 尽量命中真实路径
     */
    private static void hookWelcome(ClassLoader cl) {
        try {
            final Class<?> clsVc5 = cl.loadClass("vc5");
            Class<?> clsEy3 = null;
            try { clsEy3 = cl.loadClass("ey3"); } catch (Throwable ignored) {}
            final Class<?> param2 = clsEy3 != null ? clsEy3 : Object.class;

            // 候选资源 ID（主页欢迎语相关的所有可能值）
            final int[] CANDIDATES = {
                    0x7f0f0393, 0x7f0f01eb, 0x7f0f00c8, 0x7f0f037e, 0x7f0f00df
            };
            final String[] CAND_NAMES = {
                    "uiWelcomeMessage(0393)", "modelWelcome(01eb)",
                    "default(00c8)", "vision(037e)", "expert(00df)"
            };
            final java.util.Set<Integer> seen = new java.util.HashSet<Integer>();

            // 拦截并可能替换的公共逻辑
            final XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Object resArg = param.args[0];
                        if (!(resArg instanceof Integer)) return;
                        int resId = ((Integer) resArg).intValue();
                        // 诊断：打印所有被调用的 String 资源（去重），定位主页真实资源
                        synchronized (seen) {
                            if (seen.add(Integer.valueOf(resId))) {
                                XposedBridge.log("[ds美化] T2 stringRes called: 0x"
                                        + Integer.toHexString(resId));
                            }
                        }
                        // 候选资源：若设置了 greeting 则替换
                        boolean isCand = false;
                        for (int c : CANDIDATES) {
                            if (c == resId) { isCand = true; break; }
                        }
                        if (!isCand) return;
                        android.content.Context ctx = appCtx();
                        if (ctx == null) return;
                        String g = DsConfig.greeting(ctx);
                        if (g != null && g.length() > 0) {
                            param.setResult(g);
                            String nm = "?";
                            for (int i = 0; i < CANDIDATES.length; i++) {
                                if (CANDIDATES[i] == resId) { nm = CAND_NAMES[i]; break; }
                            }
                            XposedBridge.log("[ds美化] T2 REPLACED " + nm + " -> '" + g + "'");
                        }
                    } catch (Throwable ignored) {}
                }
            };

            // 1) hook d0(int, Composer) -> String
            try {
                XposedHelpers.findAndHookMethod(clsVc5, "d0", int.class, param2, hook);
                XposedBridge.log("[ds美化] T2 hook d0 OK");
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] T2 hook d0 EX " + t);
            }

            // 2) hook e0(int, Object[], Composer) -> String（带参数的模板资源）
            try {
                XposedHelpers.findAndHookMethod(clsVc5, "e0", int.class, Object[].class, param2, hook);
                XposedBridge.log("[ds美化] T2 hook e0 OK");
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] T2 hook e0 EX " + t);
            }

            // 3) 兜底：遍历所有 (int,...) -> String 方法全部 hook 打日志（防止签名差异）
            try {
                for (java.lang.reflect.Method m : clsVc5.getDeclaredMethods()) {
                    if (m.getReturnType() != String.class) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length < 1 || pts[0] != int.class) continue;
                    if ("d0".equals(m.getName()) || "e0".equals(m.getName())) continue; // 已 hook
                    try {
                        XposedBridge.hookMethod(m, hook);
                        XposedBridge.log("[ds美化] T2 hook extra " + m.getName() + " OK");
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] T2 hookWelcome EX " + t);
        }
    }

    /** v155：禁用热更新 —— hook sq9（UpdateInfo）构造器，force_update 强制 false（强制更新降级为可跳过） */
    private static void hookHotUpdate(final ClassLoader cl) {
        try {
            try {
                Class<?> sq9 = cl.loadClass("sq9");
                tryHookSq9Ctor(sq9);
                XposedBridge.log("[ds美化] 禁用热更新 sq9 already loaded, hooked");
                return;
            } catch (Throwable ignored) {}
            XposedHelpers.findAndHookMethod(
                    ClassLoader.class, "loadClass", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object cls = param.getResult();
                                if (cls == null) return;
                                if ("sq9".equals(param.args[0])) {
                                    tryHookSq9Ctor((Class<?>) cls);
                                    param.setResult(cls);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化] 禁用热更新 loadClass hook armed for sq9");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] hookHotUpdate EX " + t);
        }
    }

    /** v155：hook sq9 构造器 (String,String,String,Z,String)，force 参数（第4个）强制 false */
    private static void tryHookSq9Ctor(Class<?> sq9) {
        try {
            for (java.lang.reflect.Constructor<?> c : sq9.getDeclaredConstructors()) {
                Class<?>[] pts = c.getParameterTypes();
                if (pts.length == 5 && pts[0] == String.class
                        && pts[1] == String.class
                        && pts[2] == String.class
                        && pts[3] == boolean.class
                        && pts[4] == String.class) {
                    XposedBridge.hookMethod(c, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                android.content.Context ctx = appCtx();
                                if (ctx == null) return;
                                if (DsConfig.noHotUpdate(ctx)) {
                                    param.args[3] = Boolean.FALSE;
                                    XposedBridge.log("[ds美化] 禁用热更新: force -> false");
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                    XposedBridge.log("[ds美化] 禁用热更新 sq9 ctor hooked OK");
                    return;
                }
            }
            XposedBridge.log("[ds美化] 禁用热更新 sq9 ctor not matched");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] tryHookSq9Ctor EX " + t);
        }
    }

    /** v162：强制 ART 去内联（Xposed hook 对已内联方法无效） */
    private static void deoptimizeMethod(java.lang.reflect.Member member) {
        try {
            java.lang.reflect.Method m = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", java.lang.reflect.Member.class);
            m.setAccessible(true);
            m.invoke(null, member);
            XposedBridge.log("[ds美化] deoptimize OK " + member);
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] deoptimize EX " + t);
        }
    }

    /** v211：反代 —— hook vc5.U(Loo9)（URL 组装），把 chat.deepseek.com 替换为用户配置的反代域名 */
    private static void hookProxy(final ClassLoader cl) {
        try {
            Class<?> vc5 = cl.loadClass("vc5");
            int hooked = 0;
            for (java.lang.reflect.Method m : vc5.getDeclaredMethods()) {
                if (!("U".equals(m.getName()) || "V".equals(m.getName()))) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 1 || m.getReturnType() != String.class) continue;
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object r = param.getResult();
                            if (!(r instanceof String)) return;
                            String url = (String) r;
                            if (url.contains("chat.deepseek.com")) {
                                android.content.Context ctx = appCtx();
                                if (ctx == null) return;
                                String proxy = DsConfig.proxyHost(ctx);
                                if (proxy == null || proxy.length() == 0) return;
                                param.setResult(url.replace("chat.deepseek.com", proxy));
                                XposedBridge.log("[ds美化] 反代: host -> " + proxy);
                            }
                        } catch (Throwable ignored) {}
                    }
                });
                hooked++;
            }
            XposedBridge.log("[ds美化] v211 反代 hooked x" + hooked);
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v211 hookProxy EX " + t);
        }
    }

    /** v170：系统提示词注入 —— hook e41（ChatFullCompletionRequest）构造器，p9(prompt) 前缀注入 */
    private static void hookSystemPrompt(final ClassLoader cl) {
        try {
            try {
                Class<?> e41 = cl.loadClass("e41");
                tryHookE41Ctor(e41);
                XposedBridge.log("[ds美化] 系统提示词 e41 already loaded, hooked");
                return;
            } catch (Throwable ignored) {}
            XposedHelpers.findAndHookMethod(
                    ClassLoader.class, "loadClass", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object cls = param.getResult();
                                if (cls == null) return;
                                if ("e41".equals(param.args[0])) {
                                    tryHookE41Ctor((Class<?>) cls);
                                    param.setResult(cls);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化] 系统提示词 loadClass hook armed for e41");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] hookSystemPrompt EX " + t);
        }
    }

    /** v216：hook e41 普通构造器，p3（c=prompt）前缀注入系统提示词
     *  修正：之前误将 p9（model_type）当作 prompt，导致发送失败！
     *  字段映射：p1=a(chat_session_id) p2=b(parent_message_id) p3=c(prompt★) p4=d(ref_file_ids)
     *           p5=e p6=f p7=g p8=h p9=i(model_type) p10=j p11=mask */
    private static void tryHookE41Ctor(Class<?> e41) {
        try {
            for (java.lang.reflect.Constructor<?> c : e41.getDeclaredConstructors()) {
                Class<?>[] pts = c.getParameterTypes();
                if (pts.length != 11 || pts[0] != String.class) continue;
                if (pts[2] != String.class) continue;  // p3 = prompt(String) ★
                final int promptIdx = 2;
                c.setAccessible(true);
                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            android.content.Context ctx = appCtx();
                            if (ctx == null) return;
                            String sys = DsConfig.systemPrompt(ctx);
                            if (sys == null || sys.length() == 0) return;
                            String orig = param.args[promptIdx] == null ? "" : (String) param.args[promptIdx];
                            // v219：防重复注入（已含 <system> 标记则跳过）
                            if (orig.contains("<system>")) return;
                            // v214：改用 <system> 包装格式（与 DeepSeek 消息渲染兼容）
                            String injected = "<system>\n" + sys + "\n</system>\n\n" + orig;
                            param.args[promptIdx] = injected;
                            // v218：精简日志
                            XposedBridge.log("[ds美化] 系统提示词注入 sys=" + sys.length()
                                    + " orig=" + orig.length());
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log("[ds美化] 系统提示词 e41 ctor hooked OK");
                return;
            }
            XposedBridge.log("[ds美化] 系统提示词 e41 ctor not matched");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] tryHookE41Ctor EX " + t);
        }
    }

    /** v169：禁用数据优化 —— hook dr9（UpdateUserSettingsRequest 数据）构造器，training_allowed 强制 false */
    private static void hookTrainingOptOut(final ClassLoader cl) {
        try {
            try {
                Class<?> dr9 = cl.loadClass("dr9");
                tryHookDr9Ctor(dr9);
                XposedBridge.log("[ds美化] 禁用数据 dr9 already loaded, hooked");
                return;
            } catch (Throwable ignored) {}
            XposedHelpers.findAndHookMethod(
                    ClassLoader.class, "loadClass", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object cls = param.getResult();
                                if (cls == null) return;
                                if ("dr9".equals(param.args[0])) {
                                    tryHookDr9Ctor((Class<?>) cls);
                                    param.setResult(cls);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化] 禁用数据 loadClass hook armed for dr9");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] hookTrainingOptOut EX " + t);
        }
    }

    /** v169：hook dr9 构造器 (Boolean)，training_allowed 强制 false */
    private static void tryHookDr9Ctor(Class<?> dr9) {
        try {
            for (java.lang.reflect.Constructor<?> c : dr9.getDeclaredConstructors()) {
                Class<?>[] pts = c.getParameterTypes();
                if (pts.length == 1 && pts[0] == Boolean.class) {
                    XposedBridge.hookMethod(c, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                android.content.Context ctx = appCtx();
                                if (ctx == null) return;
                                if (DsConfig.disableTraining(ctx)) {
                                    param.args[0] = Boolean.FALSE;
                                    XposedBridge.log("[ds美化] 禁用数据: training_allowed -> false");
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                    XposedBridge.log("[ds美化] 禁用数据 dr9 ctor hooked OK");
                    return;
                }
            }
            XposedBridge.log("[ds美化] 禁用数据 dr9 ctor not matched");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] tryHookDr9Ctor EX " + t);
        }
    }

    /** v157：气泡外观 —— hook 用户气泡 vs9.a / AI 气泡 c30.a 的 Modifier 参数，叠加圆角 */
    private static void hookBubble(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    ClassLoader.class, "loadClass", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object cls = param.getResult();
                                if (cls == null) return;
                                String n = (String) param.args[0];
                                if ("vs9".equals(n)) tryHookUserBubble(cl, (Class<?>) cls);
                                else if ("c30".equals(n)) tryHookAssistantBubble(cl, (Class<?>) cls);
                            } catch (Throwable ignored) {}
                        }
                    });
            try { tryHookUserBubble(cl, cl.loadClass("vs9")); } catch (Throwable ignored) {}
            try { tryHookAssistantBubble(cl, cl.loadClass("c30")); } catch (Throwable ignored) {}
            XposedBridge.log("[ds美化] 气泡外观 loadClass hook armed");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] hookBubble EX " + t);
        }
    }

    /** v157：hook vs9.a（用户气泡入口，13参，p10 是气泡 Modifier） */
    private static void tryHookUserBubble(final ClassLoader cl, Class<?> vs9) {
        try {
            for (java.lang.reflect.Method m : vs9.getDeclaredMethods()) {
                if (!"a".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 14 || pts[0] != int.class) continue;
                int modCount = 0;
                for (Class<?> pt : pts) if ("c76".equals(pt.getName()) || pt.getName().equals("androidx.compose.ui.Modifier")) modCount++;
                if (modCount < 2) continue;
                m.setAccessible(true);
                deoptimizeMethod(m);  // v162：强制去内联，避免 ART 绕过 hook
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            // v318：气泡总开关（默认关）
                            if (!DsConfig.bubbleOn(appCtx())) return;
                            android.content.Context ctx = appCtx();
                            if (ctx == null) return;
                            int radius = DsConfig.bubbleRadius(ctx);
                            XposedBridge.log("[ds美化] 气泡用户 hook 触发 radius=" + radius);
                            if (radius == 18) return;  // 默认不改
                            Object modifier = param.args[10];  // p10 = 用户气泡 Modifier
                            if (modifier == null) return;
                            Object decorated = decorateModifier(cl, modifier, radius);
                            XposedBridge.log("[ds美化] 气泡用户 decorated=" + (decorated != modifier));
                            param.args[10] = decorated;
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log("[ds美化] 气泡外观 vs9.a hooked (user)");
                return;
            }
            XposedBridge.log("[ds美化] 气泡外观 vs9.a not matched");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] tryHookUserBubble EX " + t);
        }
    }

    /** v157：hook c30.a（AI 气泡入口，13参，p11 是气泡 Modifier） */
    private static void tryHookAssistantBubble(final ClassLoader cl, Class<?> c30) {
        try {
            for (java.lang.reflect.Method m : c30.getDeclaredMethods()) {
                if (!"a".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 14 || pts[0] != int.class) continue;
                int modCount = 0;
                for (Class<?> pt : pts) if ("c76".equals(pt.getName()) || pt.getName().equals("androidx.compose.ui.Modifier")) modCount++;
                if (modCount < 1) continue;
                m.setAccessible(true);
                deoptimizeMethod(m);  // v162：强制去内联，避免 ART 绕过 hook
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            android.content.Context ctx = appCtx();
                            if (ctx == null) return;
                            int radius = DsConfig.bubbleRadius(ctx);
                            XposedBridge.log("[ds美化] 气泡AI hook 触发 radius=" + radius);
                            if (radius == 18) return;  // 默认不改
                            Object modifier = param.args[11];  // p11 = AI 气泡 Modifier
                            if (modifier == null) return;
                            Object decorated = decorateModifier(cl, modifier, radius);
                            XposedBridge.log("[ds美化] 气泡AI decorated=" + (decorated != modifier));
                            param.args[11] = decorated;
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log("[ds美化] 气泡外观 c30.a hooked (assistant)");
                return;
            }
            XposedBridge.log("[ds美化] 气泡外观 c30.a not matched");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] tryHookAssistantBubble EX " + t);
        }
    }

    /** v157：反射调用 Compose w18 clip modifier，叠加圆角裁剪（不加背景，避免滚动容器冲突闪退） */
    private static Object decorateModifier(final ClassLoader cl, Object modifier, int radius) throws Throwable {
        try {
            // 1) new j18(px) —— RoundedCornerShape
            Class<?> j18 = cl.loadClass("j18");
            java.lang.reflect.Constructor<?> ctor = j18.getConstructor(int.class);
            ctor.setAccessible(true);
            float density = appCtx().getResources().getDisplayMetrics().density;
            int px = (int) (radius * density + 0.5f);
            Object shape = ctor.newInstance(Integer.valueOf(px));

            // 2) new w18(shape, false, false) —— clip modifier 节点（w18 implements c76）
            Class<?> w18 = cl.loadClass("w18");
            java.lang.reflect.Constructor<?> wc = w18.getConstructor(j18, boolean.class, boolean.class);
            wc.setAccessible(true);
            Object clipMod = wc.newInstance(shape, Boolean.FALSE, Boolean.FALSE);

            // 3) modifier.r(clipMod) —— Modifier.then(clip)（全反射，避免编译期引用 Compose）
            Class<?> modCls = cl.loadClass("c76");
            java.lang.reflect.Method then = modCls.getMethod("r", modCls);
            then.setAccessible(true);
            return then.invoke(modifier, clipMod);
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] decorateModifier EX " + t);
            return modifier;
        }
    }

    /** v154：去审查 —— hook d98（ServerMessageHint）构造器，clear_response 强制 false */
    private static void hookContentFilter(final ClassLoader cl) {
        try {
            try {
                Class<?> d98 = cl.loadClass("d98");
                tryHookD98Ctor(d98);
                XposedBridge.log("[ds美化] 去审查 d98 already loaded, hooked");
                return;
            } catch (Throwable ignored) {}
            XposedHelpers.findAndHookMethod(
                    ClassLoader.class, "loadClass", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object cls = param.getResult();
                                if (cls == null) return;
                                if ("d98".equals(param.args[0])) {
                                    tryHookD98Ctor((Class<?>) cls);
                                    param.setResult(cls);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化] 去审查 loadClass hook armed for d98");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] hookContentFilter EX " + t);
        }
    }

    /** v154：hook d98（ServerMessageHint 反序列化）方法，clear_response 强制 false */
    private static void tryHookD98Ctor(final Class<?> d98) {
        try {
            // v332：hook 所有方法（含 static b 序列化辅助 + 实例方法），after 检查返回值/参数是否为 d98 实例
            boolean anyHooked = false;
            for (java.lang.reflect.Method m : d98.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                m.setAccessible(true);
                final java.lang.reflect.Method fm = m;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            android.content.Context ctx = appCtx();
                            if (ctx == null) return;
                            if (!DsConfig.removeCensor(ctx)) return;
                            // 1) 返回值是 d98 实例
                            Object res = param.getResult();
                            if (res != null && d98.isInstance(res)) {
                                forceClearResponseFalse(res);
                                if (sCensorHooked && sHideBtnLogged < 5) {
                                    XposedBridge.log("[ds美化] v332 去审查捕获(返回): " + fm.getName() + " -> " + res.getClass().getSimpleName());
                                }
                            }
                            // 2) 参数里有 d98 实例（static b 传入解码器/实例）
                            if (param.args != null) {
                                for (Object a : param.args) {
                                    if (a != null && d98.isInstance(a)) {
                                        forceClearResponseFalse(a);
                                        if (sCensorHooked && sHideBtnLogged < 5) {
                                            XposedBridge.log("[ds美化] v332 去审查捕获(参数): " + fm.getName());
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                });
                anyHooked = true;
            }
            if (anyHooked) {
                XposedBridge.log("[ds美化] v332 去审查 d98 全方法 hooked (" + d98.getDeclaredMethods().length + "个方法)");
            } else {
                XposedBridge.log("[ds美化] v332 去审查 d98 无可用方法");
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v332 tryHookD98Ctor EX " + t);
        }
    }

    /** v330：把实例里所有 boolean 字段强制 false（clear_response 通常是唯一的 true boolean） */
    private static void forceClearResponseFalse(Object inst) {
        try {
            Class<?> c = inst.getClass();
            while (c != null && c != Object.class) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        if (f.getType() == boolean.class) {
                            Object v = f.get(inst);
                            if (Boolean.TRUE.equals(v)) {
                                f.set(inst, Boolean.FALSE);
                                if (sCensorHooked && sHideBtnLogged < 5) {
                                    XposedBridge.log("[ds美化] v330 去审查: " + c.getSimpleName() + "." + f.getName() + " true->false");
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
    }

    /** v334：自动备份定时器 —— 开启后每 6 小时检查，距上次备份超 24 小时则备份数据库 */
    private static void startAutoBackupTimer(final android.content.Context ctx) {
        try {
            final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (ctx == null) return;
                        if (!DsConfig.autoBackup(ctx)) { h.postDelayed(this, 6 * 3600 * 1000L); return; }
                        doAutoBackup(ctx);
                        h.postDelayed(this, 6 * 3600 * 1000L);
                    } catch (Throwable ignored) {
                        try { h.postDelayed(this, 6 * 3600 * 1000L); } catch (Throwable ignored2) {}
                    }
                }
            }, 60 * 1000L);  // 启动 1 分钟后首次检查
            XposedBridge.log("[ds美化] v334 自动备份定时器已启动");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v334 自动备份启动 EX " + t);
        }
    }

    /** v334：执行数据库备份（复制 databases/*.db 到 files/ds_backup） */
    private static void doAutoBackup(android.content.Context ctx) {
        try {
            java.io.File dbDir = new java.io.File(DsConfig.dataPath("databases"));
            java.io.File backupDir = new java.io.File(DsConfig.dataPath("files/ds_backup"));
            if (!dbDir.exists()) return;
            if (!backupDir.exists()) backupDir.mkdirs();
            // 距上次备份时间：用备份目录最新文件时间判断
            long newest = 0;
            java.io.File[] old = backupDir.listFiles();
            if (old != null) {
                for (java.io.File f : old) {
                    if (f.lastModified() > newest) newest = f.lastModified();
                }
            }
            if (newest > 0 && (System.currentTimeMillis() - newest) < 24 * 3600 * 1000L) return;  // 24h 内已备份
            int count = 0;
            java.io.File[] dbs = dbDir.listFiles();
            if (dbs != null) {
                for (java.io.File db : dbs) {
                    if (db.getName().endsWith(".db")) {
                        try {
                            java.io.FileInputStream in = new java.io.FileInputStream(db);
                            java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(backupDir, db.getName()));
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                            out.flush(); out.close(); in.close();
                            count++;
                        } catch (Throwable ignored) {}
                    }
                }
            }
            if (count > 0) XposedBridge.log("[ds美化] v334 自动备份完成 " + count + " 个数据库");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v334 自动备份 EX " + t);
        }
    }

    /** v223diag 计数器 */
    private static int sHintDiagCount = 0;
    /** v224 计数器 */
    private static int sMsgTime224Logged = 0;
    /** v227 计数器 */
    private static int sHideBtnLogged = 0;
    /** v225 计数器 */
    private static int sMsgTime225Logged = 0;
    /** v235 气泡诊断计数器 */
    private static int sBubble235Logged = 0;
    /** v240 气泡换色计数器 */
    private static int sBubble240Logged = 0;
    /** v226 计数器 */
    private static int sMsgTime226Logged = 0;

    /**
     * v233：AI 消息时间 —— hook uk8.e + e30 构造器。
     * 核心修复：v232 失败根因是 hook e30.f 后想从 e30 字段找 vq（消息对象），
     * 但 e30 根本没有 vq 字段（全是 hv3/cga/wx1…），vq==null 直接 return，什么都没注入。
     * v233 改为：
     *  1) hook uk8.e（before）：参数0 = Lvq → vq.a() → nx → nx.j(double) 时间戳，存 ThreadLocal
     *  2) hook e30 构造器（before）：args[10] = blocks 列表（p11 → this.k），原地 add 一个
     *     a91(t43) 时间 tip。外层 nba.d 遍历同一个列表引用 → 自然渲染。
     */
    private static void hookMsgTime233(final ClassLoader cl) {
        try {
            final Class<?> uk8Cls = cl.loadClass("uk8");
            final Class<?> e30Cls = cl.loadClass("e30");
            final Class<?> vqCls = cl.loadClass("vq");
            final Class<?> nxCls = cl.loadClass("nx");
            final Class<?> t43Cls = cl.loadClass("t43");
            final Class<?> a91Cls = cl.loadClass("a91");
            final Class<?> qg0Cls = cl.loadClass("qg0");

            // 反射工具
            final java.lang.reflect.Method mVqA = vqCls.getMethod("a");            // vq.a() → Lnx
            final java.lang.reflect.Field fNxJ = nxCls.getField("j");              // nx.j → double
            final java.lang.reflect.Constructor<?> cT43 = t43Cls.getConstructor(
                    int.class, String.class, String.class, String.class, boolean.class, boolean.class);
            final java.lang.reflect.Field fT43C = t43Cls.getField("c");            // t43.c = 文本
            final java.lang.reflect.Constructor<?> cA91 =
                    a91Cls.getConstructor(int.class, qg0Cls);

            // 每线程暂存：uk8.e 拿到的 vq（构造 e30 时要用）
            final ThreadLocal<Object> tlVq = new ThreadLocal<Object>();

            // ① hook uk8.e（before）：拿 vq
            for (java.lang.reflect.Method m : uk8Cls.getDeclaredMethods()) {
                if (!"e".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length < 1 || pts[0] != vqCls) continue;
                deoptimizeMethod(m);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            tlVq.set(param.args[0]);
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log("[ds美化] v233 uk8.e hooked");
                break;
            }

            // ② hook e30 构造器（before）：往 blocks 列表原地 add 时间 tip
            for (java.lang.reflect.Constructor<?> ctor : e30Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 14) continue;   // 14 参构造 (I;Lhv3;…;Ljava/util/List;Lu2;Lhv3;Lsv3;)
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            // v318：消息时间开关（默认关，用户开启才注入）
                            // v326 已废弃：AI 消息小尾巴文字方案回退（v327 失败，恢复纯时间显示）
                            android.content.Context tCtx = appCtx();
                            if (tCtx == null) return;
                            // 未设置自定义尾巴 → 走原逻辑（消息时间）
                            if (!DsConfig.showMsgTime(tCtx)) return;
                            // args[10] = java.util.List（构造器 iput-object p11 → this.k）
                            Object listObj = param.args[10];
                            if (!(listObj instanceof java.util.List)) return;
                            java.util.List<Object> list = (java.util.List<Object>) listObj;
                            // 时间戳：uk8.e 存的 vq → nx.j
                            Object vq = tlVq.get();
                            double ts = 0;
                            if (vq != null) {
                                try {
                                    Object nx = mVqA.invoke(vq);
                                    if (nx != null) ts = fNxJ.getDouble(nx);
                                } catch (Throwable ignored) {}
                            }
                            // 文本：消息时间
                            if (ts <= 0) return;
                            long millis = (long) (ts > 1e11 ? ts : ts * 1000);
                            String text = formatMsgTime(millis);

                            // 去重：已有相同文本的 a91 tip 则跳过
                            for (Object o : list) {
                                try {
                                    if (a91Cls.isInstance(o)) {
                                        java.lang.reflect.Field fb = a91Cls.getField("b");
                                        Object qg = fb.get(o);
                                        if (qg != null && t43Cls.isInstance(qg)
                                                && text.equals(fT43C.get(qg))) {
                                            return;
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }

                            // 构造 a91(t43(0,"",text,"INFO",false,false))，原地追加
                            Object tip = cT43.newInstance(Integer.valueOf(0), "", text, "INFO",
                                    Boolean.FALSE, Boolean.FALSE);
                            Object wrapped = cA91.newInstance(Integer.valueOf(list.size()), tip);
                            try {
                                list.add(wrapped);
                            } catch (UnsupportedOperationException uoe) {
                                // v234：不可变列表降级 —— 复制 + 追加后替换构造参数（this.k 指向新列表，外层遍历照常渲染）
                                java.util.List<Object> newList = new java.util.ArrayList<Object>(list);
                                newList.add(wrapped);
                                param.args[10] = newList;
                            }
                            if (sMsgTime226Logged++ < 8) {
                                XposedBridge.log("[ds美化] v234 e30 ctor 注入时间 tip: " + text
                                        + " listSize=" + list.size() + " vq=" + (vq != null ? vq.getClass().getName() : "null"));
                            }
                        } catch (Throwable t) {
                            if (sMsgTime226Logged++ < 8) {
                                XposedBridge.log("[ds美化] v233 e30 ctor EX " + t);
                            }
                        }
                    }
                });
                XposedBridge.log("[ds美化] v233 e30 ctor hooked");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v233 hookMsgTime233 EX " + t);
        }
    }

    /**
     * v235：气泡诊断 —— hook ge5.c（用户气泡背景候选）+ rs5.a（AI 气泡候选）。
     * 侦察结论：w95.e → x30 → ge5.c（tt7.c(24,24,0,0) 上圆角下直角 = 用户气泡特征）；
     * vx1.f → ux1 → rs5.a（四角动态切换 = 左右气泡分支）。
     * v235 只打日志不改渲染：确认消息气泡渲染时是否命中 + 颜色/方向参数特征，为 v236 换样式铺路。
     */
    private static void hookBubble235(final ClassLoader cl) {
        try {
            // ① ge5.c —— 用户气泡背景
            try {
                Class<?> ge5Cls = cl.loadClass("ge5");
                for (java.lang.reflect.Method m : ge5Cls.getDeclaredMethods()) {
                    if (!"c".equals(m.getName())) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length != 8) continue;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (sBubble235Logged++ > 40) return;
                            StringBuilder sb = new StringBuilder("[ds美化] v235 ge5.c hit");
                            try {
                                Object c76 = param.args[0];
                                sb.append(" c76=").append(c76 != null ? c76.getClass().getName() : "null");
                                sb.append(" z1=").append(param.args[1]);
                                sb.append(" str=").append(param.args[2]);
                                sb.append(" z3=").append(param.args[3]);
                            } catch (Throwable ignored) {}
                            XposedBridge.log(sb.toString());
                        }
                    });
                    XposedBridge.log("[ds美化] v235 ge5.c hooked " + m.toGenericString());
                    break;
                }
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v235 ge5.c EX " + t);
            }

            // ② rs5.a —— AI 气泡候选
            try {
                Class<?> rs5Cls = cl.loadClass("rs5");
                for (java.lang.reflect.Method m : rs5Cls.getDeclaredMethods()) {
                    if (!"a".equals(m.getName())) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length != 10) continue;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (sBubble235Logged++ > 40) return;
                            StringBuilder sb = new StringBuilder("[ds美化] v235 rs5.a hit");
                            try {
                                sb.append(" c76=").append(param.args[0] != null ? param.args[0].getClass().getName() : "null");
                                sb.append(" z2=").append(param.args[2]);
                                sb.append(" z3=").append(param.args[3]);
                                sb.append(" z4=").append(param.args[4]);
                            } catch (Throwable ignored) {}
                            XposedBridge.log(sb.toString());
                        }
                    });
                    XposedBridge.log("[ds美化] v235 rs5.a hooked " + m.toGenericString());
                    break;
                }
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v235 rs5.a EX " + t);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v235 hookBubble235 EX " + t);
        }
    }

    /**
     * v245：气泡颜色诊断 —— hook qp2 构造器 dump 全部 10 个颜色字段实际值 + tia.v 全量打印调用来源。
     * 目的：确认气泡背景色到底来自 qp2 哪个字段（a~j），以及 tia.v 被哪些类调用。
     */
    private static volatile int sBubble245Qp2Logged = 0;
    private static volatile int sBubble245TiaLogged = 0;

    private static void hookBubble245(final ClassLoader cl) {
        try {
            // ① qp2 构造器：dump 10 个颜色
            try {
                Class<?> qp2Cls = cl.loadClass("qp2");
                for (java.lang.reflect.Constructor<?> ctor : qp2Cls.getDeclaredConstructors()) {
                    if (ctor.getParameterTypes().length != 10) continue;
                    deoptimizeMethod(ctor);
                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (sBubble245Qp2Logged++ > 10) return;
                            StringBuilder sb = new StringBuilder("[ds美化] v245 qp2 颜色表");
                            for (int i = 0; i < 10; i++) {
                                long c = ((Long) param.args[i]).longValue();
                                sb.append(" ").append((char) ('a' + i)).append("=")
                                  .append(String.format("#%08x", c >>> 32));
                            }
                            XposedBridge.log(sb.toString());
                        }
                    });
                    XposedBridge.log("[ds美化] v245 qp2 ctor hooked");
                    break;
                }
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v245 qp2 EX " + t);
            }

            // ② tia.v 只打印气泡相关调用（栈含渲染类）
            try {
                Class<?> tiaCls = cl.loadClass("tia");
                for (java.lang.reflect.Method m : tiaCls.getDeclaredMethods()) {
                    if (!"v".equals(m.getName())) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length != 3 || !pts[1].getName().equals("long")) continue;
                    deoptimizeMethod(m);
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            boolean bubble = false;
                            String src = "?";
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if (cn.equals("ge5") || cn.equals("rs5") || cn.equals("x30")
                                        || cn.equals("qs5") || cn.equals("ux1") || cn.equals("uq5")
                                        || cn.equals("w95")) {
                                    bubble = true;
                                }
                                if (!cn.equals("tia") && !cn.startsWith("com.example")) {
                                    src = cn + "." + e.getMethodName();
                                    break;
                                }
                            }
                            if (!bubble) return;
                            if (sBubble245TiaLogged++ > 80) return;
                            long c = ((Long) param.args[1]).longValue();
                            XposedBridge.log("[ds美化] v245 气泡 tia.v " + String.format("#%08x", c >>> 32)
                                    + " from " + src);
                        }
                    });
                    XposedBridge.log("[ds美化] v245 tia.v hooked (bubble-only)");
                    break;
                }
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v245 tia.v EX " + t);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v245 hookBubble245 EX " + t);
        }
    }

    /**
     * v269：AI 消息圆角气泡 —— 在 v268 基础上加圆角 clip。
     * 链：modifier → clip(kd1.s 圆角) → background(se0 浅灰) → 替换 args[11]
     * kd1.s = st7（用户气泡圆角形状，vf2→md8 子类），u9a.A(Lc76;Lmd8;)Lc76 = clip 工厂
     */
    private static volatile int sBubble269Logged = 0;

    private static void hookBubble269(final ClassLoader cl) {
        try {
            final Class<?> c30Cls = cl.loadClass("c30");
            for (java.lang.reflect.Method m : c30Cls.getDeclaredMethods()) {
                if (!"a".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 14) continue;
                if (!pts[11].getName().equals("c76")) continue;
                // se0 构造器 (long, sn0, md8, int)
                final Class<?> se0Cls = cl.loadClass("se0");
                final Class<?> sn0Cls = cl.loadClass("sn0");
                final Class<?> md8Cls = cl.loadClass("md8");
                final java.lang.reflect.Constructor<?> cSe0 =
                        se0Cls.getConstructor(long.class, sn0Cls, md8Cls, int.class);
                cSe0.setAccessible(true);
                // zc.o = md8 brush 单例
                final Class<?> zcCls = cl.loadClass("zc");
                final java.lang.reflect.Field fZcO = zcCls.getField("o");
                // u9a.A(Lc76;Lmd8;)Lc76 = clip 工厂
                final Class<?> u9aCls = cl.loadClass("u9a");
                java.lang.reflect.Method clipMethod = null;
                for (java.lang.reflect.Method um : u9aCls.getDeclaredMethods()) {
                    if ("A".equals(um.getName())) {
                        Class<?>[] upts = um.getParameterTypes();
                        if (upts.length == 2 && upts[0].getName().equals("c76")
                                && upts[1].getName().equals("md8")) {
                            clipMethod = um;
                            break;
                        }
                    }
                }
                // jt5.E(Lc76;Lft6;)Lc76 = padding 工厂；kt6(FFFF) = PaddingValues
                final Class<?> jt5Cls = cl.loadClass("jt5");
                final Class<?> ft6Cls = cl.loadClass("ft6");
                final Class<?> kt6Cls = cl.loadClass("kt6");
                java.lang.reflect.Method padMethod = null;
                for (java.lang.reflect.Method jm : jt5Cls.getDeclaredMethods()) {
                    if ("E".equals(jm.getName())) {
                        Class<?>[] jpts = jm.getParameterTypes();
                        if (jpts.length == 2 && jpts[0].getName().equals("c76")
                                && jpts[1].getName().equals("ft6")) {
                            padMethod = jm;
                            break;
                        }
                    }
                }
                java.lang.reflect.Constructor<?> cKt6 = null;
                for (java.lang.reflect.Constructor<?> kc : kt6Cls.getDeclaredConstructors()) {
                    Class<?>[] kpts = kc.getParameterTypes();
                    if (kpts.length == 4 && kpts[0] == float.class) { cKt6 = kc; break; }
                }
                if (cKt6 != null) cKt6.setAccessible(true);
                final java.lang.reflect.Method fPad = padMethod;
                final java.lang.reflect.Constructor<?> fKt6 = cKt6;
                // kd1.s = 用户气泡圆角形状
                final Class<?> kd1Cls = cl.loadClass("kd1");
                final java.lang.reflect.Field fKd1S = kd1Cls.getField("s");
                final java.lang.reflect.Method fClip = clipMethod;
                deoptimizeMethod(m);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object modifier = param.args[11];
                            if (modifier == null) return;
                            if (sBubble269Logged++ > 10) return;
                            // 诊断：打印 modifier 类型，判断是整行容器还是内容层
                            if (sBubble269Logged <= 2) {
                                XposedBridge.log("[ds美化] v269 modifier类型=" + modifier.getClass().getName()
                                        + " hash=" + Integer.toHexString(System.identityHashCode(modifier)));
                            }
// AI 圆角气泡（v269）颜色 —— 读取 DsConfig.aiBubbleColor 配置
                            long aiColor;
                            android.content.Context aCtx = appCtx();
                            int aiColorSel = (aCtx != null) ? DsConfig.aiBubbleColor(aCtx) : 0;
                            switch (aiColorSel) {
                                case 1: aiColor = 0xFFE8F0FEL; break;   // 品牌蓝浅
                                case 2: aiColor = 0xFFDCE4FEL; break;   // 深蓝浅
                                case 3: aiColor = 0xFFD6F2ECL; break;   // 青绿浅
                                case 4: aiColor = 0xFFEAE3FEL; break;   // 紫色浅
                                case 5: aiColor = 0xFFFEE3E9L; break;   // 粉色浅
                                case 6: aiColor = 0xFFFFE8CCL; break;   // 橙色浅
                                default: aiColor = 0xFFF2F2F4L; break;   // 浅灰
                            }
                            Object brush = fZcO.get(null);
                            // 0. 先加内边距，让气泡内部有呼吸感
                            final int padX = Math.max(14, Math.min(20, DsConfig.bubbleRadius(aCtx != null ? aCtx : appCtx())));
                            Object padded = modifier;
                            if (fPad != null && fKt6 != null) {
                                try {
                                    Object padValues = fKt6.newInstance(Float.valueOf((float) padX),
                                            Float.valueOf((float) padX), Float.valueOf(10.0f), Float.valueOf(10.0f));
                                    padded = fPad.invoke(null, modifier, padValues);
                                } catch (Throwable tpe) {
                                    padded = modifier;
                                }
                            }
                            // 1. 圆角 clip：clip(modifier, kd1.s)
                            Object shape = fKd1S.get(null);
                            Object clipped = padded;
                            if (fClip != null && shape != null) {
                                clipped = fClip.invoke(null, padded, shape);
                            }
                            // 2. 背景：new se0 → clipped.r(se0) (在 padding 内部画背景，实现紧凑气泡)
                            Object se0 = cSe0.newInstance(Long.valueOf(aiColor << 32), null, brush,
                                    Integer.valueOf(0));
                            Object newModifier = clipped;
                            Class<?> c76Cls = cl.loadClass("c76");
                            java.lang.reflect.Method rMethod = null;
                            try {
                                rMethod = c76Cls.getMethod("r", c76Cls);
                            } catch (Throwable t1) {
                                try { rMethod = newModifier.getClass().getMethod("r", c76Cls); }
                                catch (Throwable t2) { rMethod = null; }
                            }
                            if (rMethod == null) { XposedBridge.log("[ds美化] v269 r NOT FOUND"); return; }
                            rMethod.setAccessible(true);
                            newModifier = rMethod.invoke(newModifier, se0);
                            param.args[11] = newModifier;
                            XposedBridge.log("[ds美化] v269 AI圆角气泡 已加 " + String.format("#%08x", aiColor)
                                    + " clip=" + (fClip != null && shape != null));
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v269 c30.a EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v269 c30.a hooked (AI圆角气泡)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v269 hookBubble269 EX " + t);
        }
    }

    /**
     * v268：AI 消息气泡（安全版）—— hook c30.a，before 反射构造 se0 追加 background。
     * v265 崩溃修复：不反射调 tia.v（参数类型坑），直接 new se0 + modifier.r(se0)。
     * se0 构造 (long color, sn0 shape, md8 brush, int)，c76.r(then) 拼接 modifier。
     */
    private static volatile int sBubble268Logged = 0;

    private static void hookBubble268(final ClassLoader cl) {
        try {
            final Class<?> c30Cls = cl.loadClass("c30");
            for (java.lang.reflect.Method m : c30Cls.getDeclaredMethods()) {
                if (!"a".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 14) continue;
                if (!pts[11].getName().equals("c76")) continue;
                // se0 构造器 (long, sn0, md8, int)
                final Class<?> se0Cls = cl.loadClass("se0");
                final Class<?> sn0Cls = cl.loadClass("sn0");
                final Class<?> md8Cls = cl.loadClass("md8");
                final java.lang.reflect.Constructor<?> cSe0 =
                        se0Cls.getConstructor(long.class, sn0Cls, md8Cls, int.class);
                cSe0.setAccessible(true);
                // zc.o = md8 brush 单例
                final Class<?> zcCls = cl.loadClass("zc");
                final java.lang.reflect.Field fZcO = zcCls.getField("o");
                deoptimizeMethod(m);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object modifier = param.args[11];
                            if (modifier == null) return;
                            if (sBubble268Logged++ > 8) return;
                            // AI 消息气泡：液态玻璃浅灰白（接近白色，略灰区分）
                            long aiColor = 0xF7F7F7F8L;
                            Object brush = fZcO.get(null);
                            // 构造 se0 background 修饰符
                            Object se0 = cSe0.newInstance(Long.valueOf(aiColor << 32), null, brush,
                                    Integer.valueOf(0));
                            // modifier.r(se0) = modifier.then(se0) 追加背景
                            java.lang.reflect.Method rMethod = null;
                            Class<?> c76Cls = c76ClsFor(cl);
                            if (c76Cls != null) {
                                try {
                                    rMethod = c76Cls.getMethod("r", c76Cls);
                                } catch (Throwable t1) {
                                    try { rMethod = modifier.getClass().getMethod("r", c76Cls); }
                                    catch (Throwable t2) { rMethod = null; }
                                }
                            }
                            if (rMethod == null) { XposedBridge.log("[ds美化] v268 r NOT FOUND"); return; }
                            rMethod.setAccessible(true);
                            Object newModifier = rMethod.invoke(modifier, se0);
                            param.args[11] = newModifier;
                            XposedBridge.log("[ds美化] v268 AI气泡 已加背景 " + String.format("#%08x", aiColor));
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v268 c30.a EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v268 c30.a hooked (AI气泡安全版)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v268 hookBubble268 EX " + t);
        }
    }

    private static Class<?> c76ClsFor(final ClassLoader cl) {
        try { return cl.loadClass("c76"); } catch (Throwable t) { return null; }
    }

    /** v309：文字波纹动效 —— hook TextView.onDraw 应用动态渐变 shader */
    private static final ThreadLocal<DsTextWave.PaintState> sWaveState = new ThreadLocal<>();
    private static void hookTextWave(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(android.widget.TextView.class, "onDraw",
                    android.graphics.Canvas.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                android.widget.TextView tv = (android.widget.TextView) param.thisObject;
                                android.content.Context ctx = tv.getContext();
                                if (!DsConfig.textWave(ctx)) return;
                                float density = tv.getResources().getDisplayMetrics().density;
                                DsTextWave.PaintState state = DsTextWave.apply(tv.getPaint(), density, ctx);
                                sWaveState.set(state);
                            } catch (Throwable ignored) {}
                        }
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                DsTextWave.PaintState state = sWaveState.get();
                                if (state != null) {
                                    DsTextWave.restore(state);
                                    sWaveState.set(null);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ds美化] v309 TextView.onDraw hooked (文字波纹)");
        } catch (Throwable t) {
XposedBridge.log("[ds美化] v309 hookTextWave EX " + t);
        }
    }

    /**
     * v277：ru0.c 签名诊断 —— 打印所有 c 方法的参数，确认 c76 确切索引和参数数。
     */
    private static volatile int sBubble277Logged = 0;

    private static void hookBubble277(final ClassLoader cl) {
        try {
            final Class<?> ru0Cls = cl.loadClass("ru0");
            XposedBridge.log("[ds美化] v277 ru0 方法列表:");
            for (java.lang.reflect.Method m : ru0Cls.getDeclaredMethods()) {
                if (!"c".equals(m.getName())) continue;
                StringBuilder sb = new StringBuilder("[ds美化] v277 c方法 参数数=" + m.getParameterTypes().length);
                Class<?>[] pts = m.getParameterTypes();
                for (int i = 0; i < pts.length; i++) {
                    sb.append("\n  [").append(i).append("] ").append(pts[i].getName());
                }
                XposedBridge.log(sb.toString());
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v277 hookBubble277 EX " + t);
        }
    }

    /**
     * v427【方案C探针】：画布层气泡识别 —— 只打日志，不改绘制。
     *
     * 实测 smali（/tmp/ds251_smali/tb.smali）：
     *   .field public a:Landroid/graphics/Canvas;   // 真实 Canvas
     *   .field public b:Landroid/graphics/Rect;
     *   .field public c:Landroid/graphics/Rect;
     *   tb.g(FFFFFFLze)V → Canvas.drawRoundRect(FFFFFFLandroid/graphics/Paint;)  ★气泡背景
     *   tb.k(FFFFLze)V   → Canvas.drawRect(FFFFLandroid/graphics/Paint;)
     *   tb.e(Lhf;Lze)V   → Canvas.drawPath(Landroid/graphics/Path;Landroid/graphics/Paint;)
     *   a18.G(Lze)Landroid/graphics/Paint → ze.a（真实 Paint），可 getColor()
     *
     * 目标：确认 tb.g 能否精准命中"气泡背景绘制"，为后续改绘铺路。
     */
    private static volatile int sCanvasProbeLogged = 0;
    private static volatile int sBubbleBoxLogged = 0;
    private static volatile int sShapeDiagLogged = 0;
    private static volatile int sGradLogged = 0;
    private static final ThreadLocal<Object> sLastDrawNode = new ThreadLocal<>();

    private static void hookBubbleCanvas(final ClassLoader cl) {
        try {
            final Class<?> tbCls = cl.loadClass("tb");
            final Class<?> zeCls = cl.loadClass("ze");
            final Class<?> a18Cls = cl.loadClass("a18");

            // a18.G(Lze)Landroid/graphics/Paint
            java.lang.reflect.Method gPaint = null;
            for (java.lang.reflect.Method gm : a18Cls.getDeclaredMethods()) {
                if (!"G".equals(gm.getName())) continue;
                Class<?>[] gp = gm.getParameterTypes();
                if (gp.length == 1 && gp[0].getName().equals("ze")) { gPaint = gm; break; }
            }
            final java.lang.reflect.Method fGPaint = gPaint;

            // tb.g(FFFFFFLze)V = drawRoundRect
            java.lang.reflect.Method roundRect = null;
            for (java.lang.reflect.Method m : tbCls.getDeclaredMethods()) {
                if (!"g".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 7
                        && pts[0] == float.class && pts[1] == float.class
                        && pts[2] == float.class && pts[3] == float.class
                        && pts[4] == float.class && pts[5] == float.class
                        && pts[6].getName().equals("ze")) {
                    roundRect = m; break;
                }
            }
            if (roundRect == null) {
                XposedBridge.log("[ds美化][C探针] tb.g(FFFFFFLze) NOT FOUND");
            } else {
                deoptimizeMethod(roundRect);
                final java.lang.reflect.Field fRealCanvasG = tbCls.getField("a");
                XposedBridge.hookMethod(roundRect, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            float l = (Float) param.args[0];
                            float t = (Float) param.args[1];
                            float r = (Float) param.args[2];
                            float b = (Float) param.args[3];
                            float rx = (Float) param.args[4];
                            float ry = (Float) param.args[5];
                            Object ze = param.args[6];
                            int color = -1;
                            if (fGPaint != null && ze != null) {
                                try {
                                    Object paint = fGPaint.invoke(null, ze);
                                    if (paint instanceof android.graphics.Paint) {
                                        color = ((android.graphics.Paint) paint).getColor();
                                    }
                                } catch (Throwable ignored) {}
                            }
                            float w = r - l, h = b - t;
                            if (w < 40 || h < 20) return;   // 太小不是气泡
                            // 调用栈判定
                            boolean isShapeStack = false;
                            StringBuilder sb = new StringBuilder("[ds美化][C探针] tb.g drawRoundRect");
                            sb.append(" rect=(").append(fmt(l)).append(",").append(fmt(t))
                              .append(",").append(fmt(r)).append(",").append(fmt(b)).append(")")
                              .append(" wh=(").append(fmt(w)).append("x").append(fmt(h)).append(")")
                              .append(" round=(").append(fmt(rx)).append(",").append(fmt(ry)).append(")")
                              .append(" color=").append(color == -1 ? "?" : String.format("#%08x", color));
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            int cnt = 0;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if (cn.startsWith("java.") || cn.startsWith("dalvik.")
                                        || cn.contains("XC_") || cn.contains("Xposed")
                                        || cn.startsWith("com.example")) continue;
                                if (cn.endsWith(".rj2") || cn.endsWith("xv0")) isShapeStack = true;
                                sb.append(" | ").append(cn).append(".").append(e.getMethodName());
                                if (++cnt >= 5) break;
                            }
                            if (!isShapeStack) return;  // 非形状栈跳过
                            // v502 诊断：打印 tb.g 所有候选（找 AI 气泡圆角矩形真实参数）
                            if (sShapeDiagLogged < 60 && w >= 100f && h >= 50f) {
                                sShapeDiagLogged++;
                                XposedBridge.log("[ds美化][v502diag] tb.g color=" + (color == -1 ? "?" : String.format("#%08x", color))
                                        + " wh=(" + fmt(w) + "x" + fmt(h) + ") round=(" + fmt(rx) + "," + fmt(ry) + ")");
                            }
                            // ★ v501：AI 气泡走 drawRoundRect（圆角矩形）—— 在这里也应用渐变！
                            {
                                android.content.Context gCtx = appCtx();
                                if (gCtx != null && DsConfig.gradientBubbleOn(gCtx)) {
                                    // AI 气泡：不透明彩色 + 尺寸合适（宽100-600 高50-500）
                                    boolean sizeOk = w >= 100f && w <= 600f && h >= 50f && h <= 500f;
                                    boolean isOpaqueColor = false;
                                    if (color != -1) {
                                        int aa = (color >>> 24) & 0xFF;
                                        int rr = (color >> 16) & 0xFF, gg = (color >> 8) & 0xFF, bb = color & 0xFF;
                                        boolean gray = Math.abs(rr - gg) < 12 && Math.abs(gg - bb) < 12;
                                        boolean nearWB = (rr > 245 && gg > 245 && bb > 245) || (rr < 12 && gg < 12 && bb < 12);
                                        isOpaqueColor = aa >= 0xF0 && !gray && !nearWB;
                                    }
                                    if (isOpaqueColor && sizeOk) {
                                        try {
                                            Object rc = fRealCanvasG.get(param.thisObject);
                                            if (rc instanceof android.graphics.Canvas) {
                                                android.graphics.Canvas cv = (android.graphics.Canvas) rc;
                                                int[][] pals = {
                                                        {0xFF4D6BFE, 0xFF8EA9FF}, {0xFF8B5CF6, 0xFFC4B5FD},
                                                        {0xFF10B981, 0xFF6EE7B7}, {0xFFFF6B6B, 0xFF4D6BFE},
                                                        {0xFFEC4899, 0xFF8B5CF6}, {0xFFF59E0B, 0xFFFBBF24}
                                                };
                                                int gPal = DsConfig.gradientPalette(gCtx);
                                                int cc1 = 0xFF4D6BFE, cc2 = 0xFF8EA9FF;
                                                if (gPal == 6) {
                                                    cc1 = DsConfig.gradientCustom1(gCtx);
                                                    cc2 = DsConfig.gradientCustom2(gCtx);
                                                } else {
                                                    int[] pal = pals[gPal % pals.length];
                                                    cc1 = pal[0]; cc2 = pal[1];
                                                }
                                                android.graphics.Shader sh = new android.graphics.LinearGradient(
                                                        l, t, r, b, cc1, cc2, android.graphics.Shader.TileMode.CLAMP);
                                                android.graphics.Paint gp = new android.graphics.Paint();
                                                gp.setShader(sh);
                                                // 用圆角矩形画（跟随原圆角 rx,ry）
                                                cv.drawRoundRect(l, t, r, b, rx, ry, gp);
                                                param.setResult(null);
                                                if (sCanvasProbeLogged < 400) {
                                                    sCanvasProbeLogged++;
                                                    XposedBridge.log("[ds美化][v501] AI气泡渐变(圆角) rect=(" + fmt(l) + "," + fmt(t)
                                                            + "," + fmt(r) + "," + fmt(b) + ") round=(" + fmt(rx) + "," + fmt(ry) + ")");
                                                }
                                                return;
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                            // ★ 只打诊断日志，不再画色框（避免污染搜索框等 UI）
                            if (sCanvasProbeLogged < 200) {
                                sCanvasProbeLogged++;
                                XposedBridge.log(sb.toString());
                            }
                        } catch (Throwable ignore) {}
                    }
                });
                XposedBridge.log("[ds美化][C探针] tb.g(drawRoundRect) hooked (只日志)");
            }
            // ★ v503：tb.e(hf,ze) = drawPath —— AI 气泡(se0圆角背景)可能走这里！
            java.lang.reflect.Method drawPathM = null;
            for (java.lang.reflect.Method m : tbCls.getDeclaredMethods()) {
                if (!"e".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 2 && pts[0].getName().equals("hf") && pts[1].getName().equals("ze")) {
                    drawPathM = m; break;
                }
            }
            if (drawPathM != null) {
                deoptimizeMethod(drawPathM);
                final java.lang.reflect.Field fPathA = null; // hf.a 在回调里取
                XposedBridge.hookMethod(drawPathM, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object hf = param.args[0];
                            Object ze = param.args[1];
                            if (hf == null || ze == null) return;
                            // 取真实 Path
                            java.lang.reflect.Field fa = hf.getClass().getField("a");
                            Object po = fa.get(hf);
                            if (!(po instanceof android.graphics.Path)) return;
                            android.graphics.Path path = (android.graphics.Path) po;
                            android.graphics.RectF rf = new android.graphics.RectF();
                            path.computeBounds(rf, true);
                            float pw = rf.width(), ph = rf.height();
                            // 取颜色
                            int pcolor = -1;
                            try {
                                Object paint = fGPaint.invoke(null, ze);
                                if (paint instanceof android.graphics.Paint) pcolor = ((android.graphics.Paint) paint).getColor();
                            } catch (Throwable ignored) {}
                            // v503 诊断：打印 drawPath 候选
                            if (sShapeDiagLogged < 60 && pw >= 100f && ph >= 50f) {
                                sShapeDiagLogged++;
                                XposedBridge.log("[ds美化][v503diag] tb.e path color=" + (pcolor == -1 ? "?" : String.format("#%08x", pcolor))
                                        + " wh=(" + fmt(pw) + "x" + fmt(ph) + ")");
                            }
                            // AI 气泡：不透明彩色 + 尺寸合适 → 画渐变
                            android.content.Context gCtx = appCtx();
                            if (gCtx != null && DsConfig.gradientBubbleOn(gCtx)) {
                                boolean sizeOk = pw >= 100f && pw <= 600f && ph >= 50f && ph <= 500f;
                                boolean isOpaque = false;
                                if (pcolor != -1) {
                                    int aa = (pcolor >>> 24) & 0xFF;
                                    int rr = (pcolor >> 16) & 0xFF, gg = (pcolor >> 8) & 0xFF, bb = pcolor & 0xFF;
                                    boolean gray = Math.abs(rr - gg) < 12 && Math.abs(gg - bb) < 12;
                                    boolean nearWB = (rr > 245 && gg > 245 && bb > 245) || (rr < 12 && gg < 12 && bb < 12);
                                    isOpaque = aa >= 0xF0 && !gray && !nearWB;
                                }
                                if (isOpaque && sizeOk) {
                                    try {
                                        Object rc = tbCls.getField("a").get(param.thisObject);
                                        if (rc instanceof android.graphics.Canvas) {
                                            android.graphics.Canvas cv = (android.graphics.Canvas) rc;
                                            int[][] pals = {
                                                    {0xFF4D6BFE, 0xFF8EA9FF}, {0xFF8B5CF6, 0xFFC4B5FD},
                                                    {0xFF10B981, 0xFF6EE7B7}, {0xFFFF6B6B, 0xFF4D6BFE},
                                                    {0xFFEC4899, 0xFF8B5CF6}, {0xFFF59E0B, 0xFFFBBF24}
                                            };
                                            int gPal = DsConfig.gradientPalette(gCtx);
                                            int cc1, cc2;
                                            if (gPal == 6) { cc1 = DsConfig.gradientCustom1(gCtx); cc2 = DsConfig.gradientCustom2(gCtx); }
                                            else { int[] pal = pals[gPal % pals.length]; cc1 = pal[0]; cc2 = pal[1]; }
                                            android.graphics.Shader sh = new android.graphics.LinearGradient(
                                                    rf.left, rf.top, rf.right, rf.bottom, cc1, cc2, android.graphics.Shader.TileMode.CLAMP);
                                            android.graphics.Paint gp = new android.graphics.Paint();
                                            gp.setShader(sh);
                                            gp.setAntiAlias(true);
                                            cv.drawPath(path, gp);
                                            param.setResult(null);
                                            if (sCanvasProbeLogged < 400) {
                                                sCanvasProbeLogged++;
                                                XposedBridge.log("[ds美化][v503] AI气泡渐变(Path) color=" + String.format("#%08x", pcolor)
                                                        + " wh=(" + fmt(pw) + "x" + fmt(ph) + ")");
                                            }
                                            return;
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log("[ds美化][v503] tb.e(drawPath) hooked");
            }
            // tb.k(FFFFLze)V = drawRect（兜底）
            java.lang.reflect.Method drawRect = null;
            for (java.lang.reflect.Method m : tbCls.getDeclaredMethods()) {
                if (!"k".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 5
                        && pts[0] == float.class && pts[1] == float.class
                        && pts[2] == float.class && pts[3] == float.class
                        && pts[4].getName().equals("ze")) {
                    drawRect = m; break;
                }
            }
            if (drawRect != null) {
                deoptimizeMethod(drawRect);
                final java.lang.reflect.Field fRealCanvasK = tbCls.getField("a");
                XposedBridge.hookMethod(drawRect, new XC_MethodHook() {
                    private float kl, kt, kr, kb;
                    private boolean kHit;
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        kHit = false;
                        try {
                            kl = (Float) param.args[0];
                            kt = (Float) param.args[1];
                            kr = (Float) param.args[2];
                            kb = (Float) param.args[3];
                            Object ze = param.args[4];
                            int color = -1;
                            if (fGPaint != null && ze != null) {
                                try {
                                    Object paint = fGPaint.invoke(null, ze);
                                    if (paint instanceof android.graphics.Paint) {
                                        color = ((android.graphics.Paint) paint).getColor();
                                    }
                                } catch (Throwable ignored) {}
                            }
                            float w = kr - kl, h = kb - kt;
                            if (w < 40 || h < 20) return;   // 太小不是气泡
                            // 调用栈判定
                            boolean isShapeStack = false;
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            int cnt = 0;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if (cn.startsWith("java.") || cn.startsWith("dalvik.")
                                        || cn.contains("XC_") || cn.contains("Xposed")
                                        || cn.startsWith("com.example")) continue;
                                if (cn.endsWith(".rj2") || cn.endsWith("xv0")) isShapeStack = true;
                                if (++cnt >= 5) break;
                            }
                            if (!isShapeStack) return;  // 非形状栈跳过
                            // ═══ v492：输入框玻璃化（识别宽>700 的浅色输入框条）═══
                            {
                                android.content.Context inCtx = appCtx();
                                if (inCtx != null && DsConfig.inputGlassOn(inCtx)) {
                                    // v493 诊断：打印所有宽>400 的形状绘制，找输入框真实特征
                                    if (sShapeDiagLogged < 40 && w > 400f) {
                                        sShapeDiagLogged++;
                                        XposedBridge.log("[ds美化][v493diag] wide color=" + (color == -1 ? "?" : String.format("#%08x", color))
                                                + " wh=(" + fmt(w) + "x" + fmt(h) + ") y=(" + fmt(kt) + "-" + fmt(kb) + ")");
                                    }
                                    // v494：输入框真实特征 = 透明色(#00000000) + 宽 700-900 + 高 100-160（实测 808x126）
                                    boolean isInputBar = (color == 0x00000000) && w >= 700f && w <= 900f && h >= 100f && h <= 160f;
                                    if (isInputBar) {
                                        try {
                                            Object rc = fRealCanvasK.get(param.thisObject);
                                            if (rc instanceof android.graphics.Canvas) {
                                                android.graphics.Canvas cv = (android.graphics.Canvas) rc;
                                                float h2 = kb - kt;
                                                // 玻璃底（半透明色，配置）
                                                android.graphics.Paint bgp = new android.graphics.Paint();
                                                bgp.setColor(DsConfig.inputGlassColor(inCtx));
                                                cv.drawRect(kl, kt, kr, kb, bgp);
                                                // 顶部高光
                                                android.graphics.Shader hl = new android.graphics.LinearGradient(
                                                        kl, kt, kl, kt + h2 * 0.5f,
                                                        0x66FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP);
                                                android.graphics.Paint hlp = new android.graphics.Paint();
                                                hlp.setShader(hl);
                                                cv.drawRect(kl, kt, kr, kt + h2 * 0.5f, hlp);
                                                // 边缘描边
                                                android.graphics.Paint ep = new android.graphics.Paint();
                                                ep.setStyle(android.graphics.Paint.Style.STROKE);
                                                ep.setStrokeWidth(1.5f);
                                                ep.setColor(0x55FFFFFF);
                                                cv.drawRect(kl, kt, kr, kb, ep);
                                                param.setResult(null);
                                                return;  // 已处理，跳过气泡逻辑
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                            // ★ v490 气泡识别（尺寸约束修复）：
                            //   实测：整屏容器 #d6f2ec 953x999/953x1715（假气泡）、输入框 #edf3fe 808x126
                            //   真气泡：用户 273x88；AI 气泡 228x77
                            //   v496fix：AI 气泡改用"彩色 + 尺寸"识别（颜色随配置/重绘变化，不锁定单一色）
                            boolean sizeOk = w >= 100f && w <= 600f && h >= 50f && h <= 500f;
                            // v499：AI 气泡识别（修正误伤）——用【不透明】+【彩色】+【尺寸】
                            //   误伤根因：#4700bfa5（半透明青绿图标 138x128）被当气泡
                            //   真气泡 #ffd6f2ec 是【不透明】(alpha=0xFF)；图标是【半透明】(alpha<0xFF)
                            boolean isOpaqueColor = false;
                            if (color != -1) {
                                int aa = (color >>> 24) & 0xFF;
                                int rr = (color >> 16) & 0xFF, gg = (color >> 8) & 0xFF, bb = color & 0xFF;
                                boolean gray = Math.abs(rr - gg) < 12 && Math.abs(gg - bb) < 12;
                                boolean nearWhiteOrBlack = (rr > 245 && gg > 245 && bb > 245) || (rr < 12 && gg < 12 && bb < 12);
                                // 不透明（alpha>=0xF0）+ 彩色 → 气泡
                                isOpaqueColor = aa >= 0xF0 && !gray && !nearWhiteOrBlack;
                            }
                            boolean isUserBubble = (color == 0xFFEDF3FE) && sizeOk;
                            boolean isAiBubble = isOpaqueColor && sizeOk;
                            if (!isUserBubble && !isAiBubble) return;
                            kHit = true;
                            // v498 诊断：记录气泡识别命中（看是否持续执行）
                            if (sBubbleBoxLogged < 80) {
                                sBubbleBoxLogged++;
                                XposedBridge.log("[ds美化][v498diag] BUBBLE-HIT color=" + String.format("#%08x", color)
                                        + " wh=(" + fmt(w) + "x" + fmt(h) + ") ai=" + isAiBubble + " user=" + isUserBubble);
                            }

                            // ═══ v446 方案C：液态玻璃气泡（纯Canvas，不走blur，防崩）═══
                            // glassBubbleOn=true 时：半透明白底 + 顶部高光 + 边缘描边 = 玻璃质感
                            // 优先级：玻璃 > 渐变 > 原生
                            boolean glassOn = false;
                            boolean gradOn = false;
                            int gStyle = 0, gPalette = 0;
                            final android.content.Context[] gCtxRef = new android.content.Context[1];
                            try {
                                gCtxRef[0] = appCtx();
                                if (gCtxRef[0] != null) {
                                    // v448：玻璃入口统一 —— 独立开关 OR 气泡样式=3(液态玻璃)
                                    glassOn = DsConfig.glassBubbleOn(gCtxRef[0]) || (DsConfig.bubbleStyle(gCtxRef[0]) == 3);
                                    gradOn = DsConfig.gradientBubbleOn(gCtxRef[0]);
                                    gStyle = DsConfig.gradientStyle(gCtxRef[0]);
                                    gPalette = DsConfig.gradientPalette(gCtxRef[0]);
                                }
                            } catch (Throwable ignored) {}
                            if (glassOn) {
                                try {
                                    Object realCanvas = fRealCanvasK.get(param.thisObject);
                                    if (realCanvas instanceof android.graphics.Canvas) {
                                        android.graphics.Canvas cv = (android.graphics.Canvas) realCanvas;
                                        float w2 = kr - kl, h2 = kb - kt;
                                        if (w2 <= 0 || h2 <= 0) return;
                                        // 半透明玻璃模拟（v446 安全版，不卡）
                                        android.graphics.Paint bgp = new android.graphics.Paint();
                                        bgp.setColor(0x66F5F8FF);
                                        cv.drawRect(kl, kt, kr, kb, bgp);
                                        // 顶部高光
                                        android.graphics.Shader hl = new android.graphics.LinearGradient(
                                                kl, kt, kl, kt + h2 * 0.55f,
                                                0xA6FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP);
                                        android.graphics.Paint hlp = new android.graphics.Paint();
                                        hlp.setShader(hl);
                                        cv.drawRect(kl, kt, kr, kt + h2 * 0.55f, hlp);
                                        // 底部微暗
                                        android.graphics.Shader bd = new android.graphics.LinearGradient(
                                                kl, kb - h2 * 0.35f, kl, kb,
                                                0x00FFFFFF, 0x1AFFFFFF, android.graphics.Shader.TileMode.CLAMP);
                                        android.graphics.Paint bdp = new android.graphics.Paint();
                                        bdp.setShader(bd);
                                        cv.drawRect(kl, kb - h2 * 0.35f, kr, kb, bdp);
                                        // 描边（玻璃边缘）
                                        android.graphics.Paint ep = new android.graphics.Paint();
                                        ep.setStyle(android.graphics.Paint.Style.STROKE);
                                        ep.setStrokeWidth(2f);
                                        ep.setColor(0x66FFFFFF);
                                        cv.drawRect(kl, kt, kr, kb, ep);
                                        // 跳过原纯色绘制
                                        param.setResult(null);
                                        if (sCanvasProbeLogged < 400) {
                                            sCanvasProbeLogged++;
                                            XposedBridge.log("[ds美化][v446] 液态玻璃 wh=(" + fmt(w2) + "x" + fmt(h2) + ")");
                                        }
                                    }
                                } catch (Throwable te) {
                                    XposedBridge.log("[ds美化][v446] 玻璃EX " + te);
                                }
                                return;
                            }

                            // ═══ v437 【方案C】渐变气泡（可配置样式+配色）═══
                            if (gradOn) {
                                try {
                                    Object realCanvas = fRealCanvasK.get(param.thisObject);
                                    if (realCanvas instanceof android.graphics.Canvas) {
                                        android.graphics.Canvas cv = (android.graphics.Canvas) realCanvas;
                                        float w2 = kr - kl, h2 = kb - kt;
                                        if (w2 <= 0 || h2 <= 0) return;
                                        int[][] palettes = {
                                                {0xFF4D6BFE, 0xFF8EA9FF},
                                                {0xFF8B5CF6, 0xFFC4B5FD},
                                                {0xFF10B981, 0xFF6EE7B7},
                                                {0xFFFF6B6B, 0xFF4D6BFE},
                                                {0xFFEC4899, 0xFF8B5CF6},
                                                {0xFFF59E0B, 0xFFFBBF24}
                                        };
                                        int c1 = 0xFF4D6BFE, c2 = 0xFF8EA9FF;
                                        if (gPalette == 6 && gCtxRef[0] != null) {
                                            c1 = DsConfig.gradientCustom1(gCtxRef[0]);
                                            c2 = DsConfig.gradientCustom2(gCtxRef[0]);
                                        } else {
                                            int[] pal = palettes[gPalette % palettes.length];
                                            c1 = pal[0]; c2 = pal[1];
                                        }
                                        android.graphics.Shader sh = null;
                                        switch (gStyle) {
                                            case 1:
                                                sh = new android.graphics.LinearGradient(kl, kt, kl, kb, c1, c2, android.graphics.Shader.TileMode.CLAMP);
                                                break;
                                            case 2:
                                                sh = new android.graphics.LinearGradient(kl, kt, kr, kt, c1, c2, android.graphics.Shader.TileMode.CLAMP);
                                                break;
                                            case 3: {
                                                float cx = kl + w2 / 2f, cy = kt + h2 / 2f;
                                                float rad = Math.max(w2, h2) * 0.8f + 1f;
                                                sh = new android.graphics.RadialGradient(cx, cy, rad, c1, c2, android.graphics.Shader.TileMode.CLAMP);
                                                break;
                                            }
                                            default:
                                                sh = new android.graphics.LinearGradient(kl, kt, kr, kb, c1, c2, android.graphics.Shader.TileMode.CLAMP);
                                                break;
                                        }
                                        android.graphics.Paint gp = new android.graphics.Paint();
                                        gp.setShader(sh);
                                        cv.drawRect(kl, kt, kr, kb, gp);
                                        // v504 诊断：单独计数（不受 400 限流影响）
                                        if (sGradLogged < 100) {
                                            sGradLogged++;
                                            XposedBridge.log("[ds美化][v504diag] GRAD-DRAWN rect=(" + fmt(kl) + "," + fmt(kt)
                                                    + "," + fmt(kr) + "," + fmt(kb) + ") c1=" + String.format("#%08x", c1));
                                        }
                                        param.setResult(null);
                                    }
                                } catch (Throwable te) {
                                    XposedBridge.log("[ds美化][v437] 渐变EX " + te);
                                }
                            }
                        } catch (Throwable ignore) {}
                    }
                });
                XposedBridge.log("[ds美化][C探针] tb.k(drawRect) hooked (v444渐变气泡)");
            }

        } catch (Throwable t) {
            XposedBridge.log("[ds美化][C探针] hookBubbleCanvas EX " + t);
        }
    }

    private static String fmt(float v) {
        if (v == Math.floor(v) && !Float.isInfinite(v)) return String.valueOf((int) v);
        return String.format("%.1f", v);
    }

    /**
     * v300：主题色板诊断 —— hook a42(ColorScheme) 构造器，dump 所有颜色字段。
     * 找出用户气泡文字色（黑色 #000000 附近）的字段，为文字颜色 hook 铺路。
     */
    private static volatile int sBubble300Logged = 0;

    /** 气泡生效判定：气泡美化开启 **或** 壁纸/聊天背景开启（壁纸下自动加气泡底，文字不被壁纸干扰） */
    private static boolean bubbleActive(android.content.Context ctx) {
        if (ctx == null) return false;
        return DsConfig.bubbleOn(ctx) || DsConfig.bgOn(ctx) || DsConfig.chatBgOn(ctx);
    }
    private static void hookBubble300(final ClassLoader cl) {
        try {
            final Class<?> a42Cls = cl.loadClass("a42");
            for (java.lang.reflect.Constructor<?> ctor : a42Cls.getDeclaredConstructors()) {
                if (ctor.getParameterTypes().length != 48) continue;  // 实测48个J
                // 字段 o/q = onSurface（文字色，v300 实测 o=#191B23）
                final java.lang.reflect.Field fO = a42Cls.getDeclaredField("o");
                fO.setAccessible(true);
                final java.lang.reflect.Field fQ = a42Cls.getDeclaredField("q");
                fQ.setAccessible(true);
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            // v320：气泡总开关 —— 气泡美化开启 **或** 壁纸/聊天背景开启时生效（壁纸下文字自动加底）
                            android.content.Context gCtx = appCtx();
                            if (!bubbleActive(gCtx)) return;
                            Object scheme = param.thisObject;
                            if (scheme == null) return;
                            android.content.Context ctx = appCtx();
                            if (ctx == null) return;
                            // 文字颜色：0=自动(深底白字) 1-6=预设 7=自定义色盘
                            int tc = DsConfig.msgTextColor(ctx);
                            long textColor;
                            if (tc == 7) {
                                // 自定义色盘
                                textColor = ((long) DsConfig.msgCustomColor(ctx)) & 0xFFFFFFFFL;
                                textColor = textColor << 32;
                            } else if (tc == 1) {
                                textColor = 0xFF000000L << 32;
                            } else if (tc == 2) {
                                textColor = 0xFFFFFFFFL << 32;
                            } else if (tc == 3) {
                                textColor = 0xFF4D6BFEL << 32;
                            } else if (tc == 4) {
                                textColor = 0xFF555555L << 32;
                            } else if (tc == 5) {
                                textColor = 0xFFE53935L << 32;
                            } else if (tc == 6) {
                                textColor = 0xFF2E7D32L << 32;
                            } else {
                                // 自动：深色背景白字 / 浅色背景黑字
                                int customColor = DsConfig.bubbleColor(ctx);
                                // v458：渐变配色影响文字色 —— 深色系渐变(品牌蓝/紫色)用白字
                                boolean gradOn2 = DsConfig.gradientBubbleOn(ctx);
                                int gp2 = DsConfig.gradientPalette(ctx);
                                boolean glassStyle = DsConfig.glassBubbleOn(ctx) || DsConfig.bubbleStyle(ctx) == 3;
                                boolean gradDark = gradOn2 && (gp2 == 0 || gp2 == 1);  // 品牌蓝/紫色渐变较深
                                boolean darkBubble = DsConfig.isDarkMode(ctx)
                                        || gradDark
                                        || (customColor >= 1 && customColor <= 6) || (customColor == 0 && DsConfig.bubbleStyle(ctx) >= 1);
                                // 液态玻璃是浅色半透明 → 黑字
                                if (glassStyle) darkBubble = false;
                                textColor = darkBubble ? (0xFFFFFFFFL << 32) : (0xFF191B23L << 32);
                            }
                            try {
                                fO.setLong(scheme, textColor);
                                fQ.setLong(scheme, textColor);
                                if (sBubble300Logged++ < 5) {
                                    XposedBridge.log("[ds美化] v300 onSurface -> " + String.format("#%08x", textColor >>> 32));
                                }
                            } catch (Throwable te) {
                                XposedBridge.log("[ds美化] v300 set EX " + te);
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v300 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v300 a42 ctor hooked (onSurface白字)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v300 hookBubble300 EX " + t);
        }
    }

    /**
     * v276：AI 文本贴合气泡 —— hook ru0.c（文本渲染核心，带 String + Lc76）。
     * 给文本层 modifier 加 圆角clip+背景，气泡贴合文字宽度（iMessage 风格）。
     * 验证：文本段落独立成气泡，宽度=内容宽度。
     */
    private static volatile int sBubble276Logged = 0;
    // v481：v276 注入的 AI 气泡色（动态记录，tb.k 用它精准识别 AI 气泡，替代硬编码）
    private static volatile int sAiBubbleColor = 0xFFE8F0FE;
    // v484：v276 气泡绘制标记（AI 气泡布局→绘制窗口期标记，画布层只处理标记期间）
    private static volatile boolean sBubbleMark = false;
    private static volatile long sBubbleMarkTime = 0;

    private static void hookBubble276(final ClassLoader cl) {
        try {
            final Class<?> ru0Cls = cl.loadClass("ru0");
            boolean hooked = false;
            for (java.lang.reflect.Method m : ru0Cls.getDeclaredMethods()) {
                if (!"c".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 14) continue;   // v277实测14参数
                if (!pts[10].getName().equals("c76")) continue;
                if (!pts[0].getName().equals("java.lang.String")) continue;
                // se0 构造器
                final Class<?> se0Cls = cl.loadClass("se0");
                final Class<?> sn0Cls = cl.loadClass("sn0");
                final Class<?> md8Cls = cl.loadClass("md8");
                final java.lang.reflect.Constructor<?> cSe0 =
                        se0Cls.getConstructor(long.class, sn0Cls, md8Cls, int.class);
                cSe0.setAccessible(true);
                final Class<?> zcCls = cl.loadClass("zc");
                final java.lang.reflect.Field fZcO = zcCls.getField("o");
                // u9a.A(Lc76;Lmd8;)Lc76 = clip 工厂
                final Class<?> u9aCls = cl.loadClass("u9a");
                java.lang.reflect.Method clipMethod = null;
                for (java.lang.reflect.Method um : u9aCls.getDeclaredMethods()) {
                    if ("A".equals(um.getName())) {
                        Class<?>[] upts = um.getParameterTypes();
                        if (upts.length == 2 && upts[0].getName().equals("c76")
                                && upts[1].getName().equals("md8")) {
                            clipMethod = um;
                            break;
                        }
                    }
                }
                // kd1.s = 用户气泡圆角形状
                final Class<?> kd1Cls = cl.loadClass("kd1");
                final java.lang.reflect.Field fKd1S = kd1Cls.getField("s");
                final Class<?> c76ClsF = cl.loadClass("c76");
                final java.lang.reflect.Method fClip = clipMethod;
                // jt5.E(Lc76;Lft6;)Lc76 = padding 工厂；kt6(FFFF) = PaddingValues（内边距）
                final Class<?> jt5Cls = cl.loadClass("jt5");
                final Class<?> kt6Cls = cl.loadClass("kt6");
                java.lang.reflect.Method padMethod = null;
                for (java.lang.reflect.Method jm : jt5Cls.getDeclaredMethods()) {
                    if ("E".equals(jm.getName())) {
                        Class<?>[] jpts = jm.getParameterTypes();
                        if (jpts.length == 2 && jpts[0].getName().equals("c76")
                                && jpts[1].getName().equals("ft6")) {
                            padMethod = jm;
                            break;
                        }
                    }
                }
                java.lang.reflect.Constructor<?> cKt6 = null;
                for (java.lang.reflect.Constructor<?> kc : kt6Cls.getDeclaredConstructors()) {
                    Class<?>[] kpts = kc.getParameterTypes();
                    if (kpts.length == 4 && kpts[0] == float.class) { cKt6 = kc; break; }
                }
                if (cKt6 != null) cKt6.setAccessible(true);
                final java.lang.reflect.Method fPad = padMethod;
                final java.lang.reflect.Constructor<?> fKt6 = cKt6;
                // lx0.p(Lc76;FJLmd8;)Lc76 = border 工厂（气泡描边）
                final Class<?> lx0Cls = cl.loadClass("lx0");
                java.lang.reflect.Method borderMethod = null;
                for (java.lang.reflect.Method lm : lx0Cls.getDeclaredMethods()) {
                    if ("p".equals(lm.getName())) {
                        Class<?>[] lpts = lm.getParameterTypes();
                        if (lpts.length == 4 && lpts[0].getName().equals("c76")
                                && lpts[1] == float.class && lpts[2] == long.class) {
                            borderMethod = lm;
                            break;
                        }
                    }
                }
                final java.lang.reflect.Method fBorder = borderMethod;
                // tt7.c(FFFF)Lst7 = 圆角形状工厂（动态圆角半径）
                final Class<?> tt7Cls = cl.loadClass("tt7");
                java.lang.reflect.Method cornerMethod = null;
                for (java.lang.reflect.Method tm2 : tt7Cls.getDeclaredMethods()) {
                    if ("c".equals(tm2.getName())) {
                        Class<?>[] tpts = tm2.getParameterTypes();
                        if (tpts.length == 4 && tpts[0] == float.class) {
                            cornerMethod = tm2;
                            break;
                        }
                    }
                }
                final java.lang.reflect.Method fCorner = cornerMethod;
                deoptimizeMethod(m);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            // v320：气泡总开关 —— 气泡美化开启 **或** 壁纸/聊天背景开启时生效（壁纸下自动加底）
                            android.content.Context gCtx = appCtx();
                            if (!bubbleActive(gCtx)) return;
                            Object modifier = param.args[10];
                            if (modifier == null) return;
                            // 注入不做次数限制（修复：消息多时后续文本块不生效的bug）
                            // 日志才限制次数避免刷屏
                            // AI 文本气泡颜色
                            long aiColor = 0xFFF2F2F4L;
                            android.content.Context aCtx = appCtx();
                            if (aCtx != null) {
                                // 深色/浅色都用浅色系气泡（深色聊天底上浅气泡清晰，且文字仍可读）
                                int sel = DsConfig.aiBubbleColor(aCtx);
                                if (sel == 7) {
                                    // 自定义调色盘颜色
                                    long custom = ((long) DsConfig.aiBubbleCustomColor(aCtx)) & 0xFFFFFFL;
                                    aiColor = 0xFF000000L | custom;
                                } else {
                                    switch (sel) {
                                        case 1: aiColor = 0xFFE8F0FEL; break;
                                        case 2: aiColor = 0xFFDCE4FEL; break;
                                        case 3: aiColor = 0xFFD6F2ECL; break;
                                        case 4: aiColor = 0xFFEAE3FEL; break;
                                        case 5: aiColor = 0xFFFEE3E9L; break;
                                        case 6: aiColor = 0xFFFFE8CCL; break;
                                        default: aiColor = 0xFFF2F2F4L; break;
                                    }
                                }
                            }
                            // v505：AI 气泡玻璃化（半透明液态玻璃感）——降低 alpha
                            try {
                                android.content.Context ggCtx = appCtx();
                                if (ggCtx != null && DsConfig.aiGlassOn(ggCtx)) {
                                    int pct = DsConfig.aiGlassAlpha(ggCtx);
                                    if (pct < 40) pct = 40;
                                    if (pct > 100) pct = 100;
                                    int alpha = (int) (pct * 255L / 100L);
                                    long rgb = aiColor & 0x00FFFFFFL;
                                    aiColor = ((long) alpha << 24) | rgb;
                                }
                            } catch (Throwable ignored) {}
                            Object brush = fZcO.get(null);
                            // v481：记录注入的 AI 气泡色，供 tb.k 精准识别
                            sAiBubbleColor = (int) (aiColor & 0xFFFFFFFFL);
                            // v318：只保留背景色+圆角（幂等，重复注入无视觉差异）
                            // 删除 border/padding 叠加：Compose 重组多次调用时 padding/border 会无限累加
                            Object modifier2 = modifier;
                            // 2. 圆角 clip：按样式区分（v323 美化：非方角样式都带聊天尾巴；方角=小圆角）
                            Object shape = null;
                            try {
                                android.content.Context rCtx = appCtx();
                                float radius = 18f;
                                int bStyle = 0;
                                if (rCtx != null) {
                                    radius = DsConfig.bubbleRadius(rCtx);
                                    bStyle = DsConfig.bubbleStyle(rCtx);
                                }
                                if (radius < 0) radius = 0;
                                if (radius > 32) radius = 32;
                                if (fCorner != null) {
                                    if (bStyle == 2) {
                                        // 方角：统一小圆角（真正利落方正）
                                        float sq = Math.min(4f, radius);
                                        shape = fCorner.invoke(null, Float.valueOf(sq),
                                                Float.valueOf(sq), Float.valueOf(sq), Float.valueOf(sq));
                                    } else {
                                        // 正式尾巴版 v325：参数序已确认 (左上, 右上, 右下, 左下)
                                        // AI 气泡在左侧 → 左下角做小圆角 = 聊天尾巴（微信风）
                                        // 尾巴始终很小(≤4)，主圆角跟随滑块；滑块=0时尾巴=0但主圆角也=0（此时用默认18显示效果）
                                        float mainR = (radius <= 2f) ? 18f : radius;  // 滑块0时用默认18保证可见
                                        // v447：改为四角均匀圆角（去尾巴，iOS 胶囊风）
                                        shape = fCorner.invoke(null, Float.valueOf(mainR),
                                                Float.valueOf(mainR), Float.valueOf(mainR), Float.valueOf(mainR));
                                        XposedBridge.log("[ds美化] v447 均匀圆角 style=" + bStyle
                                                + " mainR=" + mainR
                                                + " fCorner=" + (fCorner != null) + " fClip=" + (fClip != null));
                                    }
                                } else {
                                    XposedBridge.log("[ds美化] v323 fCorner=null 圆角工厂未匹配！");
                                }
                            } catch (Throwable tce) {
                                XposedBridge.log("[ds美化] v323 圆角 EX " + tce);
                                shape = null;
                            }
                            if (shape == null) {
                                try { shape = fKd1S.get(null); } catch (Throwable ignored) {}
                            }
                            Object clipped = modifier2;
                            if (fClip != null && shape != null) {
                                clipped = fClip.invoke(null, modifier2, shape);
                            }
                            // 3. 背景 se0
                            // v425：回退渐变（ij 导致气泡异常），恢复纯色
                            Object useBrush = brush;
                            Object se0 = cSe0.newInstance(Long.valueOf(aiColor << 32), null, useBrush,
                                    Integer.valueOf(0));
                            java.lang.reflect.Method rMethod = null;
                            try {
                                rMethod = c76ClsF.getMethod("r", c76ClsF);
                            } catch (Throwable t1) {
                                try { rMethod = clipped.getClass().getMethod("r", c76ClsF); }
                                catch (Throwable t2) { rMethod = null; }
                            }
                            if (rMethod == null) { XposedBridge.log("[ds美化] v276 r NOT FOUND"); return; }
                            rMethod.setAccessible(true);
                            Object newModifier = rMethod.invoke(clipped, se0);
                            param.args[10] = newModifier;
                            // v484：打气泡标记（AI 气泡布局→绘制窗口期），画布层只处理标记期间
                            sBubbleMark = true;
                            sBubbleMarkTime = System.currentTimeMillis();
                            try {
                                final long t0 = sBubbleMarkTime;
                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                                    public void run() {
                                        // 200ms 后若仍是同一轮标记则清除
                                        if (sBubbleMarkTime == t0) sBubbleMark = false;
                                    }
                                }, 200);
                            } catch (Throwable ignored) {}
                            if (sBubble276Logged <= 5) {
                                XposedBridge.log("[ds美化] v276 AI文本气泡 " + String.format("#%08x", aiColor)
                                        + " text='" + String.valueOf(param.args[0]).substring(0,
                                        Math.min(20, String.valueOf(param.args[0]).length())) + "'");
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v276 ru0.c EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v276 ru0.c hooked (AI文本贴合气泡)");
                hooked = true;
                break;
            }
            if (!hooked) XposedBridge.log("[ds美化] v276 ru0.c NOT MATCHED");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v276 hookBubble276 EX " + t);
        }
    }

    /**
     * v275：ru0.b 双 modifier 诊断 —— AI 文本条目层有两个 Lc76，确认哪个贴合文本。
     */
    private static volatile int sBubble275Logged = 0;

    private static void hookBubble275(final ClassLoader cl) {
        try {
            final Class<?> ru0Cls = cl.loadClass("ru0");
            boolean hooked = false;
            for (java.lang.reflect.Method m : ru0Cls.getDeclaredMethods()) {
                if (!"b".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 14) continue;
                if (!pts[9].getName().equals("c76")) continue;
                deoptimizeMethod(m);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (sBubble275Logged++ > 6) return;
                        StringBuilder sb = new StringBuilder("[ds美化] v275 ru0.b modifiers:");
                        try {
                            Object m0 = param.args[9];
                            Object m1 = param.args[10];
                            sb.append("\n  [9] type=").append(m0 == null ? "null" : m0.getClass().getName())
                              .append(" hash=").append(m0 == null ? "-" : Integer.toHexString(System.identityHashCode(m0)));
                            sb.append("\n  [10] type=").append(m1 == null ? "null" : m1.getClass().getName())
                              .append(" hash=").append(m1 == null ? "-" : Integer.toHexString(System.identityHashCode(m1)));
                            // 调用栈前几帧
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            int cnt = 0;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if (cn.startsWith("java.") || cn.startsWith("dalvik.")
                                        || cn.contains("XC_") || cn.contains("Xposed") || cn.startsWith("E.Aaj")
                                        || cn.startsWith("com.example")) continue;
                                sb.append("\n  | ").append(cn).append(".").append(e.getMethodName());
                                if (++cnt >= 5) break;
                            }
                        } catch (Throwable t) {
                            sb.append("\n  EX ").append(t);
                        }
                        XposedBridge.log(sb.toString());
                    }
                });
                XposedBridge.log("[ds美化] v275 ru0.b hooked (双modifier诊断)");
                hooked = true;
                break;
            }
            if (!hooked) XposedBridge.log("[ds美化] v275 ru0.b NOT MATCHED");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v275 hookBubble275 EX " + t);
        }
    }

    /**
     * v274：AI 内容层贴合气泡 —— hook e30（AssistantChatMessageContent lambda）构造器，
     * 给内容层 modifier（args[7]=Lc76）加 圆角clip+背景，让气泡贴合文字宽度（iMessage 风格）。
     */
    private static volatile int sBubble274Logged = 0;

    private static void hookBubble274(final ClassLoader cl) {
        try {
            final Class<?> e30Cls = cl.loadClass("e30");
            boolean hooked = false;
            for (java.lang.reflect.Constructor<?> ctor : e30Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 14) continue;
                if (!pts[7].getName().equals("c76")) continue;
                // se0 构造器 (long, sn0, md8, int)
                final Class<?> se0Cls = cl.loadClass("se0");
                final Class<?> sn0Cls = cl.loadClass("sn0");
                final Class<?> md8Cls = cl.loadClass("md8");
                final java.lang.reflect.Constructor<?> cSe0 =
                        se0Cls.getConstructor(long.class, sn0Cls, md8Cls, int.class);
                cSe0.setAccessible(true);
                final Class<?> zcCls = cl.loadClass("zc");
                final java.lang.reflect.Field fZcO = zcCls.getField("o");
                // u9a.A(Lc76;Lmd8;)Lc76 = clip 工厂
                final Class<?> u9aCls = cl.loadClass("u9a");
                java.lang.reflect.Method clipMethod = null;
                for (java.lang.reflect.Method um : u9aCls.getDeclaredMethods()) {
                    if ("A".equals(um.getName())) {
                        Class<?>[] upts = um.getParameterTypes();
                        if (upts.length == 2 && upts[0].getName().equals("c76")
                                && upts[1].getName().equals("md8")) {
                            clipMethod = um;
                            break;
                        }
                    }
                }
                // kd1.s = 圆角形状
                final Class<?> kd1Cls = cl.loadClass("kd1");
                final java.lang.reflect.Field fKd1S;
                try { fKd1S = kd1Cls.getField("s"); } catch (Throwable t) { throw t; }
                final Class<?> c76ClsF = cl.loadClass("c76");
                final java.lang.reflect.Method fClip = clipMethod;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object modifier = param.args[7];  // e30.h = Lc76 内容层 modifier
                            if (modifier == null) return;
                            if (sBubble274Logged++ > 20) return;
                            // AI 内容层气泡颜色：浅灰（贴合文字）
                            long aiColor = 0xFFF0F0F2L;
                            android.content.Context aCtx = appCtx();
                            if (aCtx != null) {
                                int sel = DsConfig.aiBubbleColor(aCtx);
                                switch (sel) {
                                    case 1: aiColor = 0xFFE8F0FEL; break;
                                    case 2: aiColor = 0xFFDCE4FEL; break;
                                    case 3: aiColor = 0xFFD6F2ECL; break;
                                    case 4: aiColor = 0xFFEAE3FEL; break;
                                    case 5: aiColor = 0xFFFEE3E9L; break;
                                    case 6: aiColor = 0xFFFFE8CCL; break;
                                    default: aiColor = 0xFFF0F0F2L; break;
                                }
                            }
                            Object brush = fZcO.get(null);
                            // 1. 圆角 clip
                            Object shape = fKd1S.get(null);
                            Object clipped = modifier;
                            if (fClip != null && shape != null) {
                                clipped = fClip.invoke(null, modifier, shape);
                            }
                            // 2. 背景 se0
                            Object se0 = cSe0.newInstance(Long.valueOf(aiColor << 32), null, brush,
                                    Integer.valueOf(0));
                            java.lang.reflect.Method rMethod = null;
                            try {
                                rMethod = c76ClsF.getMethod("r", c76ClsF);
                            } catch (Throwable t1) {
                                try { rMethod = clipped.getClass().getMethod("r", c76ClsF); }
                                catch (Throwable t2) { rMethod = null; }
                            }
                            if (rMethod == null) { XposedBridge.log("[ds美化] v274 r NOT FOUND"); return; }
                            rMethod.setAccessible(true);
                            Object newModifier = rMethod.invoke(clipped, se0);
                            param.args[7] = newModifier;
                            XposedBridge.log("[ds美化] v274 AI内容层气泡 " + String.format("#%08x", aiColor));
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v274 e30 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v274 e30 ctor hooked (AI内容层气泡)");
                hooked = true;
                break;
            }
            if (!hooked) XposedBridge.log("[ds美化] v274 e30 ctor NOT MATCHED");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v274 hookBubble274 EX " + t);
        }
    }

    /**
     * v267：c30.a 参数类型诊断 —— 打印每个 args 的实际类型。
     * v265 崩溃：替换 modifier 时类型不匹配。先确认 c76 modifier 在哪个索引。
     */
    private static volatile int sBubble267Logged = 0;

    private static void hookBubble267(final ClassLoader cl) {
        try {
            final Class<?> c30Cls = cl.loadClass("c30");
            for (java.lang.reflect.Method m : c30Cls.getDeclaredMethods()) {
                if (!"a".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 14) continue;
                if (!pts[11].getName().equals("c76")) continue;
                deoptimizeMethod(m);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (sBubble267Logged++ > 3) return;
                        StringBuilder sb = new StringBuilder("[ds美化] v267 c30.a args类型:");
                        java.lang.reflect.Method m = (java.lang.reflect.Method) param.method;
                        for (int i = 0; i < param.args.length; i++) {
                            Object a = param.args[i];
                            sb.append("\n  [").append(i).append("] decl=");
                            try {
                                sb.append(m.getParameterTypes()[i].getName());
                            } catch (Throwable t) { sb.append("?"); }
                            sb.append(" actual=").append(a == null ? "null" : a.getClass().getName());
                        }
                        XposedBridge.log(sb.toString());
                    }
                });
                XposedBridge.log("[ds美化] v267 c30.a hooked (参数诊断)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v267 hookBubble267 EX " + t);
        }
    }

    /**
     * v265：AI 消息气泡 —— hook c30.a（AssistantChatMessageCell 主体），
     * 给 AI 消息 modifier 追加背景（调用 App 自身 tia.v，零构造器，绝对安全）。
     * 背景色：液态玻璃白/浅灰（可后续配置），圆角跟随气泡圆角。
     */
    private static volatile int sBubble265Logged = 0;

    private static void hookBubble265(final ClassLoader cl) {
        try {
            XposedBridge.log("[ds美化] v265 start cl=" + cl);
            final Class<?> c30Cls = cl.loadClass("c30");
            XposedBridge.log("[ds美化] v265 c30 loaded " + c30Cls.getName());
            for (java.lang.reflect.Method m : c30Cls.getDeclaredMethods()) {
                if (!"a".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 14) continue;
                if (!pts[11].getName().equals("c76")) continue;
                // 反射拿到 tia.v 和 zc.o
                final Class<?> tiaCls = cl.loadClass("tia");
                final java.lang.reflect.Method mTiaV = null;
                final java.lang.reflect.Method[] tiaMethods = tiaCls.getDeclaredMethods();
                java.lang.reflect.Method tiaV = null;
                for (java.lang.reflect.Method tm : tiaMethods) {
                    if ("v".equals(tm.getName())) {
                        Class<?>[] tpts = tm.getParameterTypes();
                        if (tpts.length == 3 && tpts[0].getName().equals("c76")
                                && tpts[1].getName().equals("long")) {
                            tiaV = tm;
                            break;
                        }
                    }
                }
                if (tiaV == null) { XposedBridge.log("[ds美化] v265 tia.v NOT FOUND"); break; }
                final java.lang.reflect.Method fTiaV = tiaV;
                final Class<?> zcCls = cl.loadClass("zc");
                final java.lang.reflect.Field fZcO = zcCls.getField("o");
                deoptimizeMethod(m);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            // Xposed args 索引比反射偏移 1：c76 modifier 在 args[12]
                            Object modifier = param.args[12];
                            if (modifier == null) return;
                            if (sBubble265Logged++ > 8) return;
                            // AI 消息气泡：液态玻璃浅灰白（半透明）
                            long aiColor = 0xE6F0F0F2L;  // 90% 浅灰白（原 AI 区接近白色）
                            Object brush = fZcO.get(null);
                            // 调用 tia.v(modifier, color, brush) 追加背景
                            Object newModifier = fTiaV.invoke(null, modifier,
                                    Long.valueOf(aiColor << 32), brush);
                            param.args[10] = newModifier;
                            XposedBridge.log("[ds美化] v265 AI气泡 已加背景 " + String.format("#%08x", aiColor));
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v265 c30.a EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v265 c30.a hooked (AI气泡)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v265 hookBubble265 EX " + t);
        }
    }

    /**
     * v264：气泡透明度验证 —— hook ls9 背景 tia.v 打印实际 alpha。
     * 确认 35% 半透明是否真的传到了渲染层。
     */
    private static volatile int sBubble264Logged = 0;

    private static void hookBubble264(final ClassLoader cl) {
        try {
            final Class<?> se0Cls = cl.loadClass("se0");
            for (java.lang.reflect.Constructor<?> ctor : se0Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 4) continue;
                if (!pts[0].getName().equals("long")) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            long color = ((Long) param.args[0]).longValue();
                            long rgb = color >>> 32;
                            if (rgb == 0xffedf3feL || rgb == 0xffecf2feL
                                    || rgb == 0x597c4dffL || rgb == 0x404d6bfeL) return; // 跳过已知
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            boolean fromBubble = false;
                            for (StackTraceElement e : st) {
                                if (e.getClassName().equals("ls9")) { fromBubble = true; break; }
                            }
                            if (!fromBubble) return;
                            if (sBubble264Logged++ > 20) return;
                            long a = (rgb >>> 24) & 0xFF;
                            XposedBridge.log("[ds美化] v264 气泡色 " + String.format("#%08x", rgb)
                                    + " alpha=" + a + "%=" + (a * 100 / 255));
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v264 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v264 se0 ctor hooked (透明度验证)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v264 hookBubble264 EX " + t);
        }
    }

    /**
     * v262：用户气泡液态玻璃 —— hook up2 构造器改 tp2.a 字段。
     * tp2.a = #EDF3FE（用户气泡背景，v261 实证）
     * 液态玻璃 = 高透冰蓝（玻璃片质感），自定义颜色 = 30% 半透明。
     */
    private static volatile int sBubble262Logged = 0;

    private static void hookBubble262(final ClassLoader cl) {
        try {
            final Class<?> up2Cls = cl.loadClass("up2");
            for (java.lang.reflect.Constructor<?> ctor : up2Cls.getDeclaredConstructors()) {
                if (ctor.getParameterTypes().length != 10) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            // v320：气泡总开关 —— 气泡美化开启 **或** 壁纸/聊天背景开启时生效（壁纸下自动加底）
                            android.content.Context gCtx = appCtx();
                            if (!bubbleActive(gCtx)) return;
                            Object tp2 = param.args[8];  // up2.i
                            if (tp2 == null) return;
                            java.lang.reflect.Field fA = tp2.getClass().getDeclaredField("a");
                            fA.setAccessible(true);
                            long orig = fA.getLong(tp2);
                            long origRgb = orig >>> 32;
                            android.content.Context ctx = appCtx();
                            if (ctx == null) return;
                            boolean darkMode = DsConfig.isDarkMode(ctx);
                            // 只处理用户气泡浅蓝（浅色模式）或深色模式下的用户气泡
                            // 深色模式下原色可能是深色系，放宽判断
                            if (!darkMode && origRgb != 0xffedf3feL && origRgb != 0xffecf2feL) return;
                            int style = DsConfig.bubbleStyle(ctx);
                            int customColor = DsConfig.bubbleColor(ctx);
                            // v487：样式统一 —— 用户气泡颜色跟随 AI 气泡色
                            if (DsConfig.unifyBubble(ctx)) {
                                customColor = DsConfig.aiBubbleColor(ctx);
                            }
                            long target;
                            // 深色模式适配：系统/App 深色时，用户气泡用高饱和品牌蓝（深底上更清晰），
                            // 避免半透明浅色气泡在深色聊天背景上发暗
                            if (darkMode) {
                                // 深色模式：用户气泡用实色品牌蓝（不透明白字清晰）
                                if (customColor >= 1 && customColor <= 6) {
                                    long base;
                                    switch (customColor) {
                                        case 1: base = 0x5B7CFEL; break;
                                        case 2: base = 0x4D6BFEL; break;
                                        case 3: base = 0x00BFA5L; break;
                                        case 4: base = 0x8C5CFEL; break;
                                        case 5: base = 0xFF6B9DL; break;
                                        case 6: base = 0xFFA040L; break;
                                        default: base = 0x5B7CFEL; break;
                                    }
                                    target = 0xFF000000L | base;  // 实色（深色模式不透）
                                } else if (customColor == 7) {
                                    long base = ((long) DsConfig.bubbleCustomColor(ctx)) & 0xFFFFFFL;
                                    target = 0xFF000000L | base;  // 实色
                                } else {
                                    target = 0xFF4D6BFEL;  // 默认品牌蓝实色
                                }
                            } else if (customColor >= 1 && customColor <= 6) {
                                long base;
                                switch (customColor) {
                                    case 1: base = 0x4D6BFEL; break;
                                    case 2: base = 0x3D5BEEL; break;
                                    case 3: base = 0x00BFA5L; break;
                                    case 4: base = 0x7C4DFFL; break;
                                    case 5: base = 0xFF5C8AL; break;
                                    case 6: base = 0xFF8C00L; break;
                                    default: base = 0x4D6BFEL; break;
                                }
                                target = 0x47000000L | base;  // 28% 半透明（玻璃感平衡）
                            } else if (customColor == 7) {
                                // 自定义调色盘颜色（28% 半透明）
                                long base = ((long) DsConfig.bubbleCustomColor(ctx)) & 0xFFFFFFL;
                                target = 0x47000000L | base;
                            } else if (style == 3) {
                                target = 0x40B8E8FEL;  // 25% 冰蓝玻璃
                            } else if (style == 1) {
                                target = 0xFF4D6BFEL;
                            } else if (style == 2) {
                                target = 0xFF3D5BEEL;
                            } else {
                                // 默认样式（0）：壁纸/聊天背景开启时强制实色品牌蓝（文字清晰），否则不动原色
                                boolean wallOn = DsConfig.bgOn(ctx) || DsConfig.chatBgOn(ctx);
                                if (!wallOn) return;
                                target = 0xE64D6BFEL;  // 90% 品牌蓝（壁纸下不透，文字清晰）
                            }
                            fA.setLong(tp2, target << 32);
                            // 增强文字显示：气泡变色后同步改文字色。优先读取 msgTextColor 配置，0=自动
                            try {
                                java.lang.reflect.Field fB = tp2.getClass().getDeclaredField("b");
                                fB.setAccessible(true);
                                long txtOrig = fB.getLong(tp2);
                                long txtRgb = txtOrig >>> 32;
                                long txtTarget;
                                int tc = DsConfig.msgTextColor(ctx);
                                if (tc >= 1 && tc <= 6) {
                                    // 用户手动指定文字色
                                    switch (tc) {
                                        case 1: txtTarget = 0xFF000000L; break;   // 黑
                                        case 2: txtTarget = 0xFFFFFFFFL; break;   // 白
                                        case 3: txtTarget = 0xFF4D6BFEL; break;   // 品牌蓝
                                        case 4: txtTarget = 0xFF555555L; break;   // 深灰
                                        case 5: txtTarget = 0xFFE53935L; break;   // 红
                                        case 6: txtTarget = 0xFF2E7D32L; break;   // 绿
                                        default: txtTarget = 0xFF000000L; break;
                                    }
                                } else {
                                    // 自动：深色气泡配白字，浅色配深字
                                    int ta = (int)((target >>> 24) & 0xFF);
                                    int tr = (int)((target >>> 16) & 0xFF);
                                    int tg = (int)((target >>> 8) & 0xFF);
                                    int tb = (int)(target & 0xFF);
                                    boolean darkBg = (ta >= 0x90) || (tr < 0xB0 || tg < 0xB0 || tb < 0xB0);
                                    txtTarget = darkBg ? 0xFFFFFFFFL : 0xFF1A1A1AL;
                                }
                                fB.setLong(tp2, txtTarget << 32);
                                if (sBubble262Logged++ < 20) {
                                    XposedBridge.log("[ds美化] v262 文字色 " + String.format("#%08x", txtRgb)
                                            + " -> " + String.format("#%08x", txtTarget)
                                            + " (bg=" + String.format("#%08x", target) + ")");
                                }
                            } catch (Throwable te) {
                                XposedBridge.log("[ds美化] v262 文字色 EX " + te);
                            }
                        } catch (Throwable t) {
                            if (sBubble262Logged++ < 10) XposedBridge.log("[ds美化] v262 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v262 up2 ctor hooked (tp2.a换色)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v262 hookBubble262 EX " + t);
        }
    }

    /**
     * v261：tp2 诊断 —— dump up2.i (Ltp2) 全部颜色字段。
     * 实证：用户气泡背景 = up2.i.tp2.a（ls9 1448 行读取）
     */
    private static volatile int sBubble261Logged = 0;

    private static void hookBubble261(final ClassLoader cl) {
        try {
            final Class<?> up2Cls = cl.loadClass("up2");
            for (java.lang.reflect.Constructor<?> ctor : up2Cls.getDeclaredConstructors()) {
                if (ctor.getParameterTypes().length != 10) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            if (sBubble261Logged++ > 10) return;
                            Object tp2 = param.args[8];  // up2.i
                            if (tp2 == null) return;
                            StringBuilder sb = new StringBuilder("[ds美化] v261 tp2 字段");
                            java.lang.reflect.Field[] fs = tp2.getClass().getDeclaredFields();
                            for (java.lang.reflect.Field f : fs) {
                                f.setAccessible(true);
                                if (f.getType() == long.class) {
                                    long v = f.getLong(tp2);
                                    sb.append("\n  tp2.").append(f.getName()).append("=")
                                      .append(String.format("#%08x", v >>> 32));
                                }
                            }
                            XposedBridge.log(sb.toString());
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v261 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v261 up2 ctor hooked (tp2诊断)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v261 hookBubble261 EX " + t);
        }
    }

    /**
     * v258：用户气泡精准换色 —— se0 hook + ls9 白名单。
     * v257 实证：用户气泡背景 #EDF3FE 走 ls9.f（UserChatMessageBubble 气泡 lambda）
     * 之前 v256 用 zf5/ig8 没抓到，因为气泡渲染走的是 ls9.f！
     */
    private static volatile int sBubble258Logged = 0;

    private static void hookBubble258(final ClassLoader cl) {
        try {
            final Class<?> se0Cls = cl.loadClass("se0");
            for (java.lang.reflect.Constructor<?> ctor : se0Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 4) continue;
                if (!pts[0].getName().equals("long")) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            long color = ((Long) param.args[0]).longValue();
                            long rgb = color >>> 32;
                            // 只处理用户气泡浅蓝 #EDF3FE
                            if (rgb != 0xffedf3feL) return;
                            // 白名单：ls9 = UserChatMessageBubble
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            boolean fromBubble = false;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if (cn.equals("ls9")) {
                                    fromBubble = true;
                                    break;
                                }
                            }
                            if (!fromBubble) return;
                            android.content.Context ctx = appCtx();
                            if (ctx == null) return;
                            int style = DsConfig.bubbleStyle(ctx);
                            int alpha = DsConfig.bubbleAlpha(ctx);
                            int customColor = DsConfig.bubbleColor(ctx);
                            long target;
                            // 液态玻璃样式（style=3）或自定义颜色：固定半透明玻璃质感
                            if (customColor >= 1 && customColor <= 6) {
                                long base;
                                switch (customColor) {
                                    case 1: base = 0x4D6BFEL; break;   // 品牌蓝
                                    case 2: base = 0x3D5BEEL; break;   // 深蓝
                                    case 3: base = 0x00BFA5L; break;   // 青绿
                                    case 4: base = 0x7C4DFFL; break;   // 紫色
                                    case 5: base = 0xFF5C8AL; break;   // 粉色
                                    case 6: base = 0xFF8C00L; break;   // 橙色
                                    default: base = 0x4D6BFEL; break;
                                }
                                // 自定义颜色也走液态玻璃：固定 55% 半透明
                                target = 0x8C000000L | base;
                            } else if (style == 3) {
                                // 液态玻璃：固定 55% 半透明（玻璃质感）
                                target = 0x8C000000L | 0x4D6BFEL;
                            } else {
                                switch (style) {
                                    case 0: return;
                                    case 1: target = 0xFF4D6BFEL; break;
                                    case 2: target = 0xFF3D5BEEL; break;
                                    default:
                                        int a = (alpha * 255) / 100;
                                        target = ((long) a << 24) | 0x4D6BFEL;
                                        break;
                                }
                            }
                            param.args[0] = Long.valueOf(target << 32);
                            if (sBubble258Logged++ < 30) {
                                XposedBridge.log("[ds美化] v258 用户气泡 style=" + style
                                        + " alpha=" + alpha + " color=" + customColor
                                        + " -> " + String.format("#%08x", target));
                            }
                        } catch (Throwable t) {
                            if (sBubble258Logged++ < 30) XposedBridge.log("[ds美化] v258 se0 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v258 se0 ctor hooked (ls9白名单)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v258 hookBubble258 EX " + t);
        }
    }

    /**
     * v257：se0 全量诊断 —— 打印所有非纯白背景色的创建（含调用栈）。
     * 目的：抓"打开聊天页渲染气泡"时的 se0 调用，定位用户气泡真实颜色+调用链。
     */
    private static volatile int sBubble257Logged = 0;

    private static void hookBubble257(final ClassLoader cl) {
        try {
            final Class<?> se0Cls = cl.loadClass("se0");
            for (java.lang.reflect.Constructor<?> ctor : se0Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 4) continue;
                if (!pts[0].getName().equals("long")) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            long color = ((Long) param.args[0]).longValue();
                            long rgb = color >>> 32;
                            int a = (int) (rgb >>> 24) & 0xFF;
                            if (a == 0) return;
                            // 过滤纯白/纯黑（太多）
                            if (rgb == 0xffffffffL || rgb == 0xff000000L) return;
                            if (sBubble257Logged++ > 250) return;
                            StringBuilder sb = new StringBuilder("[ds美化] v257 bg " + String.format("#%08x", rgb));
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            int cnt = 0;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if (cn.startsWith("java.") || cn.startsWith("dalvik.")
                                        || cn.startsWith("com.android.") || cn.contains("XC_MethodHook")
                                        || cn.contains("XposedBridge") || cn.startsWith("E.Aaj")
                                        || cn.startsWith("com.example")) continue;
                                sb.append("\n  ").append(cn).append(".").append(e.getMethodName());
                                if (++cnt >= 8) break;
                            }
                            XposedBridge.log(sb.toString());
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v257 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v257 se0 ctor hooked (全量诊断)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v257 hookBubble257 EX " + t);
        }
    }

    /**
     * v256：用户气泡精准换色 —— se0 hook + zf5/ig8 白名单。
     * v253 实证：用户气泡链 = zf5.q → ge5.b → ge5.c → tia.v → se0（UserChatMessageBubble）
     * s71.q → uk8.b 是按钮（AppButton），w95 是 ToolButton —— 全部排除。
     * 只替换 #EDF3FE（用户气泡浅蓝，q8.a 确认）→ 配置色，零误伤。
     */
    private static volatile int sBubble256Logged = 0;

    private static void hookBubble256(final ClassLoader cl) {
        try {
            final Class<?> se0Cls = cl.loadClass("se0");
            for (java.lang.reflect.Constructor<?> ctor : se0Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 4) continue;
                if (!pts[0].getName().equals("long")) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            long color = ((Long) param.args[0]).longValue();
                            long rgb = color >>> 32;
                            // 只处理用户气泡浅蓝 #EDF3FE
                            if (rgb != 0xffedf3feL) return;
                            // 白名单：zf5/ig8 = UserChatMessageBubble 气泡 lambda
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            boolean fromBubble = false;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if (cn.equals("zf5") || cn.equals("ig8")) {
                                    fromBubble = true;
                                    break;
                                }
                            }
                            if (!fromBubble) return;
                            android.content.Context ctx = appCtx();
                            if (ctx == null) return;
                            int style = DsConfig.bubbleStyle(ctx);
                            int alpha = DsConfig.bubbleAlpha(ctx);
                            long target;
                            switch (style) {
                                case 0: return;
                                case 1: target = 0xFF4D6BFEL; break;
                                case 2: target = 0xFF3D5BEEL; break;
                                case 3:
                                default:
                                    int a = (alpha * 255) / 100;
                                    target = ((long) a << 24) | 0x4D6BFEL;
                                    break;
                            }
                            param.args[0] = Long.valueOf(target << 32);
                            if (sBubble256Logged++ < 30) {
                                XposedBridge.log("[ds美化] v256 用户气泡 style=" + style
                                        + " alpha=" + alpha + " -> " + String.format("#%08x", target));
                            }
                        } catch (Throwable t) {
                            if (sBubble256Logged++ < 30) XposedBridge.log("[ds美化] v256 se0 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v256 se0 ctor hooked (zf5/ig8白名单)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v256 hookBubble256 EX " + t);
        }
    }

    /**
     * v255：up2 构造器颜色诊断 —— dump 所有气泡相关颜色字段，找 #ECF2FE 来源。
     * 截图实证：用户气泡 = #ECF2FE (rgb 236,242,254)，不是 #EDF3FE。
     * 结构：up2.a=rp2(8色) e=llv(4色) h=aua.b=lga.b=q8(2色)
     */
    private static volatile int sBubble255Logged = 0;

    private static void hookBubble255(final ClassLoader cl) {
        try {
            final Class<?> up2Cls = cl.loadClass("up2");
            for (java.lang.reflect.Constructor<?> ctor : up2Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 10) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            if (sBubble255Logged++ > 30) return;
                            StringBuilder sb = new StringBuilder("[ds美化] v255 up2 颜色诊断");
                            try {
                                // 先快速检查 q8.a 是否浅蓝（气泡候选），不是就不打印
                                boolean lightTheme = false;
                                try {
                                    Object aua = param.args[7];
                                    if (aua != null) {
                                        java.lang.reflect.Field fb = aua.getClass().getDeclaredField("b");
                                        fb.setAccessible(true);
                                        Object lga = fb.get(aua);
                                        if (lga != null) {
                                            java.lang.reflect.Field fb2 = lga.getClass().getDeclaredField("b");
                                            fb2.setAccessible(true);
                                            Object q8 = fb2.get(lga);
                                            if (q8 != null) {
                                                java.lang.reflect.Field fA = q8.getClass().getDeclaredField("a");
                                                fA.setAccessible(true);
                                                long va = fA.getLong(q8);
                                                long rgba = va >>> 32;
                                                if (rgba == 0xffecf2feL || rgba == 0xffedf3feL || rgba == 0xffdeeaffL) {
                                                    lightTheme = true;
                                                }
                                            }
                                        }
                                    }
                                } catch (Throwable ignored) {}
                                if (!lightTheme) return;
                                // a = rp2 (8 long 字段)
                                Object rp2 = param.args[0];
                                if (rp2 != null) {
                                    java.lang.reflect.Field[] fs = rp2.getClass().getDeclaredFields();
                                    for (java.lang.reflect.Field f : fs) {
                                        if (f.getType() != long.class) continue;
                                        f.setAccessible(true);
                                        long v = f.getLong(rp2);
                                        sb.append("\n  rp2.").append(f.getName()).append("=")
                                          .append(String.format("#%08x", v >>> 32));
                                    }
                                }
                                // e = llv
                                Object llv = param.args[4];
                                if (llv != null) {
                                    java.lang.reflect.Field[] fs = llv.getClass().getDeclaredFields();
                                    for (java.lang.reflect.Field f : fs) {
                                        if (f.getType() != long.class) continue;
                                        f.setAccessible(true);
                                        long v = f.getLong(llv);
                                        sb.append("\n  llv.").append(f.getName()).append("=")
                                          .append(String.format("#%08x", v >>> 32));
                                    }
                                }
                                // h = aua -> b -> lga -> b -> q8
                                Object aua = param.args[7];
                                if (aua != null) {
                                    try {
                                        java.lang.reflect.Field fb = aua.getClass().getDeclaredField("b");
                                        fb.setAccessible(true);
                                        Object lga = fb.get(aua);
                                        if (lga != null) {
                                            // 打印 lga 的 type (a 字段)
                                            try {
                                                java.lang.reflect.Field fa = lga.getClass().getDeclaredField("a");
                                                fa.setAccessible(true);
                                                sb.append("\n  lga.type=").append(fa.getInt(lga));
                                            } catch (Throwable ignored) {}
                                            java.lang.reflect.Field fb2 = lga.getClass().getDeclaredField("b");
                                            fb2.setAccessible(true);
                                            Object q8 = fb2.get(lga);
                                            if (q8 != null) {
                                                java.lang.reflect.Field[] fs = q8.getClass().getDeclaredFields();
                                                for (java.lang.reflect.Field f : fs) {
                                                    if (f.getType() != long.class) continue;
                                                    f.setAccessible(true);
                                                    long v = f.getLong(q8);
                                                    sb.append("\n  q8.").append(f.getName()).append("=")
                                                      .append(String.format("#%08x", v >>> 32));
                                                }
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            } catch (Throwable t) {
                                sb.append("\n  EX ").append(t);
                            }
                            XposedBridge.log(sb.toString());
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v255 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v255 up2 ctor hooked (颜色诊断)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v255 hookBubble255 EX " + t);
        }
    }

    /**
     * v254：用户气泡精准换色 —— hook lga(5, q8) 构造器。
     * v253 实证：UserChatMessageBubble.kt 的气泡背景色 = up2.h.aua.b → lga.b → q8.a
     * lj0 只有 2 处 lga(5, q8)，type=5 是用户气泡专属，改 q8.a 不误伤任何其他 UI。
     */
    private static volatile int sBubble254Logged = 0;

    private static void hookBubble254(final ClassLoader cl) {
        try {
            final Class<?> lgaCls = cl.loadClass("lga");
            final Class<?> q8Cls = cl.loadClass("q8");
            final java.lang.reflect.Field fA = q8Cls.getDeclaredField("a");
            fA.setAccessible(true);
            for (java.lang.reflect.Constructor<?> ctor : lgaCls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 2) continue;
                if (!pts[0].getName().equals("int")) continue;
                if (!pts[1].getName().equals("java.lang.Object")) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            int type = ((Integer) param.args[0]).intValue();
                            if (type != 5) return;   // 只处理用户气泡配色
                            Object b = param.args[1];
                            if (b == null) return;
                            // 改 q8.a 字段（用户气泡背景色）
                            long orig = fA.getLong(b);
                            long rgb = orig >>> 32;
                            if (sBubble254Logged++ < 5) {
                                XposedBridge.log("[ds美化] v254 lga(5) q8.a 原色 " + String.format("#%08x", rgb));
                            }
                            android.content.Context ctx = appCtx();
                            if (ctx == null) return;
                            int style = DsConfig.bubbleStyle(ctx);
                            int alpha = DsConfig.bubbleAlpha(ctx);
                            long target;
                            switch (style) {
                                case 0: return;
                                case 1: target = 0xFF4D6BFEL; break;
                                case 2: target = 0xFF3D5BEEL; break;
                                case 3:
                                default:
                                    int a = (alpha * 255) / 100;
                                    target = ((long) a << 24) | 0x4D6BFEL;
                                    break;
                            }
                            fA.setLong(b, target << 32);
                            if (sBubble254Logged++ < 8) {
                                XposedBridge.log("[ds美化] v254 用户气泡 style=" + style
                                        + " alpha=" + alpha + " -> " + String.format("#%08x", target));
                            }
                        } catch (Throwable t) {
                            if (sBubble254Logged++ < 8) XposedBridge.log("[ds美化] v254 lga EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v254 lga(5) ctor hooked (用户气泡)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v254 hookBubble254 EX " + t);
        }
    }

    /**
     * v253：se0 完整栈诊断 —— 打印所有非透明背景色的完整调用栈（找用户气泡真实类）。
     * 目的：w95=ToolButton(按钮)、ge5=ModelSwitchTip(提示条)，都不是气泡。
     * 通过完整调用栈找到真正的消息气泡渲染类。
     */
    private static volatile int sBubble253Logged = 0;

    private static void hookBubble253(final ClassLoader cl) {
        try {
            final Class<?> se0Cls = cl.loadClass("se0");
            for (java.lang.reflect.Constructor<?> ctor : se0Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 4) continue;
                if (!pts[0].getName().equals("long")) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            long color = ((Long) param.args[0]).longValue();
                            long rgb = color >>> 32;
                            int a = (int) (rgb >>> 24) & 0xFF;
                            if (a == 0) return;
                            if (sBubble253Logged++ > 120) return;
                            // 只打印蓝色系（气泡候选）：B>=200 且 R<240，或浅蓝系
                            int r = (int) (rgb >>> 16) & 0xFF;
                            int g = (int) (rgb >>> 8) & 0xFF;
                            int b = (int) rgb & 0xFF;
                            boolean blueish = (b >= 200 && r <= 250) || (rgb == 0xffedf3feL);
                            if (!blueish) return;
                            StringBuilder sb = new StringBuilder("[ds美化] v253 蓝背景 " + String.format("#%08x", rgb));
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            int cnt = 0;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if (cn.startsWith("java.") || cn.startsWith("dalvik.")
                                        || cn.startsWith("com.android.") || cn.contains("XC_MethodHook")
                                        || cn.contains("XposedBridge") || cn.startsWith("E.Aaj")
                                        || cn.startsWith("com.example")) continue;
                                sb.append("\n  ").append(cn).append(".").append(e.getMethodName()).append(":").append(e.getLineNumber());
                                if (++cnt >= 14) break;
                            }
                            XposedBridge.log(sb.toString());
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v253 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v253 se0 ctor hooked (完整栈诊断)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v253 hookBubble253 EX " + t);
        }
    }

    /**
     * v252：用户气泡真实颜色诊断 —— hook ge5.c 参数 + w95.f 颜色打印。
     * 目的：气泡背景不是 #edf3fe（那是 ToolButton 边框），找到气泡真实颜色。
     */
    private static volatile int sBubble252Logged = 0;

    private static void hookBubble252(final ClassLoader cl) {
        try {
            // ① ge5.c —— 用户消息渲染（v235 确认命中）
            try {
                Class<?> ge5Cls = cl.loadClass("ge5");
                for (java.lang.reflect.Method m : ge5Cls.getDeclaredMethods()) {
                    if (!"c".equals(m.getName())) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length != 8) continue;
                    deoptimizeMethod(m);
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (sBubble252Logged++ > 20) return;
                            StringBuilder sb = new StringBuilder("[ds美化] v252 ge5.c hit");
                            try {
                                sb.append(" p0=").append(param.args[0] != null ? param.args[0].getClass().getSimpleName() : "null");
                                sb.append(" p1=").append(param.args[1]);
                                sb.append(" p2=").append(param.args[2]);
                            } catch (Throwable ignored) {}
                            XposedBridge.log(sb.toString());
                        }
                    });
                    XposedBridge.log("[ds美化] v252 ge5.c hooked");
                    break;
                }
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v252 ge5 EX " + t);
            }

            // ② se0 全量打印（不匹配颜色，看所有背景色 + 调用链）
            try {
                final Class<?> se0Cls = cl.loadClass("se0");
                for (java.lang.reflect.Constructor<?> ctor : se0Cls.getDeclaredConstructors()) {
                    if (ctor.getParameterTypes().length != 4) continue;
                    if (!ctor.getParameterTypes()[0].getName().equals("long")) continue;
                    deoptimizeMethod(ctor);
                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (sBubble252Logged++ > 100) return;
                            long color = ((Long) param.args[0]).longValue();
                            long rgb = color >>> 32;
                            int a = (int) (rgb >>> 24) & 0xFF;
                            if (a == 0) return;
                            // 只打印蓝色系或浅色系（气泡候选）
                            int r = (int) (rgb >>> 16) & 0xFF;
                            int g = (int) (rgb >>> 8) & 0xFF;
                            int b = (int) rgb & 0xFF;
                            if (b < 200 || r > 240) return;  // 只看蓝色系
                            StringBuilder sb = new StringBuilder("[ds美化] v252 蓝背景 " + String.format("#%08x", rgb));
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            int cnt = 0;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if (cn.startsWith("java.") || cn.startsWith("dalvik.")
                                        || cn.startsWith("com.android.") || cn.contains("XC_MethodHook")
                                        || cn.contains("XposedBridge") || cn.startsWith("E.Aaj")
                                        || cn.startsWith("com.example")) continue;
                                sb.append(" | ").append(cn).append(".").append(e.getMethodName());
                                if (++cnt >= 6) break;
                            }
                            XposedBridge.log(sb.toString());
                        }
                    });
                    XposedBridge.log("[ds美化] v252 se0 hooked (蓝背景)");
                    break;
                }
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v252 se0 EX " + t);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v252 hookBubble252 EX " + t);
        }
    }

    /**
     * v251：气泡精确换色 —— se0 hook + w95 调用栈白名单。
     * v250 实证：用户气泡调用链 = w95.f → w95.e → tia.v → se0
     * 输入框按钮走 ls9.f / vz8.c（也用 #edf3fe）→ 白名单排除，绝不误伤。
     */
    private static volatile int sBubble251Logged = 0;

    private static void hookBubble251(final ClassLoader cl) {
        try {
            final Class<?> se0Cls = cl.loadClass("se0");
            for (java.lang.reflect.Constructor<?> ctor : se0Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 4) continue;
                if (!pts[0].getName().equals("long")) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            long color = ((Long) param.args[0]).longValue();
                            long rgb = color >>> 32;
                            if (rgb != 0xffedf3feL) return;
                            // 白名单：w95=用户气泡，rs5/ux1/qs5/uq5=AI气泡
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            boolean fromBubble = false;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if (cn.equals("w95") || cn.equals("rs5") || cn.equals("ux1")
                                        || cn.equals("qs5") || cn.equals("uq5")) {
                                    fromBubble = true;
                                    break;
                                }
                            }
                            if (!fromBubble) return;
                            android.content.Context ctx = appCtx();
                            if (ctx == null) return;
                            int style = DsConfig.bubbleStyle(ctx);
                            int alpha = DsConfig.bubbleAlpha(ctx);
                            long target;
                            switch (style) {
                                case 0: return;
                                case 1: target = 0xFF4D6BFEL; break;
                                case 2: target = 0xFF3D5BEEL; break;
                                case 3:
                                default:
                                    int a = (alpha * 255) / 100;
                                    target = ((long) a << 24) | 0x4D6BFEL;
                                    break;
                            }
                            param.args[0] = Long.valueOf(target << 32);
                            if (sBubble251Logged++ < 30) {
                                XposedBridge.log("[ds美化] v251 用户气泡 style=" + style
                                        + " alpha=" + alpha + " -> " + String.format("#%08x", target));
                            }
                        } catch (Throwable t) {
                            if (sBubble251Logged++ < 30) XposedBridge.log("[ds美化] v251 se0 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v251 se0 ctor hooked (w95白名单)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v251 hookBubble251 EX " + t);
        }
    }

    /**
     * v250：气泡调用栈诊断 —— 只匹配 #ffedf3fe，打印完整调用栈 20 帧。
     * 目的：找到气泡渲染的真实调用链（区分气泡 vs 输入框按钮），为精确白名单铺路。
     */
    private static volatile int sBubble250Logged = 0;

    private static void hookBubble250(final ClassLoader cl) {
        try {
            final Class<?> se0Cls = cl.loadClass("se0");
            for (java.lang.reflect.Constructor<?> ctor : se0Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 4) continue;
                if (!pts[0].getName().equals("long")) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            long color = ((Long) param.args[0]).longValue();
                            long rgb = color >>> 32;
                            if (rgb != 0xffedf3feL) return;
                            if (sBubble250Logged++ > 40) return;
                            StringBuilder sb = new StringBuilder("[ds美化] v250 #edf3fe 调用栈:");
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            int cnt = 0;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if (cn.startsWith("java.") || cn.startsWith("dalvik.")
                                        || cn.startsWith("com.android.") || cn.startsWith("android.os")
                                        || cn.contains("XC_MethodHook") || cn.contains("XposedBridge")
                                        || cn.startsWith("E.Aaj")) continue;
                                sb.append("\n  ").append(cn).append(".").append(e.getMethodName()).append(":").append(e.getLineNumber());
                                if (++cnt >= 20) break;
                            }
                            XposedBridge.log(sb.toString());
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v250 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v250 se0 ctor hooked (诊断)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v250 hookBubble250 EX " + t);
        }
    }

    /**
     * v249：气泡外观配置驱动 —— se0 hook 读取 DsConfig（样式/透明度）。
     * 样式：0=默认(浅蓝#EDF3FE) 1=圆润(品牌蓝#4D6BFE) 2=方角(深蓝#3D5BEE) 3=液态玻璃(半透明蓝)
     * 透明度：bubbleAlpha 30-100 → alpha 换算
     */
    private static volatile int sBubble249Logged = 0;

    private static void hookBubble249(final ClassLoader cl) {
        try {
            final Class<?> se0Cls = cl.loadClass("se0");
            for (java.lang.reflect.Constructor<?> ctor : se0Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 4) continue;
                if (!pts[0].getName().equals("long")) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            long color = ((Long) param.args[0]).longValue();
                            long rgb = color >>> 32;
                            // 只处理用户气泡浅蓝（xp2.b = #EDF3FE）
                            if (rgb != 0xffedf3feL) return;
                            // 调用栈白名单：只在气泡渲染链时替换，避免误伤输入框按钮等
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            boolean fromBubble = false;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                // ge5=用户气泡 rs5=AI气泡 ux1/qs5/uq5=AI气泡入口 x30=用户气泡入口
                                if (cn.equals("ge5") || cn.equals("rs5") || cn.equals("ux1")
                                        || cn.equals("qs5") || cn.equals("uq5") || cn.equals("x30")) {
                                    fromBubble = true;
                                    break;
                                }
                            }
                            if (!fromBubble) return;
                            android.content.Context ctx = appCtx();
                            if (ctx == null) return;
                            int style = DsConfig.bubbleStyle(ctx);
                            int alpha = DsConfig.bubbleAlpha(ctx);  // 30-100
                            long target;
                            switch (style) {
                                case 0:  // 默认：保持浅蓝（不换）
                                    return;
                                case 1:  // 圆润：品牌蓝（不透明）
                                    target = 0xFF4D6BFEL;
                                    break;
                                case 2:  // 方角：深蓝（不透明）
                                    target = 0xFF3D5BEEL;
                                    break;
                                case 3:  // 液态玻璃：半透明品牌蓝
                                default:
                                    int a = (alpha * 255) / 100;
                                    target = ((long) a << 24) | 0x4D6BFEL;
                                    break;
                            }
                            param.args[0] = Long.valueOf(target << 32);
                            if (sBubble249Logged++ < 30) {
                                XposedBridge.log("[ds美化] v249 用户气泡 style=" + style
                                        + " alpha=" + alpha + " -> " + String.format("#%08x", target));
                            }
                        } catch (Throwable t) {
                            if (sBubble249Logged++ < 30) XposedBridge.log("[ds美化] v249 se0 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v249 se0 ctor hooked (配置驱动)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v249 hookBubble249 EX " + t);
        }
    }

    /**
     * v248：气泡精准换色 —— hook se0 构造器，只替换 #ffedf3fe（用户气泡浅蓝，xp2.b）。
     * 决定性证据：v247 日志显示 #ffedf3fe 是气泡专属色（启动仅出现一次），
     * se0 构造器是所有 Modifier.background(color) 的必经入口。
     * 只改这一个颜色值 → 液态玻璃蓝渐变底 0x8A3D5BEE（Compose Color = ARGB<<32）。
     * 零构造器反射、零对象替换，绝对安全。
     */
    private static volatile int sBubble248Logged = 0;

    private static void hookBubble248(final ClassLoader cl) {
        try {
            final Class<?> se0Cls = cl.loadClass("se0");
            for (java.lang.reflect.Constructor<?> ctor : se0Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 4) continue;
                if (!pts[0].getName().equals("long")) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            long color = ((Long) param.args[0]).longValue();
                            long rgb = color >>> 32;
                            // 用户气泡浅蓝 #ffedf3fe → 液态玻璃蓝
                            if (rgb == 0xffedf3feL) {
                                long target = 0x8A3D5BEEL << 32;  // 品牌蓝渐变底 54% 透明
                                param.args[0] = Long.valueOf(target);
                                if (sBubble248Logged++ < 30) {
                                    XposedBridge.log("[ds美化] v248 用户气泡 " + String.format("#%08x", rgb)
                                            + " -> " + String.format("#%08x", target >>> 32));
                                }
                            }
                        } catch (Throwable t) {
                            if (sBubble248Logged++ < 30) XposedBridge.log("[ds美化] v248 se0 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v248 se0 ctor hooked (气泡换色)");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v248 hookBubble248 EX " + t);
        }
    }

    /**
     * v247：se0 构造器全量诊断 —— 所有 Modifier.background(color) 必经入口。
     * 打印颜色 + 完整调用栈前几帧，确认气泡蓝色来自哪条链。
     */
    private static volatile int sBubble247Logged = 0;

    private static void hookBubble247(final ClassLoader cl) {
        try {
            final Class<?> se0Cls = cl.loadClass("se0");
            for (java.lang.reflect.Constructor<?> ctor : se0Cls.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 4) continue;
                if (!pts[0].getName().equals("long")) continue;
                deoptimizeMethod(ctor);
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (sBubble247Logged++ > 100) return;
                        long color = ((Long) param.args[0]).longValue();
                        // 只看非透明/非纯白/非纯黑的背景色
                        long rgb = color >>> 32;
                        int a = (int) (rgb >>> 24) & 0xFF;
                        if (a == 0) return;
                        StringBuilder sb = new StringBuilder("[ds美化] v247 se0 背景 " + String.format("#%08x", rgb));
                        StackTraceElement[] st = Thread.currentThread().getStackTrace();
                        int cnt = 0;
                        for (StackTraceElement e : st) {
                            String cn = e.getClassName();
                            if (cn.startsWith("com.example") || cn.equals("tia") || cn.equals("se0")) continue;
                            sb.append(" | ").append(cn).append(".").append(e.getMethodName());
                            if (++cnt >= 5) break;
                        }
                        XposedBridge.log(sb.toString());
                    }
                });
                XposedBridge.log("[ds美化] v247 se0 ctor hooked");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v247 hookBubble247 EX " + t);
        }
    }

    /**
     * v246：气泡红色测试 —— hook xp2 静态色板（DeepSeek 品牌色板）。
     * xp2.b=#EDF3FE（浅蓝，用户气泡默认色）、xp2.h=#DEEAFF、xp2.i=#D3E2FF
     * 改成亮红 #FFFF0000 / 亮绿 #FF00FF00，一眼确认气泡入口。
     * 时机：xp2.<clinit> 后反射改写静态字段（dea.o 格式 = ARGB<<32）。
     */
    private static void hookBubble246(final ClassLoader cl) {
        try {
            final Class<?> xp2Cls = cl.loadClass("xp2");
            for (java.lang.reflect.Method m : xp2Cls.getDeclaredMethods()) {
                if (!"<clinit>".equals(m.getName())) continue;
                deoptimizeMethod(m);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            // 用户气泡浅蓝 #EDF3FE → 亮红
                            setStaticLong(xp2Cls, "b", 0xFFFF0000L << 32);
                            // 品牌蓝 #426EFE → 保留（主按钮）
                            // setStaticLong(xp2Cls, "c", 0xFF426EFEL << 32);
                            // 浅蓝系 → 亮绿（AI 气泡候选）
                            setStaticLong(xp2Cls, "h", 0xFF00FF00L << 32);
                            setStaticLong(xp2Cls, "i", 0xFF00FF00L << 32);
                            XposedBridge.log("[ds美化] v246 xp2 色板改写 b=RED h/i=GREEN");
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v246 xp2 EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v246 xp2 clinit hooked");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v246 hookBubble246 EX " + t);
        }
    }

    private static void setStaticLong(Class<?> cls, String fieldName, long value) {
        try {
            java.lang.reflect.Field f = cls.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.setLong(null, value);
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] setStaticLong " + fieldName + " EX " + t);
        }
    }

    /**
     * v244：气泡安全换色 —— hook tia.v（Modifier.background 唯一入口），调用栈白名单过滤。
     * 根因修复：v240 反射构造 se0 对象塞进颜色参数（se0 是 background 修饰符不是颜色！）→ 类型错乱闪退。
     * 新方案：只替换 long 颜色值（param.args[1]），零构造器、零对象替换，绝对安全。
     * 白名单：
     *  - rs5.a   = AI 气泡渲染（up2.d.qp2.g → tia.v）
     *  - ge5.c   = 用户气泡渲染（up2.a.rp2.a → tia.v）
     * 颜色（Compose Color = ARGB<<32 | colorSpace，低32位 colorSpace=0）：
     *  - AI 气泡：液态玻璃白 0x66FFFFFF（40% 透明白）
     *  - 用户气泡：品牌蓝渐变 0x8A3D5BEE（54% 蓝）
     */
    private static volatile int sBubble244Logged = 0;

    private static void hookBubble244(final ClassLoader cl) {
        try {
            Class<?> tiaCls = cl.loadClass("tia");
            for (java.lang.reflect.Method m : tiaCls.getDeclaredMethods()) {
                if (!"v".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 3) continue;
                if (!pts[1].getName().equals("long")) continue;
                deoptimizeMethod(m);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            StackTraceElement[] st = Thread.currentThread().getStackTrace();
                            boolean isAI = false, isUser = false;
                            for (StackTraceElement e : st) {
                                String cn = e.getClassName();
                                if ("rs5".equals(cn)) { isAI = true; break; }
                                if ("ge5".equals(cn)) { isUser = true; break; }
                            }
                            if (!isAI && !isUser) return;
                            long orig = ((Long) param.args[1]).longValue();
                            long target;
                            if (isAI) {
                                target = 0x66FFFFFFL << 32;   // 液态玻璃白
                            } else {
                                target = 0x8A3D5BEEL << 32;   // 品牌蓝
                            }
                            param.args[1] = Long.valueOf(target);
                            if (sBubble244Logged++ < 30) {
                                XposedBridge.log("[ds美化] v244 气泡换色 " + (isAI ? "AI" : "用户")
                                        + " 0x" + Long.toHexString(orig >>> 32)
                                        + " -> 0x" + Long.toHexString(target >>> 32));
                            }
                        } catch (Throwable t) {
                            if (sBubble244Logged++ < 30) XposedBridge.log("[ds美化] v244 tia.v EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v244 tia.v hooked " + m.toGenericString());
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v244 hookBubble244 EX " + t);
        }
    }

    /**
     * v236：气泡诊断2 —— 用户消息渲染链三连 hook。
     * 侦察链：ts9.f → w95.g(Lwy5;Lft6;Lc76;Lhv3;Lhv3;Lsv3;Ley3;I) → new b60 → u9a.b(Lv56;Lc76;...)
     * v236 hook 三个点，看谁在发消息时命中、Lc76 颜色对象长什么样，为 v237 换色铺路。
     * 重点：确认 w95.g 的 args[2]（Lc76）是不是气泡背景色。
     */
    private static void hookBubble236(final ClassLoader cl) {
        try {
            // ① w95.g —— 用户消息渲染器
            try {
                Class<?> w95Cls = cl.loadClass("w95");
                for (java.lang.reflect.Method m : w95Cls.getDeclaredMethods()) {
                    if (!"g".equals(m.getName())) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length != 8) continue;
                    deoptimizeMethod(m);
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (sBubble235Logged++ > 40) return;
                            StringBuilder sb = new StringBuilder("[ds美化] v236 w95.g hit");
                            try {
                                Object c76 = param.args[2];
                                sb.append(" c76=").append(c76 != null ? c76.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(c76)) : "null");
                                sb.append(" p1=").append(param.args[1] != null ? param.args[1].getClass().getSimpleName() : "null");
                            } catch (Throwable ignored) {}
                            XposedBridge.log(sb.toString());
                        }
                    });
                    XposedBridge.log("[ds美化] v236 w95.g hooked " + m.toGenericString());
                    break;
                }
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v236 w95.g EX " + t);
            }

            // ② u9a.b —— 渲染载体
            try {
                Class<?> u9aCls = cl.loadClass("u9a");
                for (java.lang.reflect.Method m : u9aCls.getDeclaredMethods()) {
                    if (!"b".equals(m.getName())) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length != 8) continue;
                    if (pts[0].getName().equals("v56")) {
                        deoptimizeMethod(m);
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (sBubble235Logged++ > 40) return;
                                StringBuilder sb = new StringBuilder("[ds美化] v236 u9a.b hit");
                                try {
                                    Object c76 = param.args[1];
                                    sb.append(" c76=").append(c76 != null ? c76.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(c76)) : "null");
                                    sb.append(" v56=").append(param.args[0] != null ? param.args[0].getClass().getSimpleName() : "null");
                                } catch (Throwable ignored) {}
                                XposedBridge.log(sb.toString());
                            }
                        });
                        XposedBridge.log("[ds美化] v236 u9a.b hooked " + m.toGenericString());
                        break;
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v236 u9a.b EX " + t);
            }

            // ③ b60 —— 构造载体（Lwy5;Lft6;Lc76;Lhv3;Lhv3;Lsv3;I 这个重载）
            try {
                Class<?> b60Cls = cl.loadClass("b60");
                for (java.lang.reflect.Constructor<?> ctor : b60Cls.getDeclaredConstructors()) {
                    Class<?>[] pts = ctor.getParameterTypes();
                    if (pts.length != 7) continue;
                    if (pts[0].getName().equals("wy5")) {
                        deoptimizeMethod(ctor);
                        XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (sBubble235Logged++ > 40) return;
                                StringBuilder sb = new StringBuilder("[ds美化] v236 b60 ctor hit");
                                try {
                                    Object c76 = param.args[2];
                                    sb.append(" c76=").append(c76 != null ? c76.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(c76)) : "null");
                                    sb.append(" wy5=").append(param.args[0] != null ? param.args[0].getClass().getSimpleName() : "null");
                                } catch (Throwable ignored) {}
                                XposedBridge.log(sb.toString());
                            }
                        });
                        XposedBridge.log("[ds美化] v236 b60 ctor hooked " + ctor.toGenericString());
                        break;
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v236 b60 ctor EX " + t);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v236 hookBubble236 EX " + t);
        }
    }

    /**
     * v240：气泡精准换色 —— 移除全局 tia.v hook（误伤所有背景，v239 教训），
     * 只 hook 气泡专属入口：
     *  ① b60 构造器 (wy5,ft6,c76,...) args[2] = 用户气泡 Lc76（z66）
     *  ② uk8.e (vq, I, ..., c76, ...) args[12] = AI 气泡 Lc76
     * 颜色 = se0(J 色值) 半透明白 0x66FFFFFF（液态玻璃底，ARGB<<32 格式）
     */
    private static void hookBubble237(final ClassLoader cl) {
        try {
            // 钥匙：构造 se0 液态玻璃色（J = ARGB << 32，低32位 colorSpace=0）
            final Class<?> se0Cls = cl.loadClass("se0");
            final Class<?> sn0Cls = cl.loadClass("sn0");
            final Class<?> md8Cls = cl.loadClass("md8");
            final java.lang.reflect.Constructor<?> cSe0 =
                    se0Cls.getConstructor(long.class, sn0Cls, md8Cls, int.class);
            cSe0.setAccessible(true);
            // 液态玻璃底：40% 透明白（alpha=0x66），ARGB<<32 格式（与 tia.v 打印的 0xfff5f5f500000000 一致）
            final long glassColor = 0x66FFFFFFL;
            final Object glassLc76 = cSe0.newInstance(Long.valueOf(glassColor << 32), null, null,
                    Integer.valueOf(2));

            // ① hook b60 构造器 (Lwy5;Lft6;Lc76;Lhv3;Lhv3;Lsv3;I) —— 用户气泡
            try {
                Class<?> b60Cls = cl.loadClass("b60");
                for (java.lang.reflect.Constructor<?> ctor : b60Cls.getDeclaredConstructors()) {
                    Class<?>[] pts = ctor.getParameterTypes();
                    if (pts.length != 7) continue;
                    if (!pts[0].getName().equals("wy5")) continue;
                    deoptimizeMethod(ctor);
                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object orig = param.args[2];
                                param.args[2] = glassLc76;
                                if (sBubble240Logged++ < 30) {
                                    XposedBridge.log("[ds美化] v240 用户气泡换色 " + (orig != null ? orig.getClass().getSimpleName() : "null")
                                            + " -> glass(" + Long.toHexString(glassColor) + ")");
                                }
                            } catch (Throwable t) {
                                if (sBubble240Logged++ < 30) XposedBridge.log("[ds美化] v240 b60 EX " + t);
                            }
                        }
                    });
                    XposedBridge.log("[ds美化] v240 b60 ctor hooked " + ctor.toGenericString());
                    break;
                }
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v240 b60 EX " + t);
            }

            // ② hook uk8.e (vq, I, ..., c76, ... ) —— AI 气泡（p12 = Lc76）
            try {
                Class<?> uk8Cls = cl.loadClass("uk8");
                for (java.lang.reflect.Method m : uk8Cls.getDeclaredMethods()) {
                    if (!"e".equals(m.getName())) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length < 13) continue;
                    if (!pts[12].getName().equals("c76")) continue;
                    deoptimizeMethod(m);
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object orig = param.args[12];
                                param.args[12] = glassLc76;
                                if (sBubble240Logged++ < 30) {
                                    XposedBridge.log("[ds美化] v240 AI气泡换色 " + (orig != null ? orig.getClass().getSimpleName() : "null")
                                            + " -> glass(" + Long.toHexString(glassColor) + ")");
                                }
                            } catch (Throwable t) {
                                if (sBubble240Logged++ < 30) XposedBridge.log("[ds美化] v240 uk8 EX " + t);
                            }
                        }
                    });
                    XposedBridge.log("[ds美化] v240 uk8.e hooked " + m.toGenericString());
                    break;
                }
            } catch (Throwable t) {
                XposedBridge.log("[ds美化] v240 uk8 EX " + t);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v240 hookBubble237 EX " + t);
        }
    }

    /**
     * v227：隐藏消息下方按钮。
     * - i95.d = UserMessageActionView（用户消息下方操作按钮）
     * - zl9.T = MessageFeedbackActions（AI 消息反馈按钮）
     * 方式：hook 后直接跳过渲染（setResult(null)）
     */
    private static void hookHideButtons(final ClassLoader cl) {
        try {
            // 1) 用户消息按钮 i95.d
            final Class<?> i95 = cl.loadClass("i95");
            for (java.lang.reflect.Method m : i95.getDeclaredMethods()) {
                if (!"d".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 5) continue;
                if (!pts[0].getName().equals("java.lang.String")) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            // v318：隐藏按钮开关（默认关，不隐藏）
                            android.content.Context hCtx = appCtx();
                            if (hCtx != null && !DsConfig.hideBtnOn(hCtx)) return;
                            if (sHideBtnLogged++ < 5) {
                                XposedBridge.log("[ds美化] v227 隐藏用户按钮 i95.d");
                            }
                            param.setResult(null);
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log("[ds美化] v227 hookHideButtons i95.d OK");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v227 i95 EX " + t);
        }
        try {
            // 2) AI 消息反馈按钮 zl9.T（8参：Lvq;ZZLg31;Lsv3;Lhv3;Ley3;I）
            final Class<?> zl9 = cl.loadClass("zl9");
            for (java.lang.reflect.Method m : zl9.getDeclaredMethods()) {
                if (!"T".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 8) continue;
                if (!pts[0].getName().equals("vq")) continue;
                deoptimizeMethod(m);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            // v318：隐藏按钮开关（默认关，不隐藏）
                            android.content.Context hCtx2 = appCtx();
                            if (hCtx2 != null && !DsConfig.hideBtnOn(hCtx2)) return;
                            if (sHideBtnLogged++ < 5) {
                                XposedBridge.log("[ds美化] v227 隐藏AI按钮 zl9.T");
                            }
                            param.setResult(null);
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log("[ds美化] v227 hookHideButtons zl9.T OK");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v227 zl9.T EX " + t);
        }
    }

    /**
     * v225：AI 消息时间 —— hook px 构造器（after），给 px.w（tips 列表状态）追加时间 tip。
     * 原理：nx.s (tips) → px.w (rs8 状态) → uk8.e (AssistantChatMessageContent) 渲染
     *   tip 用 t43：j()=c字段（文本），l()=d字段（级别，"INFO" 时经 u9a.h 渲染小字）
     */
    private static void hookMsgTime225(final ClassLoader cl) {
        try {
            final Class<?> pxCls = cl.loadClass("px");
            final java.lang.reflect.Field fW = pxCls.getField("w");   // tips 状态
            final java.lang.reflect.Field fJ = pxCls.getField("j");   // inserted_at
            final Class<?> rs8Cls = cl.loadClass("rs8");
            final java.lang.reflect.Method mGetValue = rs8Cls.getMethod("getValue");
            final java.lang.reflect.Method mSetValue = rs8Cls.getMethod("l", Object.class);
            final Class<?> t43Cls = cl.loadClass("t43");
            final java.lang.reflect.Constructor<?> cT43 = t43Cls.getConstructor(
                    int.class, String.class, String.class, String.class, boolean.class, boolean.class);
            final java.lang.reflect.Field fT43C = t43Cls.getField("c");  // j() 文本字段
            for (java.lang.reflect.Constructor<?> ctor : pxCls.getDeclaredConstructors()) {
                if (ctor.getParameterTypes().length != 1) continue;
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object px = param.thisObject;
                            Object state = fW.get(px);
                            if (state == null) return;
                            Object cur = mGetValue.invoke(state);
                            if (!(cur instanceof java.util.List)) return;  // 空/无 tips 的消息不处理
                            java.util.List<?> list = (java.util.List<?>) cur;
                            double ts = fJ.getDouble(px);
                            if (ts <= 0) return;
                            long millis = (long) (ts > 1e11 ? ts : ts * 1000);
                            String text = formatMsgTime(millis);
                            // 去重：已有同样时间 tip 则跳过
                            for (Object o : list) {
                                try {
                                    if (t43Cls.isInstance(o)
                                            && text.equals(fT43C.get(o))) {
                                        return;
                                    }
                                } catch (Throwable ignored) {}
                            }
                            Object tip = cT43.newInstance(Integer.valueOf(0), "", text, "INFO",
                                    Boolean.FALSE, Boolean.FALSE);
                            java.util.ArrayList<Object> newList =
                                    new java.util.ArrayList<Object>(list);
                            newList.add(tip);
                            mSetValue.invoke(state, newList);
                            if (sMsgTime225Logged++ < 8) {
                                XposedBridge.log("[ds美化] v225 px.w 注入时间 tip: " + text
                                        + " listSize=" + list.size());
                            }
                        } catch (Throwable t) {
                            if (sMsgTime225Logged++ < 8) {
                                XposedBridge.log("[ds美化] v225 px.w 注入 EX " + t);
                            }
                        }
                    }
                });
                XposedBridge.log("[ds美化] v225 hookMsgTime225 px ctor OK");
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v225 hookMsgTime225 EX " + t);
        }
    }

    /** v224：格式化消息时间（今天 HH:mm，其它 MM-dd HH:mm） */
    private static String formatMsgTime(long millis) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        java.text.SimpleDateFormat fmt;
        if (now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == c.get(java.util.Calendar.DAY_OF_YEAR)) {
            fmt = new java.text.SimpleDateFormat("HH:mm");
        } else {
            fmt = new java.text.SimpleDateFormat("MM-dd HH:mm");
        }
        return fmt.format(new java.util.Date(millis));
    }

    /**
     * v224：消息时间显示 —— 最接近渲染器的注入点。
     * hook ts9.f（UserChatMessageCell.kt:283 渲染 lambda，before）：
     *  - ts9.f:Lvq = 消息对象（px/nx，含 j 时间戳）
     *  - ts9.c:Ldb6 = 渲染给 UserMessageHint 的状态
     *  - 把 ts9.c setValue(oj5(时间文本)) → 渲染器直接拿到
     */
    private static void hookMsgTime224(final ClassLoader cl) {
        try {
            final Class<?> ts9Cls = cl.loadClass("ts9");
            final java.lang.reflect.Field fF = ts9Cls.getField("f");   // Lvq 消息对象
            final java.lang.reflect.Field fC = ts9Cls.getField("c");   // Ldb6 hint 状态
            final Class<?> vqCls = cl.loadClass("vq");
            final Class<?> pxCls = cl.loadClass("px");
            final Class<?> nxCls = cl.loadClass("nx");
            java.lang.reflect.Field fJ = null;
            try { fJ = nxCls.getField("j"); } catch (Throwable ignored) {}
            if (fJ == null) { try { fJ = pxCls.getField("j"); } catch (Throwable ignored) {} }
            final java.lang.reflect.Field fj = fJ;
            final Class<?> db6Cls = cl.loadClass("db6");
            final java.lang.reflect.Method mSetValue = db6Cls.getMethod("setValue", Object.class);
            final Class<?> oj5Cls = cl.loadClass("oj5");
            final java.lang.reflect.Constructor<?> cOj5 =
                    oj5Cls.getConstructor(int.class, Integer.class, String.class);

            for (java.lang.reflect.Method m : ts9Cls.getDeclaredMethods()) {
                if (!"f".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 3) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object ts9 = param.thisObject;
                            if (ts9 == null) { XposedBridge.log("[ds美化] v224 ts9 null"); return; }
                            Object msg = fF.get(ts9);
                            XposedBridge.log("[ds美化] v224 ts9.f hit, msg=" + (msg == null ? "null" : msg.getClass().getName()));
                            if (msg == null) return;
                            double ts = 0;
                            try { ts = fj.getDouble(msg); } catch (Throwable t) { XposedBridge.log("[ds美化] v224 j read EX " + t); }
                            if (ts <= 0) { XposedBridge.log("[ds美化] v224 ts<=0 ts=" + ts); return; }
                            Object state = fC.get(ts9);
                            if (state == null) { XposedBridge.log("[ds美化] v224 state null"); return; }
                            long millis = (long) (ts > 1e11 ? ts : ts * 1000);
                            Object hint = cOj5.newInstance(Integer.valueOf(1), null,
                                    formatMsgTime(millis));
                            mSetValue.invoke(state, hint);
                            if (sMsgTime224Logged++ < 8) {
                                XposedBridge.log("[ds美化] v224 ts9.f 注入: "
                                        + formatMsgTime(millis));
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[ds美化] v224 ts9.f EX " + t);
                        }
                    }
                });
                XposedBridge.log("[ds美化] v224 hookMsgTime224 ts9.f OK");
                return;
            }
            XposedBridge.log("[ds美化] v224 ts9.f not matched");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化] v224 hookMsgTime224 EX " + t);
        }
    }

    /** v223diag：诊断 UserMessageHint 渲染链。
     * hook w95.g（UserMessageHint 渲染器），打印收到的 hint 对象：
     *  - null：渲染器根本拿不到我们注入的 hint（问题在 ts9 数据流）
     *  - oj5 实例：渲染器拿到了，但可能被折叠/位置不对
     *  - d98 实例：说明有其它提示在显示
     */
    private static void hookHintDiag(final ClassLoader cl) {
        try {
            final Class<?> w95 = cl.loadClass("w95");
            final Class<?> oj5 = cl.loadClass("oj5");
            final Class<?> d98 = cl.loadClass("d98");
            for (java.lang.reflect.Method m : w95.getDeclaredMethods()) {
                if (!"g".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 8 || !"wy5".equals(pts[0].getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            if (sHintDiagCount++ > 15) return;
                            Object hint = param.args[0];
                            if (hint == null) {
                                XposedBridge.log("[ds美化][diag] w95.g hint=null");
                            } else if (oj5.isInstance(hint)) {
                                java.lang.reflect.Field fb = oj5.getField("b");
                                XposedBridge.log("[ds美化][diag] w95.g hint=oj5 b=" + fb.get(hint));
                            } else if (d98.isInstance(hint)) {
                                java.lang.reflect.Field fb = d98.getField("b");
                                XposedBridge.log("[ds美化][diag] w95.g hint=d98 b=" + fb.get(hint));
                            } else {
                                XposedBridge.log("[ds美化][diag] w95.g hint=" + hint.getClass().getName());
                            }
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log("[ds美化][diag] w95.g hooked OK");
                return;
            }
            XposedBridge.log("[ds美化][diag] w95.g not matched");
        } catch (Throwable t) {
            XposedBridge.log("[ds美化][diag] hookHintDiag EX " + t);
        }
    }

    /** 获取全局 Application Context（不依赖 onResume 时序，任何时刻都有值） */
    private static android.content.Context appCtx() {
        try {
            android.app.Application app = (android.app.Application)
                    Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication")
                            .invoke(null);
            return app;
        } catch (Throwable t) {
            return null;
        }
    }
}