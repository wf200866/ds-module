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
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * v360：独立设置页 —— 完全照搬 DEKK 风格（配色/圆角/间距 1:1）
 *
 * DEKK 设计规范（逆向自 DekkSettingsPage）：
 *   背景：浅 #F7F7F7 / 深 #191919
 *   主文字：浅 #1A1A1A / 深 #E8E8E8
 *   次文字：浅 #757575 / 深 #8F8F8F
 *   卡片：浅白 #FFFFFF / 深 #292929，圆角 22dp
 *   分组标题：12sp 次色，padding 20/20/20/6
 *   列表项：16sp 主色，padding 20/18/20/18
 *   分隔线：高 0.5dp
 *   AppBar：高 64dp，标题 20sp 居中，返回圆钮 56dp
 *
 * 返回键修复（照 DEKK 机制）：
 *   1. 挂载到 android.R.id.content（不是 DecorView）
 *   2. PageView.dispatchKeyEvent 拦截 + requestFocus
 *   3. MainActivity.onBackPressed hook 兜底
 */
public class DsSettingsPage {

    private static final int ID_ROOT = 0xDE0002;

    private static volatile DsSettingsPage sInstance;

    private final Activity act;
    private final DsPageView root;
    private final LinearLayout contentBox;
    private final FrameLayout subLayer;
    private final TextView subTitle;
    private final LinearLayout subContent;
    private final boolean dark;

    // ── DEKK 配色（v362：强制浅色 —— 黑字/灰字，保证清晰） ──
    private int cBg()     { return 0xFFF7F7F7; }
    private int cCard()   { return 0xFFFFFFFF; }
    private int cText()   { return 0xFF1A1A1A; }
    private int cSub()    { return 0xFF1A1A1A; }
    private int cDiv()    { return 0x14000000; }

