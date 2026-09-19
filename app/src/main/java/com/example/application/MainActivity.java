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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {

    private EditText input;
    private TextView av;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float dm = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF2F2F7);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * dm);
        root.setPadding(p, p, p, p * 2);

        TextView title = new TextView(this);
        title.setText("ds美化 · DeepSeek 背景 + 悬浮球");
        title.setTextColor(0xFF1C1C1E);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView status = new TextView(this);
        status.setText(isModuleActivated() ? "✅ 模块已激活" : "❌ 模块未激活（去 LSPosed 启用）");
        status.setTextColor(isModuleActivated() ? 0xFF1F9D55 : 0xFFD64545);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        root.addView(status);

        // ===== 背景开关 =====
        Switch sw = new Switch(this);
        sw.setText("启用背景图");
        sw.setTextColor(0xFF1C1C1E);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sw.setChecked(DsConfig.bgOn(this));
        LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        swl.topMargin = (int) (24 * dm);
        sw.setLayoutParams(swl);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) {
                DsConfig.setBgOn(getApplicationContext(), v);
            }
        });
        root.addView(sw);

        // ===== 悬浮球开关 =====
        Switch swFloat = new Switch(this);
        swFloat.setText("启用美化悬浮球");
        swFloat.setTextColor(0xFF1C1C1E);
        swFloat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        swFloat.setChecked(DsConfig.floatOn(this));
        LinearLayout.LayoutParams sfl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sfl.topMargin = (int) (12 * dm);
        swFloat.setLayoutParams(sfl);
        swFloat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) {
                DsConfig.setFloatOn(getApplicationContext(), v);
            }
        });
        root.addView(swFloat);

        // ===== 背景路径输入 =====
        TextView pt = new TextView(this);
        pt.setText("背景图片完整路径（默认已填好）");
        pt.setTextColor(0xFF8E8E93);
        pt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams ptl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ptl.topMargin = (int) (20 * dm);
        pt.setLayoutParams(ptl);
        root.addView(pt);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setText(DsConfig.bgPath(this));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable ib = new GradientDrawable();
        ib.setColor(0xFFFFFFFF);
        ib.setCornerRadius(10 * dm);
        input.setBackgroundDrawable(ib);
        int ip = (int) (12 * dm);
        input.setPadding(ip, ip, ip, ip);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        il.topMargin = (int) (8 * dm);
        input.setLayoutParams(il);
        root.addView(input);

        // ===== 浓度 =====
        TextView at = new TextView(this);
        at.setText("气泡透出浓度");
        at.setTextColor(0xFF8E8E93);
        at.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams atl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        atl.topMargin = (int) (20 * dm);
        at.setLayoutParams(atl);
        root.addView(at);

        av = new TextView(this);
        av.setText(DsConfig.bgAlpha(this) + "%");
        av.setTextColor(0xFF007AFF);
        av.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        av.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(av);

        SeekBar seek = new SeekBar(this);
        seek.setMax(90);
        seek.setProgress(DsConfig.bgAlpha(this));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                DsConfig.setBgAlpha(getApplicationContext(), v);
                av.setText(v + "%");
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(seek);

        // ===== 保存 =====
        TextView apply = new TextView(this);
        apply.setText("保存设置");
        apply.setTextColor(Color.WHITE);
        apply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        apply.setTypeface(Typeface.DEFAULT_BOLD);
        apply.setGravity(Gravity.CENTER);
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(0xFF007AFF);
        bb.setCornerRadius(14 * dm);
        apply.setBackgroundDrawable(bb);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * dm));
        al.topMargin = (int) (28 * dm);
        apply.setLayoutParams(al);
        apply.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String s = input.getText().toString().trim();
                if (s.length() > 0 && !new File(s).exists()) {
                    Toast.makeText(getApplicationContext(), "图片不存在，将使用内置壁纸", Toast.LENGTH_SHORT).show();
                }
                DsConfig.setBgPath(getApplicationContext(), s);
                Toast.makeText(getApplicationContext(), "已保存，重启 DeepSeek 生效", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(apply);

        TextView tip = new TextView(this);
        tip.setText("1. 在 LSPosed 启用模块\n2. 点击保存\n3. 重启 DeepSeek\n\n默认背景图：\n/storage/emulated/0/Download/5d82752df90143ba925c3d1aeba4c226.png\n\n悬浮球位于屏幕右侧，点击展开美化面板\n（背景开关/气泡浓度/暗色氛围/提示色）");
        tip.setTextColor(0xFF8E8E93);
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = (int) (16 * dm);
        tip.setLayoutParams(tl);
        root.addView(tip);

        scroll.addView(root);
        setContentView(scroll);
    }

    public static boolean isModuleActivated() {
        return false;
    }
}