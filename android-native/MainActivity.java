package com.tiemdoan.tinyfastfood;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

/* Full screen game:
   - hide the status bar and the navigation bar (swiping from an edge shows them briefly);
   - draw into the camera cutout area instead of leaving a black strip beside it;
   - tell the page how far the cutout reaches into each edge (CSS px), so the game can keep
     its buttons out from under the camera hole. The page reads TiemDoanSafeArea.get() and also
     receives window.__setSafeArea(left, top, right, bottom) whenever the cutout insets change. */
public class MainActivity extends BridgeActivity {
    private volatile int safeLeft = 0;
    private volatile int safeTop = 0;
    private volatile int safeRight = 0;
    private volatile int safeBottom = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        hideSystemBars();

        WebView webView = currentWebView();
        if (webView != null) webView.addJavascriptInterface(new SafeAreaBridge(), "TiemDoanSafeArea");

        View content = findViewById(android.R.id.content);
        if (content != null) {
            ViewCompat.setOnApplyWindowInsetsListener(content, new OnApplyWindowInsetsListener() {
                @Override
                public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                    Insets cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                    float density = getResources().getDisplayMetrics().density;
                    safeLeft = Math.round(cut.left / density);
                    safeTop = Math.round(cut.top / density);
                    safeRight = Math.round(cut.right / density);
                    safeBottom = Math.round(cut.bottom / density);
                    pushSafeArea();
                    return ViewCompat.onApplyWindowInsets(v, insets);
                }
            });
            /* The page may not be ready the first time insets arrive: send them again a little later. */
            content.postDelayed(new Runnable() {
                @Override
                public void run() { pushSafeArea(); }
            }, 1500);
            content.postDelayed(new Runnable() {
                @Override
                public void run() { pushSafeArea(); }
            }, 4000);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        hideSystemBars();
        pushSafeArea();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private WebView currentWebView() {
        return getBridge() != null ? getBridge().getWebView() : null;
    }

    private void pushSafeArea() {
        final WebView webView = currentWebView();
        if (webView == null) return;
        final String js = "window.__setSafeArea&&window.__setSafeArea(" + safeLeft + "," + safeTop + "," + safeRight + "," + safeBottom + ")";
        webView.post(new Runnable() {
            @Override
            public void run() { webView.evaluateJavascript(js, null); }
        });
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    public class SafeAreaBridge {
        @JavascriptInterface
        public String get() {
            return safeLeft + "," + safeTop + "," + safeRight + "," + safeBottom;
        }
    }
}
