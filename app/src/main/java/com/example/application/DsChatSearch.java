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
import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * v315：聊天全局搜索 —— 遍历所有 DeepSeek 会话数据库，搜索消息内容。
 * 复用 DsChatEditor 的数据库遍历逻辑（deepseek_chat_{uuid}.db）。
 * 纯数据库操作，不依赖版本类名，安全。
 */
public class DsChatSearch {

    private static final String DB_DIR = DsConfig.dataPath("databases");

    /** 搜索结果条目 */
    static class Hit {
        String sessionTitle;
        String role;
        String content;
        String dbPath;
        String sid;
    }

    /** 打开搜索界面 */
    public static void show(final Activity act) {
        try {
            final EditText et = new EditText(act);
            et.setSingleLine(true);
            et.setHint("输入关键词搜索聊天记录");
            et.setInputType(InputType.TYPE_CLASS_TEXT);
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(40, 30, 40, 10);
            box.addView(et, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            new AlertDialog.Builder(act)
                    .setTitle("🔍 聊天搜索")
                    .setView(box)
                    .setPositiveButton("搜索", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            String kw = et.getText().toString().trim();
                            if (kw.length() == 0) { toast(act, "请输入关键词"); return; }
                            doSearch(act, kw);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Throwable t) {
            toast(act, "打开搜索失败: " + t);
        }
    }

    /** 执行搜索 */
    private static void doSearch(final Activity act, final String keyword) {
        try {
            final List<Hit> hits = searchAll(keyword);
            if (hits.isEmpty()) {
                toast(act, "未找到包含「" + keyword + "」的消息");
                return;
            }
            // 显示结果列表
            final String[] items = new String[hits.size()];
            for (int i = 0; i < hits.size(); i++) {
                Hit h = hits.get(i);
                String role = "assistant".equals(h.role) ? "🤖" : "👤";
                String title = h.sessionTitle != null && h.sessionTitle.length() > 0 ? h.sessionTitle : h.sid;
                String content = h.content != null ? h.content : "";
                if (content.length() > 26) content = content.substring(0, 26) + "...";
                items[i] = "[" + title + "] " + role + " " + content;
            }
            new AlertDialog.Builder(act)
                    .setTitle("🔍 找到 " + hits.size() + " 条")
                    .setItems(items, new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            // 点击查看完整内容
                            showHit(act, hits.get(w));
                        }
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Throwable t) {
            toast(act, "搜索失败: " + t);
        }
    }

    /** 遍历所有会话库搜索关键词 */
    private static List<Hit> searchAll(String keyword) {
        List<Hit> out = new ArrayList<>();
        File dir = new File(DB_DIR);
        File[] dbs = dir.listFiles();
        if (dbs == null) return out;
        String kw = keyword.toLowerCase();
        for (File f : dbs) {
            String name = f.getName();
            if (!name.startsWith("deepseek_chat_") || !name.endsWith(".db")) continue;
            if ("deepseek_chat.db".equals(name)) continue;
            SQLiteDatabase db = null;
            try {
                db = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                // 会话标题
                String title = null;
                try {
                    Cursor tc = db.rawQuery("SELECT title FROM chat_session_list LIMIT 1", null);
                    if (tc.moveToNext()) title = tc.getString(0);
                    tc.close();
                } catch (Throwable ignored) {}
                // 遍历所有消息表
                Cursor t2 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'chat_session_messages_%'", null);
                List<String> tables = new ArrayList<>();
                while (t2.moveToNext()) tables.add(t2.getString(0));
                t2.close();
                for (String table : tables) {
                    try {
                        Cursor c = db.rawQuery("SELECT role, fragments FROM '" + table + "'", null);
                        while (c.moveToNext()) {
                            String role = c.getString(0);
                            String frags = c.getString(1);
                            String content = extractText(frags);
                            if (content != null && content.toLowerCase().contains(kw)) {
                                Hit h = new Hit();
                                h.role = role;
                                h.content = content;
                                h.sessionTitle = title;
                                h.dbPath = f.getPath();
                                h.sid = table.replace("chat_session_messages_", "");
                                out.add(h);
                            }
                        }
                        c.close();
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {
            } finally {
                if (db != null) try { db.close(); } catch (Throwable ignored) {}
            }
        }
        return out;
    }

    /** 从 fragments JSON 提取纯文本 */
    private static String extractText(String fragments) {
        try {
            if (fragments == null) return null;
            JSONArray arr = new JSONArray(fragments);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String type = o.optString("type");
                String content = o.optString("content");
                if ("THINK".equals(type) || "RESPONSE".equals(type) || "REQUEST".equals(type)) {
                    if (content != null) sb.append(content);
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return fragments;
        }
    }

    /** 查看单条完整内容 */
    private static void showHit(final Activity act, final Hit h) {
        try {
            String role = "assistant".equals(h.role) ? "🤖 AI" : "👤 用户";
            String title = h.sessionTitle != null && h.sessionTitle.length() > 0 ? h.sessionTitle : h.sid;
            ScrollView sv = new ScrollView(act);
            TextView tv = new TextView(act);
            tv.setPadding(40, 30, 40, 30);
            tv.setTextSize(14);
            tv.setText("【" + title + "】\n" + role + "：\n\n" + h.content);
            sv.addView(tv);
            new AlertDialog.Builder(act)
                    .setTitle("消息详情")
                    .setView(sv)
                    .setPositiveButton("关闭", null)
                    .show();
        } catch (Throwable t) {
            toast(act, "查看失败: " + t);
        }
    }

    private static void toast(Context c, String s) {
        try { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
    }
}