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

public final class DsConfig {
    private static final String PREFS = "ds_cfg";
    private DsConfig() {}

    // v476：配置白名单（当前版本认识的合法键）。升级/回退时自动清理残留键，避免旧键干扰
    private static final String[] CFG_WHITELIST = {
            "bubble_on", "gradient_bubble_on", "glass_bubble_on", "gradient_multi_on",
            "gradient_style", "gradient_palette", "gradient_custom1", "gradient_custom2", "gradient_alpha",
            "bubble_radius", "bubble_alpha", "bubble_style", "ai_bubble_color", "ai_bubble_custom_color",
            "bubble_color", "bubble_custom_color", "msg_text_color", "msg_custom_color", "msg_font_size",
            "license_activated", "bubble_stroke_on", "bubble_stroke_width", "bubble_stroke_color",
            "bubble_shadow_on", "bubble_shadow_radius", "bubble_noise_intensity", "bubble_glow_on", "bubble_glow_color",
            "bubble_shape", "bg_path", "bg_off", "float_on", "ball_alpha", "ball_align", "ball_offset_y",
            "panel_theme", "panel_radius", "chat_btn_on", "hide_btn_on", "msg_time_on", "no_hot_update",
            "text_wave_on", "chat_bg_on", "chat_bg_path", "avatar_size", "avatar_x", "avatar_y",
            "greeting", "greeting_show", "hint_color", "input_text_color", "cursor_color", "sys_prompt",
            "sys_presets", "auto_clean", "proxy_host", "device_context_on", "glass_bubble_old", "latex_bubble_on",
            "latex_bubble_style", "latex_bubble_radius", "latex_bubble_padding", "latex_auto_wrap"
    };

    /** v476：清理配置中的未知键（升级/回退后调用，防止残留键干扰旧版逻辑） */
    public static void sanitizeConfig(Context c) {
        try {
            SharedPreferences sp = p(c);
            java.util.Map<String, ?> all = sp.getAll();
            boolean dirty = false;
            for (String key : all.keySet()) {
                boolean known = false;
                for (String wk : CFG_WHITELIST) {
                    if (wk.equals(key)) { known = true; break; }
                }
                if (!known) {
                    sp.edit().remove(key);
                    dirty = true;
                }
            }
            if (dirty) sp.edit().commit();
        } catch (Throwable ignored) {}
    }

    // v379：动态数据目录（支持改包名）
    private static volatile String sDataDir;
    private static volatile String sPkgName;

    /** 宿主应用包名 */
    public static String hostPackage() {
        if (sPkgName != null) return sPkgName;
        try {
            android.app.Application app = android.app.AndroidAppHelper.currentApplication();
            if (app != null) {
                sPkgName = app.getPackageName();
                return sPkgName;
            }
        } catch (Throwable ignored) {}
        return "com.deepseek.chat";
    }

    /** 宿主数据目录（/data/data/<pkg>） */
    public static String dataDir() {
        if (sDataDir != null) return sDataDir;
        try {
            android.app.Application app = android.app.AndroidAppHelper.currentApplication();
            if (app != null) {
                sDataDir = app.getApplicationInfo().dataDir;
                return sDataDir;
            }
        } catch (Throwable ignored) {}
        return "/data/data/" + hostPackage();
    }

    /** 拼接数据目录下的路径 */
    public static String dataPath(String sub) {
        return dataDir() + "/" + sub;
    }

    /** 初始化（由 HookInit 在拿到 context 时调用） */
    public static void initPaths(Context c) {
        try {
            if (c != null) {
                sPkgName = c.getPackageName();
                sDataDir = c.getApplicationInfo().dataDir;
                sanitizeConfig(c);   // v476：自动清理残留配置键
            }
        } catch (Throwable ignored) {}
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) {
        return p(c).getBoolean("enabled", true);
    }

    public static void setEnabled(Context c, boolean v) {
        p(c).edit().putBoolean("enabled", v).commit();
    }

