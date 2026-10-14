package com.qrnfc.tool;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫码历史记录，使用 SharedPreferences 持久化，最多保留 50 条。
 */
public class ScanHistory {

    private static final String PREF = "scan_history";
    private static final String KEY = "items";
    private static final int MAX = 50;

    public static class Item {
        public final String content;
        public final long time;
        public Item(String content, long time) {
            this.content = content;
            this.time = time;
        }
    }

    public static List<Item> load(Context context) {
        List<Item> list = new ArrayList<>();
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY, "");
        if (raw.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Item(o.optString("c", ""), o.optLong("t", 0L)));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public static void add(Context context, String content) {
        if (content == null || content.isEmpty()) return;
        List<Item> list = load(context);
        // 去重：相同内容移到最前
        list.removeIf(it -> it.content.equals(content));
        list.add(0, new Item(content, System.currentTimeMillis()));
        while (list.size() > MAX) list.remove(list.size() - 1);
        save(context, list);
    }

    public static void clear(Context context) {
        save(context, new ArrayList<>());
    }

    /** 用给定列表整体替换历史（保持顺序），用于删除单条等场景。 */
    public static void replace(Context context, List<Item> list) {
        save(context, list);
    }

    private static void save(Context context, List<Item> list) {
        JSONArray arr = new JSONArray();
        for (Item it : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("c", it.content);
                o.put("t", it.time);
                arr.put(o);
            } catch (JSONException ignored) {
            }
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }
}
