package org.example;

public record WorkLog(
    String issueKey,
    String summary,
    String author,
    String started,
    String timeSpent,
    int priority,
    String rank) {}