    private static final String KEY_OFF = "bg_off";
    // 背景图是否开启（默认关：修复 v317 之前默认开导致"功能默认打开"）
    public static boolean bgOn(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return !p(c).getBoolean(KEY_OFF, true);
    }

    public static void setBgOn(Context c, boolean v) {
        p(c).edit().putBoolean(KEY_OFF, !v).commit();
    }

// 背景图路径（默认用 DeepSeek 私有目录，模块一定能读到）
    public static String bgPath(Context c) {
        return p(c).getString("bg_path", DsConfig.dataPath("files/ds_bg.png"));
    }
    public static void setBgPath(Context c, String v) {
        p(c).edit().putString("bg_path", v).commit();
    }
    // 纯色背景模式：0=图片壁纸 1=纯黑 2=纯白（配合半透明气泡显玻璃透感）
    public static int bgSolid(Context c) {
        return p(c).getInt("bg_solid", 0);
    }
    public static void setBgSolid(Context c, int v) {
        p(c).edit().putInt("bg_solid", v).commit();
    }

    // 气泡透出强度（0-90，默认40）
    public static int bgAlpha(Context c) {
        return p(c).getInt("bg_alpha", 40);
    }

    public static void setBgAlpha(Context c, int v) {
        p(c).edit().putInt("bg_alpha", v).commit();
    }

// 悬浮球开关（默认开）
    public static boolean floatOn(Context c) {
        return p(c).getBoolean("float_on", false);  // v365：悬浮窗默认关闭
    }
    public static void setFloatOn(Context c, boolean v) {
        p(c).edit().putBoolean("float_on", v).commit();
    }
    // 聊天入口按钮（v243 自绘按钮，默认关）
    public static boolean chatBtnOn(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("chat_btn_on", false);
    }
    public static void setChatBtnOn(Context c, boolean v) {
        p(c).edit().putBoolean("chat_btn_on", v).commit();
    }
// 全局暗色滤镜强度 0-100（默认0）
    public static int darkFilter(Context c) {
        return p(c).getInt("dark_filter", 0);
    }

    public static void setDarkFilter(Context c, int v) {
        p(c).edit().putInt("dark_filter", v).commit();
    }

    // 输入框提示文字颜色（0=不修改）
    public static int hintColor(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return 0;
        return p(c).getInt("hint_color", 0);
    }

    public static void setHintColor(Context c, int v) {
        p(c).edit().putInt("hint_color", v).commit();
    }

    // ===== 输入框美化（v166）=====
    public static int inputTextColor(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return 0;
        return p(c).getInt("input_text_color", 0);  // 0=默认跟随主题
    }
    public static void setInputTextColor(Context c, int v) {
        p(c).edit().putInt("input_text_color", v).commit();
    }
    public static int cursorColor(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return 0;
        return p(c).getInt("cursor_color", 0);  // 0=默认
    }
    public static void setCursorColor(Context c, int v) {
        p(c).edit().putInt("cursor_color", v).commit();
    }

    // ===== 反代（v211）=====
    public static String proxyHost(Context c) {
        return p(c).getString("proxy_host", "");
    }
    public static void setProxyHost(Context c, String v) {
        p(c).edit().putString("proxy_host", v == null ? "" : v).commit();
    }

    // ===== 聊天背景独立（v174）=====
    public static String chatBgPath(Context c) {
        return p(c).getString("chat_bg_path", "");
    }
    public static void setChatBgPath(Context c, String v) {
        p(c).edit().putString("chat_bg_path", v == null ? "" : v).commit();
    }

    // ===== 系统提示词（v170）=====
    public static String systemPrompt(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return "";
        return p(c).getString("system_prompt", "");
    }
    public static void setSystemPrompt(Context c, String v) {
        p(c).edit().putString("system_prompt", v == null ? "" : v).commit();
    }

    // ===== 新功能配置 =====

