package com.kidsFriend.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.R;
import com.kidsFriend.data.model.CallResponse;
import com.kidsFriend.data.repository.RepositoryCallback;
import com.kidsFriend.data.repository.TemiRepository;

public class CallActivity extends AppCompatActivity {
    private final TemiRepository repository = new TemiRepository();

    private Button helpButton;
    private Button injuryButton;
    private Button lostItemButton;
    private Button etcButton;
    private TextView statusText;
    private String selectedReason;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        helpButton = findViewById(R.id.button_reason_help);
        injuryButton = findViewById(R.id.button_reason_injury);
        lostItemButton = findViewById(R.id.button_reason_lost_item);
        etcButton = findViewById(R.id.button_reason_etc);
        Button submitButton = findViewById(R.id.button_submit_call);
        Button backButton = findViewById(R.id.button_back);
        statusText = findViewById(R.id.text_call_status);

        helpButton.setOnClickListener(v -> selectReason(getString(R.string.call_reason_help), helpButton));
        injuryButton.setOnClickListener(v -> selectReason(getString(R.string.call_reason_injury), injuryButton));
        lostItemButton.setOnClickListener(v -> selectReason(getString(R.string.call_reason_lost_item), lostItemButton));
        etcButton.setOnClickListener(v -> selectReason(getString(R.string.call_reason_etc), etcButton));
        submitButton.setOnClickListener(v -> submitCall());
        backButton.setOnClickListener(v -> finish());
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

        statusText.setText(R.string.common_loading);
        repository.createCall(selectedReason, new RepositoryCallback<CallResponse>() {
            @Override
            public void onSuccess(CallResponse data) {
                statusText.setText(data.message);
            }

            @Override
            public void onError(String message) {
                statusText.setText(message);
            }
        });
    }
}
