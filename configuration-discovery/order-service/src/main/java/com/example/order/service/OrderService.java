package com.example.order.service;

import com.example.order.client.ProductClient;
import com.example.order.domain.Order;
import com.example.order.domain.OrderStatus;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.ProductClientResponse;
import com.example.order.exception.InactiveProductException;
import com.example.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final ProductClient productClient;
    private final OrderRepository orderRepository;

    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        ProductClientResponse product = productClient.getProduct(request.getProductId());
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new InactiveProductException(request.getProductId(), product.getStatus());
        }
        BigDecimal total = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setProductName(product.getName());
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(product.getPrice());
        order.setTotalPrice(total);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setCorrelationId(MDC.get("correlationId"));
        return OrderResponse.from(orderRepository.save(order));
    }
}
