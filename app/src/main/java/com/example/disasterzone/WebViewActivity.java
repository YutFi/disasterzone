package com.example.disasterzone;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;

public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        // 1. Setup UI Elements
        ImageView btnBack = findViewById(R.id.btnBack);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar); // Connects to the spinner in your XML

        // 2. Handle Back Button
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // 3. Configure WebView Settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        // 4. Custom Client (Handles Links AND Hides Loading Spinner)
        webView.setWebViewClient(new WebViewClient() {

            // LOGIC A: Handle specific clicks (Calls, External Links, Email)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Handle Phone Calls (tel:999)
                if (url.startsWith("tel:")) {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }

                // Handle Website Links - Open in Chrome instead of App
                if (url.startsWith("http") || url.startsWith("https")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }

                // Handle Mail (mailto:)
                if (url.startsWith("mailto:")) {
                    Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }

                return false; // Load normal links internally
            }

            // LOGIC B: Hide the Spinner when page finishes loading
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        // 5. Load the Local Guide File
        // Make sure you have created 'src/main/assets/guide.html'
        webView.loadUrl("file:///android_asset/guide.html");
    }
}