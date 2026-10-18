package com.jiumingmao.statbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 状态栏时间/电池位置调整 —— Xposed Hook 入口
 * 作者：九命猫
 *
 * 作用域：com.android.systemui
 *
 * 实现方式：
 *   - 通过资源 id 定位 clock / battery 视图（跨 ROM 兼容）
 *   - 用 translationX / translationY 做左右与上下位移
 *   - 不走 reparent，避免 SystemUI 崩溃
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "StatBarTuner";
    private static final String PKG_UI = "com.android.systemui";

    // clock 可能的资源 id
    private static final String[] CLOCK_IDS = {
            "clock", "status_bar_clock", "miui_status_bar_clock",
            "clock_view", "statusbar_clock", "miui_clock"
    };

    // battery 可能的资源 id
    private static final String[] BATTERY_IDS = {
            "battery", "battery_level", "status_bar_battery",
            "battery_indicator", "miui_battery", "battery_icon"
    };

    /** 配置 */
    static class Config {
        boolean enabled = true;
        int clockPos = 0;   // 0默认 1左 2中 3右
        int clockY = 0;     // dp
        int clockX = 0;     // dp
        int battPos = 0;    // 0默认 1左 2右
        int battY = 0;      // dp
        int battX = 0;      // dp

        void load(SharedPreferences sp) {
            enabled = sp.getBoolean(MainActivity.KEY_ENABLED, true);
            clockPos = sp.getInt(MainActivity.KEY_CLOCK_POS, 0);
            clockY   = sp.getInt(MainActivity.KEY_CLOCK_Y, 0);
            clockX   = sp.getInt(MainActivity.KEY_CLOCK_X, 0);
            battPos  = sp.getInt(MainActivity.KEY_BATT_POS, 0);
            battY    = sp.getInt(MainActivity.KEY_BATT_Y, 0);
            battX    = sp.getInt(MainActivity.KEY_BATT_X, 0);
        }

        void save(SharedPreferences sp) {
            SharedPreferences.Editor e = sp.edit();
            e.putBoolean(MainActivity.KEY_ENABLED, enabled);
            e.putInt(MainActivity.KEY_CLOCK_POS, clockPos);
            e.putInt(MainActivity.KEY_CLOCK_Y, clockY);
            e.putInt(MainActivity.KEY_CLOCK_X, clockX);
            e.putInt(MainActivity.KEY_BATT_POS, battPos);
            e.putInt(MainActivity.KEY_BATT_Y, battY);
            e.putInt(MainActivity.KEY_BATT_X, battX);
            e.apply();
        }
    }

    static Config cfg = new Config();
    static View sClockView = null;
    static View sBatteryView = null;
    static View sStatusBarView = null;
    static Context sContext = null;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG_UI.equals(lpparam.packageName)) return;

        // ---------- 1. 注册广播接收器（热更新，无需重启）----------
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.systemui.SystemUIApplication",
                    lpparam.classLoader,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Context ctx = (Context) param.thisObject;
                            sContext = ctx.getApplicationContext();
                            cfg.load(sContext.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE));
                            registerReceiver(sContext);
                        }
                    });
        } catch (Throwable t) {
            XposedBridgeLog("SystemUIApplication hook 失败，改用延迟注册: " + t.getMessage());
            // 兜底：在 statusbar 创建时再注册
        }

        // ---------- 2. Hook 状态栏视图 ----------
        hookStatusBar(lpparam);
    }

    private void hookStatusBar(final XC_LoadPackage.LoadPackageParam lpparam) {
        // 候选 hook 点，逐个尝试，成功即止
        String[][] candidates = {
                {"com.android.systemui.statusbar.phone.CollapsedStatusBarFragment", "onViewCreated"},
                {"com.android.systemui.statusbar.phone.PhoneStatusBarView", "onFinishInflate"},
                {"com.android.systemui.statusbar.window.StatusBarWindowView", "onFinishInflate"},
                {"com.android.systemui.statusbar.phone.StatusBarWindowView", "onFinishInflate"},
        };

        for (String[] c : candidates) {
            try {
                if ("onViewCreated".equals(c[1])) {
                    XposedHelpers.findAndHookMethod(c[0], lpparam.classLoader, "onViewCreated",
                            View.class, Bundle.class, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    View root = (View) param.args[0];
                                    if (root != null) onStatusBarReady(root);
                                }
                            });
                    XposedBridgeLog("hook 成功: " + c[0] + "." + c[1]);
                    return;
                } else {
                    XposedHelpers.findAndHookMethod(c[0], lpparam.classLoader, "onFinishInflate",
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    View root = (View) param.thisObject;
                                    if (root != null) onStatusBarReady(root);
                                }
                            });
                    XposedBridgeLog("hook 成功: " + c[0] + "." + c[1]);
                    return;
                }
            } catch (Throwable ignored) {
                // 换下一个候选
            }
        }
        XposedBridgeLog("所有 hook 候选均失败，请检查 SystemUI 类名");
    }

    private static void registerReceiver(Context ctx) {
        try {
            IntentFilter f = new IntentFilter(MainActivity.ACTION_APPLY);
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!MainActivity.ACTION_APPLY.equals(intent.getAction())) return;
                    cfg.enabled  = intent.getBooleanExtra(MainActivity.KEY_ENABLED, true);
                    cfg.clockPos = intent.getIntExtra(MainActivity.KEY_CLOCK_POS, 0);
                    cfg.clockY   = intent.getIntExtra(MainActivity.KEY_CLOCK_Y, 0);
                    cfg.clockX   = intent.getIntExtra(MainActivity.KEY_CLOCK_X, 0);
                    cfg.battPos  = intent.getIntExtra(MainActivity.KEY_BATT_POS, 0);
                    cfg.battY    = intent.getIntExtra(MainActivity.KEY_BATT_Y, 0);
                    cfg.battX    = intent.getIntExtra(MainActivity.KEY_BATT_X, 0);

                    Context app = context.getApplicationContext();
                    if (app != null) {
                        cfg.save(app.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE));
                    }
                    applyAll();
                    XposedBridgeLog("收到配置更新，已应用");
                }
            }, f, Context.RECEIVER_NOT_EXPORTED);
            XposedBridgeLog("广播接收器已注册");
        } catch (Throwable t) {
            XposedBridgeLog("广播注册失败: " + t.getMessage());
        }
    }

    private static void onStatusBarReady(final View root) {
        sStatusBarView = root;
        if (sContext == null) sContext = root.getContext().getApplicationContext();

        root.post(new Runnable() {
            @Override public void run() {
                try {
                    Resources res = root.getResources();
                    sClockView   = findView(res, root, CLOCK_IDS);
                    sBatteryView = findView(res, root, BATTERY_IDS);

                    XposedBridgeLog("clock=" + (sClockView != null)
                            + " battery=" + (sBatteryView != null));

                    applyAll();

                    // 布局变化（旋转、图标增减）后重新计算
                    root.getViewTreeObserver().addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override public void onGlobalLayout() {
                                    applyAll();
                                }
                            });
                } catch (Throwable t) {
                    XposedBridgeLog("apply 异常: " + t.getMessage());
                }
            }
        });
    }

    private static View findView(Resources res, View root, String[] names) {
        for (String n : names) {
            try {
                int id = res.getIdentifier(n, "id", PKG_UI);
                if (id == 0) continue;
                View v = root.findViewById(id);
                if (v != null) {
                    XposedBridgeLog("找到视图: " + n);
                    return v;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 应用全部设置 */
    private static void applyAll() {
        if (!cfg.enabled) {
            resetView(sClockView);
            resetView(sBatteryView);
            return;
        }
        if (sStatusBarView == null) return;

        int screenW = getScreenWidth();
        float density = sStatusBarView.getResources().getDisplayMetrics().density;
        int padStart = sStatusBarView.getPaddingStart();
        int padEnd   = sStatusBarView.getPaddingEnd();

        // ---- 时间 ----
        if (sClockView != null) {
            applyPos(sClockView, cfg.clockPos, screenW, padStart, padEnd,
                    cfg.clockX * density, cfg.clockY * density);
        }

        // ---- 电池 ----
        if (sBatteryView != null) {
            applyPos(sBatteryView, cfg.battPos, screenW, padStart, padEnd,
                    cfg.battX * density, cfg.battY * density);
        }
    }

    /**
     * @param pos 0默认 1左 2中 3右（电池只有 0默认/1左/2右，2对电池=最右）
     */
    private static void applyPos(View v, int pos, int screenW,
                                 int padStart, int padEnd,
                                 float extraX, float extraY) {
        if (v == null) return;
        try {
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            int w = v.getMeasuredWidth();
            if (w == 0) w = v.getWidth();

            float deltaX = 0f;

            if (pos == 1) {
                // 居左
                float target = padStart;
                deltaX = target - loc[0];
            } else if (pos == 2) {
                // 居中
                float target = screenW / 2f - (w / 2f);
                deltaX = target - loc[0];
            } else if (pos == 3) {
                // 居右
                float target = screenW - padEnd - w;
                deltaX = target - loc[0];
            }

            v.setTranslationX(deltaX + extraX);
            v.setTranslationY(extraY);

            // 保证绘制在其他图标之上
            v.setTranslationZ(5f);

        } catch (Throwable t) {
            XposedBridgeLog("applyPos 异常: " + t.getMessage());
        }
    }

    private static void resetView(View v) {
        if (v == null) return;
        try {
            v.setTranslationX(0f);
            v.setTranslationY(0f);
            v.setTranslationZ(0f);
        } catch (Throwable ignored) {
        }
    }

    private static int getScreenWidth() {
        try {
            if (sContext == null) return 0;
            WindowManager wm = (WindowManager) sContext.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return 0;
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            return dm.widthPixels;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void XposedBridgeLog(String msg) {
        de.robv.android.xposed.XposedBridge.log(TAG + ": " + msg);
    }
}
