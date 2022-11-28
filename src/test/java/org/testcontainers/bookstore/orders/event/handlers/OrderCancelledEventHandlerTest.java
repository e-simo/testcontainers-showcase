package org.testcontainers.bookstore.orders.event.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.bookstore.ApplicationProperties;
import org.testcontainers.bookstore.common.AbstractIntegrationTest;
import org.testcontainers.bookstore.events.OrderCancelledEvent;
import org.testcontainers.bookstore.orders.domain.OrderRepository;
import org.testcontainers.bookstore.orders.domain.entity.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderCancelledEventHandlerTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(OrderCancelledEventHandlerTest.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private KafkaTemplate<String,Object> kafkaTemplate;

    @Autowired
    private ApplicationProperties properties;

    @Test
    void shouldHandleOrderCancelledEvent() {
        Order order = new Order();
        order.setOrderId(UUID.randomUUID().toString());
        order.setCustomerName("Siva");
        order.setCustomerEmail("siva@gmail.com");
        order.setDeliveryAddressLine1("addr line 1");
        order.setDeliveryAddressLine2("addr line 2");
        order.setDeliveryAddressCity("Hyderabad");
        order.setDeliveryAddressState("Telangana");
        order.setDeliveryAddressZipCode("500072");
        order.setDeliveryAddressCountry("India");

        orderRepository.saveAndFlush(order);

        log.info("Cancelling OrderId: {}", order.getOrderId());
        kafkaTemplate.send(properties.cancelledOrdersTopic(), new OrderCancelledEvent(order.getOrderId()));

        await().atMost(30, SECONDS).untilAsserted(() -> {
            verify(notificationService).sendCancelledNotification(any(Order.class));
        });

    }

    @Test
    void shouldIgnoreOrderCancelledEventWhenOrderNotFound() {
        kafkaTemplate.send(properties.cancelledOrdersTopic(), new OrderCancelledEvent("non-existing-order_id"));

        await().atMost(5, SECONDS).untilAsserted(() -> {
            verify(notificationService, never()).sendCancelledNotification(any(Order.class));
        });

    }
}