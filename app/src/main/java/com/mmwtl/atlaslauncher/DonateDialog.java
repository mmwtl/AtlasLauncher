package com.mmwtl.atlaslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Voluntary donation window, shared by the Atlas apps: the CloudTips QR code to scan with a
 * phone, since head units often have no browser, and the link itself. Needs only the
 * {@code donate_*} strings, {@code ic_donate_qr} and a {@code VIEW https} query.
 */
final class DonateDialog {
    // Atlas palette, as in the Atlas widgets.
    private static final int ACCENT = 0xFF7893A0;
    private static final int SURFACE = 0xFF262626;
    private static final int SURFACE_RAISED = 0xFF333333;
    private static final int TEXT = 0xFFF5F5F5;
    private static final int TEXT_SECONDARY = 0xFFD4D4D4;
    private static final int QR_SIZE_DP = 260;

    private DonateDialog() {
    }

    static void show(Activity activity) {
        Uri link = Uri.parse(activity.getString(R.string.donate_url));
        Intent view = new Intent(Intent.ACTION_VIEW, link);
        boolean browser = view.resolveActivity(activity.getPackageManager()) != null;

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(activity, 24), dp(activity, 8), dp(activity, 24), dp(activity, 4));

        TextView message = text(activity, activity.getString(R.string.donate_message), 15, TEXT_SECONDARY);
        message.setLineSpacing(0, 1.2f);
        body.addView(message);

        // The code keeps its white quiet zone; rounded corners only soften it on graphite.
        ImageView qr = new ImageView(activity);
        qr.setImageResource(R.drawable.ic_donate_qr);
        qr.setBackground(rounded(activity, 0xFFFFFFFF, 0, 12));
        qr.setClipToOutline(true);
        qr.setContentDescription(link.toString());
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(
                dp(activity, QR_SIZE_DP), dp(activity, QR_SIZE_DP));
        qrParams.topMargin = dp(activity, 16);
        body.addView(qr, qrParams);

        TextView scan = text(activity, activity.getString(R.string.donate_scan), 14, TEXT);
        scan.setGravity(Gravity.CENTER);
        body.addView(scan, centred(activity, 12));
        TextView url = text(activity, link.toString(), 14, ACCENT);
        url.setGravity(Gravity.CENTER);
        url.setTextIsSelectable(true);
        body.addView(url, centred(activity, 4));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity,
                android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.donate_title)
                .setView(body)
                .setNegativeButton(R.string.donate_close, null);
        if (browser) {
            builder.setPositiveButton(R.string.donate_open, (dialog, which) -> {
                try {
                    activity.startActivity(view);
                } catch (ActivityNotFoundException error) {
                    Toast.makeText(activity, R.string.donate_no_browser, Toast.LENGTH_LONG)
                            .show();
                }
            });
        }
        AlertDialog dialog = builder.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(TEXT_SECONDARY);
        if (browser) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT);
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(rounded(activity, SURFACE, SURFACE_RAISED, 28));
        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        window.setLayout(Math.min(screen - dp(activity, 48), dp(activity, 520)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static TextView text(Activity activity, String value, int sizeSp, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    private static GradientDrawable rounded(Activity activity, int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(activity, radiusDp));
        if (stroke != 0) drawable.setStroke(dp(activity, 1), stroke);
        return drawable;
    }

    private static LinearLayout.LayoutParams centred(Activity activity, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(activity, topMarginDp);
        return params;
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
