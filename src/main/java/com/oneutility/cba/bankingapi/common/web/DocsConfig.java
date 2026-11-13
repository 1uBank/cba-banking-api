package com.oneutility.cba.bankingapi.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The API reference: {@code docs/index.html} at {@code /docs/}, rendering the contract served at
 * {@code /api/banking-api.yaml}. Both files are copied into the jar by the build, from the repository root, so the
 * service always serves the contract it was built with. Open to anyone (see {@code SecurityConfig}).
 */
@Configuration
public class DocsConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // The page loads the contract by a relative path, so it must be opened with the trailing slash.
        registry.addRedirectViewController("/docs", "/docs/");
        registry.addViewController("/docs/").setViewName("forward:/docs/index.html");
    }
}
