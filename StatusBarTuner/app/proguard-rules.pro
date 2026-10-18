# 保留 Xposed 入口类
-keep class com.jiumingmao.statbar.MainHook { *; }
# 不要在 release 里混淆资源 id
-keepclassmembers class **.R$* {
    public static <fields>;
}
