package com.qrnfc.tool;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScanActivity extends AppCompatActivity {

    private TextView tvResult;
    private Button btnOpen, btnCopy, btnProduct;
    private String currentContent = "";
    private BarcodeFormat currentFormat;
    private boolean hasCamera = false;

    /** 相机扫码结果（保留码类型，用于区分二维码 / 一维商品条码）。 */
    private final ActivityResultLauncher<ScanOptions> scannerLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result != null && result.getContents() != null) {
                    currentContent = result.getContents();
                    currentFormat = null; // journeyapps 未提供格式；商品条码由内容启发式判断
                    tvResult.setText(currentContent);
                    ScanHistory.add(this, currentContent);
                    updateButtons();
                } else {
                    Toast.makeText(this, R.string.result_empty, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                hasCamera = granted;
                if (granted) {
                    startScan();
                } else {
                    Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                decodeFromUri(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvResult = findViewById(R.id.tvResult);
        btnOpen = findViewById(R.id.btnOpen);
        btnCopy = findViewById(R.id.btnCopy);
        btnProduct = findViewById(R.id.btnProduct);
        Button btnScan = findViewById(R.id.btnScan);
        Button btnAgain = findViewById(R.id.btnAgain);
        Button btnGallery = findViewById(R.id.btnGallery);
        Button btnHistory = findViewById(R.id.btnHistory);

        btnScan.setOnClickListener(v -> tryStartScan());
        btnAgain.setOnClickListener(v -> tryStartScan());

        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        btnCopy.setOnClickListener(v -> {
            if (currentContent.isEmpty()) {
                Toast.makeText(this, R.string.result_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            copyText(currentContent);
        });

        btnOpen.setOnClickListener(v -> {
            if (currentContent.isEmpty() || !isHttpUrl(currentContent)) {
                Toast.makeText(this, R.string.no_link, Toast.LENGTH_SHORT).show();
                return;
            }
            openLink(currentContent);
        });

        btnProduct.setOnClickListener(v -> {
            if (currentContent.isEmpty()) return;
            queryAndShowProduct(currentContent);
        });

        updateButtons();
    }

    private void tryStartScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            hasCamera = true;
            startScan();
        }
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES); // 二维码 + 各类条码
        options.setPrompt("将二维码 / 条形码置于框内");
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(false);
        options.setCaptureActivity(PortraitCaptureActivity.class); // 强制竖屏
        scannerLauncher.launch(options);
    }

    /** 从相册选中的图片解码二维码/条码（子线程执行避免卡顿），支持一图多码。 */
    private void decodeFromUri(Uri uri) {
        Toast.makeText(this, "识别中…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Bitmap bitmap = loadSampledBitmap(uri);
            List<Decoded> results = bitmap != null ? decodeMultiple(bitmap) : new ArrayList<>();
            runOnUiThread(() -> {
                if (results.isEmpty()) {
                    tvResult.setText("未在图片中识别到二维码 / 条码");
                    currentContent = "";
                    currentFormat = null;
                    updateButtons();
                } else if (results.size() == 1) {
                    showSingle(results.get(0));
                } else {
                    showMultiple(results);
                }
            });
        }).start();
    }

    private Bitmap loadSampledBitmap(Uri uri) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, opts);
            }
            int max = 1600;
            int w = opts.outWidth, h = opts.outHeight;
            int sample = 1;
            while (Math.max(w / sample, h / sample) > max) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            try (InputStream is2 = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(is2, null, o2);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 一次解码图片中全部二维码/条码（含码类型）；失败时回退到单码解码。 */
    private List<Decoded> decodeMultiple(Bitmap bitmap) {
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(source));
            MultiFormatReader delegate = new MultiFormatReader();
            delegate.setHints(Collections.singletonMap(DecodeHintType.TRY_HARDER, Boolean.TRUE));
            GenericMultipleBarcodeReader multiReader =
                    new GenericMultipleBarcodeReader(delegate);
            Result[] results = multiReader.decodeMultiple(binary);
            List<Decoded> out = new ArrayList<>();
            if (results != null) {
                for (Result r : results) {
                    if (r != null && r.getText() != null && !r.getText().isEmpty()) {
                        out.add(new Decoded(r.getText(), r.getBarcodeFormat()));
                    }
                }
            }
            if (!out.isEmpty()) return out;
            // 回退：单码
            Result single = delegate.decode(binary);
            out.add(new Decoded(single.getText(), single.getBarcodeFormat()));
            return out;
        } catch (Exception e) {
            // 多码失败，回退单码
            try {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
                BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(source));
                MultiFormatReader reader = new MultiFormatReader();
                reader.setHints(Collections.singletonMap(DecodeHintType.TRY_HARDER, Boolean.TRUE));
                Result single = reader.decode(binary);
                List<Decoded> out = new ArrayList<>();
                out.add(new Decoded(single.getText(), single.getBarcodeFormat()));
                return out;
            } catch (Exception e2) {
                return new ArrayList<>();
            }
        }
    }

    private void showSingle(Decoded d) {
        currentContent = d.text;
        currentFormat = d.format;
        tvResult.setText(d.text);
        ScanHistory.add(this, d.text);
        updateButtons();
    }

    private void showMultiple(List<Decoded> results) {
        for (Decoded d : results) ScanHistory.add(this, d.text);
        String[] items = new String[results.size()];
        for (int i = 0; i < results.size(); i++) items[i] = results.get(i).text;
        new AlertDialog.Builder(this)
                .setTitle("识别到 " + results.size() + " 个码，点击选择操作")
                .setItems(items, (d, which) -> showItemOptions(results.get(which)))
                .setNeutralButton("复制全部", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("scan_result",
                            joinItems(results)));
                    Toast.makeText(this, R.string.copy_done, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** 对单个结果给出二级操作：复制 / 打开链接 / 查询商品。 */
    private void showItemOptions(Decoded d) {
        List<String> ops = new ArrayList<>();
        ops.add("复制");
        if (isHttpUrl(d.text)) ops.add("打开链接");
        if (isLinearBarcode(d.format) || looksLikeProductBarcode(d.text)) ops.add("查询商品");
        String[] arr = ops.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(d.text)
                .setItems(arr, (di, which) -> {
                    switch (arr[which]) {
                        case "打开链接": openLink(d.text); break;
                        case "查询商品": queryAndShowProduct(d.text); break;
                        default: copyText(d.text);
                    }
                })
                .show();
    }

    // ---------- 商品查询 ----------
    /** 查询条码商品信息（子线程）：先 OpenFoodFacts，再 barcodeapi.org。 */
    private void queryAndShowProduct(String barcode) {
        Toast.makeText(this, "正在查询商品信息…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String info = queryProduct(barcode);
            runOnUiThread(() -> {
                if (info != null) {
                    new AlertDialog.Builder(this)
                            .setTitle("条码商品信息")
                            .setMessage(info + "\n\n条码：" + barcode)
                            .setPositiveButton("复制", (d, w) -> copyText(info))
                            .setNegativeButton("关闭", null)
                            .show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("查询商品信息")
                            .setMessage("未收录该条码的商品信息。\n条码：" + barcode +
                                    "\n\n可复制条码后，在浏览器搜索该商品。")
                            .setPositiveButton("浏览器搜索", (d, w) -> {
                                String q = "https://www.baidu.com/s?wd=" + barcode;
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(q)));
                            })
                            .setNeutralButton("复制条码", (d, w) -> copyText(barcode))
                            .setNegativeButton("关闭", null)
                            .show();
                }
            });
        }).start();
    }

    private String queryProduct(String barcode) {
        String off = fetchOpenFoodFacts(barcode);
        if (off != null) return off;
        String api = fetchBarcodeApi(barcode);
        if (api != null) return api;
        return null;
    }

    private String fetchOpenFoodFacts(String barcode) {
        try {
            URL url = new URL("https://world.openfoodfacts.org/api/v2/product/" + barcode + ".json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "ScanNfcTool/2.5");
            int code = conn.getResponseCode();
            if (code == 200) {
                JSONObject obj = new JSONObject(readAll(conn.getInputStream()));
                JSONObject p = obj.optJSONObject("product");
                if (p != null) {
                    String name = p.optString("product_name", "");
                    if (name.isEmpty()) name = p.optString("generic_name", "");
                    if (!name.isEmpty()) {
                        StringBuilder sb = new StringBuilder("商品：" + name);
                        appendField(sb, "品牌", p, "brands");
                        appendField(sb, "规格", p, "quantity");
                        appendField(sb, "分类", p, "categories");
                        appendField(sb, "产地/国家", p, "countries");
                        return sb.toString();
                    }
                }
            }
            conn.disconnect();
        } catch (Exception ignored) { }
        return null;
    }

    private String fetchBarcodeApi(String barcode) {
        try {
            URL url = new URL("https://barcodeapi.org/api/" + barcode);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "ScanNfcTool/2.5");
            int code = conn.getResponseCode();
            if (code == 200) {
                JSONObject o = new JSONObject(readAll(conn.getInputStream()));
                String name = o.optString("name", "");
                if (!name.isEmpty()) {
                    StringBuilder sb = new StringBuilder("商品：" + name);
                    String desc = o.optString("description", "");
                    if (!desc.isEmpty()) sb.append("\n说明：").append(desc);
                    return sb.toString();
                }
            }
            conn.disconnect();
        } catch (Exception ignored) { }
        return null;
    }

    private void appendField(StringBuilder sb, String label, JSONObject p, String key) {
        String v = p.optString(key, "").trim();
        if (!v.isEmpty()) sb.append("\n").append(label).append("：").append(v);
    }

    private String readAll(InputStream in) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    // ---------- 工具 ----------
    private void copyText(String content) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("scan_result", content));
        Toast.makeText(this, R.string.copy_done, Toast.LENGTH_SHORT).show();
    }

    private void openLink(String content) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(content)));
        } catch (Exception e) {
            Toast.makeText(this, "没有可处理该链接的应用", Toast.LENGTH_LONG).show();
        }
    }

    private void updateButtons() {
        btnOpen.setEnabled(!currentContent.isEmpty() && isHttpUrl(currentContent));
        boolean barcode = isLinearBarcode(currentFormat)
                || looksLikeProductBarcode(currentContent);
        btnProduct.setVisibility(barcode ? View.VISIBLE : View.GONE);
    }

    private boolean isHttpUrl(String s) {
        String t = s.trim().toLowerCase();
        return t.startsWith("http://") || t.startsWith("https://");
    }

    /** 一维商品条码（EAN/UPC/Code128 等）；二维码/矩阵码不算。 */
    private boolean isLinearBarcode(BarcodeFormat f) {
        if (f == null) return false;
        switch (f) {
            case EAN_13: case EAN_8: case UPC_A: case UPC_E:
            case CODE_128: case CODE_39: case CODE_93:
            case CODABAR: case ITF: case RSS_14: case RSS_EXPANDED:
                return true;
            default:
                return false;
        }
    }

    /** 相册识别结果无码类型时，按 EAN/UPC 常见位数的纯数字启发式判断。 */
    private boolean looksLikeProductBarcode(String text) {
        return text != null && text.matches("\\d{8}|\\d{12}|\\d{13}|\\d{14}");
    }

    private String joinItems(List<Decoded> list) {
        StringBuilder sb = new StringBuilder();
        for (Decoded d : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(d.text);
        }
        return sb.toString();
    }

    /** 解码结果：文本 + 码类型。 */
    private static class Decoded {
        final String text;
        final BarcodeFormat format;
        Decoded(String text, BarcodeFormat format) {
            this.text = text;
            this.format = format;
        }
    }
}
