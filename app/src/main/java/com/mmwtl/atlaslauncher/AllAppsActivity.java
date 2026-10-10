package com.mmwtl.atlaslauncher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The built-in "All apps" catalog. A dialog-styled activity in its own task, so the dock, the climate panel and
 * the four-finger gesture open it over whatever is on screen, AtlasLauncher or another app. It closes when it
 * leaves the screen, and opening it again while it is shown closes it.
 */
public final class AllAppsActivity extends Activity {
    private static final int ICON_DP = 96;
    // Read by the climate panel service on the main thread.
    static boolean shown;
    private final List<HomeActivity.AppEntry> visible = new ArrayList<>();
    private List<HomeActivity.AppEntry> apps = new ArrayList<>();
    private BaseAdapter adapter;
    private EditText search;

    /** Opens the "All apps" activity chosen in AtlasLauncher settings, or toggles the built-in catalog. */
    @SuppressWarnings("deprecation")
    static void open(Context context) {
        // The gesture service runs in its own process; MULTI_PROCESS rereads the file HOME changed.
        String target = context.getSharedPreferences(HomeActivity.PREFS, Context.MODE_MULTI_PROCESS)
                .getString(HomeActivity.DRAWER_ACTIVITY, "");
        // A start from a service may otherwise follow focus to the display the climate panel just moved
        // Launcher3 forward on.
        Bundle options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle();
        if (!target.isEmpty()) {
            ComponentName component = ComponentName.unflattenFromString(target);
            try {
                if (component == null) throw new ActivityNotFoundException();
                context.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), options);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(context, "Выбранная Activity недоступна. Открыт встроенный каталог.", Toast.LENGTH_LONG).show();
            }
        }
        context.startActivity(new Intent(context, AllAppsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        shown = true;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(20));
        content.setBackground(round(HomeActivity.NEUTRAL_SURFACE, 32));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(76)));
        TextView title = label("Все приложения", 24);
        title.setTypeface(null, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = new Button(this);
        close.setText("×");
        close.setContentDescription("Закрыть список приложений");
        close.setTextColor(HomeActivity.NEUTRAL_TEXT);
        close.setTextSize(22);
        close.setTypeface(null, Typeface.BOLD);
        close.setMinimumHeight(0);
        close.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                round(HomeActivity.NEUTRAL_RAISED, 20), null));
        close.setOnClickListener(v -> finish());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(64), dp(64)));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Поиск приложений");
        search.setTextColor(HomeActivity.NEUTRAL_TEXT);
        search.setHintTextColor(HomeActivity.NEUTRAL_MUTED);
        search.setTextSize(16);
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setBackground(round(HomeActivity.NEUTRAL_RAISED, 20));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(76));
        searchParams.setMargins(0, dp(8), 0, dp(18));
        content.addView(search, searchParams);
        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(ICON_DP + 64));
        grid.setHorizontalSpacing(dp(10));
        grid.setVerticalSpacing(dp(10));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalScrollBarEnabled(false);
        content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public Object getItem(int position) { return visible.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View old, ViewGroup parent) {
                View tile = appTile(visible.get(position));
                tile.setLayoutParams(new AbsListView.LayoutParams(-1, dp(ICON_DP + 64)));
                return tile;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(visible.get(position)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        setContentView(content);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(1200)),
                Math.min(getResources().getDisplayMetrics().heightPixels - dp(64), dp(1400)));
        // HOME keeps the loaded list; the process may also have been started by a service before HOME loaded it.
        if (HomeActivity.loadedApps != null) {
            apps = HomeActivity.loadedApps;
            filter();
        } else {
            new Thread(() -> {
                List<HomeActivity.AppEntry> loaded = HomeActivity.queryApps(this);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    apps = loaded;
                    filter();
                });
            }).start();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The panel button, the dock tile and the gesture act as a toggle.
        finish();
    }

    @Override protected void onStop() {
        super.onStop();
        // Covered by a launched app, HOME or Launcher3: the catalog does not wait in the background.
        if (!isChangingConfigurations()) finish();
    }

    @Override protected void onDestroy() {
        shown = false;
        super.onDestroy();
    }

    private void filter() {
        String query = search.getText().toString().toLowerCase(Locale.getDefault());
        visible.clear();
        for (HomeActivity.AppEntry app : apps)
            if (app.label.toLowerCase(Locale.getDefault()).contains(query)) visible.add(app);
        adapter.notifyDataSetChanged();
    }

    private void launch(HomeActivity.AppEntry app) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            finish();
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Не удалось открыть " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private View appTile(HomeActivity.AppEntry app) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(6), dp(6), dp(6), dp(6));
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                null, round(Color.WHITE, 20)));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)));
        TextView label = label(app.label, 14);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        tile.setContentDescription(app.label);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(40));
        labelParams.setMargins(0, dp(8), 0, 0);
        tile.addView(label, labelParams);
        return tile;
    }

    private TextView label(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(HomeActivity.NEUTRAL_TEXT);
        view.setTextSize(size);
        return view;
    }

    private GradientDrawable round(int fill, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
