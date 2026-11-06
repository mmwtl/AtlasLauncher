package com.mmwtl.atlaslauncher;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriPermission;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * All launcher settings as one JSON file, in the format family of the Atlas widgets: every setting of
 * {@code SharedPreferences("home")} by type, and the desktop. Built-in widgets come back as they were; an
 * app's widget only while this device still has it bound to the same provider, since widget IDs do not
 * survive a reinstall and binding a new one needs the user.
 */
final class SettingsBackup {
    static final String FORMAT = "atlas-launcher-settings";
    static final int SCHEMA_VERSION = 1;
    static final String FILE_NAME = "AtlasLauncher-settings.json";
    static final String MIME = "application/json";
    /** Written by an import; HOME then starts over from the new settings. */
    static final String IMPORTED = "settings_imported";
    // State of this device and this run rather than settings: an import keeps it and the file leaves it out.
    private static final Set<String> LOCAL = new HashSet<>(Arrays.asList(
            HomeActivity.WIDGETS, HomeActivity.WIDGET_PADDING_MIGRATED, HomeActivity.DOCK_WIDGET_MIGRATED,
            HomeActivity.WIDGETS_HIDDEN, HomeActivity.SOURCE_LIST_WIDGET,
            HomeActivity.SLIDESHOW_CHANGED_AT, HomeActivity.modeKey(HomeActivity.SLIDESHOW_CHANGED_AT, true),
            HomeActivity.SLIDESHOW_BOOT, HomeActivity.modeKey(HomeActivity.SLIDESHOW_BOOT, true),
            SettingsActivity.TAB, IMPORTED));
    private static final String[] TYPES = {"booleans", "ints", "longs", "floats", "strings", "stringSets"};

    final Map<String, Object> settings = new TreeMap<>();
    /** Desktop placements; an app's widget also names its provider. */
    final List<JSONObject> widgets = new ArrayList<>();

    static SettingsBackup read(Context context) {
        SettingsBackup backup = new SettingsBackup();
        SharedPreferences prefs = context.getSharedPreferences(HomeActivity.PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!LOCAL.contains(entry.getKey()) && entry.getValue() != null) {
                backup.settings.put(entry.getKey(), entry.getValue());
            }
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        try {
            JSONArray placements = new JSONArray(prefs.getString(HomeActivity.WIDGETS, "[]"));
            for (int i = 0; i < placements.length(); i++) {
                JSONObject placement = placements.optJSONObject(i);
                if (placement == null) continue;
                int id = placement.getInt("id");
                if (id >= 0) {
                    AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
                    // Unbound: HOME drops it at the next start anyway.
                    if (info == null) continue;
                    placement.put("provider", info.provider.flattenToString());
                }
                backup.widgets.add(placement);
            }
        } catch (JSONException error) {
            // A desktop HOME could not read either: the file carries none.
            backup.widgets.clear();
        }
        return backup;
    }

