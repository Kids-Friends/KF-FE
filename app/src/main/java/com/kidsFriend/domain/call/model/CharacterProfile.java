package com.kidsFriend.domain.call.model;

import java.io.Serializable;

public class CharacterProfile implements Serializable {
    private final String id;
    private final String name;
    private final int imageRes;
    private final String systemPrompt;
    private final int themeColor;
    private final String voiceType;

    public CharacterProfile(String id, String name, int imageRes, String systemPrompt, int themeColor, String voiceType) {
        this.id = id;
        this.name = name;
        this.imageRes = imageRes;
        this.systemPrompt = systemPrompt;
        this.themeColor = themeColor;
        this.voiceType = voiceType;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getImageRes() { return imageRes; }
    public String getSystemPrompt() { return systemPrompt; }
    public int getThemeColor() { return themeColor; }
    public String getVoiceType() { return voiceType; }
}
