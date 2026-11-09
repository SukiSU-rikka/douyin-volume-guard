package io.github.dyvolguard;

import android.app.Activity;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.os.SystemClock;
import android.view.KeyEvent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 抖音音量守卫：
 * 拦截抖音进程内所有"调大系统音量 / 取消静音"的调用。
 * 用户刚按过音量键（KEY_WINDOW_MS 内）时放行，所以手动调音量不受影响。
 * 调低音量始终放行。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[DyVolGuard] ";
    private static final String[] TARGETS = {
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite"
    };
    /** 按下音量键后多久内允许 App 改音量（抖音可能自己接管音量键再调 AudioManager） */
    private static final long KEY_WINDOW_MS = 1000;

    private static volatile long lastVolumeKeyAt = -100_000;
    private static boolean hooked = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!isTarget(lp.packageName)) return;
        synchronized (MainHook.class) {
            if (hooked) return;
            hooked = true;
        }
        log("active in process " + lp.processName);
        hookKeyTracking();
        hookAudioManager();
        hookMediaController();
    }

    private static boolean isTarget(String pkg) {
        for (String t : TARGETS) if (t.equals(pkg)) return true;
        return false;
    }

    // ---------------- 记录用户按音量键的时间 ----------------

    private void hookKeyTracking() {
        XC_MethodHook tracker = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                for (Object a : p.args) {
                    if (a instanceof KeyEvent) {
                        noteKey((KeyEvent) a);
                        return;
                    }
                }
            }
        };
        // 最早的入口：所有窗口的按键都会经过这里，抖音自己吞掉按键也能记录到
        try {
            Class<?> vri = XposedHelpers.findClass("android.view.ViewRootImpl", null);
            XposedBridge.hookAllMethods(vri, "enqueueInputEvent", tracker);
        } catch (Throwable t) {
            log("hook ViewRootImpl.enqueueInputEvent failed: " + t);
        }
        // 兜底
        try {
            XposedBridge.hookAllMethods(Activity.class, "dispatchKeyEvent", tracker);
        } catch (Throwable t) {
            log("hook Activity.dispatchKeyEvent failed: " + t);
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

    // ---------------- AudioManager ----------------

    private void hookAudioManager() {
        // setStreamVolume(int streamType, int index, int flags)
        safeHook(AudioManager.class, "setStreamVolume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (userJustPressed()) return;
                AudioManager am = (AudioManager) p.thisObject;
                int stream = (int) p.args[0];
                int index = (int) p.args[1];
                int cur = am.getStreamVolume(stream);
                if (index > cur) {
                    block(p, "setStreamVolume(stream=" + stream + ", " + cur + " -> " + index + ")");
                }
            }
        }, int.class, int.class, int.class);

        // adjustStreamVolume(int streamType, int direction, int flags)
        safeHook(AudioManager.class, "adjustStreamVolume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (userJustPressed()) return;
                int stream = (int) p.args[0];
                int dir = (int) p.args[1];
                if (isRaise((AudioManager) p.thisObject, stream, dir)) {
                    block(p, "adjustStreamVolume(stream=" + stream + ", dir=" + dir + ")");
                }
            }
        }, int.class, int.class, int.class);

        // adjustVolume(int direction, int flags)
        safeHook(AudioManager.class, "adjustVolume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (userJustPressed()) return;
                int dir = (int) p.args[0];
                if (isRaise((AudioManager) p.thisObject, AudioManager.STREAM_MUSIC, dir)) {
                    block(p, "adjustVolume(dir=" + dir + ")");
                }
            }
        }, int.class, int.class);

        // adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags)
        safeHook(AudioManager.class, "adjustSuggestedStreamVolume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (userJustPressed()) return;
                int dir = (int) p.args[0];
                int stream = (int) p.args[1];
                if (stream == AudioManager.USE_DEFAULT_STREAM_TYPE) stream = AudioManager.STREAM_MUSIC;
                if (isRaise((AudioManager) p.thisObject, stream, dir)) {
                    block(p, "adjustSuggestedStreamVolume(stream=" + stream + ", dir=" + dir + ")");
                }
            }
        }, int.class, int.class, int.class);

        // 已废弃但仍可用：setStreamMute(int streamType, boolean state)，拦截"取消静音"
        safeHook(AudioManager.class, "setStreamMute", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (userJustPressed()) return;
                if (!(boolean) p.args[1]) {
                    block(p, "setStreamMute(stream=" + p.args[0] + ", false)");
                }
            }
        }, int.class, boolean.class);

        // 隐藏 API：setVolumeIndexForAttributes(AudioAttributes, int index, int flags)
        try {
            XposedBridge.hookAllMethods(AudioManager.class, "setVolumeIndexForAttributes", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (userJustPressed()) return;
                    block(p, "setVolumeIndexForAttributes(index=" + (p.args.length > 1 ? p.args[1] : "?") + ")");
                }
            });
        } catch (Throwable t) {
            log("hook setVolumeIndexForAttributes skipped: " + t);
        }
    }

    private static boolean isRaise(AudioManager am, int stream, int dir) {
        if (dir == AudioManager.ADJUST_RAISE || dir == AudioManager.ADJUST_UNMUTE) return true;
        if (dir == AudioManager.ADJUST_TOGGLE_MUTE) {
            try {
                return am.isStreamMute(stream); // 当前静音 -> toggle 等于取消静音
            } catch (Throwable ignored) {
                return true;
            }
        }
        return false;
    }

    // ---------------- MediaController（走媒体会话改音量的路径） ----------------

    private void hookMediaController() {
        safeHook(MediaController.class, "setVolumeTo", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (userJustPressed()) return;
                int value = (int) p.args[0];
                int cur = -1;
                try {
                    MediaController.PlaybackInfo info = ((MediaController) p.thisObject).getPlaybackInfo();
                    if (info != null) cur = info.getCurrentVolume();
                } catch (Throwable ignored) {
                }
                if (cur < 0 || value > cur) {
                    block(p, "MediaController.setVolumeTo(" + cur + " -> " + value + ")");
                }
            }
        }, int.class, int.class);

        safeHook(MediaController.class, "adjustVolume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                if (userJustPressed()) return;
                int dir = (int) p.args[0];
                if (dir == AudioManager.ADJUST_RAISE || dir == AudioManager.ADJUST_UNMUTE) {
                    block(p, "MediaController.adjustVolume(dir=" + dir + ")");
                }
            }
        }, int.class, int.class);
    }

    // ---------------- 工具 ----------------

    private static void safeHook(Class<?> clazz, String name, XC_MethodHook cb, Object... types) {
        try {
            Object[] args = new Object[types.length + 1];
            System.arraycopy(types, 0, args, 0, types.length);
            args[types.length] = cb;
            XposedHelpers.findAndHookMethod(clazz, name, args);
        } catch (Throwable t) {
            log("hook " + clazz.getSimpleName() + "." + name + " failed: " + t);
        }
    }

    private static void block(XC_MethodHook.MethodHookParam p, String what) {
        p.setResult(null);
        log("BLOCKED " + what + callerHint());
    }

    /** 打出抖音自己的调用栈（前几帧），方便以后定位是谁在调音量 */
    private static String callerHint() {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String c = e.getClassName();
            if (c.startsWith("java.") || c.startsWith("android.") || c.startsWith("com.android.")
                    || c.startsWith("dalvik.") || c.startsWith("de.robv.") || c.startsWith("io.github.dyvolguard")
                    || c.startsWith("org.lsposed") || c.startsWith("LSPHooker")) continue;
            sb.append("\n    at ").append(e);
            if (++n >= 6) break;
        }
        return sb.toString();
    }

    private static void log(String s) {
        XposedBridge.log(TAG + s);
    }
}
