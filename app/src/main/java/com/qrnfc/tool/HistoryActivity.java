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

public class HistoryActivity extends AppCompatActivity {

    private List<ScanHistory.Item> items = new ArrayList<>();
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listHistory);
        Button btnClear = findViewById(R.id.btnClear);

        adapter = new HistoryAdapter();
        listView.setAdapter(adapter);
        reload();

        listView.setOnItemClickListener((p, v, pos, id) -> {
            String content = items.get(pos).content;
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("scan_result", content));
            Toast.makeText(this, R.string.copy_done, Toast.LENGTH_SHORT).show();
        });

        listView.setOnItemLongClickListener((p, v, pos, id) -> {
            final int position = pos;
            new AlertDialog.Builder(this)
                    .setTitle("删除该条记录？")
                    .setMessage(items.get(position).content)
                    .setPositiveButton("删除", (d, w) -> {
                        List<ScanHistory.Item> all = ScanHistory.load(this);
                        all.remove(position);
                        ScanHistory.replace(this, all);
                        reload();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        btnClear.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("清空全部扫码历史？")
                        .setPositiveButton("清空", (d, w) -> {
                            ScanHistory.clear(this);
                            reload();
                        })
                        .setNegativeButton("取消", null)
                        .show());
    }

    private void reload() {
        items = ScanHistory.load(this);
        adapter.notifyDataSetChanged();
    }

    private class HistoryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(HistoryActivity.this)
                        .inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            TextView t1 = v.findViewById(android.R.id.text1);
            TextView t2 = v.findViewById(android.R.id.text2);
            ScanHistory.Item it = items.get(position);
            t1.setText(it.content);
            t1.setMaxLines(2);
            t2.setText(new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(it.time)));
            return v;
        }
    }
}
