#!/system/bin/sh
# ========================================================
#   SystemUI 状态栏类名探测
#   作者：九命猫
#
#   用途：当状态栏调整模块不生效时，用它找出你这台机器
#        SystemUI 里真实的状态栏类名和 clock / battery
#        资源 id，然后填进 MainHook 的候选列表。
#
#   用法：sh /sdcard/dump_systemui.sh
#   结果：/sdcard/Download/systemui_dump.txt
# ========================================================

OUT=/sdcard/Download/systemui_dump.txt
mkdir -p /sdcard/Download 2>/dev/null

if [ "$(id -u)" != "0" ]; then
    if command -v su >/dev/null 2>&1; then
        exec su -c "sh $0"
    fi
fi

P() { echo "$1"; echo "$1" >> "$OUT"; }
PL() { echo "$1" | sed 's/^/  /'; echo "$1" | sed 's/^/  /' >> "$OUT"; }

: > "$OUT"

P ""
P "=============================="
P "  SystemUI 状态栏类名探测"
P "  作者：九命猫"
P "=============================="
P ""
P "  时间：$(date '+%Y-%m-%d %H:%M:%S')"
P "  安卓：$(getprop ro.build.version.release)"
P "  ROM ：$(getprop ro.build.display.id)"
P ""

# ---- 1. 定位 SystemUI APK ----
P "【1】SystemUI APK 路径"
P "--------------------------------------"
APK=$(pm path com.android.systemui 2>/dev/null | sed 's/package://' | head -1)
P "  ${APK:-未找到}"
if [ -z "$APK" ]; then
    P "  ✗ 找不到 SystemUI，脚本终止"
    echo "未找到 SystemUI"; echo ""; exit 1
fi
P ""

# ---- 2. 状态栏相关类名 ----
P "【2】状态栏相关类名（重要）"
P "--------------------------------------"
P "  下面这些是 MainHook 的 hook 候选，"
P "  把实际存在的填进 MainHook.java 的 candidates 数组。"
P ""
PATTERNS="PhoneStatusBarView|StatusBarWindowView|CollapsedStatusBarFragment|StatusBarFragment|PhoneStatusBar|MiuiStatusBar"
CLASSES=$(grep -a -o -E 'com\.android\.systemui[a-zA-Z0-9_.$]*' "$APK" 2>/dev/null \
          | grep -E "$PATTERNS" | sort -u | head -40)
if [ -n "$CLASSES" ]; then
    PL "$CLASSES"
else
    P "  未匹配到（试试看下面全部含 statusbar 的类）"
fi
P ""

# ---- 3. 全部含 statusbar 的类（前 40 个）----
P "【3】全部含 statusbar 的类名（前40）"
P "--------------------------------------"
ALL=$(grep -a -o -E 'com\.android\.systemui[a-zA-Z0-9_.$]*[Ss]tatus[Bb]ar[a-zA-Z0-9_.$]*' "$APK" 2>/dev/null \
      | sort -u | head -40)
[ -n "$ALL" ] && PL "$ALL" || P "  无"
P ""

# ---- 4. clock / battery 资源名 ----
P "【4】clock / battery 候选资源名"
P "--------------------------------------"
P "  检查这些名字在你的 SystemUI 里是否存在："
P ""
for n in clock status_bar_clock miui_status_bar_clock clock_view \
         battery battery_level status_bar_battery battery_indicator \
         miui_battery battery_icon system_icons status_bar_contents; do
    # 在 resources.arsc 里搜字符串
    if grep -a -q "$n" "$APK" 2>/dev/null; then
        P "  ✓ $n"
    else
        P "  ✗ $n"
    fi
done
P ""

# ---- 5. 用 dumpsys 看当前窗口层级（备用）----
P "【5】当前状态栏窗口信息"
P "--------------------------------------"
if command -v dumpsys >/dev/null 2>&1; then
    r=$(dumpsys window 2>/dev/null | grep -i -E "StatusBar|statusbar" | head -15)
    [ -n "$r" ] && PL "$r" || P "  无输出"
else
    P "  dumpsys 不可用"
fi
P ""

# ---- 6. Xposed 日志 ----
P "【6】模块运行日志（最近30条）"
P "--------------------------------------"
if command -v logcat >/dev/null 2>&1; then
    r=$(logcat -d -v brief 2>/dev/null | grep -i -E "StatBarTuner|Xposed|LSPosed" | tail -30)
    [ -n "$r" ] && PL "$r" || P "  无相关日志"
    P ""
    P "  提示：若看到 'StatBarTuner: hook 成功: xxx' 说明 hook 点正确"
    P "        若看到 '所有 hook 候选均失败' 说明需要按【2】【3】补类名"
else
    P "  logcat 不可用"
fi
P ""

P "=============================="
P "  下一步"
P "=============================="
P ""
P "  1. 若【6】显示 hook 成功 → 模块正常，去 APP 里调参数"
P "  2. 若显示 hook 失败 → 把【2】【3】的类名发我，"
P "     我把它加进 MainHook 的候选列表重新编译"
P "  3. 若 clock/battery 都找不到 → 看【4】，"
P "     把存在的资源名补进 CLOCK_IDS / BATTERY_IDS"
P ""
P "=============================="
P "  结果已保存到：$OUT"
P "=============================="
P ""

echo ""
echo "探测完成，结果已保存到 $OUT"
echo ""
exit 0
