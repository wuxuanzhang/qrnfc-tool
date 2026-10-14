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

public class NfcHistoryActivity extends AppCompatActivity {

    private List<NfcHistory.Item> items = new ArrayList<>();
    private NfcAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_history);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.listNfc);
        Button btnClear = findViewById(R.id.btnClearNfc);

        adapter = new NfcAdapter();
        listView.setAdapter(adapter);
        reload();

        listView.setOnItemClickListener((p, v, pos, id) -> {
            NfcHistory.Item it = items.get(pos);
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("nfc_uid",
                    "HEX: " + it.hex + "\nDEC: " + it.dec + "\n类型: " + it.tech));
            Toast.makeText(this, R.string.copy_done, Toast.LENGTH_SHORT).show();
        });

        listView.setOnItemLongClickListener((p, v, pos, id) -> {
            final int position = pos;
            new AlertDialog.Builder(this)
                    .setTitle("删除该条记录？")
                    .setMessage(items.get(position).hex)
                    .setPositiveButton("删除", (d, w) -> {
                        List<NfcHistory.Item> all = NfcHistory.load(this);
                        all.remove(position);
                        NfcHistory.replace(this, all);
                        reload();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        btnClear.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("清空全部 NFC 读卡历史？")
                        .setPositiveButton("清空", (d, w) -> {
                            NfcHistory.clear(this);
                            reload();
                        })
                        .setNegativeButton("取消", null)
                        .show());
    }

    private void reload() {
        items = NfcHistory.load(this);
        adapter.notifyDataSetChanged();
    }

    private class NfcAdapter extends BaseAdapter {
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
                v = LayoutInflater.from(NfcHistoryActivity.this)
                        .inflate(R.layout.item_nfc_history, parent, false);
            }
            TextView tHex = v.findViewById(R.id.itemHex);
            TextView tDec = v.findViewById(R.id.itemDec);
            TextView tTech = v.findViewById(R.id.itemTech);
            TextView tTime = v.findViewById(R.id.itemTime);
            NfcHistory.Item it = items.get(position);
            tHex.setText("HEX: " + it.hex);
            tDec.setText("DEC: " + it.dec);
            tTech.setText("类型: " + it.tech);
            tTime.setText(new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(it.time)));
            return v;
        }
    }
}
