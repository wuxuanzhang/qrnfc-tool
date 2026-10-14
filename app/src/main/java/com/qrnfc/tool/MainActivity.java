package com.qrnfc.tool;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialCardView cardScan = findViewById(R.id.cardScan);
        MaterialCardView cardNfc = findViewById(R.id.cardNfc);
        MaterialCardView cardLdap = findViewById(R.id.cardLdap);

        cardScan.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ScanActivity.class)));

        cardNfc.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, NfcActivity.class)));

        cardLdap.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, LdapActivity.class)));
    }
}
