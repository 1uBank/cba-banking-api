package com.oneutility.cba.bankingapi.clients.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A client application registered to call the API. Its identity and scopes live in the auth service; this holds what
 * the Banking API decides itself, starting with whether the client is active.
 */
@Entity
@Table(name = "api_client")
public class ApiClient {

    @Id
    private UUID id;

    /** The client's ID in the auth service ({@code azp} on its tokens). */
    @Column(name = "client_id", nullable = false, unique = true, updatable = false)
    private String clientId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApiClientStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ApiClient() {
        // For JPA.
    }

    public ApiClient(String clientId, String name) {
        this.id = UUID.randomUUID();
        this.clientId = clientId;
        this.name = name;
        this.status = ApiClientStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public String clientId() {
        return clientId;
    }

    public String name() {
        return name;
    }

    public ApiClientStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == ApiClientStatus.ACTIVE;
    }

    public void suspend() {
        status = ApiClientStatus.SUSPENDED;
    }

    public void reactivate() {
        status = ApiClientStatus.ACTIVE;
    }
}
