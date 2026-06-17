package com.kidsFriend.domain.zone;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.R;
import com.kidsFriend.domain.zone.ZoneInfo;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.robotemi.sdk.Robot;

import java.util.List;

public class ZoneActivity extends AppCompatActivity {
    private static final String TAG = "ZoneActivity";
    private TemiRepository repository;
    private TextView zoneListText;
    private TextView zoneStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zone);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repository = new TemiRepository(this);

        zoneListText = findViewById(R.id.text_zone_list);
        zoneStatusText = findViewById(R.id.text_zone_status);
        Button reloadButton = findViewById(R.id.button_reload_zones);
        Button backButton = findViewById(R.id.button_back);

        reloadButton.setOnClickListener(v -> loadZones());
        backButton.setOnClickListener(v -> finish());

        loadZones();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            ActivityInfo activityInfo = getPackageManager()
                    .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            Robot.getInstance().onStart(activityInfo);
            Log.d(TAG, "Temi onStart: Sovereignty declared.");
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Temi activity metadata is not available.", e);
        }
    }

    private void loadZones() {
        zoneStatusText.setText(R.string.common_loading);
        repository.getZones(new RepositoryCallback<List<ZoneInfo>>() {
            @Override
            public void onSuccess(List<ZoneInfo> data) {
                zoneListText.setText(formatZones(data));
                zoneStatusText.setText(getString(R.string.zone_count_format, data.size()));
            }

            @Override
            public void onError(String message) {
                zoneStatusText.setText(message);
            }
        });
    }

    private String formatZones(List<ZoneInfo> zones) {
        StringBuilder builder = new StringBuilder();
        for (ZoneInfo zone : zones) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(zone.name)
                    .append(" (")
                    .append(zone.zoneId)
                    .append(")\n")
                    .append("- 설명: ")
                    .append(zone.description)
                    .append("\n- 근처: ")
                    .append(zone.nearbyLocations)
                    .append("\n- 혼잡도: ")
                    .append(zone.crowdLevel)
                    .append("\n- 안내: ")
                    .append(zone.guideHint);
        }
        return builder.toString();
    }
}
