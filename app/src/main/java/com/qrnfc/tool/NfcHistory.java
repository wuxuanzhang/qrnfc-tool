package com.qrnfc.tool;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * NFC 读卡历史记录，SharedPreferences 持久化，最多保留 50 条。
 */
public class NfcHistory {

    private static final String PREF = "nfc_history";
    private static final String KEY = "items";
    private static final int MAX = 50;

    public static class Item {
        public final String hex;
        public final String dec;
        public final String tech;
        public final long time;
        public Item(String hex, String dec, String tech, long time) {
            this.hex = hex;
            this.dec = dec;
            this.tech = tech;
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
                list.add(new Item(o.optString("hex", ""), o.optString("dec", ""),
                        o.optString("tech", ""), o.optLong("t", 0L)));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public static void add(Context context, String hex, String dec, String tech) {
        if (hex == null || hex.isEmpty()) return;
        List<Item> list = load(context);
        // 去重：相同 UID 移到最前
        list.removeIf(it -> it.hex.equals(hex));
        list.add(0, new Item(hex, dec, tech, System.currentTimeMillis()));
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
                o.put("hex", it.hex);
                o.put("dec", it.dec);
                o.put("tech", it.tech);
                o.put("t", it.time);
                arr.put(o);
            } catch (JSONException ignored) {
            }
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }
}
