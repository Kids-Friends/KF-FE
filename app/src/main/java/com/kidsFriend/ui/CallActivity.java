package com.kidsFriend.ui;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.R;
import com.kidsFriend.data.model.CallResponse;
import com.kidsFriend.data.repository.RepositoryCallback;
import com.kidsFriend.data.repository.TemiRepository;
import com.robotemi.sdk.Robot;

public class CallActivity extends AppCompatActivity {
    public static final String EXTRA_AUTO_SUBMIT_CALL = "com.kidsFriend.ui.CallActivity.EXTRA_AUTO_SUBMIT_CALL";
    private static final String TAG = "CallActivity";

    private TemiRepository repository;
    private Button helpButton;
    private Button injuryButton;
    private Button lostItemButton;
    private Button etcButton;
    private Button retryButton;
    private LinearLayout formPanel;
    private LinearLayout waitingPanel;
    private TextView statusText;
    private TextView waitingTitleText;
    private TextView waitingReasonText;
    private TextView waitingStatusText;
    private String selectedReason;
    private String currentCallId;
    private Robot robot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        repository = new TemiRepository(this);

        helpButton = findViewById(R.id.button_reason_help);
        injuryButton = findViewById(R.id.button_reason_injury);
        lostItemButton = findViewById(R.id.button_reason_lost_item);
        etcButton = findViewById(R.id.button_reason_etc);
        Button submitButton = findViewById(R.id.button_submit_call);
        Button backButton = findViewById(R.id.button_back);
        Button waitingBackButton = findViewById(R.id.button_waiting_back);
        Button waitingDoneButton = findViewById(R.id.button_waiting_done);
        retryButton = findViewById(R.id.button_retry_call);
        formPanel = findViewById(R.id.panel_call_form);
        waitingPanel = findViewById(R.id.panel_call_waiting);
        statusText = findViewById(R.id.text_call_status);
        waitingTitleText = findViewById(R.id.text_call_waiting_title);
        waitingReasonText = findViewById(R.id.text_call_waiting_reason);
        waitingStatusText = findViewById(R.id.text_call_waiting_status);

        helpButton.setOnClickListener(v -> selectReason(getString(R.string.call_reason_help), helpButton));
        injuryButton.setOnClickListener(v -> selectReason(getString(R.string.call_reason_injury), injuryButton));
        lostItemButton.setOnClickListener(v -> selectReason(getString(R.string.call_reason_lost_item), lostItemButton));
        etcButton.setOnClickListener(v -> selectReason(getString(R.string.call_reason_etc), etcButton));
        submitButton.setOnClickListener(v -> submitCall());
        retryButton.setOnClickListener(v -> submitCall());
        backButton.setOnClickListener(v -> finish());
        waitingBackButton.setOnClickListener(v -> cancelWaitingCallAndFinish());
        waitingDoneButton.setOnClickListener(v -> completeWaitingCallAndFinish());

        if (getIntent().getBooleanExtra(EXTRA_AUTO_SUBMIT_CALL, false)) {
            selectedReason = getString(R.string.call_default_reason);
            submitCall();
        }
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

    private void selectReason(String reason, Button selectedButton) {
        selectedReason = reason;
        helpButton.setSelected(false);
        injuryButton.setSelected(false);
        lostItemButton.setSelected(false);
        etcButton.setSelected(false);
        selectedButton.setSelected(true);
        statusText.setText(reason + " 선택됨");
    }

    private void submitCall() {
        if (selectedReason == null) {
            statusText.setText(R.string.call_select_reason_message);
            return;
        }

        showWaitingState(R.string.call_waiting_title, getString(R.string.common_loading), false);
        repository.createCall(selectedReason, new RepositoryCallback<CallResponse>() {
            @Override
            public void onSuccess(CallResponse data) {
                currentCallId = data != null ? data.getDisplayCallId() : null;
                showWaitingState(R.string.call_waiting_success_title, getSuccessMessage(data), false);
            }

            @Override
            public void onError(String message) {
                showWaitingState(R.string.call_waiting_error_title, getErrorMessage(message), true);
            }
        });
    }

    private void showWaitingState(int titleResId, String statusMessage, boolean showRetry) {
        formPanel.setVisibility(View.GONE);
        waitingPanel.setVisibility(View.VISIBLE);
        waitingTitleText.setText(titleResId);
        waitingReasonText.setText(getString(R.string.call_waiting_reason_format, selectedReason));
        waitingStatusText.setText(statusMessage);
        retryButton.setVisibility(showRetry ? View.VISIBLE : View.GONE);
    }

    private String getSuccessMessage(CallResponse data) {
        return getString(R.string.call_waiting_status_message);
    }

    private String getErrorMessage(String message) {
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return getString(R.string.call_waiting_status_message);
    }

    private void cancelWaitingCallAndFinish() {
        if (currentCallId == null || currentCallId.trim().isEmpty()) {
            finish();
            return;
        }

        repository.updateCallStatus(currentCallId, "CANCELED", new RepositoryCallback<CallResponse>() {
            @Override
            public void onSuccess(CallResponse data) {
                Toast.makeText(CallActivity.this, R.string.call_cancel_message, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                finish();
            }
        });
    }

    private void completeWaitingCallAndFinish() {
        if (currentCallId == null || currentCallId.trim().isEmpty()) {
            finish();
            return;
        }

        repository.updateCallStatus(currentCallId, "DONE", new RepositoryCallback<CallResponse>() {
            @Override
            public void onSuccess(CallResponse data) {
                Toast.makeText(CallActivity.this, R.string.call_done_message, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                waitingStatusText.setText(message);
            }
        });
    }
}
