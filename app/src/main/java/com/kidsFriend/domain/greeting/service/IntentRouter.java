package com.kidsFriend.domain.greeting.service;

public class IntentRouter {
    public enum Intent {
        IDENTITY,
        MEMBERSHIP,
        LOCATION,
        QUIZ,
        AIR_QUALITY,
        ENDING,
        CHAT,
        FREE_QUESTION
    }

    public static Intent route(String utterance) {
        if (utterance == null) return Intent.CHAT;
        
        if (utterance.contains("누구") || utterance.contains("정체")) return Intent.IDENTITY;
        if (utterance.contains("회원")) return Intent.MEMBERSHIP;
        if (utterance.contains("어디") || utterance.contains("미끄럼")) return Intent.LOCATION;
        if (utterance.contains("퀴즈")) return Intent.QUIZ;
        if (utterance.contains("미세먼지") || utterance.contains("공기")) return Intent.AIR_QUALITY;
        if (utterance.contains("고마워") || utterance.contains("잘가")) return Intent.ENDING;
        
        return Intent.CHAT;
    }
}
