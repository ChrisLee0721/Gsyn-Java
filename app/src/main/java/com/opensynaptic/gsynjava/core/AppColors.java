package com.opensynaptic.gsynjava.core;

/**
 * Java mirror of Flutter AppColors — ARGB int constants for programmatic use.
 * For XML use the @color/gsyn_* resources defined in colors.xml.
 */
public final class AppColors {

    // Core palette
    public static final int PRIMARY        = 0xFF1A73E8;
    public static final int SECONDARY      = 0xFF34A853;
    public static final int BACKGROUND     = 0xFF0F1923;
    public static final int SURFACE        = 0xFF1B2838;
    public static final int CARD           = 0xFF213040;
    public static final int TEXT_PRIMARY   = 0xFFE8EAED;
    public static final int TEXT_SECONDARY = 0xFF9AA0A6;

    // Status colors
    public static final int ONLINE  = 0xFF34A853;
    public static final int OFFLINE = 0xFF5F6368;
    public static final int WARNING = 0xFFFBBC04;
    public static final int DANGER  = 0xFFEA4335;
    public static final int INFO    = 0xFF4285F4;

    // Threshold zone colors
    public static final int ZONE_NORMAL  = 0xFF34A853;
    public static final int ZONE_WARNING = 0xFFFBBC04;
    public static final int ZONE_DANGER  = 0xFFEA4335;

    /** Chart colour palette — 8 distinct colours for multi-series charts. */
    public static final int[] CHART_PALETTE = {
            0xFF4285F4, 0xFF34A853, 0xFFFBBC04, 0xFFEA4335,
            0xFF9C27B0, 0xFF00BCD4, 0xFFFF9800, 0xFF607D8B
    };

    private AppColors() {}
}

