package com.example.youtubestarter;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;

/**
 * ============================================================================
 *  YouTube Starter – WebView wrapper for m.youtube.com
 *
 *  v3 FIXES:
 *  ─────────────────────────────────────────────────────────────────────────
 *  MINI PLAYER:
 *  • Before shrinking, webView.scrollTo(0,0) + JS scrollIntoView on the
 *    <video> element → video is always visible at top of mini player.
 *  • dispatchTouchEvent() for reliable swipe (WebView can't block it).
 *  • mini_expand_overlay intercepts taps in mini mode → expand.
 *
 *  SYSTEM PiP (Home button):
 *  • First tries the Web API: video.requestPictureInPicture() via JS.
 *    Chrome-based WebView supports this; video floats natively.
 *  • Falls back to Activity-level PiP after 400 ms if JS PiP didn't fire.
 *  • Before Activity PiP: scroll to y=0 so video is at top of tiny window.
 *  • webView background = BLACK → no white flash.
 * ============================================================================
 */
public class MainActivity extends Activity {

    // ── Views ──────────────────────────────────────────────────────────────
    private WebView     webView;
    private View        miniOverlay;
    private FrameLayout playerContainer;
    private View        miniExpandOverlay;
    private ImageButton closeMiniBtn;

    // ── State ──────────────────────────────────────────────────────────────
    private boolean isMiniPlayerActive  = false;
    private boolean jsPipTriggered      = false;   // tracks if JS PiP fired

    // ── Gesture detector ─────────────────────────────────────────────────
    private GestureDetector gestureDetector;

    // ── Constants ─────────────────────────────────────────────────────────
    private static final int MINI_WIDTH_DP  = 240;
    private static final int MINI_HEIGHT_DP = 135;
    private static final int MINI_MARGIN_DP = 12;
    private static final int ANIM_MS        = 300;
    private static final int SWIPE_DIST_PX  = 100;
    private static final int SWIPE_VEL_PX_S = 100;

    // JS snippet: scroll the page so the <video> is at the top of the viewport
    private static final String JS_SCROLL_TO_VIDEO =
        "(function(){" +
        "  var v=document.querySelector('video');" +
        "  if(v){" +
        "    v.scrollIntoView({behavior:'instant',block:'start'});" +
        "    window.scrollBy(0,-80);" +   // small offset for YouTube's top bar
        "  } else {" +
        "    window.scrollTo(0,0);" +
        "  }" +
        "})()";

    // JS snippet: ask the browser engine to put the video into native PiP.
    // Chrome-based WebView supports video.requestPictureInPicture().
    // Falls back to nothing silently if not supported / no video playing.
    private static final String JS_REQUEST_PIP =
        "(function(){" +
        "  var v=document.querySelector('video');" +
        "  if(v && !v.paused && document.pictureInPictureEnabled){" +
        "    v.requestPictureInPicture()" +
        "     .then(function(){window._pipOk=true;})" +
        "     .catch(function(){window._pipOk=false;});" +
        "  }" +
        "})()";

    // JS: read back whether JS PiP promise resolved
    private static final String JS_CHECK_PIP =
        "(function(){return String(!!window._pipOk);})()";

    // ─────────────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView           = findViewById(R.id.webview);
        miniOverlay       = findViewById(R.id.mini_overlay);
        playerContainer   = findViewById(R.id.player_container);
        miniExpandOverlay = findViewById(R.id.mini_expand_overlay);
        closeMiniBtn      = findViewById(R.id.close_mini_btn);

        setupWebView();
        setupGestureDetector();
        setupMiniPlayerControls();

        webView.loadUrl("https://m.youtube.com/");
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  WEBVIEW SETUP
    // ─────────────────────────────────────────────────────────────────────

