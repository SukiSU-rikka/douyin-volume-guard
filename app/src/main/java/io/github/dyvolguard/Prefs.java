package io.github.dyvolguard;

/** 界面和 Hook 端共用的配置键 */
public final class Prefs {
    private Prefs() {}

    public static final String GROUP = "settings";

    public static final String ENABLED = "enabled";
    public static final String KEY_WINDOW_MS = "key_window_ms";
    public static final String LOG = "log";

    public static final boolean DEF_ENABLED = true;
    public static final int DEF_KEY_WINDOW_MS = 1000;
    public static final boolean DEF_LOG = true;

    /** SeekBar 档位 → 毫秒 */
    public static final int[] WINDOW_STEPS = {0, 500, 1000, 1500, 2000, 3000};
}
