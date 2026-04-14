package com.yeven.thread.dag.demo.quote.gateway;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InventoryGateway {

    private static final Map<String, Integer> INVENTORY = Map.of(
            "SKU-1001", 12,
            "SKU-1002", 4,
            "SKU-1003", 9
    );

    public int getAvailableStock(String sku) {
        sleep(100);
        return INVENTORY.getOrDefault(sku, 0);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while loading inventory", e);
        }
    }
}