    // 壁纸模式：0=裁剪(CENTER_CROP) 1=适应(FIT_CENTER) 2=拉伸(FIT_XY)
    public static int bgMode(Context c) {
        return p(c).getInt("bg_mode", 0);
    }

    public static void setBgMode(Context c, int v) {
        p(c).edit().putInt("bg_mode", v).commit();
    }

    // 壁纸模糊强度 0-25（0=不模糊）
    public static int bgBlur(Context c) {
        return p(c).getInt("bg_blur", 0);
    }

    public static void setBgBlur(Context c, int v) {
        p(c).edit().putInt("bg_blur", v).commit();
    }

    // 全局色调：0=默认 1=暖色 2=冷色 3=青绿 4=粉紫
    public static int tintMode(Context c) {
        return p(c).getInt("tint_mode", 0);
    }

    public static void setTintMode(Context c, int v) {
        p(c).edit().putInt("tint_mode", v).commit();
    }

    // ===== 界面页 =====
    // 悬浮球位置：0=右上 1=左上 2=右中 3=右底
    public static int ballPos(Context c) {
        return p(c).getInt("ball_pos", 0);
    }
    public static void setBallPos(Context c, int v) {
        p(c).edit().putInt("ball_pos", v).commit();
    }
    // 悬浮球透明度 20-100（默认90）
    public static int ballAlpha(Context c) {
        return p(c).getInt("ball_alpha", 90);
    }
    public static void setBallAlpha(Context c, int v) {
        p(c).edit().putInt("ball_alpha", v).commit();
    }
    // 面板圆角 0-32（默认24）
    public static int panelRadius(Context c) {
        return p(c).getInt("panel_radius", 24);
    }
    public static void setPanelRadius(Context c, int v) {
        p(c).edit().putInt("panel_radius", v).commit();
    }

    // ===== 聊天页 =====
    // 气泡美化总开关（默认关：修复默认打开问题）
    public static boolean bubbleOn(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("bubble_on", false);
    }
    public static void setBubbleOn(Context c, boolean v) {
        p(c).edit().putBoolean("bubble_on", v).commit();
    }
    // 隐藏消息下方按钮（默认关：修复默认隐藏问题）
    public static boolean hideBtnOn(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("hide_btn_on", false);
    }
    public static void setHideBtnOn(Context c, boolean v) {
        p(c).edit().putBoolean("hide_btn_on", v).commit();
    }
    // 气泡圆角 0-32（默认18）
    public static int bubbleRadius(Context c) {
        return p(c).getInt("bubble_radius", 18);
    }
    public static void setBubbleRadius(Context c, int v) {
        p(c).edit().putInt("bubble_radius", v).commit();
    }
    // 气泡透明度 30-100（默认100）
    public static int bubbleAlpha(Context c) {
        return p(c).getInt("bubble_alpha", 100);
    }
    public static void setBubbleAlpha(Context c, int v) {
        p(c).edit().putInt("bubble_alpha", v).commit();
    }
    // 聊天背景独立开关
    public static boolean chatBgOn(Context c) {
        return p(c).getBoolean("chat_bg_on", false);
    }
    public static void setChatBgOn(Context c, boolean v) {
        p(c).edit().putBoolean("chat_bg_on", v).commit();
    }
    // 消息字体大小 12-24（默认15）
    public static int msgFontSize(Context c) {
        return p(c).getInt("msg_font_size", 15);
    }
    public static void setMsgFontSize(Context c, int v) {
        p(c).edit().putInt("msg_font_size", v).commit();
    }
    // 气泡样式：0=默认 1=圆润 2=方角 3=液态玻璃
    public static int bubbleStyle(Context c) {
        return p(c).getInt("bubble_style", 0);
    }
    public static void setBubbleStyle(Context c, int v) {
        p(c).edit().putInt("bubble_style", v).commit();
    }


