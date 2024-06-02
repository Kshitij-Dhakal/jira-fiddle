package org.example;

record Worklog(
    String issueKey,
    String summary,
    String author,
    String started,
    String timeSpent,
    int priority,
    String rank) {}