    private DsSettingsPage(Activity act) {
        this.act = act;
        this.dark = false;          // v362：强制浅色
        DsUI.dark = false;          // 子组件统一用浅色（黑字/灰字）
        DsFloat.setPanelDark(false);  // v452：同步 DsFloat 面板状态为浅色，避免子页组件按深色渲染变灰

        root = new DsPageView(act);
        root.setId(ID_ROOT);
        root.setBackgroundColor(cBg());
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnBack(new Runnable() {
            public void run() {
                if (subLayer.getVisibility() == View.VISIBLE) {
                    hideSub();
                } else {
                    close();
                }
            }
        });

        LinearLayout main = new LinearLayout(act);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        main.addView(buildAppBar());

        ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        contentBox = new LinearLayout(act);
        contentBox.setOrientation(LinearLayout.VERTICAL);
        contentBox.setPadding(dp(20), dp(4), dp(20), dp(32));
        sv.addView(contentBox);
        main.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 二级页
        subLayer = new FrameLayout(act);
        subLayer.setBackgroundColor(cBg());
        subLayer.setVisibility(View.GONE);
        subLayer.setClickable(true);

        LinearLayout subMain = new LinearLayout(act);
        subMain.setOrientation(LinearLayout.VERTICAL);
        subLayer.addView(subMain, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 二级顶部栏（返回 + 标题）
        LinearLayout subBar = new LinearLayout(act);
        subBar.setOrientation(LinearLayout.HORIZONTAL);
        subBar.setGravity(Gravity.CENTER_VERTICAL);
        subBar.setPadding(dp(12), dp(14), dp(16), dp(10));

        TextView back = new TextView(act);
        back.setText("‹");
        back.setTextSize(28);
        back.setTextColor(cText());
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(8), 0, dp(8), dp(4));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { hideSub(); }
        });
        subBar.addView(back);

        subTitle = new TextView(act);
        subTitle.setTextSize(20);
        subTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        subTitle.setTextColor(cText());
        subBar.addView(subTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        subMain.addView(subBar);

        ScrollView subSv = new ScrollView(act);
        subSv.setFillViewport(true);
        subSv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        subContent = new LinearLayout(act);
        subContent.setOrientation(LinearLayout.VERTICAL);
        subContent.setPadding(dp(20), dp(4), dp(20), dp(32));
        subSv.addView(subContent);
        subMain.addView(subSv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(subLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        buildContent();
    }

    // ═══════════ 打开/关闭 ═══════════

    /** 打开设置页（照 DEKK：挂到 android.R.id.content） */
    public static void show(final Activity act) {
        try {
            View old = act.findViewById(ID_ROOT);
            if (old != null && old.getParent() instanceof ViewGroup) {
                ((ViewGroup) old.getParent()).removeView(old);
            }
            // 照 DEKK：挂到 android.R.id.content
            View content = act.findViewById(android.R.id.content);
            ViewGroup host = null;
            if (content instanceof ViewGroup) {
                host = (ViewGroup) content;
            } else {
                View decor = act.getWindow().getDecorView();
                if (decor instanceof ViewGroup) host = (ViewGroup) decor;
            }
            if (host == null) return;

            DsSettingsPage page = new DsSettingsPage(act);
            sInstance = page;
            host.addView(page.root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            page.root.setVisibility(View.VISIBLE);
            page.root.requestFocus();  // 关键：让页面能接收按键

            page.root.setAlpha(0f);
            page.root.setTranslationX(DsUI.dp(act, 40));
            page.root.animate().alpha(1f).translationX(0).setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        } catch (Throwable t) {
            DsFloat.toast(act, "打开设置页失败: " + t);
        }
    }

    private void close() {
        try {
            root.animate().alpha(0f).translationX(dp(40)).setDuration(200)
                    .withEndAction(new Runnable() {
                        public void run() {
                            if (root.getParent() instanceof ViewGroup) {
                                ((ViewGroup) root.getParent()).removeView(root);
                            }
                        }
                    }).start();
            sInstance = null;
        } catch (Throwable ignored) {}
    }

    /** 返回键处理（供 onBackPressed hook 调用） */
    public static boolean handleBack(final Activity act) {
        try {
            if (sInstance == null || sInstance.act != act) return false;
            final DsSettingsPage page = sInstance;
            page.root.post(new Runnable() {
                public void run() {
                    try {
                        if (page.subLayer.getVisibility() == View.VISIBLE) {
                            page.hideSub();
                        } else {
                            page.close();
                        }
                    } catch (Throwable ignored) {}
                }
            });
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ═══════════ AppBar（照 DEKK：64dp，标题居中，返回钮） ═══════════

    private View buildAppBar() {
        FrameLayout bar = new FrameLayout(act);
        bar.setBackgroundColor(cBg());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        bar.setLayoutParams(blp);

        // 居中标题（20sp）
        TextView title = new TextView(act);
        title.setText("ds美化");
        title.setTextSize(20);
        title.setTextColor(cText());
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 返回圆钮（56dp 圆形）
        TextView back = new TextView(act);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(cText());
        back.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(dark ? 0x14FFFFFF : 0x0D000000);
        back.setBackgroundDrawable(circle);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { close(); }
        });
        DsUI.pressFeedback(back);
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(56), dp(56));
        backLp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        backLp.leftMargin = dp(8);
        bar.addView(back, backLp);
        return bar;
    }

    // ═══════════ 内容（照 DEKK 结构） ═══════════

    private void buildContent() {
        // ── 外观美化 ──
        contentBox.addView(sectionTitle("外观美化"));
        LinearLayout card1 = card();
        item(card1, "背景壁纸", "壁纸图、模糊、色调、遮罩", nav("背景壁纸"));
        divider(card1);
        item(card1, "自定义头像", "替换主页头像图片", nav("自定义头像"));
        divider(card1);
        item(card1, "气泡外观", "样式、圆角、透明度、字体", nav("气泡外观"));
        divider(card1);
        item(card1, "输入框美化", "提示文字、输入文字、光标颜色", nav("输入框美化"));
        divider(card1);
        item(card1, "灵动岛设置", "位置、透明度", nav("灵动岛设置"));
        contentBox.addView(card1);

        // ── 聊天增强 ──
        contentBox.addView(sectionTitle("聊天增强"));
        LinearLayout card2 = card();
        item(card2, "系统提示词", "注入角色/规则，让 AI 按设定回复", nav("系统提示词"));
        divider(card2);
        item(card2, "聊天增强", "去审查、多选删除、备份等", nav("聊天增强"));
        contentBox.addView(card2);

        // ── AI 一问多答（v363） ──
        contentBox.addView(sectionTitle("AI 一问多答"));
        LinearLayout cardMr = card();
        item(cardMr, "AI 一问多答", "自动多次回答（2-3 次），可设置次数", nav("AI 一问多答"));
        contentBox.addView(cardMr);

        // ── 工具 ──
        contentBox.addView(sectionTitle("工具"));
        LinearLayout card3 = card();
        item(card3, "反代设置", "自定义 API 域名（加速/绕限流）", nav("反代设置"));
        divider(card3);
        item(card3, "缓存清理", "自动清理策略、立即清理", nav("缓存清理"));
        divider(card3);
        item(card3, "聊天备份", "数据库备份、恢复说明", nav("聊天备份"));
        divider(card3);
        item(card3, "设备与进程", "设备信息、进程状态", nav("设备与进程"));
        contentBox.addView(card3);

        // ── 设置 ──
        contentBox.addView(sectionTitle("设置"));
        LinearLayout card4 = card();
        item(card4, "面板设置", "主题、圆角", nav("面板设置"));
        divider(card4);
        final boolean lic = DsLicense.isActivated(act);
        item(card4, "卡密激活", lic ? "已激活（永久）" : "未激活，点击输入卡密", new Runnable() {
            public void run() {
                DsFloat.showLicenseDialog(act, new Runnable() { public void run() { } });
            }
        });
        contentBox.addView(card4);

        // ── 关于 / 开源 ──
        contentBox.addView(sectionTitle("关于"));
        LinearLayout cardAbout = card();
        item(cardAbout, "开源仓库", "github.com/wf200866/ds-module", new Runnable() {
            public void run() {
                try {
                    android.content.Intent it = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/wf200866/ds-module"));
                    act.startActivity(it);
                } catch (Throwable t) {
                    DsFloat.toast(act, "无法打开浏览器，仓库地址：\ngithub.com/wf200866/ds-module");
                }
            }
        });
        divider(cardAbout);
        item(cardAbout, "开源许可", "GNU GPL-3.0（基于 Deekseep）", new Runnable() {
            public void run() {
                DsFloat.toast(act, "本模块以 GPL-3.0 开源\n参考自 github.com/lllucccian/Deekseep");
            }
        });
        contentBox.addView(cardAbout);

        // 关于
        TextView about = new TextView(act);
        about.setText("ds模块 · DeepSeek 美化模块\n版本 v490 · 2026-09-19\nGPL-3.0 · github.com/wf200866/ds-module");
        about.setTextSize(12);
        about.setTextColor(cSub());
        about.setGravity(Gravity.CENTER);
        about.setLineSpacing(dp(2), 1f);
        about.setPadding(0, dp(24), 0, dp(10));
        contentBox.addView(about);
    }

    // ═══════════ 组件（DEKK 风格） ═══════════

    /** 分组标题：12sp 次色，padding 20/20/20/6 */
    private TextView sectionTitle(String text) {
        TextView tv = new TextView(act);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(cSub());
        tv.setPadding(dp(20), dp(20), dp(20), dp(6));
        return tv;
    }

    /** 卡片：圆角 22dp，白/深灰 */
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cCard());
        bg.setCornerRadius(dp(22));
        c.setBackgroundDrawable(bg);
        c.setClipToPadding(false);
        return c;
    }

    /** 列表项：16sp 主色，padding 20/18/20/18，右箭头 */
    private void item(LinearLayout card, String title, String sub, final Runnable onClick) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(18), dp(20), dp(18));

        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView titleTv = new TextView(act);
        titleTv.setText(title);
        titleTv.setTextSize(16);
        titleTv.setTextColor(cText());
        texts.addView(titleTv);
        if (sub != null && sub.length() > 0) {
            TextView subTv = new TextView(act);
            subTv.setText(sub);
            subTv.setTextSize(12);
            subTv.setTextColor(cSub());
            subTv.setPadding(0, dp(4), 0, 0);
            texts.addView(subTv);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(act);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(cSub());
        row.addView(arrow);

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onClick.run(); }
        });
        DsUI.pressFeedback(row);
        card.addView(row);
    }

    /** 分隔线：高 0.5dp，缩进 20dp */
    private void divider(LinearLayout card) {
        View line = new View(act);
        line.setBackgroundColor(cDiv());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
        lp.leftMargin = dp(20);
        lp.rightMargin = dp(20);
        card.addView(line, lp);
    }

    // ═══════════ 二级页 ═══════════

    private Runnable nav(final String name) {
        return new Runnable() {
            public void run() { showSub(name); }
        };
    }

    private void showSub(String name) {
        try {
            subTitle.setText(name);
            subContent.removeAllViews();
            int tc = cText(), sc = cSub(), dc = cDiv();
            // v464：refresh 轻量空操作 —— 页面控件各自更新自身状态，无需重建整个子页
            // （互斥联动用 Switch 引用直接改 checked，不依赖 refresh）
            final Runnable refresh = new Runnable() { public void run() { } };
            if ("背景壁纸".equals(name)) {
                DsFloat.buildBgConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("自定义头像".equals(name)) {
                DsFloat.buildAvatarConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("气泡外观".equals(name)) {
                DsFloat.buildBubbleConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("输入框美化".equals(name)) {
                DsFloat.buildHintConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("灵动岛设置".equals(name)) {
                DsFloat.buildBallConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("系统提示词".equals(name)) {
                DsFloat.buildSystemPromptConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("AI 一问多答".equals(name)) {
                DsFloat.buildMultiReplyConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("聊天增强".equals(name)) {
                DsFloat.buildChatEnhanceConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("反代设置".equals(name)) {
                DsFloat.buildProxyConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("缓存清理".equals(name)) {
                DsFloat.buildCleanConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("聊天备份".equals(name)) {
                DsFloat.buildBackupConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("设备与进程".equals(name)) {
                DsFloat.buildDeviceConfig(act, subContent, act, tc, sc, dc, refresh);
            } else if ("面板设置".equals(name)) {
                DsFloat.buildPanelConfig(act, subContent, act, tc, sc, dc, refresh);
            }

            subLayer.setVisibility(View.VISIBLE);
            subLayer.setAlpha(0f);
            subLayer.setTranslationX(dp(40));
            subLayer.animate().alpha(1f).translationX(0).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
            subLayer.requestFocus();
        } catch (Throwable ignored) {}
    }

    private void hideSub() {
        subLayer.animate().alpha(0f).translationX(dp(40)).setDuration(180)
                .withEndAction(new Runnable() {
                    public void run() { subLayer.setVisibility(View.GONE); }
                }).start();
        root.requestFocus();
    }

    private int dp(float v) {
        return DsUI.dp(act, v);
    }
}