    // 消息文字颜色：0=自动 1=黑色 2=白色 3=品牌蓝 4=深灰 5=红色 6=绿色 7=自定义（默认0自动）
    public static int msgTextColor(Context c) {
        return p(c).getInt("msg_text_color", 0);
    }
    public static void setMsgTextColor(Context c, int v) {
        p(c).edit().putInt("msg_text_color", v).commit();
    }

    // ═══ v423：液态玻璃气泡（真模糊）═══
    /** 液态玻璃气泡开关（默认关） */
    public static boolean glassBubbleOn(Context c) {
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("glass_bubble_on", false);
    }
    public static void setGlassBubbleOn(Context c, boolean v) {
        p(c).edit().putBoolean("glass_bubble_on", v).commit();
    }

    // ═══ v424：气泡渐变 ═══
    /** 气泡径向渐变开关（默认关） */
    public static boolean gradientBubbleOn(Context c) {
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("gradient_bubble_on", false);
    }
    public static void setGradientBubbleOn(Context c, boolean v) {
        p(c).edit().putBoolean("gradient_bubble_on", v).commit();
    }
    // v437：渐变样式 0=左上→右下 1=上下 2=左右 3=径向（默认0）
    public static int gradientStyle(Context c) {
        if (!DsLicense.isActivated(c)) return 0;
        return p(c).getInt("gradient_style", 0);
    }
    public static void setGradientStyle(Context c, int v) {
        p(c).edit().putInt("gradient_style", v).commit();
    }
    // v437：渐变配色 0=品牌蓝 1=紫色 2=青绿 3=炫彩 4=粉紫 5=暖橙 6=自定义（默认0）
    public static int gradientPalette(Context c) {
        if (!DsLicense.isActivated(c)) return 0;
        return p(c).getInt("gradient_palette", 0);
    }
    public static void setGradientPalette(Context c, int v) {
        p(c).edit().putInt("gradient_palette", v).commit();
    }
    // v459：渐变自定义起始色（配色选"自定义"时用）
    public static int gradientCustom1(Context c) {
        if (!DsLicense.isActivated(c)) return 0xFF4D6BFE;
        return p(c).getInt("gradient_custom1", 0xFF4D6BFE);
    }
    public static void setGradientCustom1(Context c, int v) {
        p(c).edit().putInt("gradient_custom1", v).commit();
    }
    // v459：渐变自定义结束色
    public static int gradientCustom2(Context c) {
        if (!DsLicense.isActivated(c)) return 0xFF8EA9FF;
        return p(c).getInt("gradient_custom2", 0xFF8EA9FF);
    }
    public static void setGradientCustom2(Context c, int v) {
        p(c).edit().putInt("gradient_custom2", v).commit();
    }
    // v487：气泡样式统一开关 —— AI/用户气泡强制使用同一种样式（颜色统一跟随 AI 气泡色）
    public static boolean unifyBubble(Context c) {
        return p(c).getBoolean("unify_bubble", false);
    }
    public static void setUnifyBubble(Context c, boolean v) {
        p(c).edit().putBoolean("unify_bubble", v).commit();
    }
    // v492：输入框玻璃化开关（半透明玻璃 + 高光）
    public static boolean inputGlassOn(Context c) {
        return p(c).getBoolean("input_glass_on", false);
    }
    public static void setInputGlassOn(Context c, boolean v) {
        p(c).edit().putBoolean("input_glass_on", v).commit();
    }
    // v492：输入框玻璃色（默认浅白玻璃 0x66F5F8FF）
    public static int inputGlassColor(Context c) {
        return p(c).getInt("input_glass_color", 0x66F5F8FF);
    }
    public static void setInputGlassColor(Context c, int v) {
        p(c).edit().putInt("input_glass_color", v).commit();
    }
    // v505：AI 气泡玻璃化（半透明液态玻璃感）——把 AI 气泡色 alpha 降低
    public static boolean aiGlassOn(Context c) {
        return p(c).getBoolean("ai_glass_on", false);
    }
    public static void setAiGlassOn(Context c, boolean v) {
        p(c).edit().putBoolean("ai_glass_on", v).commit();
    }
    // v505：AI 气泡玻璃透明度（40-100，百分比）
    public static int aiGlassAlpha(Context c) {
        return p(c).getInt("ai_glass_alpha", 70);
    }
    public static void setAiGlassAlpha(Context c, int v) {
        p(c).edit().putInt("ai_glass_alpha", v).commit();
    }
    // v462：气泡描边开关（默认关）
    public static boolean bubbleStrokeOn(Context c) {
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("bubble_stroke_on", false);
    }
    public static void setBubbleStrokeOn(Context c, boolean v) {
        p(c).edit().putBoolean("bubble_stroke_on", v).commit();
    }
    // v462：气泡描边颜色（默认白）
    public static int bubbleStrokeColor(Context c) {
        if (!DsLicense.isActivated(c)) return 0xFFFFFFFF;
        return p(c).getInt("bubble_stroke_color", 0xFFFFFFFF);
    }
    public static void setBubbleStrokeColor(Context c, int v) {
        p(c).edit().putInt("bubble_stroke_color", v).commit();
    }
    // v462：气泡描边宽度 dp（默认 0=off，1-5）
    public static int bubbleStrokeWidth(Context c) {
        if (!DsLicense.isActivated(c)) return 0;
        return p(c).getInt("bubble_stroke_width", 0);
    }
    public static void setBubbleStrokeWidth(Context c, int v) {
        p(c).edit().putInt("bubble_stroke_width", v).commit();
    }
    // v462：气泡阴影开关（默认关）
    public static boolean bubbleShadowOn(Context c) {
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("bubble_shadow_on", false);
    }
    public static void setBubbleShadowOn(Context c, boolean v) {
        p(c).edit().putBoolean("bubble_shadow_on", v).commit();
    }
    // v462：气泡阴影半径 px（默认 12）
    public static int bubbleShadowRadius(Context c) {
        if (!DsLicense.isActivated(c)) return 0;
        return p(c).getInt("bubble_shadow_radius", 12);
    }
    public static void setBubbleShadowRadius(Context c, int v) {
        p(c).edit().putInt("bubble_shadow_radius", v).commit();
    }
    // v462：噪点纹理强度 0-100（默认 0=关）
    public static int bubbleNoiseIntensity(Context c) {
        if (!DsLicense.isActivated(c)) return 0;
        return p(c).getInt("bubble_noise_intensity", 0);
    }
    public static void setBubbleNoiseIntensity(Context c, int v) {
        p(c).edit().putInt("bubble_noise_intensity", v).commit();
    }
    // v462：渐变透明度 10-100（默认 100=不透明）
    public static int gradientAlpha(Context c) {
        if (!DsLicense.isActivated(c)) return 100;
        return p(c).getInt("gradient_alpha", 100);
    }
    public static void setGradientAlpha(Context c, int v) {
        p(c).edit().putInt("gradient_alpha", v).commit();
    }
    // v467：多色渐变开关（3-4色华丽渐变）
    public static boolean gradientMultiOn(Context c) {
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("gradient_multi_on", false);
    }
    public static void setGradientMultiOn(Context c, boolean v) {
        p(c).edit().putBoolean("gradient_multi_on", v).commit();
    }
    // v467：气泡外发光开关（金色/彩色 Glow）
    public static boolean bubbleGlowOn(Context c) {
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("bubble_glow_on", false);
    }
    public static void setBubbleGlowOn(Context c, boolean v) {
        p(c).edit().putBoolean("bubble_glow_on", v).commit();
    }
    // v467：气泡外发光颜色（默认金色 0xFFFFC107）
    public static int bubbleGlowColor(Context c) {
        if (!DsLicense.isActivated(c)) return 0xFFFFC107;
        return p(c).getInt("bubble_glow_color", 0xFFFFC107);
    }
    public static void setBubbleGlowColor(Context c, int v) {
        p(c).edit().putInt("bubble_glow_color", v).commit();
    }
    // v467：气泡形状 0=默认圆角 1=大胶囊 2=云朵 3=聊天尾巴（画布层 clipPath 替换）
    public static int bubbleShape(Context c) {
        if (!DsLicense.isActivated(c)) return 0;
        return p(c).getInt("bubble_shape", 0);
    }
    public static void setBubbleShape(Context c, int v) {
        p(c).edit().putInt("bubble_shape", v).commit();
    }

