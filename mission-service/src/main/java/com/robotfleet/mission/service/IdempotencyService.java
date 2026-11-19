
package com.robotfleet.mission.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.entity.IdempotencyRecord;
import com.robotfleet.mission.repository.IdempotencyRecordRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final JsonMapper jsonMapper;

    public IdempotencyService(
            IdempotencyRecordRepository repository,
            JsonMapper jsonMapper) {

        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public <T> ResponseEntity<T> execute(
            String scope,
            String idempotencyKey,
            Object request,
            Class<T> responseType,
            HttpStatus successStatus,
            Supplier<T> operation,
            Function<T, UUID> resourceIdExtractor) {

        Objects.requireNonNull(scope, "Operation scope is required");
        Objects.requireNonNull(request, "Request is required");
        Objects.requireNonNull(responseType, "Response type is required");
        Objects.requireNonNull(successStatus, "Success status is required");
        Objects.requireNonNull(operation, "Operation is required");
        Objects.requireNonNull(resourceIdExtractor, "Resource ID extractor is required");

        if (scope.isBlank() || scope.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid idempotency operation scope");
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 255) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid Idempotency-Key header is required");
        }

        String requestHash = hashRequest(request);

        int inserted = repository.createIfAbsent(
                UUID.randomUUID(),
                scope,
                idempotencyKey,
                requestHash
        );

        if (inserted == 0) {
            return replay(
                    scope,
                    idempotencyKey,
                    requestHash,
                    responseType
            );
        }

        T response = Objects.requireNonNull(
                operation.get(),
                "Idempotent operation returned a null response"
        );

        IdempotencyRecord record = repository
                .findByOperationScopeAndIdempotencyKey(scope, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "New idempotency record was not found"));

        try {
            record.complete(
                    successStatus.value(),
                    jsonMapper.writeValueAsString(response),
                    resourceIdExtractor.apply(response)
            );
        } catch (JacksonException ex) {
            throw new IllegalStateException(
                    "Failed to serialize idempotent response", ex);
        }

        return ResponseEntity.status(successStatus).body(response);
    }

    private <T> ResponseEntity<T> replay(
            String scope,
            String key,
            String requestHash,
            Class<T> responseType) {

        IdempotencyRecord record = repository
                .findByOperationScopeAndIdempotencyKey(scope, key)
                .orElseThrow(() -> new IllegalStateException(
                        "Existing idempotency record was not found"));

        if (!record.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key was used with a different request");
        }

        if (record.getResponseStatus() == null
                || record.getResponseBody() == null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Original request has not completed");
        }

        try {
            T response = jsonMapper.readValue(
                    record.getResponseBody(),
                    responseType
            );

            return ResponseEntity
                    .status(record.getResponseStatus())
                    .body(response);

        } catch (JacksonException ex) {
            throw new IllegalStateException(
                    "Failed to deserialize stored response", ex);
        }
    }

    private String hashRequest(Object request) {
        try {
            byte[] bytes = jsonMapper.writeValueAsBytes(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(bytes));

        } catch (JacksonException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "Failed to calculate request hash", ex);
        }
    }
}
 