    private void setupWebView() {
        webView.setBackgroundColor(Color.BLACK);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " TubeStarterApp");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  GESTURE  — uses dispatchTouchEvent so WebView never blocks it
    // ─────────────────────────────────────────────────────────────────────

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this,
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2,
                                       float velX, float velY) {
                    if (e1 == null || e2 == null || isMiniPlayerActive) return false;
                    float dY = e2.getY() - e1.getY();
                    float dX = e2.getX() - e1.getX();
                    if (dY > SWIPE_DIST_PX
                            && Math.abs(dY) > Math.abs(dX) * 1.2f
                            && velY > SWIPE_VEL_PX_S) {
                        enterMiniPlayer();
                        return true;
                    }
                    return false;
                }
            });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!isMiniPlayerActive) {
            gestureDetector.onTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  MINI PLAYER CONTROLS
    // ─────────────────────────────────────────────────────────────────────

    private void setupMiniPlayerControls() {
        miniExpandOverlay.setOnClickListener(v -> exitMiniPlayer());
        closeMiniBtn.setOnClickListener(v -> exitMiniPlayer());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  ENTER MINI PLAYER
    // ─────────────────────────────────────────────────────────────────────

    private void enterMiniPlayer() {
        isMiniPlayerActive = true;

        // ── FIX: scroll to video BEFORE shrinking so video is visible ─────
        webView.scrollTo(0, 0);
        webView.evaluateJavascript(JS_SCROLL_TO_VIDEO, null);

        float density = getResources().getDisplayMetrics().density;
        int screenW   = getResources().getDisplayMetrics().widthPixels;
        int screenH   = getResources().getDisplayMetrics().heightPixels;
        int miniW     = (int)(MINI_WIDTH_DP  * density);
        int miniH     = (int)(MINI_HEIGHT_DP * density);
        int margin    = (int)(MINI_MARGIN_DP * density);

        float scaleX = (float) miniW / screenW;
        float scaleY = (float) miniH / screenH;

        // Pivot bottom-right → container shrinks into bottom-right corner
        playerContainer.setPivotX(screenW);
        playerContainer.setPivotY(screenH);

        // Show dark background
        miniOverlay.setAlpha(0f);
        miniOverlay.setVisibility(View.VISIBLE);
        miniOverlay.animate().alpha(1f).setDuration(ANIM_MS).start();

        // Shrink animation — small inward margin from screen edge
        playerContainer.animate()
            .scaleX(scaleX)
            .scaleY(scaleY)
            .translationX(-margin)
            .translationY(-margin)
            .setDuration(ANIM_MS)
            .withEndAction(() -> {
                miniExpandOverlay.setVisibility(View.VISIBLE);
                closeMiniBtn.setVisibility(View.VISIBLE);
            })
            .start();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  EXIT MINI PLAYER
    // ─────────────────────────────────────────────────────────────────────

    private void exitMiniPlayer() {
        isMiniPlayerActive = false;
        miniExpandOverlay.setVisibility(View.GONE);
        closeMiniBtn.setVisibility(View.GONE);

        miniOverlay.animate()
            .alpha(0f).setDuration(ANIM_MS)
            .withEndAction(() -> miniOverlay.setVisibility(View.GONE))
            .start();

        playerContainer.animate()
            .scaleX(1f).scaleY(1f)
            .translationX(0f).translationY(0f)
            .setDuration(ANIM_MS)
            .start();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SYSTEM PiP  (Home button)
    //
    //  Strategy:
    //  1. Try JS PiP (video.requestPictureInPicture) — best result: only
    //     the video floats, powered by the browser engine.
    //  2. After 400 ms check if JS PiP worked. If NOT, fall back to
    //     Activity-level PiP (whole app window shrinks).
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        enterSystemPiP();
    }

    private void enterSystemPiP() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        // Reset flag
        jsPipTriggered = false;

        // Step 1 — try the Web PiP API on the video element
        webView.evaluateJavascript(JS_REQUEST_PIP, null);

        // Step 2 — after a short delay, check if JS PiP actually fired.
        // If not, use Activity-level PiP as fallback.
        new Handler(getMainLooper()).postDelayed(() -> {
            webView.evaluateJavascript(JS_CHECK_PIP, value -> {
                boolean jsWorked = ""true"".equals(value) || "true".equals(value);
                if (!jsWorked) {
                    // JS PiP didn't work → Activity-level PiP fallback
                    runOnUiThread(() -> enterActivityPiP());
                }
                // else: JS PiP is showing the video — nothing more to do
            });
        }, 400);
    }

    private void enterActivityPiP() {
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE)) return;

        // Scroll to top so the video is at the top of the tiny PiP window
        webView.scrollTo(0, 0);
        webView.evaluateJavascript(JS_SCROLL_TO_VIDEO, null);

        if (isMiniPlayerActive) exitMiniPlayer();

        PictureInPictureParams pip = new PictureInPictureParams.Builder()
            .setAspectRatio(new Rational(16, 9))
            .build();
        enterPictureInPictureMode(pip);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPiPMode) {
        super.onPictureInPictureModeChanged(isInPiPMode);
        if (isInPiPMode) {
            miniExpandOverlay.setVisibility(View.GONE);
            closeMiniBtn.setVisibility(View.GONE);
            miniOverlay.setVisibility(View.GONE);
        } else {
            isMiniPlayerActive = false;
            playerContainer.setScaleX(1f);
            playerContainer.setScaleY(1f);
            playerContainer.setTranslationX(0f);
            playerContainer.setTranslationY(0f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  BACK BUTTON
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        if (isMiniPlayerActive) { exitMiniPlayer(); return; }
        if (webView.canGoBack()) { webView.goBack(); }
        else { super.onBackPressed(); }
    }
}
