package com.qrnfc.tool;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.math.BigInteger;

public class NfcActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    private TextView tvStatus, tvTech, tvHex, tvDec;
    private String currentHex = "";
    private String currentDec = "";
    private String currentTech = "";

    private final NfcAdapter.ReaderCallback readerCallback = tag -> {
        // Reader 回调在专用线程，切回主线程更新 UI
        runOnUiThread(() -> showTag(tag));
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_nfc);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_history) {
                startActivity(new android.content.Intent(this, NfcHistoryActivity.class));
                return true;
            }
            return false;
        });

        tvStatus = findViewById(R.id.tvStatus);
        tvTech = findViewById(R.id.tvTech);
        tvHex = findViewById(R.id.tvHex);
        tvDec = findViewById(R.id.tvDec);
        Button btnCopyHex = findViewById(R.id.btnCopyHex);
        Button btnCopyDec = findViewById(R.id.btnCopyDec);
        Button btnCopyAll = findViewById(R.id.btnCopyNfc);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            tvStatus.setText(R.string.no_nfc_hw);
            tvStatus.setTextColor(0xFFD93025);
        }

        btnCopyHex.setOnClickListener(v -> copy("HEX", currentHex));
        btnCopyDec.setOnClickListener(v -> copy("DEC", currentDec));
        btnCopyAll.setOnClickListener(v -> {
            if (currentHex.isEmpty()) {
                Toast.makeText(this, "尚未读到卡片", Toast.LENGTH_SHORT).show();
                return;
            }
            copy("UID", "HEX: " + currentHex + "\nDEC: " + currentDec);
        });
    }

    private void copy(String label, String value) {
        if (value.isEmpty()) {
            Toast.makeText(this, "尚未读到卡片", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("nfc_" + label, value));
        Toast.makeText(this, R.string.copy_done, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            nfcAdapter.enableReaderMode(
                    this,
                    readerCallback,
                    NfcAdapter.FLAG_READER_NFC_A
                            | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_NFC_F
                            | NfcAdapter.FLAG_READER_NFC_V
                            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
        }
    }

    private void showTag(Tag tag) {
        if (tag == null) return;
        byte[] id = tag.getId();
        if (id == null || id.length == 0) {
            tvStatus.setText("识别失败，请重新贴卡");
            tvStatus.setTextColor(0xFFD93025);
            return;
        }
        currentHex = toHexWithColon(id);
        currentDec = new BigInteger(1, id).toString(); // 无符号十进制
        currentTech = friendlyTech(tag.getTechList());
        tvHex.setText(currentHex);
        tvDec.setText(currentDec);
        tvTech.setText(currentTech);
        tvStatus.setText("读卡成功");
        tvStatus.setTextColor(0xFF0B8043);
        // 记录到历史
        NfcHistory.add(this, currentHex, currentDec, currentTech);
        vibrate();
    }

    /** 把技术类全名转为易读名称。 */
    private String friendlyTech(String[] techList) {
        if (techList == null || techList.length == 0) return "未知";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < techList.length; i++) {
            String t = techList[i];
            int dot = t.lastIndexOf('.');
            String name = dot >= 0 ? t.substring(dot + 1) : t;
            if (i > 0) sb.append(" / ");
            sb.append(name);
        }
        return sb.toString();
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(80);
            }
        } catch (Exception ignored) {
        }
    }

    private String toHexWithColon(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(":");
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }
}
