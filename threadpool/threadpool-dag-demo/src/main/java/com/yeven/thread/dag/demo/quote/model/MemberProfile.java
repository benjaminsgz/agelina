package com.yeven.thread.dag.demo.quote.model;

public class MemberProfile {

    private final String userId;
    private final String level;

    public MemberProfile(String userId, String level) {
        this.userId = userId;
        this.level = level;
    }

    public String getUserId() { return userId; }
    public String getLevel() { return level; }
}
