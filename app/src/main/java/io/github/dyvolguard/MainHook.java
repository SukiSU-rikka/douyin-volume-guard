package io.github.dyvolguard;

import android.app.Activity;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 抖音音量守卫（libxposed API 102）
 * 拦截抖音进程里所有"调大系统音量 / 取消静音"的调用。
 * 用户刚按过音量键（KEY_WINDOW_MS 内）时放行；调低始终放行。
 */
public class MainHook extends XposedModule {

    private static final String TAG = "DyVolGuard";
    private static final String[] TARGETS = {
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite"
    };
    private static final long KEY_WINDOW_MS = 1000;

    private static volatile long lastVolumeKeyAt = -100_000;
    private boolean hooked = false;

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!isTarget(param.getPackageName())) return;
        synchronized (this) {
            if (hooked) return;
            hooked = true;
        }
        info("active in " + param.getPackageName());
        hookKeyTracking();
        hookAudioManager();
        hookMediaController();
    }

    private static boolean isTarget(String pkg) {
        for (String t : TARGETS) if (t.equals(pkg)) return true;
        return false;
    }

    // ---------------- 记录用户按音量键 ----------------

    private void hookKeyTracking() {
        XposedInterface.Hooker tracker = chain -> {
            for (Object a : chain.getArgs()) {
                if (a instanceof KeyEvent) {
                    noteKey((KeyEvent) a);
                    break;
                }
            }
            return chain.proceed();
        };
        // 最早入口：抖音自己吞掉按键也能记录到
        try {
            Class<?> vri = Class.forName("android.view.ViewRootImpl");
            for (Method m : vri.getDeclaredMethods()) {
                if (m.getName().equals("enqueueInputEvent")) safeHook(m, tracker);
            }
        } catch (Throwable t) {
            warn("ViewRootImpl hook failed: " + t);
        }
        // 兜底
        try {
            safeHook(Activity.class.getDeclaredMethod("dispatchKeyEvent", KeyEvent.class), tracker);
        } catch (Throwable t) {
            warn("Activity.dispatchKeyEvent hook failed: " + t);
        }
    }

    private static void noteKey(KeyEvent e) {
        int c = e.getKeyCode();
        if (c == KeyEvent.KEYCODE_VOLUME_UP
                || c == KeyEvent.KEYCODE_VOLUME_DOWN
                || c == KeyEvent.KEYCODE_VOLUME_MUTE) {
            lastVolumeKeyAt = SystemClock.uptimeMillis();
        }
    }

    private static boolean userJustPressed() {
        return SystemClock.uptimeMillis() - lastVolumeKeyAt < KEY_WINDOW_MS;
    }

    private static int intArg(XposedInterface.Chain chain, int i) {
        Object o = chain.getArg(i);
        return (Integer) o;
    }

    // ---------------- AudioManager ----------------

    private void hookAudioManager() {
        Class<AudioManager> am = AudioManager.class;

        // setStreamVolume(int streamType, int index, int flags)
        hookMethod(am, "setStreamVolume", chain -> {
            if (!userJustPressed()) {
                int stream = intArg(chain, 0);
                int index = intArg(chain, 1);
                int cur = ((AudioManager) chain.getThisObject()).getStreamVolume(stream);
                if (index > cur) {
                    return block("setStreamVolume(stream=" + stream + ", " + cur + " -> " + index + ")");
                }
            }
            return chain.proceed();
        }, int.class, int.class, int.class);

        // adjustStreamVolume(int streamType, int direction, int flags)
        hookMethod(am, "adjustStreamVolume", chain -> {
            if (!userJustPressed()) {
                int stream = intArg(chain, 0);
                int dir = intArg(chain, 1);
                if (isRaise((AudioManager) chain.getThisObject(), stream, dir)) {
                    return block("adjustStreamVolume(stream=" + stream + ", dir=" + dir + ")");
                }
            }
            return chain.proceed();
        }, int.class, int.class, int.class);

        // adjustVolume(int direction, int flags)
        hookMethod(am, "adjustVolume", chain -> {
            if (!userJustPressed()) {
                int dir = intArg(chain, 0);
                if (isRaise((AudioManager) chain.getThisObject(), AudioManager.STREAM_MUSIC, dir)) {
                    return block("adjustVolume(dir=" + dir + ")");
                }
            }
            return chain.proceed();
        }, int.class, int.class);

        // adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags)
        hookMethod(am, "adjustSuggestedStreamVolume", chain -> {
            if (!userJustPressed()) {
                int dir = intArg(chain, 0);
                int stream = intArg(chain, 1);
                if (stream == AudioManager.USE_DEFAULT_STREAM_TYPE) stream = AudioManager.STREAM_MUSIC;
                if (isRaise((AudioManager) chain.getThisObject(), stream, dir)) {
                    return block("adjustSuggestedStreamVolume(stream=" + stream + ", dir=" + dir + ")");
                }
            }
            return chain.proceed();
        }, int.class, int.class, int.class);

        // setStreamMute(int, boolean)：拦截取消静音
        hookMethod(am, "setStreamMute", chain -> {
            if (!userJustPressed()) {
                Object state = chain.getArg(1);
                if (Boolean.FALSE.equals(state)) {
                    return block("setStreamMute(stream=" + chain.getArg(0) + ", false)");
                }
            }
            return chain.proceed();
        }, int.class, boolean.class);

        // 隐藏 API：setVolumeIndexForAttributes(AudioAttributes, int, int)
        for (Method m : am.getDeclaredMethods()) {
            if (!m.getName().equals("setVolumeIndexForAttributes")) continue;
            safeHook(m, chain -> {
                if (!userJustPressed()) {
                    return block("setVolumeIndexForAttributes(index=" + chain.getArg(1) + ")");
                }
                return chain.proceed();
            });
        }
    }

    private static boolean isRaise(AudioManager am, int stream, int dir) {
        if (dir == AudioManager.ADJUST_RAISE || dir == AudioManager.ADJUST_UNMUTE) return true;
        if (dir == AudioManager.ADJUST_TOGGLE_MUTE) {
            try {
                return am.isStreamMute(stream);
            } catch (Throwable ignored) {
                return true;
            }
        }
        return false;
    }

    // ---------------- MediaController ----------------

    private void hookMediaController() {
        hookMethod(MediaController.class, "setVolumeTo", chain -> {
            if (!userJustPressed()) {
                int value = intArg(chain, 0);
                int cur = -1;
                try {
                    MediaController.PlaybackInfo info =
                            ((MediaController) chain.getThisObject()).getPlaybackInfo();
                    if (info != null) cur = info.getCurrentVolume();
                } catch (Throwable ignored) {
                }
                if (cur < 0 || value > cur) {
                    return block("MediaController.setVolumeTo(" + cur + " -> " + value + ")");
                }
            }
            return chain.proceed();
        }, int.class, int.class);

        hookMethod(MediaController.class, "adjustVolume", chain -> {
            if (!userJustPressed()) {
                int dir = intArg(chain, 0);
                if (dir == AudioManager.ADJUST_RAISE || dir == AudioManager.ADJUST_UNMUTE) {
                    return block("MediaController.adjustVolume(dir=" + dir + ")");
                }
            }
            return chain.proceed();
        }, int.class, int.class);
    }

    // ---------------- 工具 ----------------

    private void hookMethod(Class<?> clazz, String name, XposedInterface.Hooker hooker, Class<?>... types) {
        try {
            safeHook(clazz.getDeclaredMethod(name, types), hooker);
        } catch (Throwable t) {
            warn("find " + clazz.getSimpleName() + "." + name + " failed: " + t);
        }
    }

    private void safeHook(Method m, XposedInterface.Hooker hooker) {
        try {
            hook(m).intercept(hooker);
        } catch (Throwable t) {
            warn("hook " + m + " failed: " + t);
        }
    }

    /** 拦截：不调用原方法，返回 null（这些方法都是 void） */
    private Object block(String what) {
        info("BLOCKED " + what + callerHint());
        return null;
    }

    /** 打出抖音自己的调用栈前几帧，方便定位 */
    private static String callerHint() {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String c = e.getClassName();
            if (c.startsWith("java.") || c.startsWith("android.") || c.startsWith("com.android.")
                    || c.startsWith("dalvik.") || c.startsWith("io.github.libxposed")
                    || c.startsWith("io.github.dyvolguard") || c.startsWith("org.lsposed")
                    || c.startsWith("LSPHooker")) continue;
            sb.append("\n    at ").append(e);
            if (++n >= 6) break;
        }
        return sb.toString();
    }

    private void info(String s) {
        log(Log.INFO, TAG, s);
    }

    private void warn(String s) {
        log(Log.WARN, TAG, s);
    }
}
