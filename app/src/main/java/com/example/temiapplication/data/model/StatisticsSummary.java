package com.example.temiapplication.data.model;

public class StatisticsSummary {
    public int callCount;
    public int questionCount;
    public int quizPlayCount;
    public int quizCorrectCount;
    public String summaryMessage;

    public StatisticsSummary(int callCount, int questionCount, int quizPlayCount, int quizCorrectCount, String summaryMessage) {
        this.callCount = callCount;
        this.questionCount = questionCount;
        this.quizPlayCount = quizPlayCount;
        this.quizCorrectCount = quizCorrectCount;
        this.summaryMessage = summaryMessage;
    }
}
