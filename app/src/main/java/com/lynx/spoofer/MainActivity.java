package com.lynx.spoofer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_KEYBOX = 1;
    private static final int REQ_PIF    = 2;

    private TextView tvLog, tvKeyboxStatus, tvPifStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLog          = findViewById(R.id.tvLog);
        tvKeyboxStatus = findViewById(R.id.tvKeyboxStatus);
        tvPifStatus    = findViewById(R.id.tvPifStatus);

        // Check current state on launch
        refreshStatus();

        findViewById(R.id.btnKeybox).setOnClickListener(v -> pickFile(REQ_KEYBOX));
        findViewById(R.id.btnPif).setOnClickListener(v -> pickFile(REQ_PIF));
        findViewById(R.id.btnReset).setOnClickListener(v -> resetSpoofing());
        findViewById(R.id.btnIntegrity).setOnClickListener(v -> openPlayIntegrity());
    }

    private void refreshStatus() {
        String keybox = Settings.Secure.getString(getContentResolver(), "keybox_data");
        String pif    = Settings.Secure.getString(getContentResolver(), "pif_data");

        tvKeyboxStatus.setText((keybox != null && !keybox.isEmpty())
            ? "✓ Keybox active (" + keybox.length() + " chars)"
            : "No keybox loaded");

        tvPifStatus.setText((pif != null && !pif.isEmpty())
            ? "✓ pif.json active (" + pif.length() + " chars)"
            : "No pif.json loaded");
    }

    private void pickFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if (requestCode == REQ_KEYBOX) {
            // Accept XML — use wildcard since some file managers don't pass text/xml
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/xml", "application/xml", "text/plain"
            });
        } else {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "text/plain"
            });
        }

        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) { log("✗ Cannot open file"); return; }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            String content = sb.toString().trim();
            if (content.isEmpty()) { log("✗ File is empty"); return; }

            if (requestCode == REQ_KEYBOX) {
                // Basic XML sanity check
                if (!content.contains("<") ) {
                    log("✗ Doesn't look like a valid XML");
                    return;
                }
                Settings.Secure.putString(getContentResolver(), "keybox_data", content);
                log("✓ Keybox written (" + content.length() + " chars)");
                tvKeyboxStatus.setText("✓ Keybox active (" + content.length() + " chars)");

            } else {
                // Basic JSON sanity check
                if (!content.startsWith("{")) {
                    log("✗ Doesn't look like valid JSON");
                    return;
                }
                Settings.Secure.putString(getContentResolver(), "pif_data", content);
                log("✓ pif.json written (" + content.length() + " chars)");
                tvPifStatus.setText("✓ pif.json active (" + content.length() + " chars)");
            }

        } catch (SecurityException e) {
            log("✗ Permission denied — is WRITE_SECURE_SETTINGS granted?");
        } catch (Exception e) {
            log("✗ Error: " + e.getMessage());
        }
    }

    private void resetSpoofing() {
        try {
            Settings.Secure.putString(getContentResolver(), "keybox_data", "");
            Settings.Secure.putString(getContentResolver(), "pif_data", "");
            tvKeyboxStatus.setText("No keybox loaded");
            tvPifStatus.setText("No pif.json loaded");
            log("✓ Spoofing data cleared");
        } catch (SecurityException e) {
            log("✗ Permission denied on reset");
        } catch (Exception e) {
            log("✗ Reset error: " + e.getMessage());
        }
    }

    private void openPlayIntegrity() {
        log("↗ Launching Play Store integrity check...");
        try {
            // Open Play Store app directly — triggers integrity token fetch
            Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.android.vending"));
            intent.setPackage("com.android.vending");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            // Fallback: open via browser
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.android.vending")));
            } catch (Exception ex) {
                log("✗ Play Store not available");
            }
        }
    }

    private void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String current = tvLog.getText().toString();
        tvLog.setText("[" + time + "] " + msg + "\n" + current);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}