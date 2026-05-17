package com.kidsFriend;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.kidsFriend.data.api.RetrofitClient;
import com.kidsFriend.data.config.AppConfig;
import com.kidsFriend.data.model.ClientResponse;
import com.kidsFriend.data.repository.RepositoryCallback;
import com.kidsFriend.data.repository.TemiRepository;
import com.kidsFriend.data.session.SessionManager;
import com.kidsFriend.ui.CallActivity;
import com.kidsFriend.ui.QuestionActivity;
import com.kidsFriend.ui.QuizActivity;
import com.kidsFriend.ui.StatisticsActivity;
import com.kidsFriend.ui.ZoneActivity;
import com.kidsFriend.voice.VoiceInputManager;
import com.kidsFriend.voice.WakeWordMatcher;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.listeners.OnRobotReadyListener;

import java.util.List;

public class ApiTestActivity extends AppCompatActivity implements OnRobotReadyListener {
    private static final String TAG = "ApiTestActivity";
    private static final int REQUEST_RECORD_AUDIO = 1001;
    private static final int LOW_BATTERY_PERCENT = 20;
    private static boolean errorReporterInstalled;

    private TemiRepository repository;
    private VoiceInputManager wakeVoiceInputManager;
    private TextView wakeStatusText;
    private BroadcastReceiver batteryReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_test);
        repository = new TemiRepository(this);
        
        Button callButton = findViewById(R.id.button_call);
        Button questionButton = findViewById(R.id.button_question);
        Button quizButton = findViewById(R.id.button_quiz);
        Button statisticsButton = findViewById(R.id.button_statistics);
        Button approachButton = findViewById(R.id.button_approach);
        Button locationGuideButton = findViewById(R.id.button_location_guide);
        Button ttsButton = findViewById(R.id.button_tts);
        Button rewardButton = findViewById(R.id.button_reward);
        Button photoButton = findViewById(R.id.button_photo);
        Button recyclingButton = findViewById(R.id.button_recycling);
        Button serverSettingsButton = findViewById(R.id.button_server_settings);
        Button clientSettingsButton = findViewById(R.id.button_client_settings);
        wakeStatusText = findViewById(R.id.text_wake_status);
        Button wakeButton = findViewById(R.id.button_start_wake_listening);

        wakeVoiceInputManager = new VoiceInputManager(this);

        callButton.setOnClickListener(v -> openCallWaitingScreen());
        questionButton.setOnClickListener(v -> openScreen(QuestionActivity.class));
        quizButton.setOnClickListener(v -> openScreen(QuizActivity.class));
        statisticsButton.setOnClickListener(v -> openScreen(StatisticsActivity.class));
        approachButton.setOnClickListener(v -> showPendingFeature());
        locationGuideButton.setOnClickListener(v -> openScreen(ZoneActivity.class));
        ttsButton.setOnClickListener(v -> showPendingFeature());
        rewardButton.setOnClickListener(v -> showPendingFeature());
        photoButton.setOnClickListener(v -> showPhotoUrlDialog());
        recyclingButton.setOnClickListener(v -> showPendingFeature());
        serverSettingsButton.setOnClickListener(v -> showServerSettingsDialog());
        clientSettingsButton.setOnClickListener(v -> showClientSettingsDialog());
        wakeButton.setOnClickListener(v -> startWakeWordStandby());

        ensureAudioPermission();
    }

    private void openScreen(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    private void openCallWaitingScreen() {
        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtra(CallActivity.EXTRA_AUTO_SUBMIT_CALL, true);
        startActivity(intent);
    }

    private void showPendingFeature() {
        Toast.makeText(this, R.string.feature_pending_message, Toast.LENGTH_SHORT).show();
    }

    private void showServerSettingsDialog() {
        AppConfig appConfig = AppConfig.init(this);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(appConfig.getBaseUrl());
        input.setSelection(input.getText().length());
        input.setHint("192.168.1.100");

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_server_title)
                .setMessage(R.string.settings_server_message)
                .setView(input)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    String newBaseUrl = appConfig.buildBaseUrlFromIp(input.getText().toString());
                    appConfig.updateBaseUrl(newBaseUrl);
                    RetrofitClient.resetInstance();
                    repository = new TemiRepository(this);
                    Toast.makeText(this, R.string.settings_server_saved, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showClientSettingsDialog() {
        repository.getClients(new RepositoryCallback<List<ClientResponse>>() {
            @Override
            public void onSuccess(List<ClientResponse> data) {
                showClientListDialog(data);
            }

            @Override
            public void onError(String message) {
                showManualClientIdDialog();
            }
        });
    }

    private void showClientListDialog(List<ClientResponse> clients) {
        if (clients == null || clients.isEmpty()) {
            showManualClientIdDialog();
            return;
        }

        String[] labels = new String[clients.size()];
        for (int i = 0; i < clients.size(); i++) {
            ClientResponse client = clients.get(i);
            String childName = client.childName == null || client.childName.trim().isEmpty()
                    ? client.clientId
                    : client.childName;
            labels[i] = childName + " / " + client.clientPoint + "pt";
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_client_title)
                .setItems(labels, (dialog, which) -> {
                    ClientResponse selected = clients.get(which);
                    SessionManager.getInstance(this).setCurrentClientId(selected.clientId);
                    Toast.makeText(this, R.string.settings_client_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .setNeutralButton(R.string.settings_client_manual, (dialog, which) -> showManualClientIdDialog())
                .show();
    }

    private void showManualClientIdDialog() {
        SessionManager sessionManager = SessionManager.getInstance(this);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(sessionManager.getCurrentClientId());
        input.setSelection(input.getText().length());
        input.setHint("client UUID");

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_client_title)
                .setMessage(R.string.settings_client_message)
                .setView(input)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    String clientId = input.getText().toString().trim();
                    if (clientId.isEmpty()) {
                        Toast.makeText(this, R.string.settings_client_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    loadAndSaveCurrentClient(clientId);
                })
                .show();
    }

    private void loadAndSaveCurrentClient(String clientId) {
        repository.getClient(clientId, new RepositoryCallback<ClientResponse>() {
            @Override
            public void onSuccess(ClientResponse data) {
                SessionManager.getInstance(ApiTestActivity.this).setCurrentClientId(data.clientId);
                Toast.makeText(ApiTestActivity.this, R.string.settings_client_saved, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ApiTestActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPhotoUrlDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("https://storage.example.com/photo.jpg");

        new AlertDialog.Builder(this)
                .setTitle(R.string.photo_url_title)
                .setMessage(R.string.photo_url_message)
                .setView(input)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    String photoUrl = input.getText().toString().trim();
                    if (photoUrl.isEmpty()) {
                        Toast.makeText(this, R.string.photo_url_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    savePhotoUrl(photoUrl);
                })
                .show();
    }

    private void savePhotoUrl(String photoUrl) {
        String photoName = photoUrl.substring(photoUrl.lastIndexOf('/') + 1);
        repository.savePhoto(photoUrl, photoName, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(ApiTestActivity.this, R.string.photo_saved, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ApiTestActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRobotStatus("ACTIVE");
        if (hasAudioPermission()) {
            startWakeWordStandby();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        wakeVoiceInputManager.stopListening();
        updateRobotStatus("INACTIVE");
    }

    @Override
    protected void onDestroy() {
        wakeVoiceInputManager.destroy();
        try {
            Robot.getInstance().removeOnRobotReadyListener(this);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Temi listener removal failed.", exception);
        }
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
        super.onDestroy();
    }

    @Override
    public void onRobotReady(boolean isReady) {
        if (isReady) {
            try {
                ActivityInfo activityInfo = getPackageManager()
                        .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                Robot.getInstance().onStart(activityInfo);
            } catch (PackageManager.NameNotFoundException exception) {
                Log.w(TAG, "Temi activity metadata is not available.", exception);
            }
        }
        updateRobotStatus(isReady ? "ACTIVE" : "INACTIVE");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) {
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWakeWordStandby();
        } else {
            wakeStatusText.setText(R.string.voice_permission_denied);
        }
    }

    private void ensureAudioPermission() {
        if (hasAudioPermission()) {
            return;
        }
        wakeStatusText.setText(R.string.voice_permission_required);
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO
        );
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startWakeWordStandby() {
        if (!hasAudioPermission()) {
            ensureAudioPermission();
            return;
        }
        if (!VoiceInputManager.isRecognitionAvailable(this)) {
            wakeStatusText.setText(R.string.voice_recognition_unavailable);
            return;
        }

        wakeStatusText.setText(R.string.voice_wake_standby);
        wakeVoiceInputManager.startContinuousListening(new VoiceInputManager.Callback() {
            @Override
            public void onReady() {
                wakeStatusText.setText(R.string.voice_wake_standby);
            }

            @Override
            public void onPartialResult(String text) {
                if (text == null || text.trim().isEmpty()) {
                    return;
                }
                wakeStatusText.setText(getString(R.string.voice_heard_format, text));
            }

            @Override
            public void onResult(String text) {
                if (!WakeWordMatcher.containsWakeWord(text)) {
                    wakeStatusText.setText(getString(R.string.voice_heard_format, text));
                    return;
                }
                wakeVoiceInputManager.stopListening();
                wakeStatusText.setText(R.string.voice_wake_detected);
                Intent intent = new Intent(ApiTestActivity.this, QuestionActivity.class);
                intent.putExtra(QuestionActivity.EXTRA_START_VOICE_LISTENING, true);
                intent.putExtra(QuestionActivity.EXTRA_INITIAL_VOICE_TEXT, WakeWordMatcher.textAfterWakeWord(text));
                startActivity(intent);
            }

            @Override
            public void onError(String message) {
                wakeStatusText.setText(message);
            }
        });
    }

    private void updateRobotStatus(String status) {
        if (repository == null) {
            return;
        }
        repository.updateRobotStatus(status, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Log.d(TAG, "Robot status updated: " + status);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Robot status update failed: " + message);
            }
        });
    }

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level < 0 || scale <= 0) {
                    return;
                }

                int batteryPercent = Math.round(level * 100f / scale);
                if (batteryPercent <= LOW_BATTERY_PERCENT) {
                    updateRobotStatus("INACTIVE");
                }
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void installUnexpectedErrorReporter() {
        if (errorReporterInstalled) {
            return;
        }
        errorReporterInstalled = true;
        Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            updateRobotStatus("ERROR");
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            }
        });
    }
}
