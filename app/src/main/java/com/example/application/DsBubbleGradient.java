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

import de.robv.android.xposed.XposedHelpers;

/**
 * v424：气泡渐变支持（实验）
 *
 * 现有气泡只支持纯色背景（se0 的第3参 brush = zc.o 单例）。
 * 本类探索用 Compose 渐变 Brush 替换纯色：
 *
 * Brush 实现（2.5.1）：
 *   - c84   = SolidColor（纯色，静态单例）
 *   - ij    = RadialGradient  ((FFFFF) = cx, cy, radius, ?, color)
 *   - gn8   = SweepGradient   ((xf2,xf2,xf2,xf2,F) = 4×ColorStop + tileMode)
 *   - i99   = PathBrush       ((hf) = Path 包装)
 *   - vf2   = 渐变基类（4 个 ColorStop，颜色由 a(J) 传入）
 *
 * 颜色档位：xf2 接口 → n03(float) 固定位置 / az6 / gf7
 */
public class DsBubbleGradient {

    /** 用 ij 创建"矩形渐变" brush（ij = Rect 区域内渐变） */
    public static Object makeRadial(ClassLoader cl, float cx, float cy, float radius, float tile) {
        // 注意：ij(FFFFF) 的 4 个 float 是【矩形范围】(left, top, right, bottom)，不是圆心半径！
        // 之前传 (0.5,0.3,0.8,0,0) 导致 right<left 崩溃
        try {
            Class<?> ij = XposedHelpers.findClass("ij", cl);
            java.lang.reflect.Constructor<?> c = ij.getConstructor(
                    float.class, float.class, float.class, float.class, float.class);
            c.setAccessible(true);
            return c.newInstance(0f, 0f, 1f, 1f, tile);   // 单位矩形
        } catch (Throwable t) {
            de.robv.android.xposed.XposedBridge.log("[ds美化][渐变] makeRadial 失败: " + t);
            return null;
        }
    }

    /** 用 n03(float) 创建 ColorStop */
    public static Object makeStop(ClassLoader cl, float pos) {
        try {
            Class<?> n03 = XposedHelpers.findClass("n03", cl);
            java.lang.reflect.Constructor<?> c = n03.getConstructor(float.class);
            c.setAccessible(true);
            return c.newInstance(pos);
        } catch (Throwable t) {
            de.robv.android.xposed.XposedBridge.log("[ds美化][渐变] makeStop 失败: " + t);
            return null;
        }
    }

    /** 用 gn8(stop,stop,stop,stop,tile) 创建扫描/线性渐变 */
    public static Object makeSweep(ClassLoader cl, float tile) {
        try {
            Class<?> gn8 = XposedHelpers.findClass("gn8", cl);
            Class<?> xf2 = XposedHelpers.findClass("xf2", cl);
            java.lang.reflect.Constructor<?> c = gn8.getConstructor(xf2, xf2, xf2, xf2, float.class);
            c.setAccessible(true);
            Object s0 = makeStop(cl, 0f);
            Object s1 = makeStop(cl, 0.33f);
            Object s2 = makeStop(cl, 0.66f);
            Object s3 = makeStop(cl, 1f);
            return c.newInstance(s0, s1, s2, s3, tile);
        } catch (Throwable t) {
            de.robv.android.xposed.XposedBridge.log("[ds美化][渐变] makeSweep 失败: " + t);
            return null;
        }
    }
}