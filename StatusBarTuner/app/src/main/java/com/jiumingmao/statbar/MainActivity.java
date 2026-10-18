package com.jiumingmao.statbar;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 状态栏时间/电池位置调整 - 设置界面
 * 作者：九命猫
 */
public class MainActivity extends Activity {

    public static final String ACTION_APPLY = "com.jiumingmao.statbar.APPLY";
    public static final String PREF_NAME = "statbar_prefs";

    public static final String KEY_ENABLED   = "enabled";
    public static final String KEY_CLOCK_POS = "clock_pos";  // 0默认 1左 2中 3右
    public static final String KEY_CLOCK_Y   = "clock_y";    // -20 ~ 20 dp
    public static final String KEY_BATT_POS  = "batt_pos";   // 0默认 1左 2右
    public static final String KEY_BATT_Y    = "batt_y";     // -20 ~ 20 dp
    public static final String KEY_CLOCK_X   = "clock_x";    // -200 ~ 200 dp
    public static final String KEY_BATT_X    = "batt_x";     // -200 ~ 200 dp

    private Switch swEnabled;
    private Spinner spClockPos, spBattPos;
    private SeekBar sbClockX, sbClockY, sbBattX, sbBattY;
    private TextView tvClockX, tvClockY, tvBattX, tvBattY;
    private Button btnApply, btnReset, btnRestartUi;

    private SharedPreferences prefs;

    private final String[] clockPosItems = {"默认(不动)", "居左", "居中", "居右"};
    private final String[] battPosItems  = {"默认(不动)", "移到左侧", "移到最右"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        findViews();
        setupSpinners();
        setupSeekBars();
        loadPrefs();
        setupButtons();
    }

    private void findViews() {
        swEnabled  = findViewById(R.id.swEnabled);
        spClockPos = findViewById(R.id.spClockPos);
        spBattPos  = findViewById(R.id.spBattPos);
        sbClockX   = findViewById(R.id.sbClockX);
        sbClockY   = findViewById(R.id.sbClockY);
        sbBattX    = findViewById(R.id.sbBattX);
        sbBattY    = findViewById(R.id.sbBattY);
        tvClockX   = findViewById(R.id.tvClockX);
        tvClockY   = findViewById(R.id.tvClockY);
        tvBattX    = findViewById(R.id.tvBattX);
        tvBattY    = findViewById(R.id.tvBattY);
        btnApply   = findViewById(R.id.btnApply);
        btnReset   = findViewById(R.id.btnReset);
        btnRestartUi = findViewById(R.id.btnRestartUi);
    }

    private void setupSpinners() {
        ArrayAdapter<String> a1 = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, clockPosItems);
        spClockPos.setAdapter(a1);

        ArrayAdapter<String> a2 = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, battPosItems);
        spBattPos.setAdapter(a2);
    }

    private void setupSeekBars() {
        // 左右微调 -200 ~ 200 dp
        setRange(sbClockX, tvClockX, "时间左右", -200, 200);
        setRange(sbBattX,  tvBattX,  "电池左右", -200, 200);
        // 上下偏移 -20 ~ 20 dp
        setRange(sbClockY, tvClockY, "时间上下", -20, 20);
        setRange(sbBattY,  tvBattY,  "电池上下", -20, 20);
    }

    private void setRange(SeekBar sb, TextView tv, final String label, final int min, final int max) {
        sb.setMax(max - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tv.setText(label + "：" + (progress + min) + " dp");
            }
        });
    }

    private int sbValue(SeekBar sb, int min) {
        return sb.getProgress() + min;
    }

    private void sbSet(SeekBar sb, TextView tv, String label, int value, int min, int max) {
        int p = value - min;
        if (p < 0) p = 0;
        if (p > max - min) p = max - min;
        sb.setProgress(p);
        tv.setText(label + "：" + value + " dp");
    }

    private void loadPrefs() {
        boolean en    = prefs.getBoolean(KEY_ENABLED, true);
        int clockPos  = prefs.getInt(KEY_CLOCK_POS, 0);
        int clockY    = prefs.getInt(KEY_CLOCK_Y, 0);
        int battPos   = prefs.getInt(KEY_BATT_POS, 0);
        int battY     = prefs.getInt(KEY_BATT_Y, 0);
        int clockX    = prefs.getInt(KEY_CLOCK_X, 0);
        int battX     = prefs.getInt(KEY_BATT_X, 0);

        swEnabled.setChecked(en);
        spClockPos.setSelection(clockPos);
        spBattPos.setSelection(battPos);
        sbSet(sbClockX, tvClockX, "时间左右", clockX, -200, 200);
        sbSet(sbClockY, tvClockY, "时间上下", clockY, -20, 20);
        sbSet(sbBattX,  tvBattX,  "电池左右", battX, -200, 200);
        sbSet(sbBattY,  tvBattY,  "电池上下", battY, -20, 20);
    }

    private void savePrefs() {
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(KEY_ENABLED,   swEnabled.isChecked());
        e.putInt(KEY_CLOCK_POS, spClockPos.getSelectedItemPosition());
        e.putInt(KEY_CLOCK_Y,   sbValue(sbClockY, -20));
        e.putInt(KEY_BATT_POS,  spBattPos.getSelectedItemPosition());
        e.putInt(KEY_BATT_Y,    sbValue(sbBattY, -20));
        e.putInt(KEY_CLOCK_X,   sbValue(sbClockX, -200));
        e.putInt(KEY_BATT_X,    sbValue(sbBattX, -200));
        e.apply();
    }

    private void broadcastApply() {
        Intent i = new Intent(ACTION_APPLY);
        i.setPackage("com.android.systemui");
        i.putExtra(KEY_ENABLED,   swEnabled.isChecked());
        i.putExtra(KEY_CLOCK_POS, spClockPos.getSelectedItemPosition());
        i.putExtra(KEY_CLOCK_Y,   sbValue(sbClockY, -20));
        i.putExtra(KEY_BATT_POS,  spBattPos.getSelectedItemPosition());
        i.putExtra(KEY_BATT_Y,    sbValue(sbBattY, -20));
        i.putExtra(KEY_CLOCK_X,   sbValue(sbClockX, -200));
        i.putExtra(KEY_BATT_X,    sbValue(sbBattX, -200));
        sendBroadcast(i);
    }

    private void setupButtons() {
        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                savePrefs();
                broadcastApply();
                Toast.makeText(MainActivity.this, "已应用，若没变化点『重启系统界面』", Toast.LENGTH_LONG).show();
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                SharedPreferences.Editor e = prefs.edit();
                e.putBoolean(KEY_ENABLED, true);
                e.putInt(KEY_CLOCK_POS, 0);
                e.putInt(KEY_CLOCK_Y, 0);
                e.putInt(KEY_BATT_POS, 0);
                e.putInt(KEY_BATT_Y, 0);
                e.putInt(KEY_CLOCK_X, 0);
                e.putInt(KEY_BATT_X, 0);
                e.apply();
                loadPrefs();
                broadcastApply();
                Toast.makeText(MainActivity.this, "已恢复默认", Toast.LENGTH_SHORT).show();
            }
        });

        btnRestartUi.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    // 需要 root，用 su 重启 systemui
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "killall com.android.systemui"});
                    p.waitFor();
                    Toast.makeText(MainActivity.this, "系统界面已重启", Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "重启失败：" + ex.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
