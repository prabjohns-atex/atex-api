package com.atex.customer.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test customer plugin REST controller.
 * Proves that classes in com.atex.customer.* are picked up by Spring component scan.
 */
@RestController
@RequestMapping("/custom/test")
public class TestPluginController {

    private final TestPluginService service;

    @Autowired
    public TestPluginController(TestPluginService service) {
        this.service = service;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("plugin", "desk-custom-test");
        response.put("status", "alive");
        response.put("service", service.status());
        return response;
    }

    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("echoed", body);
        response.put("transformed", service.transform(body));
        return response;
    }
}
