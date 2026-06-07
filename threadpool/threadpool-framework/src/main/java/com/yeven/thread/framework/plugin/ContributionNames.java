package com.yeven.thread.framework.plugin;

final class ContributionNames {

    private ContributionNames() {
    }

    static void validate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
