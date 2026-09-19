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
import android.view.Gravity;
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
 * v306：聊天消息编辑器（精简版，移植自 Deekseep ChatEditorUi 核心）。
 * 直接操作 DeepSeek SQLite 数据库：选会话 → 列消息 → 编辑文本 → 保存回 DB。
 * 不依赖版本类名（纯数据库操作），安全。
 */
public class DsChatEditor {

    private static final String DB_DIR = DsConfig.dataPath("databases");

    /** 消息结构 */
    static class Msg {
        long id;
        String role;
        String content;
        String rawFragments;
    }

    /** 打开编辑器 */
    public static void show(final Activity act) {
        try {
            List<String[]> sessions = listSessions();
            if (sessions.isEmpty()) {
                toast(act, "未找到聊天会话");
                return;
            }
            // 选会话
            final String[] names = new String[sessions.size()];
            final String[] sids = new String[sessions.size()];
            final String[] dbPaths = new String[sessions.size()];
            for (int i = 0; i < sessions.size(); i++) {
                sids[i] = sessions.get(i)[0];
                dbPaths[i] = sessions.get(i)[2];
                String title = sessions.get(i)[1];
                names[i] = title != null && title.length() > 0 ? title : sids[i];
            }
            new AlertDialog.Builder(act)
                    .setTitle("选择会话")
                    .setItems(names, new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            showMessages(act, dbPaths[w], sids[w]);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Throwable t) {
            toast(act, "打开编辑器失败: " + t);
        }
    }

    /** 列出所有会话（遍历 deepseek_chat_*.db，找含 chat_session_list 的库） */
    private static List<String[]> listSessions() {
        List<String[]> out = new ArrayList<>();
        File dir = new File(DB_DIR);
        File[] dbs = dir.listFiles();
        if (dbs == null) return out;
        for (File f : dbs) {
            String name = f.getName();
            if (!name.startsWith("deepseek_chat_") || !name.endsWith(".db")) continue;
            if ("deepseek_chat.db".equals(name)) continue;  // 跳过空元数据库
            SQLiteDatabase db = null;
            try {
            db = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                // 检查是否有会话表
                Cursor tc = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='chat_session_list'", null);
                boolean hasSession = tc.moveToNext();
                tc.close();
                if (!hasSession) continue;
                Cursor c = db.rawQuery("SELECT id,title FROM chat_session_list ORDER BY inserted_at DESC", null);
                while (c.moveToNext()) {
                    String id = c.getString(0);
                    String title = c.getString(1);
                    if (id == null || id.length() == 0) continue;
                    out.add(new String[]{id, title, f.getPath()});
                }
                c.close();
                android.util.Log.i("ds美化", "v337 listSessions: " + f.getName() + " -> " + out.size() + " sessions");
            } catch (Throwable ignored) {
                android.util.Log.i("ds美化", "v337 listSessions open EX: " + f.getName() + " " + ignored);
            } finally {
                if (db != null) try { db.close(); } catch (Throwable ignored) {}
            }
        }
        return out;
    }

    /** 列出会话消息 */
    private static void showMessages(final Activity act, final String dbPath, final String sid) {
        final List<Msg> msgs = listMessages(dbPath, sid);
        if (msgs.isEmpty()) {
            toast(act, "会话无消息");
            return;
        }
        final String[] items = new String[msgs.size()];
        for (int i = 0; i < msgs.size(); i++) {
            Msg m = msgs.get(i);
            String role = "assistant".equals(m.role) ? "🤖" : "👤";
            String content = m.content != null ? m.content : "";
            if (content.length() > 30) content = content.substring(0, 30) + "...";
            items[i] = role + " " + content;
        }
        new AlertDialog.Builder(act)
                .setTitle("选择消息（共" + msgs.size() + "条）")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        editMessage(act, dbPath, sid, msgs.get(w));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 列出消息（从 fragments JSON 提取文本） */
    private static List<Msg> listMessages(String dbPath, String sid) {
        List<Msg> out = new ArrayList<>();
        SQLiteDatabase db = null;
        try {
            File f = new File(dbPath);
            if (!f.exists()) return out;
            db = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            String table = "chat_session_messages_" + sid;
            Cursor c = db.rawQuery("SELECT message_id,role,fragments FROM '" + table + "' ORDER BY message_id", null);
            while (c.moveToNext()) {
                Msg m = new Msg();
                m.id = c.getLong(0);
                m.role = c.getString(1);
                m.rawFragments = c.getString(2);
                m.content = extractText(m.rawFragments);
                out.add(m);
            }
            c.close();
        } catch (Throwable ignored) {
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
        return out;
    }

    /** 从 fragments JSON 提取文本内容 */
    private static String extractText(String fragments) {
        if (fragments == null || fragments.length() == 0) return "";
        try {
            JSONArray a = new JSONArray(fragments);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String type = o.optString("type");
                String content = o.optString("content");
                if ("THINK".equals(type)) continue;  // 跳过思考
                if (content != null && content.length() > 0) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(content);
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 编辑消息（改文本片段 content 后写回） */
    private static void editMessage(final Activity act, final String dbPath, final String sid, final Msg msg) {
        final EditText et = new EditText(act);
        et.setMinLines(4);
        et.setText(msg.content != null ? msg.content : "");
        et.setGravity(Gravity.START | Gravity.TOP);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20, 10, 20, 0);
        box.addView(et);
        new AlertDialog.Builder(act)
                .setTitle("编辑消息")
                .setView(box)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        String newContent = et.getText().toString();
                        if (saveMessage(dbPath, sid, msg, newContent)) {
                            toast(act, "已保存（重新进入会话生效）");
                        } else {
                            toast(act, "保存失败");
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 保存：更新 fragments 中第一个非 THINK 片段的 content */
    private static boolean saveMessage(String dbPath, String sid, Msg msg, String newContent) {
        SQLiteDatabase db = null;
        try {
            File f = new File(dbPath);
            if (!f.exists()) return false;
            db = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
            String table = "chat_session_messages_" + sid;
            // 解析原 fragments，替换文本
            JSONArray a;
            if (msg.rawFragments != null && msg.rawFragments.length() > 0) {
                a = new JSONArray(msg.rawFragments);
            } else {
                a = new JSONArray();
            }
            boolean replaced = false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String type = o.optString("type");
                if (!"THINK".equals(type) && o.has("content")) {
                    o.put("content", newContent);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                // 没有可替换的文本片段，新增一个 RESPONSE 片段
                JSONObject o = new JSONObject();
                o.put("type", "RESPONSE");
                o.put("content", newContent);
                a.put(o);
            }
            db.execSQL("UPDATE '" + table + "' SET fragments=? WHERE message_id=?",
                    new Object[]{a.toString(), msg.id});
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    /** v340：批量删除会话（数据库级多选删除）—— 列出会话勾选，删除会话记录+消息表 */
    public static void deleteSessions(final Activity act) {
        try {
            final List<String[]> sessions = listSessions();
            if (sessions.isEmpty()) {
                toast(act, "没有可删除的会话");
                return;
            }
            final String[] titles = new String[sessions.size()];
            for (int i = 0; i < sessions.size(); i++) {
                String t = sessions.get(i)[1];
                titles[i] = (t == null || t.length() == 0) ? "未命名会话" : t;
            }
            final boolean[] checked = new boolean[sessions.size()];
            new AlertDialog.Builder(act)
                    .setTitle("勾选要删除的会话（不可恢复）")
                    .setMultiChoiceItems(titles, checked, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                        public void onClick(android.content.DialogInterface d, int which, boolean isChecked) {
                            checked[which] = isChecked;
                        }
                    })
                    .setPositiveButton("删除选中", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int which) {
                            int count = 0;
                            for (int i = 0; i < checked.length; i++) {
                                if (checked[i]) {
                                    String[] s = sessions.get(i);
                                    if (deleteSessionDb(s[2], s[0])) count++;
                                }
                            }
                            toast(act, "已删除 " + count + " 个会话");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Throwable t) {
            toast(act, "删除异常: " + t);
        }
    }

    /** v340：从数据库删除单个会话（会话记录 + 消息表） */
    private static boolean deleteSessionDb(String dbPath, String sid) {
        SQLiteDatabase db = null;
        try {
            File f = new File(dbPath);
            if (!f.exists()) return false;
            db = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
            db.beginTransaction();
            try {
                // 保护触发器放行（inserted_at=0 表示本地删除）
                db.execSQL("UPDATE chat_session_list SET inserted_at=0 WHERE id=?", new Object[]{sid});
                db.delete("chat_session_list", "id=?", new String[]{sid});
                db.execSQL("DROP TABLE IF EXISTS \"chat_session_messages_" + sid + "\"");
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    /** v336：按会话选择备份 —— 弹出会话列表，勾选后导出每个会话为 JSON 到 ds_backup/sessions/ */
    public static void backupSessions(final Activity act) {
        try {
            final List<String[]> sessions = listSessions();
            if (sessions.isEmpty()) {
                toast(act, "没有可备份的会话");
                return;
            }
            final String[] titles = new String[sessions.size()];
            for (int i = 0; i < sessions.size(); i++) {
                String t = sessions.get(i)[1];
                titles[i] = (t == null || t.length() == 0) ? "未命名会话" : t;
            }
            final boolean[] checked = new boolean[sessions.size()];
            new AlertDialog.Builder(act)
                    .setTitle("选择要备份的会话")
                    .setMultiChoiceItems(titles, checked, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                        public void onClick(android.content.DialogInterface d, int which, boolean isChecked) {
                            checked[which] = isChecked;
                        }
                    })
                    .setPositiveButton("备份选中", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int which) {
                            int count = 0;
                            for (int i = 0; i < checked.length; i++) {
                                if (checked[i]) {
                                    String[] s = sessions.get(i);
                                    if (exportSession(s[2], s[0], s[1])) count++;
                                }
                            }
                            toast(act, "已备份 " + count + " 个会话到 ds_backup/sessions/");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Throwable t) {
            toast(act, "备份异常: " + t);
        }
    }

    /** v336：导出单个会话为 JSON 到 /data/data/com.deepseek.chat/files/ds_backup/sessions/ */
    private static boolean exportSession(String dbPath, String sid, String title) {
        SQLiteDatabase db = null;
        try {
            File f = new File(dbPath);
            if (!f.exists()) return false;
            db = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("sid", sid);
            root.put("title", title == null ? "" : title);
            root.put("exported_at", System.currentTimeMillis());
            // 会话信息
            Cursor sc = db.rawQuery("SELECT id,title FROM chat_session_list WHERE id=?", new String[]{sid});
            if (sc.moveToFirst()) {
                root.put("id", sc.getString(0));
                if (sc.getString(1) != null) root.put("title_db", sc.getString(1));
            }
            sc.close();
            // 消息列表 —— 兼容两种表名：chat_session_messages_<sid> 或 chat_messages_<sid>
            String table = null;
            for (String cand : new String[]{"chat_session_messages_" + sid, "chat_messages_" + sid}) {
                Cursor tc2 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{cand});
                boolean has = tc2.moveToNext();
                tc2.close();
                if (has) { table = cand; break; }
            }
            if (table == null) {
                android.util.Log.i("ds美化", "v337 exportSession 找不到消息表 sid=" + sid);
                return false;
            }
            Cursor mc = db.rawQuery("SELECT message_id,role,fragments FROM '" + table + "' ORDER BY message_id", null);
            org.json.JSONArray msgs = new org.json.JSONArray();
            while (mc.moveToNext()) {
                org.json.JSONObject m = new org.json.JSONObject();
                m.put("id", mc.getLong(0));
                m.put("role", mc.getString(1));
                m.put("fragments", mc.getString(2));
                msgs.put(m);
            }
            mc.close();
            root.put("messages", msgs);
            // 写文件
            File dir = new File(DsConfig.dataPath("files/ds_backup/sessions"));
            if (!dir.exists()) dir.mkdirs();
            File target = new File(dir, sid + ".json");
            java.io.FileWriter w = new java.io.FileWriter(target, false);
            w.write(root.toString());
            w.close();
            android.util.Log.i("ds美化", "v337 exportSession OK sid=" + sid + " msgs=" + msgs.length() + " -> " + target.getPath());
            return true;
        } catch (Throwable t) {
            android.util.Log.i("ds美化", "v337 exportSession EX sid=" + sid + " " + t);
            return false;
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    private static void toast(Context c, String s) {
        try { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) {}
    }
}