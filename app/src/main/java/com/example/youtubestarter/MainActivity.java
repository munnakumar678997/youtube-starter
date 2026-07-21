package com.example.youtubestarter;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;

/**
 * ============================================================================
 *  THE CORE MECHANISM — this is the ONLY thing that makes "YouTube" show up.
 * ============================================================================
 *
 *  There is no YouTube API here. No video list, no search endpoint, nothing
 *  custom-built. This Activity simply opens Android's built-in WebView (the
 *  same rendering engine Chrome uses) and points it at the REAL YouTube
 *  mobile website:
 *
 *          https://m.youtube.com/
 *
 *  Everything you see after that — search, video playback, recommendations,
 *  comments — is Google's own YouTube website rendering exactly like it
 *  would in Chrome. The WebView is just a window showing that real website
 *  inside your app's UI instead of inside a browser's UI.
 *
 *  This is why it "just works" for search etc. — you are not talking to any
 *  API at all, you're loading youtube.com itself.
 * ============================================================================
 */
public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);

        // --- WebView settings needed for a modern website like YouTube to work ---
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);           // YouTube's site is JS-heavy (it's a single-page app)
        settings.setDomStorageEnabled(true);            // needed for YouTube's own local/session storage
        settings.setMediaPlaybackRequiresUserGesture(false); // allow videos to autoplay like normal
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " TubeStarterApp");

        // --- Cookies: needed so "Sign in" actually keeps you signed in ---
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // --- Keep navigation inside the WebView instead of opening Chrome ---
        webView.setWebViewClient(new WebViewClient());

        // --- Needed for video fullscreen, JS alerts/permissions, etc. ---
        webView.setWebChromeClient(new WebChromeClient());

        // --- This one line is the actual "make YouTube appear" step ---
        webView.loadUrl("https://m.youtube.com/");
    }

    @Override
    public void onBackPressed() {
        // Go back within YouTube's own page history first (e.g. video -> search
        // results -> home) before actually leaving the app.
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}