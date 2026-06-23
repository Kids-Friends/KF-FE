package com.kidsFriend.domain.call.model;

/** 카톡식 채팅의 말풍선 한 개. 내가 보낸 것(MINE)과 친구가 보낸 것(FRIEND)을 구분한다. */
public class ChatMessage {
    public static final int TYPE_MINE = 0;
    public static final int TYPE_FRIEND = 1;

    public final int type;
    public final String text;

    public ChatMessage(int type, String text) {
        this.type = type;
        this.text = text;
    }
}
