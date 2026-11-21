package org.mhatta.rakkyo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

/** Small helpers shared by the screens. */
final class UiUtil {
    private UiUtil() {
    }

    static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Draw behind the status and navigation bars, as Android 15 does for
     * every app that targets it, on every version, so that the screens look
     * the same everywhere: the top view is padded by the status bar, the
     * bottom view by the navigation bar.  On Android 7.x, whose navigation
     * bar cannot have dark buttons, the screens stay above it.
     */
    @SuppressWarnings("deprecation")
    static void edgeToEdge(Activity activity, final View top, final View bottom) {
        final Window window = activity.getWindow();
        final boolean night = isNight(activity);
        final boolean behindNavigation = Build.VERSION.SDK_INT >= 26;
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            final WindowInsetsController c = window.getInsetsController();
            if (c != null) {
                final int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(night ? 0 : light, light);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (!night) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (behindNavigation) {
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                if (!night) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
        window.setStatusBarColor(Color.TRANSPARENT);
        if (behindNavigation) {
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
        }

        final int topStart = top.getPaddingStart(), topTop = top.getPaddingTop(), topEnd = top.getPaddingEnd();
        final int bottomStart = bottom.getPaddingStart(), bottomEnd = bottom.getPaddingEnd(), bottomBottom = bottom.getPaddingBottom();
        window.getDecorView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                final int left = insets.getSystemWindowInsetLeft();
                final int right = insets.getSystemWindowInsetRight();
                final int statusBar = insets.getSystemWindowInsetTop();
                final int navigationBar = behindNavigation ? insets.getSystemWindowInsetBottom() : 0;
                top.setPaddingRelative(topStart + left, topTop + statusBar, topEnd + right, top.getPaddingBottom());
                if (bottom == top) {
                    bottom.setPaddingRelative(topStart + left, topTop + statusBar, topEnd + right, bottomBottom + navigationBar);
                } else {
                    bottom.setPaddingRelative(bottomStart + left, bottom.getPaddingTop(), bottomEnd + right,
                            bottomBottom + navigationBar);
                }
                return v.onApplyWindowInsets(insets);
            }
        });
    }

    /* How long a copied password stays on the clipboard. */
    private static final long SENSITIVE_CLIP_MILLIS = 60 * 1000;

    /**
     * Copy to the clipboard.  A password is marked sensitive, so that
     * Android 13+ does not show it in its clipboard preview, and is taken
     * off the clipboard after a minute if it is still there (any app in
     * the foreground, and the keyboard, can read the clipboard).
     */
    static void copy(Context context, String label, final String text, boolean sensitive) {
        final ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            return;
        }
        final ClipData clip = ClipData.newPlainText(label, text);
        if (sensitive && Build.VERSION.SDK_INT >= 24) {
            final PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(Build.VERSION.SDK_INT >= 33 ? ClipDescription.EXTRA_IS_SENSITIVE
                    : "android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(extras);
        }
        cm.setPrimaryClip(clip);
        if (sensitive) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> clearIfStill(cm, text), SENSITIVE_CLIP_MILLIS);
        }
        // Android 13 and later confirm the copy themselves.
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    /*
     * Clear the clipboard if it still holds text.  Android 10 and later
     * show the clipboard only to the app in the foreground: when Rakkyo is
     * not, getPrimaryClip() gives null and the clipboard is left alone,
     * rather than clearing what the user copied since.
     */
    private static void clearIfStill(ClipboardManager cm, String text) {
        try {
            final ClipData now = cm.getPrimaryClip();
            if (now == null || now.getItemCount() == 0) {
                return;
            }
            final CharSequence current = now.getItemAt(0).getText();
            if (current == null || !text.contentEquals(current)) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 28) {
                cm.clearPrimaryClip();
            } else {
                cm.setPrimaryClip(ClipData.newPlainText("", ""));
            }
        } catch (RuntimeException e) {
            // A clipboard that cannot be read or cleared: nothing more to do.
        }
    }

    /**
     * Keep a dialog that shows bridge lines out of screenshots and the
     * recent apps screen.  A dialog has a window of its own, which does not
     * take FLAG_SECURE from the activity.  Call after create().
     */
    static void secure(Dialog dialog) {
        final Window window = dialog.getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    /**
     * Ask the keyboard not to learn from what is typed: some keyboards,
     * among them Japanese ones with cloud conversion, send or sync what they
     * learn, and a bridge address is meant to stay secret.  Keyboards before
     * Android 8.0 (API 26), where the flag was added, ignore it.
     */
    @SuppressLint("InlinedApi")
    static void noKeyboardLearning(EditText edit) {
        edit.setImeOptions(edit.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
    }

    static void openUrl(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_app_for_link, Toast.LENGTH_SHORT).show();
        }
    }

    static int dp(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    static void show(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
