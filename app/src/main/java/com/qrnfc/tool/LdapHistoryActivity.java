package com.qrnfc.tool;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LdapHistoryActivity extends AppCompatActivity {

    private List<LdapHistory.Item> items = new ArrayList<>();
    private LdapAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ldap_history);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listLdap);
        Button btnClear = findViewById(R.id.btnClearLdap);

        adapter = new LdapAdapter();
        listView.setAdapter(adapter);
        reload();

        listView.setOnItemClickListener((p, v, pos, id) -> {
            LdapHistory.Item it = items.get(pos);
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("ldap_history",
                    "查询类型: " + it.type + "\n名称: " + it.name));
            Toast.makeText(this, R.string.copy_done, Toast.LENGTH_SHORT).show();
        });

        listView.setOnItemLongClickListener((p, v, pos, id) -> {
            final int position = pos;
            new AlertDialog.Builder(this)
                    .setTitle("删除该条记录？")
                    .setMessage(items.get(position).type + "：" + items.get(position).name)
                    .setPositiveButton("删除", (d, w) -> {
                        List<LdapHistory.Item> all = LdapHistory.load(this);
                        all.remove(position);
                        LdapHistory.replace(this, all);
                        reload();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        btnClear.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("清空全部 LDAP 查询历史？")
                        .setPositiveButton("清空", (d, w) -> {
                            LdapHistory.clear(this);
                            reload();
                        })
                        .setNegativeButton("取消", null)
                        .show());
    }

    private void reload() {
        items = LdapHistory.load(this);
        adapter.notifyDataSetChanged();
    }

    private class LdapAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(LdapHistoryActivity.this)
                        .inflate(R.layout.item_ldap_history, parent, false);
            }
            TextView tTitle = v.findViewById(R.id.itemTitle);
            TextView tTime = v.findViewById(R.id.itemTime);
            LdapHistory.Item it = items.get(position);
            tTitle.setText("[" + it.type + "] " + it.name);
            tTime.setText(new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(it.time)));
            return v;
        }
    }
}
