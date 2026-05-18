package com.yeven.thread.dag.demo.quote.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "quotes.preview")
public class QuotePreviewProperties {

    /**
     * Maximum in-flight quote preview DAG executions.
     *
     * <p>Values less than one disable the limiter.</p>
     */
    private int maxConcurrent = 128;

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }
}
