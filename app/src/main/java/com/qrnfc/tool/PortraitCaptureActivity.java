package com.qrnfc.tool;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

/**
 * 强制竖屏 + 内置手电筒开关的扫码页。
 * 覆写 initializeContent() 拿到扫描视图，叠加一个手电筒按钮（右上角）。
 */
public class PortraitCaptureActivity extends CaptureActivity {

    private DecoratedBarcodeView barcodeView;
    private boolean torchOn = false;

    @Override
    protected DecoratedBarcodeView initializeContent() {
        barcodeView = super.initializeContent();
        addTorchButton();
        return barcodeView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void addTorchButton() {
        if (barcodeView == null) return;
        ImageView torch = new ImageView(this);
        int size = dp(52);
        int margin = dp(16);
        // DecoratedBarcodeView 是 FrameLayout，可直接用 FrameLayout.LayoutParams 定位右上角
        FrameLayout.LayoutParams lp =
                new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END);
        lp.topMargin = margin;
        lp.rightMargin = margin;
        torch.setLayoutParams(lp);
        torch.setPadding(dp(14), dp(14), dp(14), dp(14));
        torch.setBackgroundResource(R.drawable.torch_bg);
        torch.setImageResource(R.drawable.ic_flash_off);
        torch.setOnClickListener(v -> toggleTorch(torch));
        barcodeView.addView(torch);
    }

    private void toggleTorch(ImageView btn) {
        torchOn = !torchOn;
        if (torchOn) {
            barcodeView.setTorchOn();
            btn.setImageResource(R.drawable.ic_flash_on);
        } else {
            barcodeView.setTorchOff();
            btn.setImageResource(R.drawable.ic_flash_off);
        }
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v);
    }
}
