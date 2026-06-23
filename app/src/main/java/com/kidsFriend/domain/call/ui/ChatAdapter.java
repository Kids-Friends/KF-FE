package com.kidsFriend.domain.call.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kidsFriend.R;
import com.kidsFriend.domain.call.model.ChatMessage;

import java.util.List;

/** 카톡식 채팅 말풍선 목록 어댑터. 내 말풍선/친구 말풍선 두 가지 뷰 타입을 그린다. */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private final List<ChatMessage> messages;
    private final int friendAvatarRes;

    public ChatAdapter(List<ChatMessage> messages, int friendAvatarRes) {
        this.messages = messages;
        this.friendAvatarRes = friendAvatarRes;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).type;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == ChatMessage.TYPE_MINE)
                ? R.layout.item_chat_mine
                : R.layout.item_chat_friend;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bubble.setText(message.text);
        if (message.type == ChatMessage.TYPE_FRIEND && holder.avatar != null) {
            holder.avatar.setImageResource(friendAvatarRes);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView bubble;
        final ImageView avatar;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            bubble = itemView.findViewById(R.id.text_bubble);
            avatar = itemView.findViewById(R.id.image_friend_avatar); // 내 말풍선엔 없음(null)
        }
    }
}
