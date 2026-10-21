package com.qrnfc.tool;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * LDAP 查询页：右上角设置图标进入设置页；查询支持前缀模糊；
 * 结果以列表呈现，点击某条再显示详细信息。
 */
public class LdapActivity extends AppCompatActivity {

    private static final String PREF = "ldap_settings";

    private AutoCompleteTextView etName;
    private RadioGroup rgType;
    private TextView tvSummary;
    private ListView listResults;

    private final List<Map<String, List<String>>> items = new ArrayList<>();
    private ResultAdapter adapter;

    private ArrayAdapter<String> suggestionAdapter;
    private final List<String> suggestionList = new ArrayList<>();

    private String lastHost, lastPort, lastBase, lastBindDn, lastPwd;
    private boolean lastTls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ldap);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_ldap);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, LdapSettingsActivity.class));
                return true;
            }
            if (item.getItemId() == R.id.action_history) {
                startActivity(new Intent(this, LdapHistoryActivity.class));
                return true;
            }
            return false;
        });

        etName = findViewById(R.id.etName);
        rgType = findViewById(R.id.rgType);
        tvSummary = findViewById(R.id.tvSummary);
        listResults = findViewById(R.id.listResults);
        Button btnQuery = findViewById(R.id.btnQuery);

        // 输入框历史补全：点开即下拉历史，点选直接填充并查询
        etName.setThreshold(0);
        suggestionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, suggestionList);
        etName.setAdapter(suggestionAdapter);
        etName.setOnItemClickListener((p, v, pos, id) -> {
            String picked = suggestionAdapter.getItem(pos);
            if (picked != null && !picked.isEmpty()) {
                etName.setText(picked);
                new Thread(this::queryNow).start();
            }
        });
        // 获得焦点时主动弹出历史下拉，确保一点输入框就能看到历史
        etName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) etName.showDropDown();
        });

        // 切换查询条件时清空已输入的名称，并刷新历史建议
        rgType.setOnCheckedChangeListener((g, id) -> {
            etName.setText("");
            refreshSuggestions();
        });

        adapter = new ResultAdapter();
        listResults.setAdapter(adapter);

        btnQuery.setOnClickListener(v -> {
            if (etName.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "请输入要查询的名称", Toast.LENGTH_SHORT).show();
                return;
            }
            new Thread(this::queryNow).start();
        });

        listResults.setOnItemClickListener((p, v, pos, id) -> showDetail(items.get(pos)));

        loadFromPrefs();
        refreshSuggestions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFromPrefs(); // 从设置页返回时重新读取
        refreshSuggestions();
    }

    /** 按当前查询类型加载历史名称，作为输入框下拉补全建议。 */
    private void refreshSuggestions() {
        boolean isUser = rgType.getCheckedRadioButtonId() == R.id.rdUser;
        String type = isUser ? "用户" : "计算机";
        suggestionList.clear();
        for (LdapHistory.Item it : LdapHistory.load(this)) {
            if (it.type.equals(type) && !suggestionList.contains(it.name)) {
                suggestionList.add(it.name);
            }
        }
        suggestionAdapter.notifyDataSetChanged();
    }

    private void loadFromPrefs() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        lastHost = sp.getString("host", "");
        lastPort = sp.getString("port", "389");
        lastBase = sp.getString("base", "");
        lastBindDn = sp.getString("bind_dn", "");
        lastPwd = sp.getString("pwd", "");
        lastTls = sp.getBoolean("tls", false);
    }

    /** 执行查询（子线程）。 */
    private void queryNow() {
        if (TextUtils.isEmpty(lastHost) || TextUtils.isEmpty(lastPort)) {
            toast("请先在右上角设置里填写连接信息");
            return;
        }
        String name = etName.getText().toString().trim();
        boolean isUser = rgType.getCheckedRadioButtonId() == R.id.rdUser;
        setSummary("正在查询…");
        LdapClient client = new LdapClient();
        try {
            client.connect(lastHost, Integer.parseInt(lastPort.trim()), lastTls);
            client.bind(lastBindDn, lastPwd);
            byte[] filter = isUser ? buildUserFilter(name) : buildComputerFilter(name);
            List<Map<String, List<String>>> list = client.search(lastBase, filter);
            // 记录查询历史（去重，最多 50 条）
            LdapHistory.add(this, isUser ? "用户" : "计算机", name);
            runOnUiThread(() -> {
                items.clear();
                items.addAll(list);
                adapter.notifyDataSetChanged();
                refreshSuggestions();
                setSummary(list.isEmpty()
                        ? "未找到匹配对象。" : "命中 " + list.size() + " 条，点击查看详细信息");
            });
        } catch (LdapClient.LdapException e) {
            setSummary("查询失败：" + e.getMessage() + "\n" + e.diagnostic);
        } catch (Exception e) {
            setSummary("查询失败：" + e.getMessage());
        } finally {
            client.close();
        }
    }

    /** 计算机：任意位置包含匹配 cn / name / sAMAccountName。 */
    private byte[] buildComputerFilter(String name) {
        String esc = "*" + escape(name) + "*";
        return LdapClient.and(
                LdapClient.eq("objectClass", "computer"),
                LdapClient.or(LdapClient.substring("cn", esc),
                        LdapClient.substring("name", esc),
                        LdapClient.substring("sAMAccountName", esc)));
    }

    /** 用户：任意位置包含匹配 sAMAccountName / userPrincipalName / cn / mail / displayName。 */
    private byte[] buildUserFilter(String name) {
        String esc = "*" + escape(name) + "*";
        return LdapClient.and(
                LdapClient.eq("objectClass", "user"),
                LdapClient.or(LdapClient.substring("sAMAccountName", esc),
                        LdapClient.substring("userPrincipalName", esc),
                        LdapClient.substring("cn", esc),
                        LdapClient.substring("mail", esc),
                        LdapClient.substring("displayName", esc)));
    }

    /** LDAP 过滤器特殊字符转义（* 由调用方追加，不作转义）。 */
    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("*", "\\*")
                .replace("\u0000", "\\00");
    }

    /** 弹出该条目的详细信息对话框。 */
    private void showDetail(Map<String, List<String>> e) {
        String detail = formatEntry(e);
        new AlertDialog.Builder(this)
                .setTitle("详细信息")
                .setMessage(detail)
                .setPositiveButton("复制", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("ldap_detail", detail));
                    Toast.makeText(this, R.string.copy_done, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // ---------- 结果列表 ----------
    private class ResultAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(LdapActivity.this)
                        .inflate(R.layout.item_ldap_result, parent, false);
            }
            TextView tTitle = v.findViewById(R.id.itemTitle);
            TextView tSub = v.findViewById(R.id.itemSub);
            Map<String, List<String>> e = items.get(position);
            String cn = first(e, "cn");
            String sam = first(e, "sAMAccountName");
            String dn = first(e, "dn");
            tTitle.setText(!cn.isEmpty() ? cn : (!sam.isEmpty() ? sam : "（无名称）"));
            tSub.setText(dn.isEmpty() ? "" : dn);
            return v;
        }
    }

    private String first(Map<String, List<String>> e, String key) {
        List<String> vals = e.get(key);
        return (vals != null && !vals.isEmpty()) ? vals.get(0) : "";
    }

    // ---------- 格式化 ----------
    /** 精选字段优先，其余按字典序补充。 */
    private String formatEntry(Map<String, List<String>> e) {
        String[] priority = {
                "cn", "name", "dn", "distinguishedName", "displayName",
                "sAMAccountName", "userPrincipalName", "mail", "department",
                "title", "telephoneNumber", "mobile", "description",
                "operatingSystem", "operatingSystemVersion", "dNSHostName",
                "location", "managedBy", "manager", "memberOf",
                "whenCreated", "lastLogonTimestamp", "userAccountControl",
                "accountExpires", "objectCategory"
        };
        StringBuilder sb = new StringBuilder();
        List<String> done = new ArrayList<>();
        for (String key : priority) {
            List<String> vals = e.get(key);
            if (vals != null && !vals.isEmpty()) {
                sb.append(key).append(": ")
                        .append(fmtValue(key, String.join(", ", vals)))
                        .append("\n");
                done.add(key);
            }
        }
        for (Map.Entry<String, List<String>> entry : new TreeMap<>(e).entrySet()) {
            if (done.contains(entry.getKey())) continue;
            sb.append(entry.getKey()).append(": ")
                    .append(fmtValue(entry.getKey(), String.join(", ", entry.getValue())))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    /** 对 Windows FILETIME 时间字段做可读转换。 */
    private String fmtValue(String key, String value) {
        if (("lastLogonTimestamp".equals(key) || "accountExpires".equals(key)
                || "whenCreated".equals(key) || "lastLogoff".equals(key)
                || "pwdLastSet".equals(key)) && value.matches("\\d{15,19}")) {
            try {
                long ft = Long.parseLong(value);
                if (ft > 0) {
                    long millis = (ft / 10000L) - 11644473600000L;
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date(millis));
                }
            } catch (Exception ignored) {
            }
        }
        return value;
    }

    private void setSummary(String s) {
        runOnUiThread(() -> tvSummary.setText(s));
    }

    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }
}
