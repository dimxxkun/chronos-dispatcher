package com.chronos.dispatcher.model;

public enum JobState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    DEAD_LETTER
}
