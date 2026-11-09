package io.github.dyvolguard;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import io.github.libxposed.service.XposedService;

public class MainActivity extends Activity implements App.Listener {

    private LinearLayout statusCard;
    private TextView statusTitle, statusDetail, windowValue;
    private Switch swEnabled, swLog;
    private SeekBar sbWindow;

    private SharedPreferences prefs; // LSPosed 远程配置，未激活时为 null
    private boolean binding = false;  // 回填界面时不触发写入

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusCard = findViewById(R.id.statusCard);
        statusTitle = findViewById(R.id.statusTitle);
        statusDetail = findViewById(R.id.statusDetail);
        windowValue = findViewById(R.id.windowValue);
        swEnabled = findViewById(R.id.swEnabled);
        swLog = findViewById(R.id.swLog);
        sbWindow = findViewById(R.id.sbWindow);
        Button btnRestart = findViewById(R.id.btnRestart);

        ((TextView) findViewById(R.id.version)).setText("v" + BuildConfigHolder.versionName(this) + " · libxposed API 102");

        swEnabled.setOnCheckedChangeListener((b, on) -> put(e -> e.putBoolean(Prefs.ENABLED, on)));
        swLog.setOnCheckedChangeListener((b, on) -> put(e -> e.putBoolean(Prefs.LOG, on)));
        sbWindow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int ms = Prefs.WINDOW_STEPS[p];
                windowValue.setText(formatMs(ms));
                if (fromUser) put(e -> e.putInt(Prefs.KEY_WINDOW_MS, ms));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        btnRestart.setOnClickListener(v -> restartDouyin());

        onServiceChanged(App.getService());
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.addListener(this);
        onServiceChanged(App.getService());
    }

    @Override
    protected void onPause() {
        super.onPause();
        App.removeListener(this);
    }

    @Override
    public void onServiceChanged(XposedService service) {
        runOnUiThread(() -> render(service));
    }

    private void render(XposedService service) {
        prefs = null;
        if (service != null) {
            try {
                prefs = service.getRemotePreferences(Prefs.GROUP);
            } catch (Throwable ignored) {
            }
        }

        boolean active = service != null;
        statusCard.setBackgroundResource(active ? R.drawable.bg_status_ok : R.drawable.bg_status_bad);
        int fg = getColor(active ? R.color.ok_text : R.color.bad_text);
        statusTitle.setTextColor(fg);
        statusDetail.setTextColor(fg);
        if (active) {
            statusTitle.setText("已激活");
            String fw;
            try {
                fw = service.getFrameworkName() + " " + service.getFrameworkVersion();
            } catch (Throwable t) {
                fw = "LSPosed";
            }
            statusDetail.setText(fw + " · 作用域：抖音");
        } else {
            statusTitle.setText("未激活");
            statusDetail.setText("请在 LSPosed 中启用本模块并勾选抖音");
        }

        binding = true;
        boolean enabled = prefs == null ? Prefs.DEF_ENABLED : prefs.getBoolean(Prefs.ENABLED, Prefs.DEF_ENABLED);
        boolean log = prefs == null ? Prefs.DEF_LOG : prefs.getBoolean(Prefs.LOG, Prefs.DEF_LOG);
        int ms = prefs == null ? Prefs.DEF_KEY_WINDOW_MS : prefs.getInt(Prefs.KEY_WINDOW_MS, Prefs.DEF_KEY_WINDOW_MS);
        swEnabled.setChecked(enabled);
        swLog.setChecked(log);
        sbWindow.setProgress(stepOf(ms));
        windowValue.setText(formatMs(ms));
        binding = false;

        swEnabled.setEnabled(prefs != null);
        swLog.setEnabled(prefs != null);
        sbWindow.setEnabled(prefs != null);
    }

    private interface Edit { void apply(SharedPreferences.Editor e); }

    private void put(Edit edit) {
        if (binding || prefs == null) return;
        SharedPreferences.Editor e = prefs.edit();
        edit.apply(e);
        e.apply();
    }

    private void restartDouyin() {
        new Thread(() -> {
            boolean ok;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{
                        "su", "-c",
                        "am force-stop com.ss.android.ugc.aweme; monkey -p com.ss.android.ugc.aweme -c android.intent.category.LAUNCHER 1"
                });
                ok = p.waitFor() == 0;
            } catch (Throwable t) {
                ok = false;
            }
            boolean finalOk = ok;
            runOnUiThread(() -> Toast.makeText(this,
                    finalOk ? "抖音已重启" : "重启失败，请检查 root 授权",
                    Toast.LENGTH_SHORT).show());
        }).start();
    }

    private static int stepOf(int ms) {
        int best = 0;
        for (int i = 0; i < Prefs.WINDOW_STEPS.length; i++) {
            if (Math.abs(Prefs.WINDOW_STEPS[i] - ms) < Math.abs(Prefs.WINDOW_STEPS[best] - ms)) best = i;
        }
        return best;
    }

    private static String formatMs(int ms) {
        if (ms == 0) return "不放行";
        return (ms % 1000 == 0 ? String.valueOf(ms / 1000) : String.valueOf(ms / 1000f)) + " 秒";
    }
}
