package io.github.dyvolguard;

import android.app.Application;

import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** 尽早注册监听，LSPosed 在模块 App 启动时会把服务递过来 */
public class App extends Application {

    public interface Listener {
        void onServiceChanged(XposedService service);
    }

    private static volatile XposedService service;
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService s) {
                service = s;
                for (Listener l : listeners) l.onServiceChanged(s);
            }

            @Override
            public void onServiceDied(XposedService s) {
                service = null;
                for (Listener l : listeners) l.onServiceChanged(null);
            }
        });
    }

    public static XposedService getService() {
        return service;
    }

    public static void addListener(Listener l) {
        listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }
}
