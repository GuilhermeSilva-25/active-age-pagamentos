package com.activeage.payment.model;

import java.util.Map;

public record WebhookNotification(
        String action,
        String type,
        Map<String, String> data
) {
}