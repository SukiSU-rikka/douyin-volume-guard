package io.github.dyvolguard;

import android.content.Context;

/** 不依赖 BuildConfig（AGP 8 默认关闭），直接读包信息 */
final class BuildConfigHolder {
    private BuildConfigHolder() {}

    static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }
}
