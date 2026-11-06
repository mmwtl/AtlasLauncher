package com.mmwtl.atlaslauncher;

import android.Manifest;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Full-screen settings in the look of the other Atlas apps. They only write {@link HomeActivity#PREFS};
 * HOME applies every change itself.
 */
public final class SettingsActivity extends ScaledActivity {
    static final int PAGE_MAIN = 0;
    static final int PAGE_DOCK = 1;
    static final int PAGE_CLOCK = 2;
    static final int PAGE_VOLUME = 3;
    static final int PAGE_CONNECTIVITY = 4;
    static final int PAGE_TEXT = 5;
    // Swatches of the text widget's colors; any other color is entered as hex.
    private static final int[] TEXT_COLORS = {Color.BLACK, Color.WHITE, 0xFF9AA0A6, 0xFFE53935, 0xFFFB8C00,
            0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA};
    private static final String EXTRA_PAGE = "page";
    private static final String EXTRA_WIDGET = "widget";
    static final String TAB = "settings_tab";
    private static final String EDIT_NIGHT = "edit_night";
    private static final int[] TAB_LABELS = {R.string.settings_tab_wallpaper, R.string.settings_tab_desktop,
            R.string.settings_tab_gestures, R.string.settings_tab_system};
    private static final String GIB_LAUNCHER = "com.salat.gbinder/com.salat.gbinder.AppLauncher";
    /** Each entry: section title, then pairs of action and its name. */
    private static final String[][] GESTURE_ACTIONS = {
            {"Регулировка", "VOLUME", "Громкость", "TEMPERATURE_LEFT", "Температура водителя",
                    "TEMPERATURE_RIGHT", "Температура пассажира", "BRIGHTNESS", "Яркость", "FAN", "Обдув"},
            {"Система", GestureMap.HOME, "Главный экран", GestureMap.BACK, "Назад",
                    GestureMap.ALL_APPS, "Все приложения", GestureMap.RECENTS, "Недавние приложения",
                    GestureMap.NOTIFICATIONS, "Шторка", GestureMap.SCREENSHOT, "Снимок экрана",
                    GestureMap.WIFI, "Wi-Fi вкл/выкл", GestureMap.BLUETOOTH, "Bluetooth вкл/выкл"},
            {"Музыка", GestureMap.PLAY_PAUSE, "Пауза и продолжение", GestureMap.NEXT_TRACK, "Следующий трек",
                    GestureMap.PREVIOUS_TRACK, "Предыдущий трек", GestureMap.MUTE, "Выключить звук"},
            {"Климат", GestureMap.CLIMATE_SCREEN, "Экран климата", GestureMap.CLIMATE_PANEL, "Скрыть панель климата",
                    GestureMap.CLIMATE_POWER, "Климат вкл/выкл", GestureMap.CLIMATE_AUTO, "AUTO вкл/выкл",
                    GestureMap.AC, "Кондиционер вкл/выкл", GestureMap.RECIRCULATION, "Рециркуляция вкл/выкл",
                    GestureMap.DEFROST_FRONT, "Обдув лобового стекла", GestureMap.DEFROST_REAR, "Обогрев заднего стекла"},
    };
    private static final int PICK_WALLPAPER = 1;
    private static final int PICK_SLIDESHOW = 2;
    private static final int PICK_VIDEO = 3;
    private static final int PICK_BACKUP = 4;
    private static final int BACKUP_MAX_BYTES = 256 * 1024;
    private static final int LOCATION_PERMISSION = 4;

    private final List<TextView> tabs = new ArrayList<>();
    private final List<LinearLayout> panels = new ArrayList<>();
    private final ExecutorService imageLoader = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private ScrollView scroll;
    private ImageView wallpaperPreview;
    private TextView wallpaperKind;
    private int wallpaperPreviewGeneration;
    private List<HomeActivity.AppEntry> apps;
    private LinearLayout wallpaperPresets;
    private LinearLayout slidePresets;
    private Switch slideshowSwitch;
    private LinearLayout ownSlides;
    private LinearLayout modeChoice;
    private LinearLayout wallpaperCard;
    private LinearLayout slideshowCard;
    /** Whether the wallpaper tab edits the night settings; they may differ from the mode on screen. */
    private boolean editNight;
    private LinearLayout redirectState;
    private LinearLayout gestureRows;
    // Index of the finger count shown in the gesture table: two, three, four.
    private int gestureFingers;
    private Runnable connectivityChanged;
    /** The clock, volume, Wi-Fi/Bluetooth or text widget whose page is open. */
    private int widgetId;

    static void open(Context context, int page) {
        open(context, page, 0);
    }

    static void open(Context context, int page, int widgetId) {
        // A second tap before the settings cover HOME must not open them twice.
        context.startActivity(new Intent(context, SettingsActivity.class).putExtra(EXTRA_PAGE, page)
                .putExtra(EXTRA_WIDGET, widgetId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(HomeActivity.PREFS, MODE_PRIVATE);
        int page = getIntent().getIntExtra(EXTRA_PAGE, PAGE_MAIN);
        widgetId = getIntent().getIntExtra(EXTRA_WIDGET, 0);
        // A file picked after the mode switched, which recreates this activity, still goes to the mode being edited.
        editNight = savedInstanceState != null ? savedInstanceState.getBoolean(EDIT_NIGHT)
                : HomeActivity.nightWallpaper(this);

        LinearLayout root = column();
        root.setBackgroundColor(Ui.BACKGROUND);
        root.addView(buildHeader(page));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        // The OEM climate panel covers the bottom of com.geely.* windows without insets; the end of the list
        // scrolls above it.
        content.setPadding(dp(24), dp(8), dp(24), dp(24) + HomeActivity.CLIMATE_PANEL_PX);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        // Dock, clock, volume, Wi-Fi/Bluetooth and text are widgets: their pages open only from the widget's own settings button.
        if (page == PAGE_DOCK) buildDockPage(content);
        else if (page == PAGE_CLOCK) buildClockPage(content);
        else if (page == PAGE_VOLUME) buildVolumePage(content);
        else if (page == PAGE_CONNECTIVITY) buildConnectivityPage(content);
        else if (page == PAGE_TEXT) buildTextPage(content);
        else {
            buildWallpaperTab(page(content));
            buildDesktopTab(page(content));
            buildGesturesTab(page(content));
            buildSystemTab(page(content));
            showTab(prefs.getInt(TAB, 0));
        }
        setContentView(root);
        Ui.applySystemBarInsets(root);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(EDIT_NIGHT, editNight);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The accessibility service may have been enabled in Android settings.
        updateRedirectState();
    }

    @Override
    protected void onDestroy() {
        imageLoader.shutdownNow();
        super.onDestroy();
    }

    // ---- Разметка ----

    private View buildHeader(int page) {
        LinearLayout header = column();
        header.setPadding(dp(24), dp(16), dp(24), dp(12));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setBaselineAligned(false);
        int title = page == PAGE_DOCK ? R.string.settings_dock : page == PAGE_CLOCK ? R.string.settings_clock
                : page == PAGE_VOLUME ? R.string.settings_volume
                : page == PAGE_CONNECTIVITY ? R.string.settings_connectivity
                : page == PAGE_TEXT ? R.string.settings_text : R.string.app_name;
        titleRow.addView(Ui.heading(this, title, 24));
        TextView versionView = Ui.text(this, page == PAGE_MAIN ? version() : "", 12, Ui.TEXT_SECONDARY);
        versionView.setPadding(dp(8), dp(6), 0, 0);
        titleRow.addView(versionView, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView donate = new ImageView(this);
        donate.setImageResource(R.drawable.ic_heart);
        donate.setColorFilter(Ui.ACCENT);
        donate.setPadding(dp(8), dp(8), dp(8), dp(8));
        donate.setBackground(Ui.rounded(Ui.SURFACE, dp(8)));
        donate.setContentDescription(getString(R.string.donate_title));
        donate.setOnClickListener(v -> DonateDialog.show(this));
        LinearLayout.LayoutParams heart = new LinearLayout.LayoutParams(dp(40), dp(40));
        heart.leftMargin = dp(12);
        titleRow.addView(donate, heart);
        header.addView(titleRow);
        if (page != PAGE_MAIN) return header;

        LinearLayout tabBar = new LinearLayout(this);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            int index = i;
            TextView tab = Ui.segment(this, TAB_LABELS[i]);
            tab.setTextSize(15);
            tab.setOnClickListener(v -> showTab(index));
            Ui.addSegment(tabBar, tab);
            tabs.add(tab);
        }
        Ui.topMargin(tabBar, 12);
        header.addView(tabBar);
        return header;
    }

    private void buildWallpaperTab(LinearLayout page) {
        LinearLayout modes = card(page, "День и ночь",
                "Свой фон и сменяемый фон для дневного и ночного режима. Фон меняется вместе с темой лаунчера "
                        + "(вкладка «Рабочий стол»). "
                        + "Частота смены общая для обоих режимов.");
        toggle(modes, "Разные фоны днём и ночью", prefs.getBoolean(HomeActivity.WALLPAPER_BY_MODE, false), on -> {
            SharedPreferences.Editor edit = prefs.edit().putBoolean(HomeActivity.WALLPAPER_BY_MODE, on);
            // The first time the night starts as a copy of the current settings, so nothing is lost at dusk.
            if (on && !prefs.contains(HomeActivity.modeKey(HomeActivity.SLIDESHOW, true))) {
                for (String key : new String[]{HomeActivity.WALLPAPER, HomeActivity.VIDEO_WALLPAPER, HomeActivity.SLIDESHOW_IMAGES})
                    edit.putString(HomeActivity.modeKey(key, true), prefs.getString(key, null));
                edit.putBoolean(HomeActivity.modeKey(HomeActivity.SLIDESHOW, true), prefs.getBoolean(HomeActivity.SLIDESHOW, false));
            }
            edit.apply();
            editNight = HomeActivity.nightWallpaper(this);
            showEditedMode();
        });
        modeChoice = column();
        modes.addView(modeChoice);

        LinearLayout slideshow = card(page, "Сменяемый фон",
                "Новый фон проявляется плавно, поверх прежнего, чтобы не отвлекать в дороге.");
        slideshowCard = slideshow;
        LinearLayout options = column();
        slideshowSwitch = toggle(slideshow, "Менять фон", prefs.getBoolean(editKey(HomeActivity.SLIDESHOW), false), on -> {
            prefs.edit().putBoolean(editKey(HomeActivity.SLIDESHOW), on).apply();
            options.setVisibility(on ? View.VISIBLE : View.GONE);
            // A changing background replaces the single one: its card would only distract.
            wallpaperCard.setVisibility(on ? View.GONE : View.VISIBLE);
            if (on) {
                prefs.edit().remove(editKey(HomeActivity.VIDEO_WALLPAPER)).apply();
                releaseUnusedFiles();
                showWallpaperPreview();
            }
        });
        options.setVisibility(slideshowSwitch.isChecked() ? View.VISIBLE : View.GONE);
        slideshow.addView(options);
        options.addView(label("Изображения · отметьте фоны, которые будут сменять друг друга"));
        slidePresets = thumbnailRow(options);
        for (int i = 0; i < HomeActivity.PRESET_WALLPAPERS.length; i++) {
            String uri = HomeActivity.presetUri(this, HomeActivity.PRESET_WALLPAPERS[i]);
            ImageView thumbnail = thumbnail(slidePresets, uri, HomeActivity.PRESET_NAMES[i]);
            thumbnail.setOnClickListener(v -> {
                List<String> current = HomeActivity.slideshowImages(this, editNight);
                if (!current.remove(uri)) current.add(uri);
                HomeActivity.saveSlideshowImages(this, editNight, current);
                mark(thumbnail, current.contains(uri));
            });
        }
        ownSlides = column();
        options.addView(ownSlides);
        action(options, "Добавить свои изображения", () -> pickMedia(PICK_SLIDESHOW, true));
        options.addView(label("Как часто менять"));
        TextView interval = Ui.text(this, "", 14, Ui.TEXT);
        SeekBar intervalSlider = new SeekBar(this);
        intervalSlider.setContentDescription("Интервал смены фона");
        Runnable showInterval = () -> {
            int unit = prefs.getInt(HomeActivity.SLIDESHOW_UNIT, 0);
            interval.setVisibility(unit == 0 ? View.GONE : View.VISIBLE);
            intervalSlider.setVisibility(unit == 0 ? View.GONE : View.VISIBLE);
            if (unit == 0) return;
            int value = HomeActivity.slideshowInterval(prefs, unit);
            intervalSlider.setMax(HomeActivity.SLIDESHOW_MAX_INTERVAL[unit] - 1);
            intervalSlider.setProgress(value - 1);
            interval.setText(unit == 1 ? every(value, "минуту", "минуты", "минут") : every(value, "час", "часа", "часов"));
        };
        intervalSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (!user) return;
                prefs.edit().putInt(HomeActivity.SLIDESHOW_INTERVAL, progress + 1).apply();
                showInterval.run();
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }

            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        choice(options, HomeActivity.SLIDESHOW_UNIT, new String[]{"При включении", "Минуты", "Часы"}, 0, showInterval);
        options.addView(interval, margin(14, -2));
        options.addView(intervalSlider, margin(4, -2));
        showInterval.run();

        LinearLayout wallpaper = card(page, "Фон рабочего стола",
                "Изображение, GIF/WebP-анимация или видео. Видео повторяется без звука. "
                        + "Анимации воспроизводятся на Android 9 и новее. Фон заполняет экран с обрезкой по краям.");
        wallpaperCard = wallpaper;
        wallpaperKind = label("");
        wallpaper.addView(wallpaperKind);
        wallpaperPreview = new ImageView(this);
        wallpaperPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperPreview.setBackground(Ui.rounded(Ui.SURFACE_RAISED, dp(8)));
        wallpaperPreview.setClipToOutline(true);
        wallpaper.addView(wallpaperPreview, margin(12, dp(180)));
        wallpaper.addView(label("Встроенные фоны · листайте в сторону"));
        wallpaperPresets = thumbnailRow(wallpaper);
        for (int i = 0; i < HomeActivity.PRESET_WALLPAPERS.length; i++) {
            String uri = HomeActivity.presetUri(this, HomeActivity.PRESET_WALLPAPERS[i]);
            thumbnail(wallpaperPresets, uri, HomeActivity.PRESET_NAMES[i]).setOnClickListener(v ->
                    chooseWallpaper(uri));
        }
        action(wallpaper, "Выбрать изображение или анимацию", () -> pickMedia(PICK_WALLPAPER, false));
        action(wallpaper, "Выбрать видео", () -> pickMedia(PICK_VIDEO, false));
        action(wallpaper, "Вернуть стандартный фон", () -> chooseWallpaper(null));

        wallpaperCard.setVisibility(slideshowSwitch.isChecked() ? View.GONE : View.VISIBLE);
        showEditedMode();
    }

    private void buildDesktopTab(LinearLayout page) {
        LinearLayout theme = card(page, "Тема",
                "Дневная или ночная тема дока, встроенных виджетов, каталога приложений и подсказок жестов. "
                        + "«Авто» переключает её вместе с режимом системы. Другие виджеты выбирают тему в своих настройках.");
        // The theme is set on the resources when an activity starts; this one shows it right away too.
        choice(theme, ThemeMode.KEY, new String[]{"Авто", "День", "Ночь"}, ThemeMode.AUTO, this::recreate);

        LinearLayout desktops = card(page, "Рабочие столы",
                "Листайте столы свайпом и переносите виджеты к краю экрана. При выключении останется только "
                        + "основной стол. Виджеты на остальных сохранятся и появятся после включения.");
        toggle(desktops, "Несколько рабочих столов", HomeActivity.MULTIPLE_PAGES,
                HomeActivity.multiplePagesEnabled(this), () -> { });

        LinearLayout edit = card(page, "Вход в редактирование",
                "Сколько держать палец на рабочем столе, доке или виджете, чтобы начать перемещать и настраивать "
                        + "виджеты. Долгое удержание защищает от случайного входа в редактирование.");
        choice(edit, HomeActivity.EDIT_DELAY, new String[]{"Стандартно", "1 секунда", "2 секунды", "3 секунды"}, 0,
                () -> { });

        LinearLayout hide = card(page, "Скрытие виджетов",
                "Можно спрятать все виджеты, включая часы, док и громкость: останется только фон. "
                        + "Повторное действие или вход в редактирование возвращают их.");
        toggle(hide, "Скрывать двойным касанием", HomeActivity.HIDE_BY_DOUBLE_TAP, false, () -> { });
        toggle(hide, "Скрывать кнопкой «Домой» панели климата", HomeActivity.HIDE_BY_PANEL_HOME, false, () -> { });
        hide.addView(hint("На основном рабочем столе кнопка «Домой» прячет и возвращает виджеты. С другого стола "
                + "сначала возвращает на основной, а из приложения — показывает рабочий стол с виджетами. "
                + "Нужны включённый перехват «Домой» на вкладке «Система» и служба специальных возможностей."));

        LinearLayout margin = card(page, "Отступ виджетов по бокам",
                "Сужает сетку с обеих сторон. Виджеты, которые больше не помещаются, переносятся.");
        choice(margin, HomeActivity.WIDGET_SIDE_MARGIN, new String[]{"Нет", "½ ячейки", "1 ячейка"}, 0, () -> { });

        LinearLayout drawer = card(page, "Все приложения",
                "Что открывают кнопка «Все приложения» в доке и такая же кнопка на панели климата.");
        Button action = action(drawer, "", () -> { });
        Runnable refreshAction = () -> action.setText("Открывать: "
                + drawerActionLabel(prefs.getString(HomeActivity.DRAWER_ACTIVITY, "")));
        refreshAction.run();
        action.setOnClickListener(v -> chooseDrawerAction(refreshAction));
    }

    private void buildGesturesTab(LinearLayout page) {
        LinearLayout gestures = card(page, "Жесты",
                "Работают поверх любых приложений. Жест начинается между верхней и климатической панелями. "
                        + "Касания в этой области проходят через AtlasLauncher, а не через штатную службу жестов.");
        toggle(gestures, "Жесты несколькими пальцами", HomeActivity.GESTURES_ENABLED, false, () -> { });
        toggle(gestures, "Жесты важнее приложений", HomeActivity.GESTURES_PRIORITY, false, () -> { });
        gestures.addView(hint("Включено: касание несколькими пальцами сначала проверяется как жест, и приложение "
                + "не сдвигается под ним. Масштаб и поворот в картах начнутся немного позже, до полусекунды. "
                + "Выключено: приложение получает касание сразу, а при срабатывании жеста оно отменяется."));
        gestures.addView(hint("Нужно разрешение «Поверх других приложений»: без него не будет шкалы и карточек "
                + "со значениями, а жесты не смогут открывать экраны и приложения поверх другого приложения."));
        action(gestures, "Разрешить показ поверх приложений  ↗", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Настройка разрешения недоступна", Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout map = card(page, "Что делают жесты",
                "Нажмите на жест, чтобы выбрать действие. Регулировка, например громкость, занимает оба направления. "
                        + "Жест без действия остаётся у приложения.");
        gestureRows = column();
        map.addView(gestureRows);
        showGestureRows();
        action(map, "Вернуть жесты по умолчанию", () -> {
            prefs.edit().remove(HomeActivity.GESTURES_MAP).apply();
            showGestureRows();
        });

        LinearLayout trigger = card(page, "Срабатывание жестов",
                "Раньше: жест срабатывает после более короткого движения, но чаще перехватывает прокрутку "
                        + "и масштаб в приложениях. Позже: нужно движение длиннее.");
        choice(trigger, HomeActivity.GESTURES_TRIGGER, new String[]{"Раньше", "Средне", "Позже"},
                MultiFingerGestures.TRIGGER_NORMAL, () -> { });

        LinearLayout scale = card(page, "Шкала на весь экран",
                "После срабатывания жеста значение задаёт положение пальцев: внизу минимум, вверху максимум, "
                        + "при жесте вбок — слева и справа. Значение сразу переходит туда, где пальцы, поэтому начинайте "
                        + "жест примерно у нужного уровня. Выключенные регулировки меняются по шагам.");
        scaleToggle(scale, "Громкость", MultiFingerGestures.SCALE_VOLUME);
        scaleToggle(scale, "Температура", MultiFingerGestures.SCALE_TEMPERATURE);
        scaleToggle(scale, "Яркость", MultiFingerGestures.SCALE_BRIGHTNESS);
        scaleToggle(scale, "Обдув", MultiFingerGestures.SCALE_FAN);
        toggle(scale, "Рисовать шкалу поверх экрана", HomeActivity.GESTURES_SCALE_OVERLAY, true, () -> { });
        scale.addView(hint("Полупрозрачная шкала со значениями на весь экран, пока пальцы на нём."));

        LinearLayout car = card(page, "Управление автомобилем",
                "Температуре, яркости, обдуву и переключателям климата нужен доступ к автомобилю. Без GInputBridge "
                        + "AtlasLauncher обращается к функциям ГУ сам. Громкость и остальные действия от этого не зависят.");
        toggle(car, "Работать без GInputBridge", HomeActivity.GESTURES_DIRECT,
                GestureFilterService.directByDefault(this), () -> { });
        if (!DirectCar.available()) car.addView(hint("На этом устройстве нет функций ГУ: останется GInputBridge."));
    }

    private void scaleToggle(LinearLayout parent, String text, int bit) {
        toggle(parent, text, (prefs.getInt(HomeActivity.GESTURES_SCALE, 0) & bit) != 0, checked -> {
            int kinds = prefs.getInt(HomeActivity.GESTURES_SCALE, 0);
            prefs.edit().putInt(HomeActivity.GESTURES_SCALE, checked ? kinds | bit : kinds & ~bit).apply();
        });
    }

    private GestureMap gestureMap() {
        return GestureMap.parse(prefs.getString(HomeActivity.GESTURES_MAP, null));
    }

    /** The gestures of the chosen finger count, a row each; an adjustment holds both directions in one row. */
    private void showGestureRows() {
        gestureRows.removeAllViews();
        GestureMap map = gestureMap();
        choice(gestureRows, new String[]{"Два пальца", "Три пальца", "Четыре пальца"}, gestureFingers, index -> {
            gestureFingers = index;
            showGestureRows();
        });
        if (gestureFingers == 0) {
            gesturePair(map, "В центре экрана  ↑↓", "В центре экрана  ↑", GestureMap.Gesture.TWO_CENTER_UP,
                    "В центре экрана  ↓", GestureMap.Gesture.TWO_CENTER_DOWN);
            gesturePair(map, "У левого края  ↑↓", "У левого края  ↑", GestureMap.Gesture.TWO_LEFT_UP,
                    "У левого края  ↓", GestureMap.Gesture.TWO_LEFT_DOWN);
            gesturePair(map, "У правого края  ↑↓", "У правого края  ↑", GestureMap.Gesture.TWO_RIGHT_UP,
                    "У правого края  ↓", GestureMap.Gesture.TWO_RIGHT_DOWN);
            gesturePair(map, "Вбок  ←→", "Влево  ←", GestureMap.Gesture.TWO_SWIPE_LEFT,
                    "Вправо  →", GestureMap.Gesture.TWO_SWIPE_RIGHT);
        } else if (gestureFingers == 1) {
            gesturePair(map, "Вверх и вниз  ↑↓", "Вверх  ↑", GestureMap.Gesture.THREE_UP,
                    "Вниз  ↓", GestureMap.Gesture.THREE_DOWN);
            gesturePair(map, "Вбок  ←→", "Влево  ←", GestureMap.Gesture.THREE_LEFT,
                    "Вправо  →", GestureMap.Gesture.THREE_RIGHT);
            gestureRow(map, "Касание", GestureMap.Gesture.THREE_TAP);
        } else {
            gesturePair(map, "Вверх и вниз  ↑↓", "Вверх  ↑", GestureMap.Gesture.FOUR_UP,
                    "Вниз  ↓", GestureMap.Gesture.FOUR_DOWN);
            gesturePair(map, "Вбок  ←→", "Влево  ←", GestureMap.Gesture.FOUR_LEFT,
                    "Вправо  →", GestureMap.Gesture.FOUR_RIGHT);
            gestureRow(map, "Свести", GestureMap.Gesture.FOUR_PINCH);
            gestureRow(map, "Развести", GestureMap.Gesture.FOUR_SPREAD);
            gestureRow(map, "Касание", GestureMap.Gesture.FOUR_TAP);
        }
    }

    /** Opposite directions: one row while an adjustment takes both, a row each otherwise. */
    private void gesturePair(GestureMap map, String bothTitle, String firstTitle, GestureMap.Gesture first,
            String secondTitle, GestureMap.Gesture second) {
        String action = map.action(first);
        if (GestureMap.kind(action) != null && action.equals(map.action(second))) {
            gestureRow(map, bothTitle, first);
        } else {
            gestureRow(map, firstTitle, first);
            gestureRow(map, secondTitle, second);
        }
    }

    private void gestureRow(GestureMap map, String title, GestureMap.Gesture gesture) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(Ui.rounded(Ui.SURFACE_RAISED, dp(8)));
        row.setOnClickListener(v -> chooseGestureAction(gesture));
        row.addView(Ui.text(this, title, 15, Ui.TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = Ui.text(this, actionLabel(map.action(gesture)) + "  ›", 15,
                map.bound(gesture) ? Ui.ACCENT : Ui.TEXT_SECONDARY);
        value.setSingleLine();
        value.setEllipsize(TextUtils.TruncateAt.END);
        // A long app name must not squeeze out the gesture.
        value.setMaxWidth(dp(280));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = dp(12);
        row.addView(value, params);
        gestureRows.addView(row, margin(6, -2));
    }

    private String actionLabel(String action) {
        if (action.startsWith(GestureMap.APP_PREFIX)) {
            String component = action.substring(GestureMap.APP_PREFIX.length());
            for (HomeActivity.AppEntry app : apps()) {
                if (app.component.flattenToString().equals(component)) return app.label;
            }
            return "Приложение недоступно";
        }
        if (action.startsWith(GestureMap.ACTIVITY_PREFIX)) {
            return activityLabel(action.substring(GestureMap.ACTIVITY_PREFIX.length()));
        }
        for (String[] section : GESTURE_ACTIONS) {
            for (int i = 1; i < section.length; i += 2) {
                if (section[i].equals(action)) return section[i + 1];
            }
        }
        return "Ничего";
    }

    /** The action list, grouped; adjustments are offered only where the gesture can hold one. */
    private void chooseGestureAction(GestureMap.Gesture gesture) {
        LinearLayout list = column();
        list.setPadding(dp(24), dp(8), dp(24), dp(16));
        ScrollView scroller = new ScrollView(this);
        scroller.addView(list);
        String current = gestureMap().action(gesture);
        AlertDialog[] dialog = new AlertDialog[1];
        Consumer<String> choose = action -> {
            dialog[0].dismiss();
            setGestureAction(gesture, action);
        };
        // First, apart from the sections: removing a binding must be easy to find.
        list.addView(actionOption("Ничего — жест остаётся у приложения", GestureMap.NONE.equals(current),
                v -> choose.accept(GestureMap.NONE)), margin(6, -2));
        for (String[] section : GESTURE_ACTIONS) {
            boolean adjustments = GestureMap.kind(section[1]) != null;
            if (adjustments && !gesture.takesAdjustment()) continue;
            list.addView(label(section[0]));
            for (int i = 1; i < section.length; i += 2) {
                String action = section[i];
                list.addView(actionOption(section[i + 1], action.equals(current), v -> choose.accept(action)), margin(6, -2));
            }
        }
        list.addView(label("Приложение"));
        boolean app = current.startsWith(GestureMap.APP_PREFIX);
        list.addView(actionOption(app ? "Открыть: " + actionLabel(current) : "Открыть приложение…", app, v -> {
            dialog[0].dismiss();
            chooseGestureApp(gesture);
        }), margin(6, -2));
        boolean activity = current.startsWith(GestureMap.ACTIVITY_PREFIX);
        Consumer<ComponentName> setActivity = component ->
                setGestureAction(gesture, GestureMap.ACTIVITY_PREFIX + component.flattenToString());
        list.addView(actionOption(activity ? "Activity: " + actionLabel(current) : "Activity приложения…", activity, v -> {
            dialog[0].dismiss();
            chooseActivityPackage(setActivity);
        }), margin(6, -2));
        list.addView(actionOption("Указать Activity вручную…", false, v -> {
            dialog[0].dismiss();
            enterActivity(activity ? current.substring(GestureMap.ACTIVITY_PREFIX.length()) : "", setActivity);
        }), margin(6, -2));
        dialog[0] = new AlertDialog.Builder(this).setTitle("Действие жеста").setView(scroller)
                .setNegativeButton("Отмена", null).show();
        sized(dialog[0]);
    }

    private Button actionOption(String text, boolean current, View.OnClickListener click) {
        Button option = Ui.button(this, text);
        option.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        if (current) {
            option.setBackground(Ui.rounded(Ui.ACCENT, dp(8)));
            option.setTextColor(Ui.ON_ACCENT);
        }
        option.setOnClickListener(click);
        return option;
    }

    private void chooseGestureApp(GestureMap.Gesture gesture) {
        List<HomeActivity.AppEntry> entries = apps();
        String[] titles = new String[entries.size()];
        for (int i = 0; i < titles.length; i++) titles[i] = entries.get(i).label;
        show(new AlertDialog.Builder(this).setTitle("Открывать приложение").setItems(titles, (dialog, which) ->
                setGestureAction(gesture, GestureMap.APP_PREFIX + entries.get(which).component.flattenToString())));
    }

    private void setGestureAction(GestureMap.Gesture gesture, String action) {
        GestureMap map = gestureMap();
        map.set(gesture, action);
        prefs.edit().putString(HomeActivity.GESTURES_MAP, map.toString()).apply();
        showGestureRows();
    }

    private void buildSystemTab(LinearLayout page) {
        LinearLayout system = card(page, "Система", "Домашнее приложение и настройки Android.");
        action(system, "Настройки HOME в Android  ↗", () -> {
            try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "Настройки HOME недоступны", Toast.LENGTH_SHORT).show(); }
        });
        action(system, "Открыть штатный Launcher3  ↗", () -> {
            // OneOS Launcher3 has several LAUNCHER activities; the package launch intent may pick CarLink.
            Intent intent = new Intent(Intent.ACTION_MAIN).setClassName("com.android.launcher3", "com.android.launcher3.Launcher")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            StockHomeRedirectService.allowStockLauncherUntil = SystemClock.elapsedRealtime() + 5000;
            // After boot Launcher3 waits on the hidden display; this brings it to the main screen.
            try { startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()); }
            catch (ActivityNotFoundException | SecurityException e) { Toast.makeText(this, "Штатный Launcher3 недоступен", Toast.LENGTH_SHORT).show(); }
        });
        action(system, "Настройки устройства  ↗", () -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "Настройки недоступны", Toast.LENGTH_SHORT).show(); }
        });

        LinearLayout cluster = card(page, "Приборная панель",
                "Яркость приборки меняется вместе с яркостью экрана: ползунки в шторке встают на одно деление. "
                        + "Ползунок приборки по-прежнему работает, но до следующего изменения яркости экрана.");
        toggle(cluster, "Яркость как у экрана", HomeActivity.CLUSTER_BRIGHTNESS, false, () -> { });

        LinearLayout redirect = card(page, "Панель климата",
                "«Домой» и «Все приложения» на панели всегда открывают штатный Launcher3. AtlasLauncher может перехватывать "
                        + "их сразу по нажатию: «Домой» откроет рабочий стол Atlas, «Все приложения» — то же, что кнопка в доке, "
                        + "а при обеих включённых функциях — поверх текущего приложения. Когда функции выключены, служба не получает событий.");
        toggle(redirect, "Скрывать панель на главном экране", HomeActivity.CLIMATE_PANEL_HIDDEN, false, () -> { });
        toggle(redirect, "«Домой»: возвращать на AtlasLauncher", StockHomeRedirectService.ENABLED, false,
                this::updateRedirectState);
        toggle(redirect, "«Все приложения»: как кнопка в доке", StockHomeRedirectService.ALL_APPS_ENABLED, false,
                this::updateRedirectState);
        redirectState = column();
        redirectState.addView(hint(""));
        action(redirectState, "Специальные возможности Android  ↗", () -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "Специальные возможности недоступны", Toast.LENGTH_SHORT).show(); }
        });
        redirect.addView(redirectState);

        LinearLayout ui = card(page, "Интерфейс настроек",
                "Меняет размер этого экрана без изменения системного масштаба ГУ и рабочего стола.");
        LinearLayout scaleHead = new LinearLayout(this);
        scaleHead.addView(Ui.text(this, "Масштаб", 14, Ui.TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        TextView scaleValue = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        scaleHead.addView(scaleValue);
        ui.addView(scaleHead, margin(14, -2));
        SeekBar scale = new SeekBar(this);
        scale.setMin(MIN_SCALE_TENTHS);
        scale.setMax(MAX_SCALE_TENTHS);
        scale.setContentDescription("Масштаб");
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                scaleValue.setText(scaleText(value));
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }

            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (bar.getProgress() != configuredScaleTenths(SettingsActivity.this)) {
                    setScaleTenths(SettingsActivity.this, bar.getProgress());
                    recreate();
                }
            }
        });
        scale.setProgress(configuredScaleTenths(this));
        scaleValue.setText(scaleText(scale.getProgress()));
        ui.addView(scale, margin(4, -2));

        LinearLayout backup = card(page, "Импорт и экспорт настроек",
                "JSON содержит все настройки лаунчера: фон, рабочий стол с виджетами, док, жесты, встроенные виджеты, "
                        + "тему и масштаб. Экспорт отправляет файл через «Поделиться» — например, себе в Telegram. "
                        + "Разрешения не переносятся. Свои картинки и видео фона и виджеты приложений восстанавливаются, "
                        + "только пока они есть на этом ГУ: после переустановки виджеты приложений нужно добавить заново.");
        action(backup, "Экспортировать настройки в JSON", this::exportBackup);
        action(backup, "Импортировать настройки из JSON", this::chooseBackup);

        LinearLayout gstore = card(page, getString(R.string.gstore_title), getString(R.string.gstore_hint));
        action(gstore, getString(R.string.gstore_button), () -> GStoreDialog.show(this));
        LinearLayout support = card(page, getString(R.string.donate_title), getString(R.string.donate_hint));
        action(support, getString(R.string.donate_button), () -> DonateDialog.show(this));
    }

    private void buildDockPage(LinearLayout parent) {
        LinearLayout look = card(parent, "Внешний вид", "Размер значков, плотность и фон дока на рабочем столе.");
        toggle(look, "Подписи в доке", HomeActivity.DOCK_LABELS, true, () -> { });
        look.addView(Ui.text(this, "Размер значков", 14, Ui.TEXT), margin(14, -2));
        int size = Math.max(40, Math.min(HomeActivity.MAX_ICON_DP, prefs.getInt(HomeActivity.DRAWER_ICON_SIZE, 64)));
        SeekBar sizeSlider = new SeekBar(this);
        sizeSlider.setMax((HomeActivity.MAX_ICON_DP - 40) / 8);
        sizeSlider.setProgress((size - 40) / 8);
        sizeSlider.setContentDescription("Размер значков дока");
        sizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (user) prefs.edit().putInt(HomeActivity.DRAWER_ICON_SIZE, 40 + progress * 8).apply();
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }

            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        look.addView(sizeSlider, margin(4, -2));
        opacitySlider(look, HomeActivity.DOCK_OPACITY, () -> { });

        LinearLayout drawer = card(parent, "Все приложения",
                "Что открывает кнопка, выбирается в общих настройках AtlasLauncher.");
        toggle(drawer, "Показывать кнопку", HomeActivity.DOCK_APPS, true, () -> { });

        LinearLayout pinned = card(parent, "Закреплённые приложения", "Стрелки меняют порядок, × убирает приложение из дока.");
        LinearLayout selectedApps = column();
        pinned.addView(selectedApps);
        showFavorites(selectedApps);
        action(pinned, "＋  Добавить приложение", () -> pickFavorite(selectedApps));
    }

    private void buildClockPage(LinearLayout content) {
        LinearLayout preview = card(content, "Предпросмотр", "Параметры применяются к часам на рабочем столе.");
        HomeActivity.WidgetPlacement previewPlacement = new HomeActivity.WidgetPlacement(widgetId, 0, 0, 280, 192);
        previewPlacement.clockSize = 64;
        View clock = BuiltInWidgets.createClock(this, previewPlacement);
        preview.addView(clock, margin(12, dp(156)));
        Runnable changed = () -> BuiltInWidgets.updateClock(this, clock, previewPlacement);
        toggle(preview, "Показывать дату", key(BuiltInWidgets.CLOCK_DATE), true, changed);
        preview.addView(label("Формат времени"));
        choice(preview, key(BuiltInWidgets.CLOCK_FORMAT), new String[]{"Как в системе", "24 часа", "12 часов"}, 0, changed);
        preview.addView(label("Начертание"));
        choice(preview, key(BuiltInWidgets.CLOCK_WEIGHT), new String[]{"Тонкий", "Обычный", "Жирный"}, 0, changed);
        preview.addView(label("Шрифт"));
        choice(preview, key(BuiltInWidgets.CLOCK_FONT), new String[]{"Без засечек", "С засечками", "Моноширинный"}, 0, changed);
    }

    private void buildVolumePage(LinearLayout content) {
        LinearLayout preview = card(content, "Предпросмотр",
                "Кнопки встают в ряд у широкого виджета и в столбик у высокого.");
        HomeActivity.WidgetPlacement previewPlacement = new HomeActivity.WidgetPlacement(widgetId, 0, 0, 288, 96);
        View volume = BuiltInWidgets.createVolume(this, previewPlacement);
        // Only shows the style: its buttons would change the car's volume.
        FrameLayout frame = new FrameLayout(this) {
            @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }
        };
        frame.addView(volume, new FrameLayout.LayoutParams(-1, -1));
        preview.addView(frame, margin(12, dp(96)));
        Runnable changed = () -> BuiltInWidgets.updateVolume(this, volume, previewPlacement);
        toggle(preview, "Кнопка отключения звука", key(BuiltInWidgets.VOLUME_MUTE), true, changed);
        toggle(preview, "Одним блоком", key(BuiltInWidgets.VOLUME_JOINED), false, changed);
        preview.addView(label("Значки"));
        choice(preview, key(BuiltInWidgets.VOLUME_ICONS), new String[]{"Динамик", "Плюс и минус", "Стрелки"}, 0, changed);
        opacitySlider(preview, key(BuiltInWidgets.VOLUME_OPACITY), changed);
    }

    private void buildConnectivityPage(LinearLayout content) {
        LinearLayout preview = card(content, "Предпросмотр",
                "У широкой плитки подпись стоит рядом со значком, у узкой — под ним.");
        HomeActivity.WidgetPlacement previewPlacement = new HomeActivity.WidgetPlacement(widgetId, 0, 0, 384, 96);
        View connectivity = BuiltInWidgets.createConnectivity(this, previewPlacement);
        // Only shows the style: its tiles would switch the car's radios.
        FrameLayout frame = new FrameLayout(this) {
            @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }
        };
        frame.addView(connectivity, new FrameLayout.LayoutParams(-1, -1));
        preview.addView(frame, margin(12, dp(96)));
        Runnable changed = () -> BuiltInWidgets.updateConnectivity(this, connectivity, previewPlacement);
        connectivityChanged = changed;
        Switch[] shown = new Switch[3];
        String[] keys = {key(BuiltInWidgets.CONNECTIVITY_WIFI), key(BuiltInWidgets.CONNECTIVITY_BLUETOOTH),
                key(BuiltInWidgets.CONNECTIVITY_HOTSPOT)};
        String[] names = {"Wi-Fi", "Bluetooth", "Точка доступа"};
        for (int i = 0; i < 3; i++) {
            int index = i;
            shown[i] = toggle(preview, names[i], prefs.getBoolean(keys[i], i < 2), checked -> {
                // At least one tile stays: an empty widget could not be found to remove.
                if (!checked && Arrays.stream(shown).noneMatch(Switch::isChecked)) {
                    shown[index].setChecked(true);
                    return;
                }
                prefs.edit().putBoolean(keys[index], checked).apply();
                changed.run();
            });
        }
        preview.addView(label("Подпись"));
        choice(preview, key(BuiltInWidgets.CONNECTIVITY_STYLE), new String[]{"Только значки", "Вкл и выкл", "Сеть и устройство"},
                BuiltInWidgets.STYLE_STATE, () -> {
                    changed.run();
                    if (prefs.getInt(key(BuiltInWidgets.CONNECTIVITY_STYLE), BuiltInWidgets.STYLE_STATE) == BuiltInWidgets.STYLE_NETWORK
                            && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION);
                });
        preview.addView(Ui.text(this, "Название сети Wi-Fi Android показывает только с доступом к местоположению "
                + "и при включённой геолокации; без них плитка пишет «Подключено». Точке доступа нужно разрешение "
                + "на изменение системных настроек: его попросят при первом нажатии.", 13, Ui.TEXT_SECONDARY), margin(10, -2));
        opacitySlider(preview, key(BuiltInWidgets.CONNECTIVITY_OPACITY), changed);
    }

    private void buildTextPage(LinearLayout content) {
        LinearLayout preview = card(content, "Предпросмотр", "Днём текст показывается дневным цветом, ночью — ночным.");
        HomeActivity.WidgetPlacement previewPlacement = new HomeActivity.WidgetPlacement(widgetId, 0, 0, 288, 96);
        Context[] modes = {modeContext(false), modeContext(true)};
        View[] texts = new View[2];
        for (int i = 0; i < 2; i++) {
            preview.addView(label(i == 0 ? "День" : "Ночь"));
            texts[i] = BuiltInWidgets.createText(modes[i], previewPlacement);
            preview.addView(texts[i], margin(8, -2));
        }
        Runnable changed = () -> {
            for (int i = 0; i < 2; i++) BuiltInWidgets.updateText(modes[i], texts[i], previewPlacement);
        };
        EditText input = new EditText(this);
        input.setText(prefs.getString(key(BuiltInWidgets.TEXT_CONTENT), BuiltInWidgets.TEXT_DEFAULT));
        input.setTextColor(Ui.TEXT);
        input.setTextSize(16);
        input.setHint("Текст виджета");
        input.setHintTextColor(Ui.TEXT_SECONDARY);
        input.setBackground(Ui.rounded(Ui.SURFACE_RAISED, dp(12)));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }

            @Override public void afterTextChanged(Editable text) {
                prefs.edit().putString(key(BuiltInWidgets.TEXT_CONTENT), text.toString()).apply();
                changed.run();
            }
        });
        preview.addView(label("Текст"));
        preview.addView(input, margin(10, -2));

        LinearLayout look = card(content, "Шрифт", null);
        look.addView(label("Начертание"));
        choice(look, key(BuiltInWidgets.TEXT_WEIGHT), new String[]{"Тонкий", "Обычный", "Жирный"}, 1, changed);
        look.addView(label("Шрифт"));
        choice(look, key(BuiltInWidgets.TEXT_FONT), new String[]{"Без засечек", "С засечками", "Моноширинный"}, 0, changed);
        LinearLayout head = new LinearLayout(this);
        head.addView(Ui.text(this, "Размер", 14, Ui.TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        head.addView(value);
        look.addView(head, margin(14, -2));
        // 12 to 120 sp in steps of 4.
        SeekBar size = new SeekBar(this);
        size.setMax(27);
        size.setProgress((Math.max(12, Math.min(120, prefs.getInt(key(BuiltInWidgets.TEXT_SIZE), BuiltInWidgets.TEXT_DEFAULT_SIZE))) - 12) / 4);
        size.setContentDescription("Размер текста");
        value.setText(String.valueOf(12 + size.getProgress() * 4));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                value.setText(String.valueOf(12 + progress * 4));
                if (!user) return;
                prefs.edit().putInt(key(BuiltInWidgets.TEXT_SIZE), 12 + progress * 4).apply();
                changed.run();
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }

            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        look.addView(size, margin(4, -2));

        LinearLayout colors = card(content, "Цвет и подложка", null);
        colors.addView(label("Цвет днём"));
        colorChoice(colors, key(BuiltInWidgets.TEXT_COLOR_DAY), Color.BLACK, changed);
        colors.addView(label("Цвет ночью"));
        colorChoice(colors, key(BuiltInWidgets.TEXT_COLOR_NIGHT), Color.WHITE, changed);
        opacitySlider(colors, key(BuiltInWidgets.TEXT_OPACITY), changed);
    }

    /** A context of this activity with the day or the night resources, for a preview of that mode. */
    private Context modeContext(boolean night) {
        Configuration config = new Configuration(getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return createConfigurationContext(config);
    }

    /** A row of color swatches and a button for any other color; the color is saved under the key. */
    private void colorChoice(LinearLayout parent, String key, int defaultColor, Runnable changed) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        List<View> swatches = new ArrayList<>();
        TextView other = Ui.text(this, "#", 18, Ui.TEXT);
        other.setGravity(Gravity.CENTER);
        other.setContentDescription("Другой цвет");
        Runnable mark = () -> {
            int color = prefs.getInt(key, defaultColor);
            boolean known = false;
            for (int i = 0; i < TEXT_COLORS.length; i++) {
                boolean selected = TEXT_COLORS[i] == color;
                known |= selected;
                swatches.get(i).setBackground(swatch(TEXT_COLORS[i], selected));
            }
            other.setBackground(swatch(known ? Ui.SURFACE_RAISED : color, !known));
            other.setTextColor(known || Color.luminance(color) < 0.5f ? Ui.TEXT : Color.BLACK);
        };
        Consumer<Integer> choose = color -> {
            prefs.edit().putInt(key, color).apply();
            mark.run();
            changed.run();
        };
        for (int color : TEXT_COLORS) {
            View swatch = new View(this);
            swatch.setContentDescription(String.format("#%06X", color & 0xFFFFFF));
            swatch.setOnClickListener(v -> choose.accept(color));
            swatches.add(swatch);
            row.addView(swatch, swatchParams());
        }
        other.setOnClickListener(v -> enterColor(prefs.getInt(key, defaultColor), choose));
        row.addView(other, swatchParams());
        mark.run();
        parent.addView(row, margin(10, -2));
    }

    private LinearLayout.LayoutParams swatchParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(40), dp(40));
        params.rightMargin = dp(8);
        return params;
    }

    private GradientDrawable swatch(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(dp(selected ? 3 : 1), selected ? Ui.ACCENT : Ui.SURFACE_RAISED);
        return drawable;
    }

    private void enterColor(int current, Consumer<Integer> chosen) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("RRGGBB");
        input.setText(String.format("%06X", current & 0xFFFFFF));
        input.setPadding(dp(24), dp(16), dp(24), dp(16));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Цвет текста")
                .setMessage("Шестнадцатеричный код цвета, например FF8800.")
                .setView(input).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String hex = input.getText().toString().trim().replaceFirst("^#", "");
            if (!hex.matches("[0-9A-Fa-f]{6}")) {
                input.setError("Формат: RRGGBB");
                return;
            }
            chosen.accept(0xFF000000 | Integer.parseInt(hex, 16));
            dialog.dismiss();
        }));
        dialog.show();
        sized(dialog);
    }

    /** Opacity of the widget cards in steps of 5%. */
    private void opacitySlider(LinearLayout parent, String key, Runnable changed) {
        LinearLayout head = new LinearLayout(this);
        head.addView(Ui.text(this, "Непрозрачность фона", 14, Ui.TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        head.addView(value);
        parent.addView(head, margin(14, -2));
        SeekBar slider = new SeekBar(this);
        slider.setMax(20);
        slider.setProgress(Math.max(0, Math.min(100, prefs.getInt(key, 100))) / 5);
        slider.setContentDescription("Непрозрачность фона");
        value.setText(slider.getProgress() * 5 + "%");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                value.setText(progress * 5 + "%");
                if (!user) return;
                prefs.edit().putInt(key, progress * 5).apply();
                changed.run();
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }

            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(slider, margin(4, -2));
    }

    /** Settings key of the widget whose page is open. */
    private String key(String base) {
        return BuiltInWidgets.key(base, widgetId);
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        if (request == LOCATION_PERMISSION && connectivityChanged != null) connectivityChanged.run();
    }

    // ---- Фон ----

    private String editKey(String key) {
        return HomeActivity.modeKey(key, editNight);
    }

    /** Shows the settings of the mode being edited on the wallpaper tab. */
    private void showEditedMode() {
        boolean byMode = prefs.getBoolean(HomeActivity.WALLPAPER_BY_MODE, false);
        modeChoice.removeAllViews();
        if (byMode) {
            modeChoice.addView(label("Какой режим настроить"));
            choice(modeChoice, new String[]{"День", "Ночь"}, editNight ? 1 : 0, index -> {
                editNight = index == 1;
                showEditedMode();
            });
        }
        // The cards are long: each one names the mode it changes.
        String mode = !byMode ? "" : editNight ? " · ночь" : " · день";
        ((TextView) wallpaperCard.getChildAt(0)).setText("Фон рабочего стола" + mode);
        ((TextView) slideshowCard.getChildAt(0)).setText("Сменяемый фон" + mode);
        slideshowSwitch.setChecked(prefs.getBoolean(editKey(HomeActivity.SLIDESHOW), false));
        List<String> slides = HomeActivity.slideshowImages(this, editNight);
        for (int i = 0; i < slidePresets.getChildCount(); i++) {
            View thumbnail = slidePresets.getChildAt(i);
            mark(thumbnail, slides.contains(thumbnail.getTag()));
        }
        showOwnSlides();
        showWallpaperPreview();
    }

    private void chooseWallpaper(String uri) {
        SharedPreferences.Editor edit = prefs.edit().putBoolean(editKey(HomeActivity.SLIDESHOW), false)
                .remove(editKey(HomeActivity.VIDEO_WALLPAPER));
        (uri == null ? edit.remove(editKey(HomeActivity.WALLPAPER)) : edit.putString(editKey(HomeActivity.WALLPAPER), uri)).apply();
        slideshowSwitch.setChecked(false);
        releaseUnusedFiles();
        showWallpaperPreview();
    }

    /** Gives up access to picked files no background uses any more; Android keeps only so many grants. */
    private void releaseUnusedFiles() {
        List<String> used = new ArrayList<>();
        // Both modes count: the night settings wait for their mode, and for the switch when it is off.
        for (boolean night : new boolean[]{false, true}) {
            used.addAll(HomeActivity.slideshowImages(this, night));
            used.add(prefs.getString(HomeActivity.modeKey(HomeActivity.WALLPAPER, night), null));
            used.add(prefs.getString(HomeActivity.modeKey(HomeActivity.VIDEO_WALLPAPER, night), null));
        }
        for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (used.contains(permission.getUri().toString())) continue;
            try {
                getContentResolver().releasePersistableUriPermission(permission.getUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // The permission is already gone.
            }
        }
    }

    private void showWallpaperPreview() {
        int generation = ++wallpaperPreviewGeneration;
        String saved = prefs.getString(editKey(HomeActivity.WALLPAPER), null);
        String video = prefs.getString(editKey(HomeActivity.VIDEO_WALLPAPER), null);
        wallpaperKind.setText(video == null ? "Изображение или анимация" : "Видео · повтор без звука");
        for (int i = 0; i < wallpaperPresets.getChildCount(); i++) {
            View thumbnail = wallpaperPresets.getChildAt(i);
            mark(thumbnail, video == null && thumbnail.getTag().equals(saved));
        }
        int maxSize = dp(720);
        imageLoader.execute(() -> {
            Bitmap bitmap = video != null ? VideoWallpaperView.preview(this, Uri.parse(video), maxSize)
                    : saved == null ? null : HomeActivity.decodeWallpaper(this, Uri.parse(saved), maxSize);
            runOnUiThread(() -> {
                if (isDestroyed() || generation != wallpaperPreviewGeneration) return;
                if (bitmap != null) wallpaperPreview.setImageBitmap(bitmap);
                else wallpaperPreview.setImageResource(R.drawable.coastal_twilight);
                if (video != null && bitmap == null) wallpaperKind.setText("Видео · предпросмотр недоступен");
            });
        });
    }

    private void showOwnSlides() {
        ownSlides.removeAllViews();
        List<String> slides = HomeActivity.slideshowImages(this, editNight);
        if (slides.stream().allMatch(HomeActivity::isPresetUri)) return;
        ownSlides.addView(label("Свои изображения · нажмите, чтобы убрать"));
        LinearLayout own = thumbnailRow(ownSlides);
        for (String uri : slides) {
            if (HomeActivity.isPresetUri(uri)) continue;
            ImageView thumbnail = thumbnail(own, uri, "Своё изображение");
            mark(thumbnail, true);
            thumbnail.setOnClickListener(v -> {
                List<String> current = HomeActivity.slideshowImages(this, editNight);
                current.remove(uri);
                HomeActivity.saveSlideshowImages(this, editNight, current);
                showOwnSlides();
                // The wallpaper on screen keeps its image until the slideshow replaces it.
                releaseUnusedFiles();
            });
        }
    }

    private void pickMedia(int request, boolean multiple) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(request == PICK_VIDEO ? "video/*" : "image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        try { startActivityForResult(intent, request); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "Выбор файла недоступен", Toast.LENGTH_SHORT).show(); }
    }

    /** As in the Atlas widgets: a dated file in the cache, sent through the share sheet. */
    private void exportBackup() {
        String name = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date())
                + "_" + SettingsBackup.FILE_NAME;
        Uri uri;
        try {
            uri = BackupProvider.publish(this, name, SettingsBackup.read(this).toJson(appVersion()));
        } catch (IOException | RuntimeException e) {
            Log.w("AtlasLauncher", "Cannot export settings", e);
            Toast.makeText(this, "Не удалось экспортировать настройки", Toast.LENGTH_LONG).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND).setType(SettingsBackup.MIME)
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(name, uri));
        try {
            startActivity(Intent.createChooser(send, "Экспорт настроек").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Нет приложений, которые могут принять файл", Toast.LENGTH_LONG).show();
        }
    }

    private String appVersion() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** The system document picker, any type: JSON sent through messengers often arrives without one. */
    private void chooseBackup() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{SettingsBackup.MIME, "application/octet-stream",
                        "text/plain", "*/*"});
        try { startActivityForResult(picker, PICK_BACKUP); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "В прошивке нет системного выбора файлов", Toast.LENGTH_LONG).show(); }
    }

    private void readBackup(Uri uri) {
        String text;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open " + uri);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > BACKUP_MAX_BYTES) throw new IOException("Settings file is larger than 256 KB");
            }
            text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Log.w("AtlasLauncher", "Cannot read settings " + uri, e);
            Toast.makeText(this, "Не удалось прочитать настройки", Toast.LENGTH_LONG).show();
            return;
        }
        SettingsBackup backup = SettingsBackup.parse(text);
        if (backup == null) {
            Toast.makeText(this, "Это не файл настроек AtlasLauncher", Toast.LENGTH_LONG).show();
            return;
        }
        int lost = backup.widgets.size() - backup.restorableWidgets(this).size();
        show(new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Импортировать настройки?")
                .setMessage("Текущие настройки и рабочий стол будут заменены настройками из файла. Виджетов в файле — "
                        + backup.widgets.size() + (lost > 0 ? ", из них " + lost + " не восстановятся: таких виджетов "
                        + "приложений нет на этом ГУ, их нужно добавить заново." : "."))
                .setPositiveButton("Импортировать", (dialog, which) -> {
                    backup.apply(this);
                    releaseUnusedFiles();
                    Toast.makeText(this, "Настройки импортированы", Toast.LENGTH_SHORT).show();
                    // The theme and the interface scale may have changed too.
                    recreate();
                })
                .setNegativeButton("Отмена", null));
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if (request == PICK_BACKUP && data.getData() != null) {
            readBackup(data.getData());
            return;
        }
        if ((request == PICK_WALLPAPER || request == PICK_VIDEO) && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (request == PICK_VIDEO) {
                    prefs.edit().putString(editKey(HomeActivity.VIDEO_WALLPAPER), uri.toString())
                            .putBoolean(editKey(HomeActivity.SLIDESHOW), false).apply();
                    slideshowSwitch.setChecked(false);
                    releaseUnusedFiles();
                    showWallpaperPreview();
                } else chooseWallpaper(uri.toString());
            } catch (SecurityException e) {
                Toast.makeText(this, "Нет доступа к файлу", Toast.LENGTH_SHORT).show();
            }
        } else if (request == PICK_SLIDESHOW) {
            ClipData clip = data.getClipData();
            int count = clip != null ? clip.getItemCount() : data.getData() != null ? 1 : 0;
            List<String> slides = HomeActivity.slideshowImages(this, editNight);
            boolean denied = false;
            for (int i = 0; i < count; i++) {
                Uri uri = clip != null ? clip.getItemAt(i).getUri() : data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    if (!slides.contains(uri.toString())) slides.add(uri.toString());
                } catch (SecurityException e) {
                    denied = true;
                }
            }
            HomeActivity.saveSlideshowImages(this, editNight, slides);
            if (denied) Toast.makeText(this, "Нет доступа к части изображений", Toast.LENGTH_SHORT).show();
            showOwnSlides();
        }
    }

    private LinearLayout thumbnailRow(LinearLayout parent) {
        HorizontalScrollView row = new HorizontalScrollView(this);
        row.setHorizontalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        row.addView(content);
        parent.addView(row, margin(8, -2));
        return content;
    }

    private ImageView thumbnail(LinearLayout row, String uri, String name) {
        ImageView thumbnail = new ImageView(this);
        // Twice the thumbnail height at most: power-of-two sampling keeps it at least as large as the tile.
        int maxSize = dp(192);
        imageLoader.execute(() -> {
            Bitmap bitmap = HomeActivity.decodeWallpaper(this, Uri.parse(uri), maxSize);
            runOnUiThread(() -> thumbnail.setImageBitmap(bitmap));
        });
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setContentDescription(name);
        thumbnail.setBackground(Ui.rounded(Ui.SURFACE_RAISED, dp(8)));
        thumbnail.setClipToOutline(true);
        thumbnail.setTag(uri);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(72), dp(96));
        params.rightMargin = dp(8);
        row.addView(thumbnail, params);
        return thumbnail;
    }

    private void mark(View thumbnail, boolean marked) {
        GradientDrawable frame = null;
        if (marked) {
            frame = Ui.rounded(Color.TRANSPARENT, dp(8));
            frame.setStroke(dp(3), Ui.ACCENT);
        }
        thumbnail.setForeground(frame);
    }

    // ---- Док и «Все приложения» ----

    private List<HomeActivity.AppEntry> apps() {
        // Querying loads every icon; do it at most once if HOME has not loaded the apps yet.
        if (apps == null) apps = HomeActivity.loadedApps != null ? HomeActivity.loadedApps : HomeActivity.queryApps(this);
        return apps;
    }

    private List<String> favorites() {
        List<String> favorites = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs.getString(HomeActivity.FAVORITES, "[]"));
            for (int i = 0; i < saved.length(); i++) favorites.add(saved.getString(i));
        } catch (JSONException e) {
            favorites.clear();
        }
        return favorites;
    }

    private void saveFavorites(List<String> favorites, LinearLayout selectedApps) {
        prefs.edit().putString(HomeActivity.FAVORITES, new JSONArray(favorites).toString()).apply();
        showFavorites(selectedApps);
    }

    private void showFavorites(LinearLayout selectedApps) {
        selectedApps.removeAllViews();
        List<String> favorites = favorites();
        if (favorites.isEmpty()) {
            selectedApps.addView(hint("Нет закреплённых приложений"));
            return;
        }
        List<HomeActivity.AppEntry> apps = apps();
        for (int i = 0; i < favorites.size(); i++) {
            String flattened = favorites.get(i);
            HomeActivity.AppEntry app = apps.stream()
                    .filter(entry -> entry.component.flattenToString().equals(flattened)).findFirst().orElse(null);
            String title = app != null ? app.label
                    : ComponentName.unflattenFromString(flattened).getPackageName() + " · недоступно";
            int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this);
            if (app != null) icon.setImageDrawable(app.icon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
            TextView name = Ui.text(this, title, 15, app != null ? Ui.TEXT : Ui.TEXT_SECONDARY);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
            nameParams.leftMargin = dp(12);
            row.addView(name, nameParams);
            row.addView(rowButton("←", "Передвинуть " + title + " влево", i > 0, () -> {
                Collections.swap(favorites, index, index - 1);
                saveFavorites(favorites, selectedApps);
            }));
            row.addView(rowButton("→", "Передвинуть " + title + " вправо", i < favorites.size() - 1, () -> {
                Collections.swap(favorites, index, index + 1);
                saveFavorites(favorites, selectedApps);
            }));
            row.addView(rowButton("×", "Убрать " + title + " из дока", true, () -> {
                favorites.remove(index);
                saveFavorites(favorites, selectedApps);
            }));
            selectedApps.addView(row, margin(8, -2));
        }
    }

    private Button rowButton(String text, String description, boolean enabled, Runnable action) {
        Button button = Ui.button(this, text);
        button.setTextSize(18);
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription(description);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1 : 0.4f);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
        params.leftMargin = dp(6);
        button.setLayoutParams(params);
        return button;
    }

    private void pickFavorite(LinearLayout selectedApps) {
        List<String> favorites = favorites();
        List<HomeActivity.AppEntry> available = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (HomeActivity.AppEntry app : apps()) {
            if (favorites.contains(app.component.flattenToString())) continue;
            available.add(app);
            names.add(app.label + " · " + app.component.getPackageName());
        }
        if (available.isEmpty()) {
            Toast.makeText(this, "Все приложения уже добавлены", Toast.LENGTH_SHORT).show();
            return;
        }
        show(new AlertDialog.Builder(this).setTitle("Добавить в док")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    favorites.add(available.get(which).component.flattenToString());
                    saveFavorites(favorites, selectedApps);
                }));
    }

    private String drawerActionLabel(String target) {
        if (target.isEmpty()) return "встроенный каталог";
        if (target.equals(GIB_LAUNCHER)) return "GLauncher (GIB)";
        return activityLabel(target);
    }

    /** "App · Activity" of a flattened component, or the component itself when it is not installed. */
    private String activityLabel(String target) {
        ComponentName component = ComponentName.unflattenFromString(target);
        if (component == null) return target;
        PackageManager pm = getPackageManager();
        try {
            ActivityInfo activity = pm.getActivityInfo(component, 0);
            String app = String.valueOf(activity.applicationInfo.loadLabel(pm));
            String name = String.valueOf(activity.loadLabel(pm));
            return name.equals(app) ? app : app + " · " + name;
        } catch (PackageManager.NameNotFoundException e) {
            return target + " · недоступно";
        }
    }

    private void chooseDrawerAction(Runnable onChanged) {
        String[] choices = {"Встроенный каталог", "GLauncher (GIB)", "Activity установленного приложения", "Указать Activity вручную"};
        show(new AlertDialog.Builder(this).setTitle("Открывать по кнопке «Все приложения»")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        prefs.edit().remove(HomeActivity.DRAWER_ACTIVITY).apply();
                        onChanged.run();
                    } else if (which == 1) {
                        prefs.edit().putString(HomeActivity.DRAWER_ACTIVITY, GIB_LAUNCHER).apply();
                        onChanged.run();
                    } else if (which == 2) {
                        chooseActivityPackage(component -> saveDrawerActivity(component, onChanged));
                    } else {
                        enterActivity(prefs.getString(HomeActivity.DRAWER_ACTIVITY, ""),
                                component -> saveDrawerActivity(component, onChanged));
                    }
                }));
    }

    private void saveDrawerActivity(ComponentName component, Runnable onChanged) {
        prefs.edit().putString(HomeActivity.DRAWER_ACTIVITY, component.flattenToString()).apply();
        onChanged.run();
    }

    /** Every package with an Activity we may start, not only those in the catalog: OEM screens often have no launcher icon. */
    private void chooseActivityPackage(Consumer<ComponentName> chosen) {
        PackageManager pm = getPackageManager();
        imageLoader.execute(() -> {
            List<String[]> found = new ArrayList<>();
            for (ApplicationInfo app : pm.getInstalledApplications(0)) {
                List<ActivityInfo> activities = launchableActivities(app.packageName);
                if (activities != null && !activities.isEmpty()) {
                    String label = app.loadLabel(pm).toString().trim();
                    found.add(new String[] {label.isEmpty() ? app.packageName : label, app.packageName});
                }
            }
            Collator collator = Collator.getInstance(Locale.getDefault());
            Collections.sort(found, (a, b) -> collator.compare(a[0], b[0]));
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                String[] titles = new String[found.size()];
                for (int i = 0; i < titles.length; i++) titles[i] = found.get(i)[0] + "\n" + found.get(i)[1];
                show(new AlertDialog.Builder(this).setTitle("Выберите приложение")
                        .setItems(titles, (dialog, which) -> choosePackageActivity(found.get(which)[1], chosen)));
            });
        });
    }

    /** Exported, enabled Activities that need no permission we lack; null if the package is gone. */
    @SuppressWarnings("deprecation")
    private List<ActivityInfo> launchableActivities(String packageName) {
        List<ActivityInfo> launchable = new ArrayList<>();
        try {
            ActivityInfo[] activities = getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities;
            if (activities != null) for (ActivityInfo activity : activities) {
                if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled) continue;
                if (activity.permission != null && checkSelfPermission(activity.permission) != PackageManager.PERMISSION_GRANTED) continue;
                launchable.add(activity);
            }
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        return launchable;
    }

    private void choosePackageActivity(String packageName, Consumer<ComponentName> chosen) {
        List<ComponentName> components = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<ActivityInfo> activities = launchableActivities(packageName);
        if (activities == null) {
            Toast.makeText(this, "Приложение больше не установлено", Toast.LENGTH_SHORT).show();
            return;
        }
        for (ActivityInfo activity : activities) {
            ComponentName component = new ComponentName(packageName, activity.name);
            components.add(component);
            titles.add(activity.loadLabel(getPackageManager()) + "\n" + activity.name);
        }
        if (components.isEmpty()) {
            Toast.makeText(this, "Нет доступных для внешнего запуска Activity", Toast.LENGTH_LONG).show();
            return;
        }
        show(new AlertDialog.Builder(this).setTitle("Activity для запуска")
                .setItems(titles.toArray(new String[0]), (dialog, which) -> chosen.accept(components.get(which))));
    }

    private void enterActivity(String current, Consumer<ComponentName> chosen) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("package.name/.ActivityName");
        input.setText(current);
        input.setPadding(dp(24), dp(16), dp(24), dp(16));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Activity для запуска")
                .setMessage("Укажите пакет и класс Activity через /. Приложение должно разрешать её внешний запуск.")
                .setView(input).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ComponentName component = ComponentName.unflattenFromString(input.getText().toString().trim());
            if (component == null || component.getPackageName().isEmpty() || component.getClassName().isEmpty()) {
                input.setError("Формат: package.name/.ActivityName");
                return;
            }
            chosen.accept(component);
            dialog.dismiss();
        }));
        dialog.show();
        sized(dialog);
    }

    // ---- Состояние ----

    private void showTab(int index) {
        index = Math.max(0, Math.min(tabs.size() - 1, index));
        for (int i = 0; i < tabs.size(); i++) {
            Ui.setSegmentSelected(this, tabs.get(i), i == index);
            panels.get(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        scroll.scrollTo(0, 0);
        prefs.edit().putInt(TAB, index).apply();
    }

    private void updateRedirectState() {
        if (redirectState == null) return;
        boolean enabled = prefs.getBoolean(StockHomeRedirectService.ENABLED, false)
                || prefs.getBoolean(StockHomeRedirectService.ALL_APPS_ENABLED, false);
        redirectState.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) return;
        ((TextView) redirectState.getChildAt(0)).setText(StockHomeRedirectService.isActive(this)
                ? "Служба работает."
                : "Служба не работает: включите «" + getString(R.string.stock_home_redirect_label)
                        + "» в специальных возможностях Android. Если она уже включена, но AtlasLauncher "
                        + "останавливали принудительно, перезагрузите ГУ.");
    }

    // ---- Элементы ----

    private LinearLayout page(LinearLayout content) {
        LinearLayout page = column();
        content.addView(page, new LinearLayout.LayoutParams(-1, -2));
        panels.add(page);
        return page;
    }

    private LinearLayout card(LinearLayout parent, String title, String description) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, title, 20));
        if (description != null) card.addView(hint(description));
        parent.addView(card);
        return card;
    }

    /** "Раз в минуту", "Раз в 2 минуты", "Раз в 21 минуту", "Раз в 5 минут". */
    private static String every(int count, String one, String few, String many) {
        if (count == 1) return "Раз в " + one;
        int tens = count % 100 / 10, last = count % 10;
        String word = tens != 1 && last == 1 ? one : tens != 1 && last >= 2 && last <= 4 ? few : many;
        return "Раз в " + count + " " + word;
    }

    private TextView label(String text) {
        TextView view = Ui.text(this, text, 14, Ui.TEXT_SECONDARY);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        Ui.topMargin(view, 14);
        return view;
    }

    private TextView hint(String text) {
        TextView view = Ui.text(this, text, 13, Ui.TEXT_SECONDARY);
        view.setLineSpacing(0, 1.15f);
        Ui.topMargin(view, 6);
        return view;
    }

    private Button action(LinearLayout parent, String text, Runnable action) {
        Button button = Ui.button(this, text);
        button.setOnClickListener(v -> action.run());
        parent.addView(button, margin(10, -2));
        return button;
    }

    private Switch toggle(LinearLayout parent, String text, String key, boolean defaultValue, Runnable changed) {
        return toggle(parent, text, prefs.getBoolean(key, defaultValue), checked -> {
            prefs.edit().putBoolean(key, checked).apply();
            changed.run();
        });
    }

    private Switch toggle(LinearLayout parent, String text, boolean checked, Consumer<Boolean> changed) {
        Switch view = new Switch(this);
        view.setText(text);
        view.setTextColor(Ui.TEXT);
        view.setTextSize(15);
        view.setChecked(checked);
        view.setPadding(0, dp(10), 0, dp(4));
        view.setOnCheckedChangeListener((v, value) -> changed.accept(value));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    /** A segmented row; the index of the chosen option is saved under the key. */
    private void choice(LinearLayout parent, String key, String[] options, int defaultValue, Runnable changed) {
        choice(parent, options, prefs.getInt(key, defaultValue), index -> {
            prefs.edit().putInt(key, index).apply();
            changed.run();
        });
    }

    private void choice(LinearLayout parent, String[] options, int selected, Consumer<Integer> changed) {
        LinearLayout row = new LinearLayout(this);
        List<TextView> segments = new ArrayList<>();
        for (int i = 0; i < options.length; i++) {
            int index = i;
            TextView segment = Ui.text(this, options[i], 14, Ui.TEXT);
            segment.setGravity(Gravity.CENTER);
            segment.setMaxLines(1);
            segment.setPadding(dp(8), dp(11), dp(8), dp(11));
            segment.setClickable(true);
            segment.setOnClickListener(v -> {
                for (int j = 0; j < segments.size(); j++) Ui.setSegmentSelected(this, segments.get(j), j == index);
                changed.accept(index);
            });
            Ui.setSegmentSelected(this, segment, i == selected);
            Ui.addSegment(row, segment);
            segments.add(segment);
        }
        parent.addView(row, margin(10, -2));
    }

    private void show(AlertDialog.Builder builder) {
        sized(builder.show());
    }

    /** The scale of the activity stretches dialogs past the screen edge. */
    private void sized(AlertDialog dialog) {
        int screen = getResources().getDisplayMetrics().widthPixels;
        dialog.getWindow().setLayout(Math.min(screen - dp(48), dp(640)), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams margin(int top, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height);
        params.topMargin = dp(top);
        return params;
    }

    private static String scaleText(int tenths) {
        return (tenths / 10) + "." + (tenths % 10) + "×";
    }

    @SuppressWarnings("deprecation")
    private String version() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName + " (" + info.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
