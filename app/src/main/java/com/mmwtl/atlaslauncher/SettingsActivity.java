package com.mmwtl.atlaslauncher;

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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private static final String EXTRA_PAGE = "page";
    private static final String TAB = "settings_tab";
    private static final int[] TAB_LABELS = {R.string.settings_tab_wallpaper, R.string.settings_tab_desktop,
            R.string.settings_tab_gestures, R.string.settings_tab_system};
    private static final String GIB_LAUNCHER = "com.salat.gbinder/com.salat.gbinder.AppLauncher";
    private static final int PICK_WALLPAPER = 1;
    private static final int PICK_SLIDESHOW = 2;
    private static final int PICK_VIDEO = 3;

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
    private Switch slideshowSwitch;
    private LinearLayout ownSlides;
    private LinearLayout redirectState;

    static void open(Context context, int page) {
        // A second tap before the settings cover HOME must not open them twice.
        context.startActivity(new Intent(context, SettingsActivity.class).putExtra(EXTRA_PAGE, page)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(HomeActivity.PREFS, MODE_PRIVATE);
        int page = getIntent().getIntExtra(EXTRA_PAGE, PAGE_MAIN);

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
        // Dock, clock and volume are widgets: their pages open only from the widget's own settings button.
        if (page == PAGE_DOCK) buildDockPage(content);
        else if (page == PAGE_CLOCK) buildClockPage(content);
        else if (page == PAGE_VOLUME) buildVolumePage(content);
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
                : page == PAGE_VOLUME ? R.string.settings_volume : R.string.app_name;
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
        LinearLayout wallpaper = card(page, "Фон рабочего стола",
                "Изображение, GIF/WebP-анимация или видео. Видео повторяется без звука. "
                        + "Анимации воспроизводятся на Android 9 и новее. Фон заполняет экран с обрезкой по краям.");
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
        showWallpaperPreview();

        LinearLayout slideshow = card(page, "Сменяемый фон",
                "Фон меняется, только пока главный экран закрыт другим приложением, и никогда у вас на глазах.");
        slideshowSwitch = toggle(slideshow, "Менять фон", HomeActivity.SLIDESHOW, false, () -> {
            if (prefs.getBoolean(HomeActivity.SLIDESHOW, false)) {
                prefs.edit().remove(HomeActivity.VIDEO_WALLPAPER).apply();
                releaseUnusedFiles();
                showWallpaperPreview();
            }
        });
        slideshow.addView(label("Изображения · отметьте фоны, которые будут сменять друг друга"));
        LinearLayout presets = thumbnailRow(slideshow);
        List<String> slides = HomeActivity.slideshowImages(this);
        for (int i = 0; i < HomeActivity.PRESET_WALLPAPERS.length; i++) {
            String uri = HomeActivity.presetUri(this, HomeActivity.PRESET_WALLPAPERS[i]);
            ImageView thumbnail = thumbnail(presets, uri, HomeActivity.PRESET_NAMES[i]);
            mark(thumbnail, slides.contains(uri));
            thumbnail.setOnClickListener(v -> {
                List<String> current = HomeActivity.slideshowImages(this);
                if (!current.remove(uri)) current.add(uri);
                HomeActivity.saveSlideshowImages(this, current);
                mark(thumbnail, current.contains(uri));
            });
        }
        ownSlides = column();
        slideshow.addView(ownSlides);
        showOwnSlides();
        action(slideshow, "Добавить свои изображения", () -> pickMedia(PICK_SLIDESHOW, true));
        slideshow.addView(label("Как часто менять"));
        choice(slideshow, HomeActivity.SLIDESHOW_PERIOD,
                new String[]{"При включении", "15 минут", "Час", "День"}, 0, () -> { });
    }

    private void buildDesktopTab(LinearLayout page) {
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
                "Два пальца вверх и вниз: громкость в центре экрана, температура у левого и правого края. "
                        + "Три пальца вверх и вниз: яркость, влево и вправо: обдув (в AUTO — его профиль). Щипок четырьмя пальцами: главный экран. Четыре пальца вверх: как кнопка «Все приложения». Жест начинается между верхней и климатической панелями. "
                        + "Температуре и яркости нужен GInputBridge. Касания в этой области проходят через "
                        + "AtlasLauncher, а не через штатную службу жестов.");
        toggle(gestures, "Жесты несколькими пальцами", HomeActivity.GESTURES_ENABLED, false, () -> { });
        toggle(gestures, "Жесты важнее приложений", HomeActivity.GESTURES_PRIORITY, false, () -> { });
        gestures.addView(hint("Включено: касание несколькими пальцами сначала проверяется как жест, и приложение "
                + "не сдвигается под ним. Масштаб и поворот в картах начнутся немного позже, до полусекунды. "
                + "Выключено: приложение получает касание сразу, а при срабатывании жеста оно отменяется."));
        gestures.addView(hint("Нужно разрешение «Поверх других приложений»: без него не будет карточек "
                + "с температурой и яркостью, а жесты четырьмя пальцами не смогут открыть главный экран "
                + "и приложения поверх другого приложения."));
        action(gestures, "Разрешить показ поверх приложений  ↗", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Настройка разрешения недоступна", Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout trigger = card(page, "Срабатывание жестов",
                "Раньше: жест срабатывает после более короткого движения, но чаще перехватывает прокрутку "
                        + "и масштаб в приложениях. Позже: нужно движение длиннее.");
        choice(trigger, HomeActivity.GESTURES_TRIGGER, new String[]{"Раньше", "Средне", "Позже"},
                MultiFingerGestures.TRIGGER_NORMAL, () -> { });
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

        LinearLayout gstore = card(page, getString(R.string.gstore_title), getString(R.string.gstore_hint));
        action(gstore, getString(R.string.gstore_button), () -> GStoreDialog.show(this));
        LinearLayout support = card(page, getString(R.string.donate_title), getString(R.string.donate_hint));
        action(support, getString(R.string.donate_button), () -> DonateDialog.show(this));
    }

    private void buildDockPage(LinearLayout parent) {
        LinearLayout look = card(parent, "Внешний вид", "Размер значков и плотность дока на рабочем столе.");
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
        HomeActivity.WidgetPlacement previewPlacement = new HomeActivity.WidgetPlacement(0, 0, 0, 280, 192);
        previewPlacement.clockSize = 64;
        View clock = BuiltInWidgets.createClock(this, previewPlacement);
        preview.addView(clock, margin(12, dp(156)));
        Runnable changed = () -> BuiltInWidgets.updateClock(this, clock, previewPlacement);
        toggle(preview, "Показывать дату", BuiltInWidgets.CLOCK_DATE, true, changed);
        preview.addView(label("Формат времени"));
        choice(preview, BuiltInWidgets.CLOCK_FORMAT, new String[]{"Как в системе", "24 часа", "12 часов"}, 0, changed);
        preview.addView(label("Начертание"));
        choice(preview, BuiltInWidgets.CLOCK_WEIGHT, new String[]{"Тонкий", "Обычный", "Жирный"}, 0, changed);
        preview.addView(label("Шрифт"));
        choice(preview, BuiltInWidgets.CLOCK_FONT, new String[]{"Без засечек", "С засечками", "Моноширинный"}, 0, changed);
    }

    private void buildVolumePage(LinearLayout content) {
        LinearLayout preview = card(content, "Предпросмотр",
                "Кнопки встают в ряд у широкого виджета и в столбик у высокого.");
        HomeActivity.WidgetPlacement previewPlacement = new HomeActivity.WidgetPlacement(0, 0, 0, 288, 96);
        View volume = BuiltInWidgets.createVolume(this, previewPlacement);
        // Only shows the style: its buttons would change the car's volume.
        FrameLayout frame = new FrameLayout(this) {
            @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }
        };
        frame.addView(volume, new FrameLayout.LayoutParams(-1, -1));
        preview.addView(frame, margin(12, dp(96)));
        Runnable changed = () -> BuiltInWidgets.updateVolume(this, volume, previewPlacement);
        toggle(preview, "Кнопка отключения звука", BuiltInWidgets.VOLUME_MUTE, true, changed);
        toggle(preview, "Одним блоком", BuiltInWidgets.VOLUME_JOINED, false, changed);
        preview.addView(label("Значки"));
        choice(preview, BuiltInWidgets.VOLUME_ICONS, new String[]{"Динамик", "Плюс и минус", "Стрелки"}, 0, changed);
    }

    // ---- Фон ----

    private void chooseWallpaper(String uri) {
        SharedPreferences.Editor edit = prefs.edit().putBoolean(HomeActivity.SLIDESHOW, false)
                .remove(HomeActivity.VIDEO_WALLPAPER);
        (uri == null ? edit.remove(HomeActivity.WALLPAPER) : edit.putString(HomeActivity.WALLPAPER, uri)).apply();
        slideshowSwitch.setChecked(false);
        releaseUnusedFiles();
        showWallpaperPreview();
    }

    /** Gives up access to picked files no background uses any more; Android keeps only so many grants. */
    private void releaseUnusedFiles() {
        List<String> used = HomeActivity.slideshowImages(this);
        used.add(prefs.getString(HomeActivity.WALLPAPER, null));
        used.add(prefs.getString(HomeActivity.VIDEO_WALLPAPER, null));
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
        String saved = prefs.getString(HomeActivity.WALLPAPER, null);
        String video = prefs.getString(HomeActivity.VIDEO_WALLPAPER, null);
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
        List<String> slides = HomeActivity.slideshowImages(this);
        if (slides.stream().allMatch(HomeActivity::isPresetUri)) return;
        ownSlides.addView(label("Свои изображения · нажмите, чтобы убрать"));
        LinearLayout own = thumbnailRow(ownSlides);
        for (String uri : slides) {
            if (HomeActivity.isPresetUri(uri)) continue;
            ImageView thumbnail = thumbnail(own, uri, "Своё изображение");
            mark(thumbnail, true);
            thumbnail.setOnClickListener(v -> {
                List<String> current = HomeActivity.slideshowImages(this);
                current.remove(uri);
                HomeActivity.saveSlideshowImages(this, current);
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

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if ((request == PICK_WALLPAPER || request == PICK_VIDEO) && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (request == PICK_VIDEO) {
                    prefs.edit().putString(HomeActivity.VIDEO_WALLPAPER, uri.toString())
                            .putBoolean(HomeActivity.SLIDESHOW, false).apply();
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
            List<String> slides = HomeActivity.slideshowImages(this);
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
            HomeActivity.saveSlideshowImages(this, slides);
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
                        chooseActivityPackage(onChanged);
                    } else {
                        enterDrawerActivity(onChanged);
                    }
                }));
    }

    private void chooseActivityPackage(Runnable onChanged) {
        List<String> packages = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (HomeActivity.AppEntry app : apps()) {
            String name = app.component.getPackageName();
            if (packages.contains(name)) continue;
            packages.add(name);
            titles.add(app.label + "\n" + name);
        }
        show(new AlertDialog.Builder(this).setTitle("Выберите приложение")
                .setItems(titles.toArray(new String[0]), (dialog, which) -> choosePackageActivity(packages.get(which), onChanged)));
    }

    @SuppressWarnings("deprecation")
    private void choosePackageActivity(String packageName, Runnable onChanged) {
        List<ComponentName> components = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        try {
            ActivityInfo[] activities = getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities;
            if (activities != null) for (ActivityInfo activity : activities) {
                if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled) continue;
                if (activity.permission != null && checkSelfPermission(activity.permission) != PackageManager.PERMISSION_GRANTED) continue;
                ComponentName component = new ComponentName(packageName, activity.name);
                components.add(component);
                titles.add(activity.loadLabel(getPackageManager()) + "\n" + activity.name);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, "Приложение больше не установлено", Toast.LENGTH_SHORT).show();
            return;
        }
        if (components.isEmpty()) {
            Toast.makeText(this, "Нет доступных для внешнего запуска Activity", Toast.LENGTH_LONG).show();
            return;
        }
        show(new AlertDialog.Builder(this).setTitle("Activity для запуска")
                .setItems(titles.toArray(new String[0]), (dialog, which) -> {
                    prefs.edit().putString(HomeActivity.DRAWER_ACTIVITY, components.get(which).flattenToString()).apply();
                    onChanged.run();
                }));
    }

    private void enterDrawerActivity(Runnable onChanged) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("package.name/.ActivityName");
        input.setText(prefs.getString(HomeActivity.DRAWER_ACTIVITY, ""));
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
            prefs.edit().putString(HomeActivity.DRAWER_ACTIVITY, component.flattenToString()).apply();
            onChanged.run();
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
                prefs.edit().putInt(key, index).apply();
                for (int j = 0; j < segments.size(); j++) Ui.setSegmentSelected(this, segments.get(j), j == index);
                changed.run();
            });
            Ui.setSegmentSelected(this, segment, i == prefs.getInt(key, defaultValue));
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
