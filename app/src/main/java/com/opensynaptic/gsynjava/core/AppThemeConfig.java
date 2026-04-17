package com.opensynaptic.gsynjava.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.view.Window;

/**
 * Java mirror of Flutter theme_provider.dart.
 * Manages accent-colour presets (ThemePreset) and background presets (BgPreset),
 * both persisted to SharedPreferences.
 */
public final class AppThemeConfig {

    private static final String PREFS_NAME = "app_theme_prefs";
    private static final String KEY_THEME  = "app_theme_preset";
    private static final String KEY_BG     = "app_bg_preset";

    // ── Accent colour presets ────────────────────────────────────────────────

    public enum ThemePreset {
        DEEP_BLUE ("#1A73E8", "深蓝"),
        TEAL      ("#00897B", "青绿"),
        PURPLE    ("#7B1FA2", "紫色"),
        AMBER     ("#FF8F00", "琥珀"),
        RED       ("#D32F2F", "红色"),
        CYAN      ("#0097A7", "蓝绿"),
        GREEN     ("#2E7D32", "绿色"),
        PINK      ("#C2185B", "粉色");

        public final String hex;
        public final String label;

        ThemePreset(String hex, String label) {
            this.hex   = hex;
            this.label = label;
        }

        public int color() {
            return (int) (0xFF000000L | Long.parseLong(hex.substring(1), 16));
        }
    }

    // ── Background presets ───────────────────────────────────────────────────

    public enum BgPreset {
        // Dark
        DEEP_NAVY      ("#0F1923", "#1B2838", "#213040", "深海蓝 (默认)", false),
        DARK_SLATE     ("#121420", "#1D2033", "#252840", "暗石板",         false),
        CHARCOAL       ("#1A1A1A", "#262626", "#303030", "炭灰",           false),
        TRUE_BLACK     ("#080808", "#141414", "#1E1E1E", "纯黑 (AMOLED)", false),
        FOREST_DARK    ("#0D1A0D", "#172617", "#1F301F", "林暗绿",         false),
        WARM_DARK      ("#1A1209", "#261D0F", "#302318", "暖棕暗",         false),
        // Light
        SNOW_WHITE     ("#FAFAFA", "#FFFFFF", "#F1F3F4", "雪白",           true),
        CLOUD_GREY     ("#F1F3F4", "#FFFFFF", "#E8EAED", "云雾灰",         true),
        PAPER_CREAM    ("#FFFDE7", "#FFFFFF", "#FFF9C4", "纸张米黄",       true),
        MINT_LIGHT     ("#E8F5E9", "#FFFFFF", "#C8E6C9", "薄荷浅绿",       true),
        LAVENDER_LIGHT ("#EDE7F6", "#FFFFFF", "#D1C4E9", "薰衣草紫",       true),
        SKY_BLUE       ("#E3F2FD", "#FFFFFF", "#BBDEFB", "天空蓝",         true);

        public final String bgHex;
        public final String surfaceHex;
        public final String cardHex;
        public final String label;
        public final boolean isLight;

        BgPreset(String bg, String surface, String card, String label, boolean isLight) {
            this.bgHex      = bg;
            this.surfaceHex = surface;
            this.cardHex    = card;
            this.label      = label;
            this.isLight    = isLight;
        }

        public int bgColor()      { return parseHex(bgHex); }
        public int surfaceColor() { return parseHex(surfaceHex); }
        public int cardColor()    { return parseHex(cardHex); }

        private static int parseHex(String hex) {
            return (int) (0xFF000000L | Long.parseLong(hex.substring(1), 16));
        }
    }

    // ── SharedPreferences helpers ────────────────────────────────────────────

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static ThemePreset loadThemePreset(Context ctx) {
        String name = prefs(ctx).getString(KEY_THEME, ThemePreset.DEEP_BLUE.name());
        try { return ThemePreset.valueOf(name); } catch (Exception e) { return ThemePreset.DEEP_BLUE; }
    }

    public static void saveThemePreset(Context ctx, ThemePreset preset) {
        prefs(ctx).edit().putString(KEY_THEME, preset.name()).apply();
    }

    public static BgPreset loadBgPreset(Context ctx) {
        String name = prefs(ctx).getString(KEY_BG, BgPreset.DEEP_NAVY.name());
        try { return BgPreset.valueOf(name); } catch (Exception e) { return BgPreset.DEEP_NAVY; }
    }

    public static void saveBgPreset(Context ctx, BgPreset preset) {
        prefs(ctx).edit().putString(KEY_BG, preset.name()).apply();
    }

    // ── Accent overlay resource IDs ──────────────────────────────────────────

    /**
     * Returns the ThemeOverlay style resource ID for the given ThemePreset.
     * Apply via getTheme().applyStyle(id, true) BEFORE super.onCreate().
     */
    public static int getAccentOverlayRes(ThemePreset preset) {
        switch (preset) {
            case TEAL:   return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Accent_Teal;
            case PURPLE: return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Accent_Purple;
            case AMBER:  return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Accent_Amber;
            case RED:    return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Accent_Red;
            case CYAN:   return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Accent_Cyan;
            case GREEN:  return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Accent_Green;
            case PINK:   return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Accent_Pink;
            default:     return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Accent_DeepBlue;
        }
    }

    // ── Background overlay resource IDs ─────────────────────────────────────

    /**
     * Returns the ThemeOverlay style resource ID for the given BgPreset.
     * Apply via getTheme().applyStyle(id, true) BEFORE super.onCreate().
     */
    public static int getBgOverlayRes(BgPreset preset) {
        if (preset == null) return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_DeepNavy;
        switch (preset) {
            case DARK_SLATE:     return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_DarkSlate;
            case CHARCOAL:       return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_Charcoal;
            case TRUE_BLACK:     return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_TrueBlack;
            case FOREST_DARK:    return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_ForestDark;
            case WARM_DARK:      return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_WarmDark;
            case SNOW_WHITE:     return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_SnowWhite;
            case CLOUD_GREY:     return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_CloudGrey;
            case PAPER_CREAM:    return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_PaperCream;
            case MINT_LIGHT:     return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_MintLight;
            case LAVENDER_LIGHT: return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_LavenderLight;
            case SKY_BLUE:       return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_SkyBlue;
            default:             return com.opensynaptic.gsynjava.R.style.ThemeOverlay_GsynJava_Bg_DeepNavy;
        }
    }

    // ── Window helpers ───────────────────────────────────────────────────────

    /**
     * Explicitly updates status-bar and navigation-bar colours to match the
     * saved BgPreset. Call this from Activity.onCreate() AFTER super.onCreate().
     */
    @SuppressWarnings({"deprecation", "InlinedApi"})
    public static void applyBgToWindow(Window window, Context ctx) {
        if (window == null) return;
        try {
            BgPreset bg = loadBgPreset(ctx);
            int bgColor = bg.bgColor();
            window.setStatusBarColor(bgColor);
            window.setNavigationBarColor(bgColor);
            // Update status-bar / navigation-bar icon colours for legibility
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (bg.isLight) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            decor.setSystemUiVisibility(flags);
        } catch (Exception ignored) {
            // Defensive: some custom ROMs / API levels may not support all window flags.
        }
    }

    /**
     * Apply the saved BgPreset background colour to a fragment root view.
     * Useful as a fallback when the ThemeOverlay has already been applied.
     */
    public static void applyBgToRoot(View root, Context ctx) {
        if (root == null) return;
        BgPreset bg = loadBgPreset(ctx);
        root.setBackgroundColor(bg.bgColor());
    }

    private AppThemeConfig() {}
}

