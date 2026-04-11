package com.atex.customer.test;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Test customer plugin @Service.
 * Proves that plugin Spring services are created and can be autowired.
 */
@Service
public class TestPluginService {

    public String status() {
        return "test plugin service ready";
    }

    public Map<String, Object> transform(Map<String, Object> input) {
        return input.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toUpperCase(),
                        Map.Entry::getValue,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new));
    }
}
