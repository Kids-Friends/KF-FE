package com.kidsFriend.domain.call.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.kidsFriend.R;
import com.kidsFriend.domain.call.model.CharacterProfile;
import java.util.List;

public class CharacterAdapter extends RecyclerView.Adapter<CharacterAdapter.ViewHolder> {
    private final List<CharacterProfile> characters;
    private final OnCharacterClickListener listener;

    public interface OnCharacterClickListener {
        void onCharacterClick(CharacterProfile character);
    }

    public CharacterAdapter(List<CharacterProfile> characters, OnCharacterClickListener listener) {
        this.characters = characters;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_character, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CharacterProfile character = characters.get(position);
        holder.nameText.setText(character.getName());
        holder.imageView.setImageResource(character.getImageRes());
        holder.itemView.setOnClickListener(v -> {
            animateClick(v, () -> listener.onCharacterClick(character));
        });
    }

    private void animateClick(View view, Runnable endAction) {
        ScaleAnimation anim = new ScaleAnimation(1.0f, 1.08f, 1.0f, 1.08f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(90);
        anim.setRepeatCount(1);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationEnd(Animation animation) { endAction.run(); }
            @Override public void onAnimationRepeat(Animation animation) {}
        });
        view.startAnimation(anim);
    }

    @Override
    public int getItemCount() {
        return characters.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView nameText;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_character);
            nameText = itemView.findViewById(R.id.text_character_name);
        }
    }
}
