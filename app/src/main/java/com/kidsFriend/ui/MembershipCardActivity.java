package com.kidsFriend.ui;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.R;
import com.kidsFriend.data.model.ClientResponse;
import com.kidsFriend.data.repository.RepositoryCallback;
import com.kidsFriend.data.repository.TemiRepository;
import com.kidsFriend.data.session.SessionManager;
import com.kidsFriend.voice.TemiSpeechSpeaker;
import com.robotemi.sdk.Robot;

public class MembershipCardActivity extends AppCompatActivity {
    private static final String TAG = "MembershipCardActivity";

    private final TemiSpeechSpeaker speaker = new TemiSpeechSpeaker();

    private TemiRepository repository;
    private TextView nameText;
    private TextView pointText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_membership_card);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repository = new TemiRepository(this);

        nameText = findViewById(R.id.text_member_name);
        pointText = findViewById(R.id.text_member_point);
        Button backButton = findViewById(R.id.button_back);
        backButton.setOnClickListener(v -> finish());

        loadMember();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            ActivityInfo activityInfo = getPackageManager()
                    .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            Robot.getInstance().onStart(activityInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Temi activity metadata is not available.", e);
        }
    }

    private void loadMember() {
        String clientId = SessionManager.getInstance(this).getCurrentClientId();
        repository.getClient(clientId, new RepositoryCallback<ClientResponse>() {
            @Override
            public void onSuccess(ClientResponse data) {
                nameText.setText(data.childName);
                pointText.setText(String.valueOf(data.clientPoint));
                speaker.speak(getString(R.string.membership_speak_format, data.childName, data.clientPoint));
            }

            @Override
            public void onError(String message) {
                nameText.setText(R.string.membership_load_failed);
                pointText.setText("-");
            }
        });
    }
}
