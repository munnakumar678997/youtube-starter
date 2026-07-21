package com.example.youtubestarter;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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
 *  YouTube Starter – WebView wrapper around m.youtube.com
 *
 *  FEATURES:
 *  ─────────────────────────────────────────────────────────────────────────
 *  1. IN-APP MINI PLAYER  (swipe down while watching)
 *     • Swipe DOWN anywhere on screen → player shrinks to 240×135 dp
 *       anchored bottom-right (like YouTube's own mini player).
 *     • Tap mini player  → expands back to full screen.
 *     • Tap × button     → closes mini player.
 *
 *     FIX vs v1: gesture detection moved to Activity.dispatchTouchEvent()
 *     so it fires BEFORE WebView can consume the touch.  A transparent
 *     mini_expand_overlay view (inside player_container) intercepts taps
 *     in mini mode without fighting the WebView.
 *
 *  2. SYSTEM PICTURE-IN-PICTURE  (Home button press)
 *     • Home button → Android system PiP (16:9 floating window).
 *     • Before entering PiP we scroll the WebView to y=0 so the video
 *       (always at the top of the page) is visible in the tiny window.
 *     • JS injection scrolls the page to the video element for good measure.
 *     • Requires Android 8.0+ (API 26); graceful no-op below that.
 *
 *  FIX vs v1: added hardwareAccelerated in manifest, scroll-to-top before
 *  PiP, and WebView background forced to black to kill the white flash.
 * ============================================================================
 */
public class MainActivity extends Activity {

    // ── Views ──────────────────────────────────────────────────────────────
    private WebView      webView;
    private View         miniOverlay;         // dark bg layer
    private FrameLayout  playerContainer;     // animates between full ↔ mini
    private View         miniExpandOverlay;   // transparent tap-catcher in mini mode
    private ImageButton  closeMiniBtn;

    // ── State ──────────────────────────────────────────────────────────────
    private boolean isMiniPlayerActive = false;

    // ── Gesture detector (swipe-down) ─────────────────────────────────────
    private GestureDetector gestureDetector;

    // ── Constants ─────────────────────────────────────────────────────────
    private static final int MINI_WIDTH_DP    = 240;
    private static final int MINI_HEIGHT_DP   = 135;
    private static final int MINI_MARGIN_DP   = 12;
    private static final int ANIM_MS          = 300;
    private static final int SWIPE_DIST_PX    = 100;   // min vertical distance
    private static final int SWIPE_VEL_PX_S   = 100;   // min velocity

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
        webView.setBackgroundColor(Color.BLACK);   // prevent white flash in PiP

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
    //  GESTURE DETECTOR
    //  KEY FIX: we hook dispatchTouchEvent() (Activity level) not a View's
    //  OnTouchListener, so WebView can never swallow the gesture before us.
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
                    // Downward swipe: dY big, Y-axis dominant, fast enough
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

    /**
     * Activity-level touch dispatch — fires before ANY child view.
     * We feed every event to the GestureDetector (for swipe-down detection)
     * while still forwarding to the view hierarchy via super.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!isMiniPlayerActive) {
            // Normal mode: detect swipe-down; let WebView handle everything else
            gestureDetector.onTouchEvent(event);
        }
        // Always call super so views (WebView, buttons) receive their events
        return super.dispatchTouchEvent(event);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  MINI PLAYER CONTROLS
    // ─────────────────────────────────────────────────────────────────────

    private void setupMiniPlayerControls() {
        // Transparent overlay inside playerContainer — tap anywhere on mini
        // player to expand back. Active only when mini mode is on.
        miniExpandOverlay.setOnClickListener(v -> exitMiniPlayer());

        // × button closes the mini player
        closeMiniBtn.setOnClickListener(v -> exitMiniPlayer());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  ENTER MINI PLAYER
    // ─────────────────────────────────────────────────────────────────────

    private void enterMiniPlayer() {
        isMiniPlayerActive = true;

        float density = getResources().getDisplayMetrics().density;
        int screenW   = getResources().getDisplayMetrics().widthPixels;
        int screenH   = getResources().getDisplayMetrics().heightPixels;

        int miniW  = (int)(MINI_WIDTH_DP  * density);
        int miniH  = (int)(MINI_HEIGHT_DP * density);
        int margin = (int)(MINI_MARGIN_DP * density);

        // Scale factors: pivot = bottom-right corner → shrinks into corner
        float scaleX = (float) miniW / screenW;
        float scaleY = (float) miniH / screenH;

        playerContainer.setPivotX(screenW);
        playerContainer.setPivotY(screenH);

        // Shift inward so there's a margin from the screen edge
        float tx = -margin;
        float ty = -margin;

        // Show dark overlay
        miniOverlay.setAlpha(0f);
        miniOverlay.setVisibility(View.VISIBLE);
        miniOverlay.animate().alpha(1f).setDuration(ANIM_MS).start();

        // Shrink playerContainer to bottom-right
        playerContainer.animate()
            .scaleX(scaleX)
            .scaleY(scaleY)
            .translationX(tx)
            .translationY(ty)
            .setDuration(ANIM_MS)
            .withEndAction(() -> {
                // Enable tap-to-expand overlay and show × button
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

        // Hide controls immediately so they don't flash during animation
        miniExpandOverlay.setVisibility(View.GONE);
        closeMiniBtn.setVisibility(View.GONE);

        // Fade out dark bg
        miniOverlay.animate()
            .alpha(0f)
            .setDuration(ANIM_MS)
            .withEndAction(() -> miniOverlay.setVisibility(View.GONE))
            .start();

        // Expand back to full screen
        playerContainer.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(ANIM_MS)
            .start();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SYSTEM PiP  (Home button)
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        enterSystemPiP();
    }

    private void enterSystemPiP() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE)) return;

        // ── FIX: scroll WebView to top so video (always at top of page) ──
        // is visible in the tiny PiP window, not the comments section.
        webView.scrollTo(0, 0);
        webView.evaluateJavascript(
            "(function(){" +
            "  var v=document.querySelector('video');" +
            "  if(v){v.scrollIntoView({behavior:'instant',block:'start'});}" +
            "})()", null);

        // Close in-app mini player first if active
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
            // PiP window is tiny — hide all overlays, show only the WebView
            miniExpandOverlay.setVisibility(View.GONE);
            closeMiniBtn.setVisibility(View.GONE);
            miniOverlay.setVisibility(View.GONE);
        } else {
            // Returning to full screen — reset any leftover transform
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
        if (isMiniPlayerActive) {
            exitMiniPlayer();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
