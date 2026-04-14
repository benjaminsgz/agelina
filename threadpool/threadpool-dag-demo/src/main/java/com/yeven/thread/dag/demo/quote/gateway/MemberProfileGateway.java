package com.yeven.thread.dag.demo.quote.gateway;

import com.yeven.thread.dag.demo.quote.model.MemberProfile;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MemberProfileGateway {

    private static final Map<String, MemberProfile> MEMBERS = Map.of(
            "u100", new MemberProfile("u100", "GOLD"),
            "u200", new MemberProfile("u200", "SILVER"),
            "u300", new MemberProfile("u300", "BRONZE")
    );

    public MemberProfile getByUserId(String userId) {
        sleep(90);
        return MEMBERS.getOrDefault(userId, new MemberProfile(userId, "BRONZE"));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while loading member profile", e);
        }
    }
}
