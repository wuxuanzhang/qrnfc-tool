package com.qrnfc.tool;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * LDAP 连接设置页：服务器地址、端口、TLS、Base DN、绑定凭据，支持测试连接。
 */
public class LdapSettingsActivity extends AppCompatActivity {

    private static final String PREF = "ldap_settings";

    private EditText etHost, etPort, etBase, etBindDn, etPwd;
    private CheckBox swTls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ldap_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etHost = findViewById(R.id.etHost);
        etPort = findViewById(R.id.etPort);
        swTls = findViewById(R.id.swTls);
        etBase = findViewById(R.id.etBase);
        etBindDn = findViewById(R.id.etBindDn);
        etPwd = findViewById(R.id.etPwd);

        Button btnSave = findViewById(R.id.btnSave);
        Button btnTest = findViewById(R.id.btnTest);

        loadSettings();

        btnSave.setOnClickListener(v -> saveSettings());
        btnTest.setOnClickListener(v -> new Thread(this::testConnection).start());
    }

    private void loadSettings() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        etHost.setText(sp.getString("host", ""));
        etPort.setText(sp.getString("port", "389"));
        etBase.setText(sp.getString("base", ""));
        etBindDn.setText(sp.getString("bind_dn", ""));
        etPwd.setText(sp.getString("pwd", ""));
        swTls.setChecked(sp.getBoolean("tls", false));
    }

    private void saveSettings() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        sp.edit()
                .putString("host", etHost.getText().toString().trim())
                .putString("port", etPort.getText().toString().trim())
                .putString("base", etBase.getText().toString().trim())
                .putString("bind_dn", etBindDn.getText().toString().trim())
                .putString("pwd", etPwd.getText().toString())
                .putBoolean("tls", swTls.isChecked())
                .apply();
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
    }

    private void testConnection() {
        String host = etHost.getText().toString().trim();
        String port = etPort.getText().toString().trim();
        String base = etBase.getText().toString().trim();
        String bindDn = etBindDn.getText().toString().trim();
        String pwd = etPwd.getText().toString();
        boolean tls = swTls.isChecked();
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(port)) {
            toast("请先填写服务器地址和端口");
            return;
        }
        toast("正在连接 " + host + ":" + port + " …");
        LdapClient client = new LdapClient();
        try {
            client.connect(host, Integer.parseInt(port.trim()), tls);
            client.bind(bindDn, pwd);
            String bindMsg = (bindDn.isEmpty() && pwd.isEmpty()) ? "（匿名绑定）" : "（使用绑定 DN）";
            toast("连接成功，已绑定" + bindMsg + "：" + host + ":" + port);
        } catch (LdapClient.LdapException e) {
            toast("连接/绑定失败：" + e.getMessage() + " " + e.diagnostic);
        } catch (Exception e) {
            toast("连接失败：" + e.getMessage());
        } finally {
            client.close();
        }
    }

    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }
}
