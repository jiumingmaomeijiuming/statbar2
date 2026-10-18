package de.robv.android.xposed;

/**
 * 编译期 stub —— 运行时由 LSPosed 提供真实实现。
 */
public class XposedBridge {

    public static void log(String text) {
        android.util.Log.i("Xposed", text);
    }

    public static void log(Throwable t) {
        android.util.Log.e("Xposed", "hook error", t);
    }
}
