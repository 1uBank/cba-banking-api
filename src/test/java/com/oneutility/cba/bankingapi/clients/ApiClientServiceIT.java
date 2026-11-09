package com.oneutility.cba.bankingapi.clients;

import static org.assertj.core.api.Assertions.assertThat;

import com.oneutility.cba.bankingapi.IntegrationTest;
import com.oneutility.cba.bankingapi.clients.application.ApiClientService;
import com.oneutility.cba.bankingapi.clients.domain.ApiClient;
import com.oneutility.cba.bankingapi.clients.infrastructure.ApiClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

/** The client registry against the real schema, as the Liquibase migrations build it. */
@IntegrationTest
class ApiClientServiceIT {

    @Autowired
    private ApiClientService service;

    @Autowired
    private ApiClientRepository clients;

    @Autowired
    private CacheManager caches;

    @Test
    void theAppAndIrechargeAreActive() {
        assertThat(service.isActive("app")).isTrue();
        assertThat(service.isActive("irecharge")).isTrue();
    }

    @Test
    void anUnknownClientIsNotActive() {
        assertThat(service.isActive("nobody")).isFalse();
    }

    @Test
    void aSuspensionTakesEffectOnceTheCachedAnswerExpires() {
        ApiClient client = clients.save(new ApiClient("to-suspend", "To suspend"));
        assertThat(service.isActive("to-suspend")).isTrue();

        client.suspend();
        clients.save(client);
        assertThat(service.isActive("to-suspend")).isTrue();

        caches.getCache(ApiClientService.ACTIVE_CLIENTS_CACHE).clear();
        assertThat(service.isActive("to-suspend")).isFalse();
        assertThat(clients.findByClientId("to-suspend")).get()
                .satisfies(saved -> assertThat(saved.isActive()).isFalse());
    }
}
