package com.oneutility.cba.bankingapi.clients.infrastructure;

import com.oneutility.cba.bankingapi.clients.domain.ApiClient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {

    Optional<ApiClient> findByClientId(String clientId);
}
