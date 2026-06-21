package com.kidsFriend.domain.call.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.kidsFriend.R;
import com.kidsFriend.domain.call.model.CharacterProfile;
import com.kidsFriend.domain.call.service.CharacterRepository;
import com.kidsFriend.global.ui.FullscreenHelper;

public class FriendSelectionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullscreenHelper.setFullscreen(this);
        setContentView(R.layout.activity_friend_selection);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recycler_characters);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        
        CharacterAdapter adapter = new CharacterAdapter(CharacterRepository.getCharacters(), character -> {
            Intent intent = new Intent(this, VoiceCallActivity.class);
            intent.putExtra("character", character);
            startActivity(intent);
            overridePendingTransition(R.anim.combined_enter, 0);
        });
        recyclerView.setAdapter(adapter);
    }
}
