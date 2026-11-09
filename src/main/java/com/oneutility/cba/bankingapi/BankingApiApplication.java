package com.oneutility.cba.bankingapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

/**
 * The Banking API: the only service client applications call, and the only one that calls CBA-CORE.
 *
 * <p>A modular monolith. Each top-level package is a business module ({@code auth}, {@code clients},
 * {@code customers}, ...) split into {@code api}, {@code application}, {@code domain} and {@code infrastructure};
 * {@code shared} holds cross-cutting code. {@code ArchitectureTest} enforces the boundaries.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
public class BankingApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingApiApplication.class, args);
    }
}
