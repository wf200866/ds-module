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
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v309：文字波纹动效（移植自 Deekseep TextWaveEngine 核心）。
 * 给 TextView 的 Paint 设置动态 LinearGradient shader（光折射波纹），
 * 每帧更新矩阵产生流动效果。纯绘制层，不依赖 DeepSeek 类名。
 */
public class DsTextWave {

    private static final String ENABLED = "text_wave_enabled";
    private static final String SPEED = "text_wave_speed";

    private static final float[] STOPS = {0f, 0.12f, 0.26f, 0.50f, 0.74f, 1f};
    private static final float DIAGONAL = 0.70710677f;
    private static final Map<Paint, ShaderEntry> SHADERS =
            Collections.synchronizedMap(new WeakHashMap<Paint, ShaderEntry>());
    private static volatile WeakReference<Activity> activityRef = new WeakReference<>(null);

    static class ShaderEntry {
        final LinearGradient shader;
        final android.graphics.Matrix matrix = new android.graphics.Matrix();
        float wavelength;
        boolean lightGlyph;
        int alpha;
        ShaderEntry(float wavelength, boolean lightGlyph, int alpha) {
            this.wavelength = wavelength;
            this.lightGlyph = lightGlyph;
            this.alpha = alpha;
            this.shader = buildShader(lightGlyph);
        }
    }

    public static class PaintState {
        final Paint paint;
        final Shader previous;
        public PaintState(Paint p, Shader s) { paint = p; previous = s; }
    }

    static boolean isEnabled(Context c) {
        return DsConfig.textWave(c);
    }

    static void setEnabled(Context c, boolean v) {
        DsConfig.setTextWave(c, v);
    }

    static float speed(Context c) {
        return 1.0f;
    }

    private static LinearGradient buildShader(boolean lightGlyph) {
        int[] colors = lightGlyph
                ? new int[]{0x80FFFFFF, 0x80E0E8FF, 0x8040A0FF, 0x80E0E8FF, 0x80FFFFFF}
                : new int[]{0x804060A0, 0x8040A0FF, 0x8020C0A0, 0x8040A0FF, 0x804060A0};
        return new LinearGradient(0, 0, 1, 1, colors, STOPS, Shader.TileMode.REPEAT);
    }

    /** 应用波纹到 paint（onDraw 前调用） */
    public static PaintState apply(Paint paint, float density, Context ctx) {
        if (paint == null || paint.getAlpha() <= 8 || paint.getTextSize() <= 0f
                || paint.getShader() != null || !isEnabled(ctx)) {
            return null;
        }
        boolean lightGlyph = luminance(paint.getColor()) >= 0.48f;
        float wavelength = clamp(paint.getTextSize() * 0.82f, 9.5f * density, 24f * density);
        int alpha = Color.alpha(paint.getColor());
        ShaderEntry entry;
        synchronized (SHADERS) {
            entry = SHADERS.get(paint);
            if (entry == null || Math.abs(entry.wavelength - wavelength) > 0.5f
                    || entry.lightGlyph != lightGlyph || entry.alpha != alpha) {
                entry = new ShaderEntry(wavelength, lightGlyph, alpha);
                SHADERS.put(paint, entry);
            }
        }
        long period = Math.max(360L, Math.round(1180f / Math.max(0.1f, speed(ctx))));
        float phase = (SystemClock.uptimeMillis() % period) / (float) period * wavelength;
        entry.matrix.reset();
        entry.matrix.setTranslate(-phase * DIAGONAL, phase * DIAGONAL);
        entry.shader.setLocalMatrix(entry.matrix);
        Shader previous = paint.getShader();
        paint.setShader(entry.shader);
        return new PaintState(paint, previous);
    }

    public static void restore(PaintState state) {
        if (state != null && state.paint != null) state.paint.setShader(state.previous);
    }

    /** 启动帧驱动（每帧重绘 decor，让波纹流动） */
    static void start(final Activity activity) {
        activityRef = new WeakReference<>(activity);
        final View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor == null) return;
        decor.post(new Runnable() {
            @Override public void run() {
                try {
                    Activity a = activityRef.get();
                    if (a == null) return;
                    Context c = a.getApplicationContext();
                    if (!isEnabled(c)) return;
                    View d = a.getWindow() == null ? null : a.getWindow().getDecorView();
                    if (d != null) d.invalidate();
                    d.postDelayed(this, 16);  // ~60fps
                } catch (Throwable ignored) {
                }
            }
        });
    }

    static void stop() {
        activityRef.clear();
    }

    private static float luminance(int color) {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color)
                + 0.114f * Color.blue(color)) / 255f;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}