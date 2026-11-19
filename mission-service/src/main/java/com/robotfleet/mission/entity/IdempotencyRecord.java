
package com.robotfleet.mission.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "operation_scope", nullable = false,
            length = 200, updatable = false)
    private String operationScope;

    @Column(name = "idempotency_key", nullable = false,
            length = 255, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false,
            length = 64, updatable = false)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public void complete(int responseStatus, String responseBody, UUID resourceId) {

        if (this.responseStatus != null || this.responseBody != null) {
            throw new IllegalStateException(
                    "Idempotency record is already completed");
        }

        if (responseStatus < 200 || responseStatus > 599) {
            throw new IllegalArgumentException("Invalid HTTP response status");
        }

        if (responseBody == null) {
            throw new IllegalArgumentException("Response body is required");
        }

        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.resourceId = resourceId;
    }

    public UUID getId() {
        return id;
    }

    public String getOperationScope() {
        return operationScope;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
