package com.example.order.controller;

import com.example.order.client.ProductClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders/discovery-demo")
@RequiredArgsConstructor
public class DiscoveryDemoController {
    private final ProductClient productClient;
    @GetMapping
    public ProductClient.ProductInstanceInfo discoverProductInstance() { return productClient.getInstanceInfo(); }
}