    String toJson(String appVersion) {
        try {
            JSONObject json = new JSONObject();
            json.put("format", FORMAT);
            json.put("schemaVersion", SCHEMA_VERSION);
            json.put("appVersion", appVersion == null ? "" : appVersion);
            JSONObject typed = new JSONObject();
            for (String type : TYPES) typed.put(type, new JSONObject());
            for (Map.Entry<String, Object> entry : settings.entrySet()) {
                Object value = entry.getValue();
                String type = value instanceof Boolean ? "booleans" : value instanceof Integer ? "ints"
                        : value instanceof Long ? "longs" : value instanceof Float ? "floats"
                        : value instanceof String ? "strings" : "stringSets";
                typed.getJSONObject(type).put(entry.getKey(),
                        value instanceof Set ? new JSONArray((Set<?>) value) : value);
            }
            json.put("settings", typed);
            json.put("widgets", new JSONArray(widgets));
            return json.toString(2);
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Reads a settings file; {@code null} when it is not one of ours. */
    static SettingsBackup parse(String text) {
        try {
            JSONObject json = new JSONObject(text.trim());
            if (!FORMAT.equals(json.optString("format"))) return null;
            SettingsBackup backup = new SettingsBackup();
            JSONObject typed = json.getJSONObject("settings");
            for (String type : TYPES) {
                JSONObject values = typed.optJSONObject(type);
                if (values == null) continue;
                for (Iterator<String> keys = values.keys(); keys.hasNext(); ) {
                    String key = keys.next();
                    Object value = value(type, values.get(key));
                    if (value != null && !LOCAL.contains(key)) backup.settings.put(key, value);
                }
            }
            JSONArray placements = json.optJSONArray("widgets");
            for (int i = 0; placements != null && i < placements.length(); i++) {
                JSONObject placement = placements.getJSONObject(i);
                // The fields HOME requires; a placement without them would cost the whole desktop.
                for (String field : new String[]{"id", "x", "y", "width", "height"}) placement.getInt(field);
                backup.widgets.add(placement);
            }
            return backup;
        } catch (JSONException | RuntimeException error) {
            return null;
        }
    }

    /** The value as SharedPreferences stores that type; {@code null} for a value of another type. */
    private static Object value(String type, Object value) throws JSONException {
        switch (type) {
            case "booleans": return value instanceof Boolean ? value : null;
            case "ints": return value instanceof Number ? ((Number) value).intValue() : null;
            case "longs": return value instanceof Number ? ((Number) value).longValue() : null;
            case "floats": return value instanceof Number ? ((Number) value).floatValue() : null;
            case "strings": return value instanceof String ? value : null;
            default:
                if (!(value instanceof JSONArray)) return null;
                Set<String> set = new HashSet<>();
                for (int i = 0; i < ((JSONArray) value).length(); i++) set.add(((JSONArray) value).getString(i));
                return set;
        }
    }

    /** The placements this device can show: built-in widgets and widgets still bound here to the same provider. */
    List<JSONObject> restorableWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        List<JSONObject> restorable = new ArrayList<>();
        for (JSONObject placement : widgets) {
            int id = placement.optInt("id");
            AppWidgetProviderInfo info = id < 0 ? null : manager.getAppWidgetInfo(id);
            if (id < 0 || info != null && info.provider.flattenToString().equals(placement.optString("provider"))) {
                restorable.add(placement);
            }
        }
        return restorable;
    }

    /** Replaces the settings and the desktop; settings missing from the file go back to their defaults. */
    void apply(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(HomeActivity.PREFS, Context.MODE_PRIVATE);
        Map<String, ?> current = prefs.getAll();
        Set<String> granted = new HashSet<>();
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (permission.isReadPermission()) granted.add(permission.getUri().toString());
        }
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : current.keySet()) if (!LOCAL.contains(key)) edit.remove(key);
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            String key = entry.getKey();
            Object value = readable(key, entry.getValue(), granted);
            Object old = current.get(key);
            // A value of another type than the launcher keeps there would break reading it.
            if (value == null || old != null && old.getClass() != value.getClass()) continue;
            if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof String) edit.putString(key, (String) value);
            else {
                @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value;
                edit.putStringSet(key, set);
            }
        }
        JSONArray placements = new JSONArray();
        for (JSONObject placement : restorableWidgets(context)) {
            try {
                JSONObject copy = new JSONObject(placement.toString());
                copy.remove("provider");
                placements.put(copy);
            } catch (JSONException error) {
                throw new IllegalStateException(error);
            }
        }
        edit.putString(HomeActivity.WIDGETS, placements.toString())
                // The file comes from a launcher that has already migrated its desktop.
                .putBoolean(HomeActivity.WIDGET_PADDING_MIGRATED, true)
                .putBoolean(HomeActivity.DOCK_WIDGET_MIGRATED, true)
                .putLong(IMPORTED, System.currentTimeMillis())
                .apply();
    }

    /**
     * A picked wallpaper, video or slide only while the launcher may still read it: after a reinstall or on
     * another device its file permission is gone. Built-in wallpapers always are.
     */
    private static Object readable(String key, Object value, Set<String> granted) {
        boolean wallpaper = either(key, HomeActivity.WALLPAPER) || either(key, HomeActivity.VIDEO_WALLPAPER);
        boolean slides = either(key, HomeActivity.SLIDESHOW_IMAGES);
        if ((wallpaper || slides) && !(value instanceof String)) return null;
        if (wallpaper) return readable((String) value, granted) ? value : null;
        if (slides) {
            try {
                JSONArray images = new JSONArray((String) value);
                JSONArray kept = new JSONArray();
                for (int i = 0; i < images.length(); i++) {
                    if (readable(images.getString(i), granted)) kept.put(images.getString(i));
                }
                return kept.toString();
            } catch (JSONException error) {
                return null;
            }
        }
        return value;
    }

    /** The key of the setting for either wallpaper mode. */
    private static boolean either(String key, String setting) {
        return key.equals(setting) || key.equals(HomeActivity.modeKey(setting, true));
    }

    private static boolean readable(String uri, Set<String> granted) {
        return HomeActivity.isPresetUri(uri) || granted.contains(uri);
    }
}
