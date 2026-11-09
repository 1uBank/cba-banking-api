package com.oneutility.cba.bankingapi.clients.application;

import com.oneutility.cba.bankingapi.clients.infrastructure.ApiClientRepository;
import com.oneutility.cba.bankingapi.shared.security.ActiveClientCheck;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers whether a client is active. Asked on every call, so the answer is cached briefly
 * ({@code spring.cache.caffeine.spec}): a suspension takes effect within that time.
 */
@Service
public class ApiClientService implements ActiveClientCheck {

    public static final String ACTIVE_CLIENTS_CACHE = "activeClients";

    private final ApiClientRepository clients;

    public ApiClientService(ApiClientRepository clients) {
        this.clients = clients;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(ACTIVE_CLIENTS_CACHE)
    public boolean isActive(String clientId) {
        return clients.findByClientId(clientId).map(client -> client.isActive()).orElse(false);
    }
}
