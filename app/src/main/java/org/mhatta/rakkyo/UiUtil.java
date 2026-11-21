package org.mhatta.rakkyo;

import android.app.Activity;
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
import android.os.PersistableBundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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

    /**
     * Copy to the clipboard.  A password is marked sensitive, so that
     * Android 13+ does not show it in its clipboard preview.
     */
    static void copy(Context context, String label, String text, boolean sensitive) {
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
        // Android 13 and later confirm the copy themselves.
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show();
        }
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
