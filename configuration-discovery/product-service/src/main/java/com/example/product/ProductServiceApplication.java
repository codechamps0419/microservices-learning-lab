package com.example.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/*
 * @SpringBootApplication is a shortcut for three annotations:
 *
 * @Configuration      — this class can declare @Bean methods
 * @EnableAutoConfiguration — Spring Boot auto-configures JPA, Flyway,
 *                            web server etc. based on what is on the classpath
 * @ComponentScan      — scans this package and all sub-packages for
 *                       @Component, @Service, @Repository, @Controller
 *
 * Everything in com.example.product.* is picked up automatically.
 * No XML configuration needed.
 */
@ConfigurationPropertiesScan
@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
