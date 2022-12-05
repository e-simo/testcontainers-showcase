package org.testcontainers.bookstore.orders.api;

import io.restassured.http.ContentType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.bookstore.common.AbstractIntegrationTest;
import org.testcontainers.bookstore.orders.domain.OrderService;
import org.testcontainers.bookstore.orders.domain.entity.Order;
import org.testcontainers.bookstore.orders.domain.entity.OrderStatus;
import org.testcontainers.bookstore.orders.domain.model.OrderConfirmationDTO;

import java.time.Duration;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

public class CreateOrderApiTests extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(CreateOrderApiTests.class);

    @Autowired
    private OrderService orderService;

    @ParameterizedTest
    @CsvSource({
            "P100",
            "P101",
            "P102",
            "P103"
    })
    void shouldCreateOrderSuccessfully(String productCode) {
        OrderConfirmationDTO orderConfirmationDTO = given()
                .contentType(ContentType.JSON)
                .body(
                        """
                                {
                                    "customerName": "Siva",
                                    "customerEmail": "siva@gmail.com",
                                    "deliveryAddressLine1": "Birkelweg",
                                    "deliveryAddressLine2": "Hans-Edenhofer-Straße 23",
                                    "deliveryAddressCity": "Berlin",
                                    "deliveryAddressState": "Berlin",
                                    "deliveryAddressZipCode": "94258",
                                    "deliveryAddressCountry": "Germany",
                                    "cardNumber": "1111222233334444",
                                    "cvv": "123",
                                    "expiryMonth": 2,
                                    "expiryYear": 2030,
                                    "items": [
                                        {
                                            "productCode": "%s",
                                            "productName": "Product name",
                                            "productPrice": 25.50,
                                            "quantity": 1
                                        }
                                    ]
                                }
                                """.formatted(productCode)
                )
                .when()
                .post("/api/orders")
                .then()
                .statusCode(202)
                .body("orderId", notNullValue())
                .body("orderStatus", is("NEW"))
                .extract().body().as(OrderConfirmationDTO.class);

        log.info("Created new order with order_id:{}. Now verifying it's status...", orderConfirmationDTO.getOrderId());
        await().pollInterval(Duration.ofSeconds(5)).atMost(30, SECONDS).until(() -> {
            log.info("verifying order status of order_id:{} to be DELIVERED", orderConfirmationDTO.getOrderId());
            Optional<Order> orderOptional = orderService.findOrderByOrderId(orderConfirmationDTO.getOrderId());
            log.info("order exists with order_id:{}", orderOptional.isPresent());
            if(orderOptional.isPresent()) {
                log.info("order_id:{}, status:{}", orderOptional.get().getOrderId(), orderOptional.get().getStatus());
            }
            return orderOptional.isPresent() && orderOptional.get().getStatus() == OrderStatus.DELIVERED;
        });
    }

    @ParameterizedTest
    @CsvSource({
            "1111111111111",
            "2222222222222",
            "3333333333333",
            "4444444444444"
    })
    void shouldCreateOrderWithErrorStatusWhenPaymentRejected(String cardNumber) {
        given()
                .contentType(ContentType.JSON)
                .body(
                        """
                                {
                                    "customerName": "Siva",
                                    "customerEmail": "siva@gmail.com",
                                    "deliveryAddressLine1": "Birkelweg",
                                    "deliveryAddressLine2": "Hans-Edenhofer-Straße 23",
                                    "deliveryAddressCity": "Berlin",
                                    "deliveryAddressState": "Berlin",
                                    "deliveryAddressZipCode": "94258",
                                    "deliveryAddressCountry": "Germany",
                                    "cardNumber": "%s",
                                    "cvv": "345",
                                    "expiryMonth": 2,
                                    "expiryYear": 2024,
                                    "items": [
                                        {
                                            "productCode": "P100",
                                            "productName": "Product 1",
                                            "productPrice": 25.50,
                                            "quantity": 1
                                        }
                                    ]
                                }
                                """.formatted(cardNumber)
                )
                .when()
                .post("/api/orders")
                .then()
                .statusCode(202)
                .body("orderId", notNullValue())
                .body("orderStatus", is("ERROR"))
        ;
    }

    @ParameterizedTest
    @CsvSource({
            "1111111111111",
            "2222222222222",
            "3333333333333",
            "4444444444444"
    })
    void shouldReturnBadRequestWhenMandatoryDataIsMissing(String cardNumber) {
        given()
                .contentType(ContentType.JSON)
                .body(
                        """
                                {
                                    "cardNumber": "%s",
                                    "cvv": "345",
                                    "expiryMonth": 2,
                                    "expiryYear": 2024,
                                    "items": [
                                        {
                                            "productCode": "P100",
                                            "productName": "Product 1",
                                            "productPrice": 25.50,
                                            "quantity": 1
                                        }
                                    ]
                                }
                                """.formatted(cardNumber)
                )
                .when()
                .post("/api/orders")
                .then()
                .statusCode(400)
        ;
    }

    @ParameterizedTest
    @CsvSource({
            "Belgium",
            "Denmark",
            "Dubai",
            "Poland"
    })
    void shouldCancelOrderWhenCanNotBeDelivered(String country) {
        OrderConfirmationDTO orderConfirmationDTO = given()
                .contentType(ContentType.JSON)
                .body(
                        """
                                {
                                    "customerName": "Siva",
                                    "customerEmail": "siva@gmail.com",
                                    "deliveryAddressLine1": "Birkelweg",
                                    "deliveryAddressLine2": "Hans-Edenhofer-Straße 23",
                                    "deliveryAddressCity": "Turkey",
                                    "deliveryAddressState": "Turkey",
                                    "deliveryAddressZipCode": "94258",
                                    "deliveryAddressCountry": "%s",
                                    "cardNumber": "1111222233334444",
                                    "cvv": "123",
                                    "expiryMonth": 2,
                                    "expiryYear": 2030,
                                    "items": [
                                        {
                                            "productCode": "P100",
                                            "productName": "Product 1",
                                            "productPrice": 25.50,
                                            "quantity": 1
                                        }
                                    ]
                                }
                                """.formatted(country)
                )
                .when()
                .post("/api/orders")
                .then()
                .statusCode(202)
                .body("orderId", notNullValue())
                .body("orderStatus", is("NEW"))
                .extract().body().as(OrderConfirmationDTO.class);

        await().pollInterval(Duration.ofSeconds(5)).atMost(30, SECONDS).until(() -> {
            Optional<Order> orderOptional = orderService.findOrderByOrderId(orderConfirmationDTO.getOrderId());
            return orderOptional.isPresent() && orderOptional.get().getStatus() == OrderStatus.CANCELLED;
        });
    }
}