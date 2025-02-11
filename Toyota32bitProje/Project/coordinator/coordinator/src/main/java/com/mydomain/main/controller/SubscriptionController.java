package com.mydomain.main.controller;

import com.mydomain.main.coordinator.CoordinatorInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final CoordinatorInterface coordinator;

    @Autowired
    public SubscriptionController(CoordinatorInterface coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Örneğin:
     *  POST /api/subscription/subscribe?platform=TCP_PROVIDER&rateName=PF1_USDTRY
     */
    @PostMapping("/subscribe")
    public String subscribe(@RequestParam String platform, @RequestParam String rateName) {
        // Koordinatörden ilgili provider'ı çekiyoruz
        if ("TCP_PROVIDER".equalsIgnoreCase(platform)) {
            coordinator.getTcpProvider().subscribe(platform, rateName);
        } else if ("REST_PROVIDER".equalsIgnoreCase(platform)) {
            // REST provider normalde subscribe desteklemiyor (UnsupportedOperationException)
            // Fakat yine de handle edebilirsiniz veya "NotSupported" gibi bir cevap dönebilirsiniz.
            return "Subscribe not supported for REST_PROVIDER.";
        } else {
            return "Unknown platform: " + platform;
        }
        return "Subscribed to " + rateName + " on " + platform;
    }

    /**
     * Örneğin:
     *  POST /api/subscription/unsubscribe?platform=TCP_PROVIDER&rateName=PF1_USDTRY
     */
    @PostMapping("/unsubscribe")
    public String unsubscribe(@RequestParam String platform, @RequestParam String rateName) {
        if ("TCP_PROVIDER".equalsIgnoreCase(platform)) {
            coordinator.getTcpProvider().unsubscribe(platform, rateName);
        } else {
            return "Unsubscribe not supported for platform: " + platform;
        }
        return "Unsubscribed from " + rateName + " on " + platform;
    }
}
