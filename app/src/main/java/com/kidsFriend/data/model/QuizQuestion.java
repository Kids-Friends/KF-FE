package com.kidsFriend.data.model;

import java.util.List;

public class QuizQuestion {
    public String quizId;
    public String question;
    public List<String> options;
    public String correctAnswer;

    public QuizQuestion(String quizId, String question, List<String> options, String correctAnswer) {
        this.quizId = quizId;
        this.question = question;
        this.options = options;
        this.correctAnswer = correctAnswer;
    }
}
