package com.example.youtubestarter;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
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
 *  FEATURES ADDED:
 *  ─────────────────────────────────────────────────────────────────────────
 *  1. IN-APP MINI PLAYER
 *     When the user swipes DOWN on the screen while a video is playing,
 *     the player container shrinks to a 240×135 dp thumbnail anchored at
 *     the bottom-right corner (exactly like YouTube's mini player).
 *     • Tap the mini player  → expands back to full screen.
 *     • Tap the × button    → closes the mini player (video continues
 *                              playing inside the WebView in background).
 *     • Swipe is detected via GestureDetector on the container view.
 *
 *  2. SYSTEM PICTURE-IN-PICTURE (PiP)
 *     When the user presses the Home button while a video is playing,
 *     the Activity enters Android's system PiP mode — the app window
 *     shrinks to a floating 16:9 overlay that stays on top of the home
 *     screen / other apps.
 *     • Triggered in onUserLeaveHint() (Home / Recents press).
 *     • Requires Android 8.0+ (API 26). Graceful no-op on older devices.
 *     • Manifest must have:  android:supportsPictureInPicture="true"
 *       and the extended configChanges list so the Activity is NOT
 *       recreated when PiP window is resized.
 * ============================================================================
 */
public class MainActivity extends Activity {

    // ── Views ──────────────────────────────────────────────────────────────
    private WebView  webView;
    private View     miniOverlay;      // dark background layer
    private FrameLayout playerContainer; // wrapper that animates
    private ImageButton closeMiniBtn;  // × inside mini player

    // ── State ──────────────────────────────────────────────────────────────
    private boolean isMiniPlayerActive = false;

    // ── Gesture detection ─────────────────────────────────────────────────
    private GestureDetector gestureDetector;

    // ── Mini player geometry constants (dp) ───────────────────────────────
    private static final int MINI_WIDTH_DP    = 240;
    private static final int MINI_HEIGHT_DP   = 135;
    private static final int MINI_MARGIN_DP   = 12;
    private static final int BOTTOM_NAV_DP    = 0;   // WebView fills full screen; adjust if you add a nav bar
    private static final int ANIM_DURATION_MS = 300;

    // ── Swipe-detection thresholds ────────────────────────────────────────
    private static final int SWIPE_DIST_PX  = 120;
    private static final int SWIPE_VEL_PX_S = 150;

    // ─────────────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView         = findViewById(R.id.webview);
        miniOverlay     = findViewById(R.id.mini_overlay);
        playerContainer = findViewById(R.id.player_container);
        closeMiniBtn    = findViewById(R.id.close_mini_btn);

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
    //  GESTURE DETECTOR  (swipe-down → enter mini player)
    // ─────────────────────────────────────────────────────────────────────

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float velX, float velY) {
                        if (e1 == null || e2 == null) return false;
                        float dY = e2.getY() - e1.getY();
                        float dX = e2.getX() - e1.getX();
                        // Downward swipe: dY > threshold, dominant axis is Y
                        if (dY > SWIPE_DIST_PX
                                && Math.abs(dY) > Math.abs(dX)
                                && velY > SWIPE_VEL_PX_S
                                && !isMiniPlayerActive) {
                            enterMiniPlayer();
                            return true;
                        }
                        return false;
                    }
                });

        // Attach gesture detector to the player container (not directly to WebView
        // so we still forward all events to WebView in normal mode).
        playerContainer.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);

            if (isMiniPlayerActive) {
                // In mini mode: single tap on container → expand back
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    exitMiniPlayer();
                }
                return true; // consume all touches in mini mode
            }
            return false; // normal mode: let WebView handle
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    //  MINI PLAYER CONTROLS
    // ─────────────────────────────────────────────────────────────────────

    private void setupMiniPlayerControls() {
        // × button: close the mini player
        closeMiniBtn.setOnClickListener(v -> exitMiniPlayer());

        // Tapping the dark overlay does nothing (video plays behind it);
        // user must tap the mini player itself to expand.
        miniOverlay.setOnClickListener(null);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  ENTER MINI PLAYER
    //  Shrinks playerContainer toward the bottom-right corner.
    // ─────────────────────────────────────────────────────────────────────

    private void enterMiniPlayer() {
        isMiniPlayerActive = true;

        float density   = getResources().getDisplayMetrics().density;
        int screenW     = getResources().getDisplayMetrics().widthPixels;
        int screenH     = getResources().getDisplayMetrics().heightPixels;

        int miniW       = (int) (MINI_WIDTH_DP  * density);
        int miniH       = (int) (MINI_HEIGHT_DP * density);
        int margin      = (int) (MINI_MARGIN_DP * density);
        int bottomPad   = (int) (BOTTOM_NAV_DP  * density);

        // Scale factors so the container visually becomes mini-sized
        float scaleX = (float) miniW / screenW;
        float scaleY = (float) miniH / screenH;

        // Pivot at bottom-right corner → container shrinks into that corner
        playerContainer.setPivotX(screenW);
        playerContainer.setPivotY(screenH);

        // After scaling, the bottom-right pixel stays at (screenW, screenH).
        // We want a small margin from the edge, so translate inward slightly.
        float tx = -margin;          // shift left by margin
        float ty = -(margin + bottomPad); // shift up by margin + nav height

        // ── Show dark background ───────────────────────────────────────────
        miniOverlay.setAlpha(0f);
        miniOverlay.setVisibility(View.VISIBLE);
        miniOverlay.animate().alpha(1f).setDuration(ANIM_DURATION_MS).start();

        // ── Animate container to mini size ────────────────────────────────
        playerContainer.animate()
                .scaleX(scaleX)
                .scaleY(scaleY)
                .translationX(tx)
                .translationY(ty)
                .setDuration(ANIM_DURATION_MS)
                .withEndAction(() -> closeMiniBtn.setVisibility(View.VISIBLE))
                .start();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  EXIT MINI PLAYER
    //  Restores playerContainer to full screen.
    // ─────────────────────────────────────────────────────────────────────

    private void exitMiniPlayer() {
        isMiniPlayerActive = false;
        closeMiniBtn.setVisibility(View.GONE);

        // ── Hide dark background ──────────────────────────────────────────
        miniOverlay.animate()
                .alpha(0f)
                .setDuration(ANIM_DURATION_MS)
                .withEndAction(() -> miniOverlay.setVisibility(View.GONE))
                .start();

        // ── Restore container to full screen ──────────────────────────────
        playerContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(ANIM_DURATION_MS)
                .start();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SYSTEM PICTURE-IN-PICTURE  (Home button)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Called when the user navigates away (Home / Recents press).
     * We enter Android's system PiP so the video keeps playing in a
     * floating 16:9 window above the home screen.
     */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        enterSystemPiP();
    }

    private void enterSystemPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Only if the device/launcher supports PiP
            if (getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                // Exit in-app mini player first (if active) so the full
                // WebView is shown inside the PiP window.
                if (isMiniPlayerActive) {
                    exitMiniPlayer();
                }
                PictureInPictureParams pip = new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9))
                        .build();
                enterPictureInPictureMode(pip);
            }
        }
    }

    /**
     * Fires when PiP mode starts or ends.
     * Hide controls when in PiP; restore when returning to full screen.
     */
    @Override
    public void onPictureInPictureModeChanged(boolean isInPiPMode) {
        super.onPictureInPictureModeChanged(isInPiPMode);
        // In PiP the window is tiny – hide everything except the WebView.
        closeMiniBtn.setVisibility(View.GONE);
        miniOverlay.setVisibility(View.GONE);
        if (!isInPiPMode) {
            // Returning to full screen: reset any leftover transform
            playerContainer.setScaleX(1f);
            playerContainer.setScaleY(1f);
            playerContainer.setTranslationX(0f);
            playerContainer.setTranslationY(0f);
            isMiniPlayerActive = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  BACK BUTTON
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        // 1. If mini player is active, back = expand to full screen
        if (isMiniPlayerActive) {
            exitMiniPlayer();
            return;
        }
        // 2. Navigate back inside YouTube's page history
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
