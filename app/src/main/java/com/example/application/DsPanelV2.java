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
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * v351：全新面板 —— 从零构建（iOS 26 液态玻璃）
 *
 * 结构：
 *   root (FrameLayout)
 *   ├─ glassBg（截屏模糊背景层）
 *   ├─ mainLayer
 *   │   ├─ topBar（玻璃标题栏：标题 + 关闭）
 *   │   ├─ pageHost（4 页内容）
 *   │   └─ dock（玻璃胶囊底部导航）
 *   └─ configLayer（配置子页，底部弹出）
 */
public class DsPanelV2 {

    private final Activity act;
    private final FrameLayout root;
    private final FrameLayout pageHost;
    private final ScrollView[] pages = new ScrollView[4];
    private final LinearLayout[] pageContent = new LinearLayout[4];
    private final LinearLayout configLayer;
    private TextView configTitle;
    private LinearLayout configContent;
    private final View configPanel;
    private final LinearLayout dock;
    private final TextView[] dockTabs = new TextView[4];
    private final String[] dockNames = {"主页", "界面", "聊天", "更多"};
    private final int[] dockIcons = {0x1F3A8, 0x1F3A8, 0x1F4AC, 0x1F9E9};
    private int currentPage = 0;

    public DsPanelV2(Activity act) {
        this.act = act;
        DsUI.dark = DsFloat.isDark(act);

        // ── 根容器 ──
        root = new FrameLayout(act);
        root.setBackgroundColor(DsUI.panelBg());
        root.setClickable(true);
        root.setFocusable(true);
        root.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) { return true; }
        });

        // ── 玻璃背景层（截屏模糊）──
        android.widget.ImageView glassBg = new android.widget.ImageView(act);
        glassBg.setTag("glass_bg");
        glassBg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        glassBg.setAlpha(0f);
        root.addView(glassBg, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // v352：色调覆盖层（统一玻璃色调，避免发灰浑浊）
        View scrim = new View(act);
        scrim.setTag("glass_scrim");
        scrim.setBackgroundColor(DsUI.dark ? 0xB30A0A0E : 0xCCF2F2F6);
        scrim.setAlpha(0f);
        root.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ── 主层（顶部栏 + 页面 + dock）──
        LinearLayout mainLayer = new LinearLayout(act);
        mainLayer.setOrientation(LinearLayout.VERTICAL);
        root.addView(mainLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 顶部栏
        mainLayer.addView(buildTopBar());

        // 页面容器
        pageHost = new FrameLayout(act);
        mainLayer.addView(pageHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 4 个页面
        for (int i = 0; i < 4; i++) {
            ScrollView sv = new ScrollView(act);
            sv.setFillViewport(true);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            LinearLayout content = new LinearLayout(act);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(DsUI.dp(act, 16), DsUI.dp(act, 4), DsUI.dp(act, 16), DsUI.dp(act, 20));
            sv.addView(content);
            pageHost.addView(sv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            sv.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            pages[i] = sv;
            pageContent[i] = content;
        }

        // 底部 dock
        dock = buildDock();
        mainLayer.addView(dock, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, DsUI.dp(act, 64)));

        // ── 配置层（底部弹出子页）──
        configLayer = new LinearLayout(act);
        configLayer.setOrientation(LinearLayout.VERTICAL);
        configLayer.setVisibility(View.GONE);
        configLayer.setBackgroundColor(0x66000000);
        configLayer.setClickable(true);
        configLayer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { hideConfig(); }
        });
        // 子页面板
        configPanel = buildConfigPanel();
        FrameLayout.LayoutParams cfgLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (int)(act.getResources().getDisplayMetrics().heightPixels * 0.72f));
        cfgLp.gravity = Gravity.BOTTOM;
        configLayer.addView(configPanel, cfgLp);
        root.addView(configLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 构建页面内容
        buildHomePage();
        buildFacePage();
        buildChatPage();
        buildMorePage();
    }

    public View getRoot() { return root; }

    /** 截屏模糊（在面板可见前调用） */
    public void captureGlass(View decor) {
        try {
            android.widget.ImageView gb = (android.widget.ImageView) root.findViewWithTag("glass_bg");
            View scrim = root.findViewWithTag("glass_scrim");
            if (gb == null) return;
            int w = decor.getWidth(), h = decor.getHeight();
            if (w <= 0 || h <= 0) return;
            int sw = Math.max(1, w / 4), sh = Math.max(1, h / 4);
            android.graphics.Bitmap shot = android.graphics.Bitmap.createBitmap(
                    sw, sh, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(shot);
            c.scale(1f / 4f, 1f / 4f);
            decor.draw(c);
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                gb.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                        40f, 40f, android.graphics.Shader.TileMode.CLAMP));
            }
            gb.setImageBitmap(shot);
            gb.setAlpha(1f);
            if (scrim != null) scrim.setAlpha(1f);  // v352：显示色调层
        } catch (Throwable ignored) {}
    }

    // ═══════════════════════ 顶部栏 ═══════════════════════

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(DsUI.dp(act, 18), DsUI.dp(act, 14), DsUI.dp(act, 14), DsUI.dp(act, 14));

        TextView title = new TextView(act);
        title.setText("ds美化");
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(DsUI.cText());
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 关闭按钮（圆形）
        TextView close = new TextView(act);
        close.setText("✕");
        close.setTextSize(15);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(DsUI.cTextSub());
        GradientDrawable cb = new GradientDrawable();
        cb.setShape(GradientDrawable.OVAL);
        cb.setColor(DsUI.cRowBg());
        close.setBackgroundDrawable(cb);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { DsFloat.closePanel(act); }
        });
        DsUI.pressFeedback(close);
        bar.addView(close, new LinearLayout.LayoutParams(DsUI.dp(act, 32), DsUI.dp(act, 32)));
        return bar;
    }

    // ═══════════════════════ 底部导航 ═══════════════════════

    private LinearLayout buildDock() {
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(DsUI.dp(act, 8), DsUI.dp(act, 6), DsUI.dp(act, 8), DsUI.dp(act, 10));

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout tab = new LinearLayout(act);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, DsUI.dp(act, 6), 0, DsUI.dp(act, 6));

            TextView icon = new TextView(act);
            icon.setText(dockIconChar(i));
            icon.setTextSize(17);
            icon.setGravity(Gravity.CENTER);
            icon.setIncludeFontPadding(false);
            tab.addView(icon);

            TextView label = new TextView(act);
            label.setText(dockNames[i]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            llp.topMargin = DsUI.dp(act, 2);
            tab.addView(label, llp);

            applyDockStyle(tab, i == 0);
            tab.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    switchPage(idx);
                }
            });
            DsUI.pressFeedback(tab);
            bar.addView(tab, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            dockTabs[i] = label;
        }
        return bar;
    }

    private String dockIconChar(int i) {
        switch (i) {
            case 0: return "⌂";
            case 1: return "◐";
            case 2: return "✦";
            default: return "⋯";
        }
    }

    private void applyDockStyle(LinearLayout tab, boolean selected) {
        // 选中：玻璃高亮胶囊；未选中：透明
        if (selected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(DsUI.alpha(DsUI.BRAND, DsUI.dark ? 0x33 : 0x1F));
            bg.setCornerRadius(DsUI.dp(act, 16));
            tab.setBackgroundDrawable(bg);
        } else {
            tab.setBackgroundColor(0x00000000);
        }
        // 文字/图标颜色
        for (int i = 0; i < tab.getChildCount(); i++) {
            View c = tab.getChildAt(i);
            if (c instanceof TextView) {
                ((TextView) c).setTextColor(selected ? DsUI.BRAND : DsUI.cTextSub());
            }
        }
    }

    private void switchPage(int idx) {
        currentPage = idx;
        for (int i = 0; i < 4; i++) {
            pages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            applyDockStyle((LinearLayout) dockTabs[i].getParent(), i == idx);
        }
        pages[idx].scrollTo(0, 0);
    }

    // ═══════════════════════ 配置子页 ═══════════════════════

    private View buildConfigPanel() {
        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(DsUI.dark ? 0xF2131318 : 0xF2FFFFFF);
        bg.setCornerRadii(new float[]{
                DsUI.dp(act, 24), DsUI.dp(act, 24),
                DsUI.dp(act, 24), DsUI.dp(act, 24), 0, 0, 0, 0});
        panel.setBackgroundDrawable(bg);
        panel.setClickable(true);

        // 顶部栏
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(DsUI.dp(act, 18), DsUI.dp(act, 16), DsUI.dp(act, 18), DsUI.dp(act, 10));

        configTitle = new TextView(act);
        configTitle.setText("");
        configTitle.setTextSize(18);
        configTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        configTitle.setTextColor(DsUI.cText());
        bar.addView(configTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(act);
        close.setText("✕");
        close.setTextSize(15);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(DsUI.cTextSub());
        GradientDrawable cb = new GradientDrawable();
        cb.setShape(GradientDrawable.OVAL);
        cb.setColor(DsUI.cRowBg());
        close.setBackgroundDrawable(cb);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { hideConfig(); }
        });
        DsUI.pressFeedback(close);
        bar.addView(close, new LinearLayout.LayoutParams(DsUI.dp(act, 32), DsUI.dp(act, 32)));
        panel.addView(bar);

        // 滚动内容
        ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        configContent = new LinearLayout(act);
        configContent.setOrientation(LinearLayout.VERTICAL);
        configContent.setPadding(DsUI.dp(act, 16), DsUI.dp(act, 4), DsUI.dp(act, 16), DsUI.dp(act, 28));
        sv.addView(configContent);
        panel.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return panel;
    }

    /** 打开配置子页 */
    public void showConfig(String title, DsFloat.ConfigBuilder builder) {
        try {
            configTitle.setText(title);
            configContent.removeAllViews();
            builder.build(configContent);
            configLayer.setVisibility(View.VISIBLE);
            configLayer.setAlpha(0f);
            configPanel.setTranslationY(DsUI.dp(act, 60));
            configLayer.animate().alpha(1f).setDuration(180).start();
            configPanel.animate().translationY(0).setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        } catch (Throwable ignored) {}
    }

    /** 关闭配置子页 */
    public void hideConfig() {
        configLayer.animate().alpha(0f).setDuration(160).withEndAction(new Runnable() {
            public void run() {
                configLayer.setVisibility(View.GONE);
            }
        }).start();
    }

    // ═══════════════════════ 页面构建 ═══════════════════════

    private void buildHomePage() {
        LinearLayout c = pageContent[0];

        // Hero 卡
        LinearLayout hero = DsUI.glassCard(act, c);
        LinearLayout heroRow = new LinearLayout(act);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        heroRow.setPadding(DsUI.dp(act, 16), DsUI.dp(act, 14), DsUI.dp(act, 16), DsUI.dp(act, 14));
        TextView whale = new TextView(act);
        whale.setText("🐋");
        whale.setTextSize(26);
        heroRow.addView(whale);
        LinearLayout heroTexts = new LinearLayout(act);
        heroTexts.setOrientation(LinearLayout.VERTICAL);
        heroTexts.setPadding(DsUI.dp(act, 12), 0, 0, 0);
        TextView t1 = new TextView(act);
        t1.setText("ds美化 控制中心");
        t1.setTextSize(17);
        t1.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t1.setTextColor(DsUI.cText());
        heroTexts.addView(t1);
        TextView t2 = new TextView(act);
        t2.setText("DeepSeek 增强 · 完全本地");
        t2.setTextSize(12);
        t2.setTextColor(DsUI.cTextSub());
        heroTexts.addView(t2);
        heroRow.addView(heroTexts);
        hero.addView(heroRow);

        // 快捷入口（2x2 网格）
        c.addView(DsUI.sectionHeader(act, "快捷入口"));
        LinearLayout grid = DsUI.glassCard(act, c);
        LinearLayout row1 = new LinearLayout(act);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(DsUI.dp(act, 8), DsUI.dp(act, 8), DsUI.dp(act, 8), DsUI.dp(act, 4));
        LinearLayout row2 = new LinearLayout(act);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(DsUI.dp(act, 8), DsUI.dp(act, 4), DsUI.dp(act, 8), DsUI.dp(act, 8));
        grid.addView(row1);
        grid.addView(row2);

        addQuickTile(row1, "壁", "背景壁纸", "背景壁纸");
        addQuickTile(row1, "像", "自定义头像", "自定义头像");
        addQuickTile(row2, "泡", "气泡外观", "气泡外观");
        addQuickTile(row2, "增", "聊天增强", "聊天增强");

        // 主页欢迎语
        c.addView(DsUI.sectionHeader(act, "主页欢迎语"));
        LinearLayout greetCard = DsUI.glassCard(act, c);
        // 输入框
        android.widget.EditText et = new android.widget.EditText(act);
        et.setText(DsConfig.greeting(act));
        et.setHint("输入自定义欢迎语（留空恢复默认）");
        et.setSingleLine(true);
        et.setTextSize(14);
        et.setTextColor(DsUI.cText());
        et.setHintTextColor(DsUI.cTextFaint());
        et.setPadding(DsUI.dp(act, 14), DsUI.dp(act, 12), DsUI.dp(act, 14), DsUI.dp(act, 12));
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(DsUI.dark ? 0x14FFFFFF : 0x0A000000);
        etBg.setCornerRadius(DsUI.dp(act, 12));
        et.setBackgroundDrawable(etBg);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etLp.leftMargin = DsUI.dp(act, 12);
        etLp.rightMargin = DsUI.dp(act, 12);
        etLp.topMargin = DsUI.dp(act, 10);
        greetCard.addView(et, etLp);
        // 应用按钮
        final android.widget.EditText etF = et;
        DsUI.button(act, greetCard, "应用欢迎语", DsUI.BRAND, true, new Runnable() {
            public void run() {
                DsConfig.setGreeting(act, etF.getText().toString());
                DsFloat.toast(act, "欢迎语已更新");
            }
        });
        // 欢迎语位置行
        DsUI.separator(act, greetCard);
        DsUI.row(act, greetCard, "🎯", DsUI.ACCENT_PURPLE, "欢迎语位置与显隐", "调整偏移、隐藏欢迎语",
                new Runnable() {
                    public void run() {
                        showConfig("欢迎语设置", new DsFloat.ConfigBuilder() {
                            public void build(LinearLayout content) {
                                DsFloat.buildGreetConfig(act, content, act,
                                        DsUI.cText(), DsUI.cTextSub(), DsUI.cSeparator(),
                                        new Runnable() { public void run() { } });
                            }
                        });
                    }
                });
    }

    private void addQuickTile(LinearLayout row, String icon, String label,
                              final String configTitleName) {
        LinearLayout tile = new LinearLayout(act);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(0, DsUI.dp(act, 16), 0, DsUI.dp(act, 16));
        // v353：纯净卡片
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(DsUI.dark ? 0x14FFFFFF : 0xFFFFFFFF);
        bg.setCornerRadius(DsUI.dp(act, 14));
        bg.setStroke(DsUI.dp(act, 1), DsUI.dark ? 0x14FFFFFF : 0x0A000000);
        tile.setBackgroundDrawable(bg);

        // v353：汉字单字图标（统一品牌色）
        TextView iconTv = new TextView(act);
        iconTv.setText(icon);
        iconTv.setTextSize(16);
        iconTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        iconTv.setGravity(Gravity.CENTER);
        iconTv.setTextColor(DsUI.BRAND);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(DsUI.alpha(DsUI.BRAND, DsUI.dark ? 0x2E : 0x1A));
        ibg.setCornerRadius(DsUI.dp(act, 10));
        iconTv.setBackgroundDrawable(ibg);
        tile.addView(iconTv, new LinearLayout.LayoutParams(DsUI.dp(act, 40), DsUI.dp(act, 40)));

        TextView labelTv = new TextView(act);
        labelTv.setText(label);
        labelTv.setTextSize(12.5f);
        labelTv.setTextColor(DsUI.cText());
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = DsUI.dp(act, 8);
        tile.addView(labelTv, llp);

        tile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openNamedConfig(configTitleName); }
        });
        DsUI.pressFeedback(tile);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = DsUI.dp(act, 4);
        lp.rightMargin = DsUI.dp(act, 4);
        row.addView(tile, lp);
    }

    /** 按名称打开配置页（路由） */
    private void openNamedConfig(String name) {
        final String n = name;
        showConfig(name, new DsFloat.ConfigBuilder() {
            public void build(LinearLayout content) {
                int tc = DsUI.cText(), sc = DsUI.cTextSub(), dc = DsUI.cSeparator();
                if ("背景壁纸".equals(n)) {
                    DsFloat.buildBgConfig(act, content, act, tc, sc, dc, noop());
                } else if ("自定义头像".equals(n)) {
                    DsFloat.buildAvatarConfig(act, content, act, tc, sc, dc, noop());
                } else if ("气泡外观".equals(n)) {
                    DsFloat.buildBubbleConfig(act, content, act, tc, sc, dc, noop());
                } else if ("聊天增强".equals(n)) {
                    DsFloat.buildChatEnhanceConfig(act, content, act, tc, sc, dc, noop());
                } else if ("AI 一问多答".equals(n)) {
                    DsFloat.buildMultiReplyConfig(act, content, act, tc, sc, dc, noop());
                } else if ("系统提示词".equals(n)) {
                    DsFloat.buildSystemPromptConfig(act, content, act, tc, sc, dc, noop());
                } else if ("输入框美化".equals(n)) {
                    DsFloat.buildHintConfig(act, content, act, tc, sc, dc, noop());
                } else if ("灵动岛设置".equals(n)) {
                    DsFloat.buildBallConfig(act, content, act, tc, sc, dc, noop());
                } else if ("面板设置".equals(n)) {
                    DsFloat.buildPanelConfig(act, content, act, tc, sc, dc, noop());
                } else if ("反代设置".equals(n)) {
                    DsFloat.buildProxyConfig(act, content, act, tc, sc, dc, noop());
                } else if ("缓存清理".equals(n)) {
                    DsFloat.buildCleanConfig(act, content, act, tc, sc, dc, noop());
                } else if ("聊天备份".equals(n)) {
                    DsFloat.buildBackupConfig(act, content, act, tc, sc, dc, noop());
                } else if ("设备与进程".equals(n)) {
                    DsFloat.buildDeviceConfig(act, content, act, tc, sc, dc, noop());
                }
            }
        });
    }

    private Runnable noop() {
        return new Runnable() { public void run() { } };
    }

    // ═══════════════════════ 界面页 ═══════════════════════

    private void buildFacePage() {
        LinearLayout c = pageContent[1];
        c.addView(DsUI.sectionHeader(act, "外观"));
        LinearLayout card1 = DsUI.glassCard(act, c);
        DsUI.row(act, card1, "壁", DsUI.BRAND, "背景壁纸", "壁纸图、模糊、色调、遮罩", nav("背景壁纸"));
        DsUI.separator(act, card1);
        DsUI.row(act, card1, "框", DsUI.BRAND, "输入框美化", "提示文字、输入文字、光标颜色", nav("输入框美化"));
        DsUI.separator(act, card1);
        DsUI.row(act, card1, "岛", DsUI.BRAND, "灵动岛设置", "位置、透明度", nav("灵动岛设置"));
        DsUI.separator(act, card1);
        DsUI.row(act, card1, "板", DsUI.BRAND, "面板设置", "主题、圆角", nav("面板设置"));

        c.addView(DsUI.sectionHeader(act, "功能"));
        LinearLayout card2 = DsUI.glassCard(act, c);
        DsUI.row(act, card2, "词", DsUI.BRAND, "系统提示词", "注入角色/规则，让 AI 按设定回复", nav("系统提示词"));
        DsUI.separator(act, card2);
        DsUI.row(act, card2, "像", DsUI.BRAND, "自定义头像", "替换主页头像图片", nav("自定义头像"));
    }

    private Runnable nav(final String name) {
        return new Runnable() { public void run() { openNamedConfig(name); } };
    }

    // ═══════════════════════ 聊天页 ═══════════════════════

    private void buildChatPage() {
        LinearLayout c = pageContent[2];
        c.addView(DsUI.sectionHeader(act, "聊天外观"));
        LinearLayout card1 = DsUI.glassCard(act, c);
        DsUI.row(act, card1, "泡", DsUI.BRAND, "气泡外观", "样式、圆角、透明度、字体", nav("气泡外观"));
        c.addView(DsUI.sectionHeader(act, "聊天增强"));
        LinearLayout card2 = DsUI.glassCard(act, c);
        DsUI.row(act, card2, "答", DsUI.BRAND, "AI 一问多答", "自动多次回答（2-3 次）", nav("AI 一问多答"));
        DsUI.separator(act, card2);
        DsUI.row(act, card2, "增", DsUI.BRAND, "聊天增强", "去审查、多选删除、备份等", nav("聊天增强"));
    }

    // ═══════════════════════ 更多页 ═══════════════════════

    private void buildMorePage() {
        LinearLayout c = pageContent[3];
        c.addView(DsUI.sectionHeader(act, "工具"));
        LinearLayout card1 = DsUI.glassCard(act, c);
        DsUI.row(act, card1, "网", DsUI.BRAND, "反代设置", "自定义 API 域名（加速/绕限流）", nav("反代设置"));
        DsUI.separator(act, card1);
        DsUI.row(act, card1, "清", DsUI.BRAND, "缓存清理", "自动清理策略、立即清理", nav("缓存清理"));
        DsUI.separator(act, card1);
        DsUI.row(act, card1, "备", DsUI.BRAND, "聊天备份", "数据库备份、恢复说明", nav("聊天备份"));
        DsUI.separator(act, card1);
        DsUI.row(act, card1, "机", DsUI.BRAND, "设备与进程", "设备信息、进程状态", nav("设备与进程"));

        c.addView(DsUI.sectionHeader(act, "设置"));
        LinearLayout card2 = DsUI.glassCard(act, c);
        final boolean lic = DsLicense.isActivated(act);
        DsUI.row(act, card2, "钥", DsUI.BRAND, "卡密激活", lic ? "已激活（永久）" : "未激活，点击输入卡密", new Runnable() {
                    public void run() {
                        DsFloat.showLicenseDialog(act, new Runnable() { public void run() { } });
                    }
                });
        DsUI.separator(act, card2);
        DsUI.row(act, card2, "复", DsUI.ACCENT_RED, "恢复默认设置", "重置所有配置项", new Runnable() {
            public void run() {
                resetDefaults();
                DsFloat.toast(act, "已恢复默认设置");
            }
        });

        // 关于
        TextView about = new TextView(act);
        about.setText("ds美化 · DeepSeek 美化模块\n版本 v353 · 2026-09-18");
        about.setTextSize(12);
        about.setTextColor(DsUI.cTextFaint());
        about.setGravity(Gravity.CENTER);
        about.setLineSpacing(DsUI.dp(act, 2), 1f);
        about.setPadding(0, DsUI.dp(act, 20), 0, DsUI.dp(act, 10));
        c.addView(about);
    }

    /** 恢复默认设置 */
    private void resetDefaults() {
        try {
            DsConfig.setBgOn(act, false);
            DsConfig.setBgAlpha(act, 40);
            DsConfig.setDarkFilter(act, 0);
            DsConfig.setBgMode(act, 0);
            DsConfig.setBgBlur(act, 0);
            DsConfig.setTintMode(act, 0);
            DsConfig.setHintColor(act, 0);
            DsConfig.setBallPos(act, 0);
            DsConfig.setBallAlpha(act, 90);
            DsConfig.setPanelRadius(act, 24);
            DsConfig.setChatBgOn(act, false);
            DsConfig.setBubbleRadius(act, 18);
            DsConfig.setBubbleAlpha(act, 100);
            DsConfig.setAutoClean(act, 0);
            DsConfig.setBubbleOn(act, false);
            DsConfig.setHideBtnOn(act, false);
            DsConfig.setChatBtnOn(act, false);
        } catch (Throwable ignored) {}
    }
}