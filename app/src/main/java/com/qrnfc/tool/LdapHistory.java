package com.qrnfc.tool;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * LDAP 查询历史记录，SharedPreferences 持久化，最多保留 50 条。
 */
public class LdapHistory {

    private static final String PREF = "ldap_history";
    private static final String KEY = "items";
    private static final int MAX = 50;

    public static class Item {
        public final String type; // "计算机" / "用户"
        public final String name;
        public final long time;
        public Item(String type, String name, long time) {
            this.type = type;
            this.name = name;
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
                list.add(new Item(o.optString("type", ""), o.optString("name", ""),
                        o.optLong("t", 0L)));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public static void add(Context context, String type, String name) {
        if (name == null || name.trim().isEmpty()) return;
        final String n = name.trim();
        List<Item> list = load(context);
        // 去重：相同类型+名称移到最前
        list.removeIf(it -> it.type.equals(type) && it.name.equalsIgnoreCase(n));
        list.add(0, new Item(type, n, System.currentTimeMillis()));
        while (list.size() > MAX) list.remove(list.size() - 1);
        save(context, list);
    }

    public static void clear(Context context) {
        save(context, new ArrayList<>());
    }

    public static void replace(Context context, List<Item> list) {
        save(context, list);
    }

    private static void save(Context context, List<Item> list) {
        JSONArray arr = new JSONArray();
        for (Item it : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("type", it.type);
                o.put("name", it.name);
                o.put("t", it.time);
                arr.put(o);
            } catch (JSONException ignored) {
            }
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }
}