    // ═══ v421：设备上下文注入 ═══
    /** 设备状态注入开关（默认关） */
    public static boolean deviceContextOn(Context c) {
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("device_context_on", false);
    }
    public static void setDeviceContextOn(Context c, boolean v) {
        p(c).edit().putBoolean("device_context_on", v).commit();
    }

    // 消息文字自定义颜色值（msgTextColor=7 时生效）
    public static int msgCustomColor(Context c) {
        return p(c).getInt("msg_custom_color", 0xFFFFFFFF);
    }
    public static void setMsgCustomColor(Context c, int v) {
        p(c).edit().putInt("msg_custom_color", v).commit();
    }
    // 用户气泡自定义颜色值（调色盘）
    public static int bubbleCustomColor(Context c) {
        return p(c).getInt("bubble_custom_color", 0xFF4D6BFE);
    }
    public static void setBubbleCustomColor(Context c, int v) {
        p(c).edit().putInt("bubble_custom_color", v).commit();
    }
    // AI 气泡自定义颜色值（调色盘）
    public static int aiBubbleCustomColor(Context c) {
        return p(c).getInt("ai_bubble_custom_color", 0xFFF2F2F4);
    }
    public static void setAiBubbleCustomColor(Context c, int v) {
        p(c).edit().putInt("ai_bubble_custom_color", v).commit();
    }
    // 文字波纹动效（v309）
    public static boolean textWave(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("text_wave", false);
    }
    public static void setTextWave(Context c, boolean v) {
        p(c).edit().putBoolean("text_wave", v).commit();
    }
    // 晃动视差（v309）
    public static boolean shakeParallax(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("shake_parallax", false);
    }
    public static void setShakeParallax(Context c, boolean v) {
        p(c).edit().putBoolean("shake_parallax", v).commit();
    }
    // 判断系统/App 是否深色模式
    public static boolean isDarkMode(Context c) {
        try {
            int mode = c.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) {
            return false;
        }
    }
    // 提示词预设：JSON 数组 [{"name":"...","content":"..."}]
    public static String promptPresets(Context c) {
        return p(c).getString("sys_presets", "[]");
    }
    public static void setPromptPresets(Context c, String v) {
        p(c).edit().putString("sys_presets", v).commit();
    }
    // 用户气泡自定义颜色：0=跟随样式 1=品牌蓝 2=深蓝 3=青绿 4=紫色 5=粉色 6=橙色
    public static int bubbleColor(Context c) {
        return p(c).getInt("bubble_color", 0);
    }
    public static void setBubbleColor(Context c, int v) {
        p(c).edit().putInt("bubble_color", v).commit();
    }
    // AI 气泡颜色：0=浅灰 1=品牌蓝 2=深蓝 3=青绿 4=紫色 5=粉色 6=橙色
    public static int aiBubbleColor(Context c) {
        return p(c).getInt("ai_bubble_color", 0);
    }
    public static void setAiBubbleColor(Context c, int v) {
        p(c).edit().putInt("ai_bubble_color", v).commit();
    }
    // ===== 更多页 =====
    // 自动清理缓存：0=关 1=启动时 2=每天
    public static int autoClean(Context c) {
        return p(c).getInt("auto_clean", 0);
    }
    // 文字晃动视差开关（已废弃，保留读取兼容）
    public static boolean parallaxOn(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("parallax_on", false);
    }
    // 调试日志开关
    public static void setAutoClean(Context c, int v) {
        p(c).edit().putInt("auto_clean", v).commit();
    }
    // 调试日志开关
    public static boolean debugLog(Context c) {
        return p(c).getBoolean("debug_log", false);
    }
    public static void setDebugLog(Context c, boolean v) {
        p(c).edit().putBoolean("debug_log", v).commit();
    }
    // 隐藏悬浮球开关（更多页控制）
    public static boolean ballHidden(Context c) {
        return p(c).getBoolean("ball_hidden", false);
    }
    public static void setBallHidden(Context c, boolean v) {
        p(c).edit().putBoolean("ball_hidden", v).commit();
    }
    // 灵动岛水平位置：0=左 1=中 2=右（默认中）
    public static int ballAlign(Context c) {
        return p(c).getInt("ball_align", 1);
    }
    public static void setBallAlign(Context c, int v) {
        p(c).edit().putInt("ball_align", v).commit();
    }
    // 灵动岛垂直偏移（dp，距顶部/底部距离，默认 55）
    public static int ballOffsetY(Context c) {
        return p(c).getInt("ball_offy", 55);
    }
    public static void setBallOffsetY(Context c, int v) {
        p(c).edit().putInt("ball_offy", v).commit();
    }
    // 面板主题：0=跟随系统 1=强制深色 2=强制浅色（默认1深色液态玻璃）
    public static int panelTheme(Context c) {
        return p(c).getInt("panel_theme", 1);
    }
    public static void setPanelTheme(Context c, int v) {
        p(c).edit().putInt("panel_theme", v).commit();
    }
    // 壁纸遮罩强度 0-80（默认0：不用遮罩压暗，靠文字对比度增强保证清晰）
    public static int maskStrength(Context c) {
        return p(c).getInt("mask_strength", 0);
    }
    public static void setMaskStrength(Context c, int v) {
        p(c).edit().putInt("mask_strength", v).commit();
    }
    // 主页欢迎语（空=不修改）
    public static String greeting(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return "";
        return p(c).getString("greeting", "");
    }
    public static void setGreeting(Context c, String v) {
        p(c).edit().putString("greeting", v).commit();
    }
    // 消息时间显示
    public static boolean showMsgTime(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("show_msg_time", false);
    }
    public static void setShowMsgTime(Context c, boolean v) {
        p(c).edit().putBoolean("show_msg_time", v).commit();
    }
    // AI 消息小尾巴文字（空=不显示；如 "AI生成" "DeepSeek"）
    public static String aiTailText(Context c) {
        return p(c).getString("ai_tail_text", "");
    }
    public static void setAiTailText(Context c, String v) {
        p(c).edit().putString("ai_tail_text", v).commit();
    }
    // 自动继续生成
    public static boolean autoContinue(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("auto_continue", false);
    }
    public static void setAutoContinue(Context c, boolean v) {
        p(c).edit().putBoolean("auto_continue", v).commit();
    }
    // ===== Deekseep 全功能迁移 =====
    // 去除安全审查
    public static boolean removeCensor(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("remove_censor", false);
    }
    public static void setRemoveCensor(Context c, boolean v) {
        p(c).edit().putBoolean("remove_censor", v).commit();
    }
    // 聊天记录多选删除
    public static boolean multiSelectDel(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("multi_select_del", false);
    }
    public static void setMultiSelectDel(Context c, boolean v) {
        p(c).edit().putBoolean("multi_select_del", v).commit();
    }
    // 编辑聊天记录
    public static boolean editMessages(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("edit_messages", false);
    }
    public static void setEditMessages(Context c, boolean v) {
        p(c).edit().putBoolean("edit_messages", v).commit();
    }
    // 自动备份数据库
    public static boolean autoBackup(Context c) {
        return p(c).getBoolean("auto_backup", false);
    }
    public static void setAutoBackup(Context c, boolean v) {
        p(c).edit().putBoolean("auto_backup", v).commit();
    }
    // 解锁专家模式
    public static boolean expertMode(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("expert_mode", false);
    }
    public static void setExpertMode(Context c, boolean v) {
        p(c).edit().putBoolean("expert_mode", v).commit();
    }
    // AI 心跳
    public static boolean aiHeartbeat(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("ai_heartbeat", false);
    }
    public static void setAiHeartbeat(Context c, boolean v) {
        p(c).edit().putBoolean("ai_heartbeat", v).commit();
    }
    // 本地禁言
    public static boolean localMute(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("local_mute", false);
    }
    public static void setLocalMute(Context c, boolean v) {
        p(c).edit().putBoolean("local_mute", v).commit();
    }
    // 禁用数据优化
    public static boolean disableTraining(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("disable_training", false);
    }
    public static void setDisableTraining(Context c, boolean v) {
        p(c).edit().putBoolean("disable_training", v).commit();
    }
    // 自定义头像
    public static String avatarPath(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return "";
        return p(c).getString("avatar_path", "");
    }
    public static void setAvatarPath(Context c, String v) {
        p(c).edit().putString("avatar_path", v).commit();
    }
    // 头像水平位置 -50~50（默认0=居中，正=右移）
    public static int avatarX(Context c) {
        return p(c).getInt("avatar_x", 0);
    }
    public static void setAvatarX(Context c, int v) {
        p(c).edit().putInt("avatar_x", v).commit();
    }
    // 头像垂直位置 -50~50（默认0=居中，正=下移）
    public static int avatarY(Context c) {
        return p(c).getInt("avatar_y", 0);
    }
    public static void setAvatarY(Context c, int v) {
        p(c).edit().putInt("avatar_y", v).commit();
    }
    // 头像大小 40~120dp（默认72）
    public static int avatarSize(Context c) {
        return p(c).getInt("avatar_size", 72);
    }
    public static void setAvatarSize(Context c, int v) {
        p(c).edit().putInt("avatar_size", v).commit();
    }
    // 欢迎语水平偏移 -50~50（默认0）
    public static int greetX(Context c) {
        return p(c).getInt("greet_x", 0);
    }
    public static void setGreetX(Context c, int v) {
        p(c).edit().putInt("greet_x", v).commit();
    }
    // 欢迎语垂直偏移 -50~50（默认0）
    public static int greetY(Context c) {
        return p(c).getInt("greet_y", 0);
    }
    public static void setGreetY(Context c, int v) {
        p(c).edit().putInt("greet_y", v).commit();
    }
    // 欢迎语隐藏（默认显示）
    public static boolean greetHidden(Context c) {
        return p(c).getBoolean("greet_hidden", false);
    }
    public static void setGreetHidden(Context c, boolean v) {
        p(c).edit().putBoolean("greet_hidden", v).commit();
    }
    // 鲸鱼动效
    public static boolean whaleAnim(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("whale_anim", true);
    }
    public static void setWhaleAnim(Context c, boolean v) {
        p(c).edit().putBoolean("whale_anim", v).commit();
    }
    // 禁用热更新
    public static boolean noHotUpdate(Context c) {
        return p(c).getBoolean("no_hot_update", false);
    }
    public static void setNoHotUpdate(Context c, boolean v) {
        p(c).edit().putBoolean("no_hot_update", v).commit();
    }
    // 实时时钟显示（壁纸层叠加）
    public static boolean showClock(Context c) {
        // v377：卡密门禁
        if (!DsLicense.isActivated(c)) return false;
        return p(c).getBoolean("show_clock", false);
    }
    public static void setShowClock(Context c, boolean v) {
        p(c).edit().putBoolean("show_clock", v).commit();
    }
    // v368：壁纸模式（0=叠加层[默认]，1=原生绘制[DEKK 风格]）
    public static int wallpaperMode(Context c) {
        return p(c).getInt("wallpaper_mode", 0);
    }
    public static void setWallpaperMode(Context c, int v) {
        p(c).edit().putInt("wallpaper_mode", v).commit();
    }
}