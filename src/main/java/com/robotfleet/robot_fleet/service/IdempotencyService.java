
package com.robotfleet.robot_fleet.service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;



import com.robotfleet.robot_fleet.entity.IdempotencyRecord;
import com.robotfleet.robot_fleet.repository.IdempotencyRecordRepository;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRepository;
    private final JsonMapper objectMapper;
    public IdempotencyService(
            IdempotencyRecordRepository idempotencyRepository,
            JsonMapper objectMapper) {

        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> ResponseEntity<T> execute(
            String operationScope,
            String idempotencyKey,
            Object request,
            Class<T> responseType,
            HttpStatus successStatus,
            Supplier<T> operation) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required"
            );
        }

        if (idempotencyKey.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must not exceed 255 characters"
            );
        }

        String requestHash = calculateHash(request);

        int inserted = idempotencyRepository.createIfAbsent(
                UUID.randomUUID(),
                operationScope,
                idempotencyKey,
                requestHash
        );

        if (inserted == 0) {

            IdempotencyRecord existing =
                    idempotencyRepository
                            .findByOperationScopeAndIdempotencyKey(
                                    operationScope,
                                    idempotencyKey
                            )
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "Idempotency record could not be found"
                            ));

            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency key was already used with a different request"
                );
            }

            if (existing.getResponseBody() == null ||
                    existing.getResponseStatus() == null) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The original request has no completed response"
                );
            }

            try {
                T originalResponse = objectMapper.readValue(
                        existing.getResponseBody(),
                        responseType
                );

                return ResponseEntity
                        .status(existing.getResponseStatus())
                        .body(originalResponse);

            } catch (JacksonException exception){
                throw new IllegalStateException(
                        "Could not read the stored idempotency response",
                        exception
                );
            }
        }

        T result = operation.get();

        try {
            IdempotencyRecord record =
                    idempotencyRepository
                            .findByOperationScopeAndIdempotencyKey(
                                    operationScope,
                                    idempotencyKey
                            )
                            .orElseThrow(() -> new IllegalStateException(
                                    "Idempotency record was not created"
                            ));

            record.setResponseStatus(successStatus.value());

            record.setResponseBody(
                    objectMapper.writeValueAsString(result)
            );

            idempotencyRepository.save(record);

        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not store the idempotency response",
                    exception
            );
        }

        return ResponseEntity
                .status(successStatus)
                .body(result);
    }

    private String calculateHash(Object request) {

        try {
            byte[] requestBytes = objectMapper.writeValueAsBytes(request);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(requestBytes);

            StringBuilder hash = new StringBuilder();

            for (byte value : hashBytes) {
                hash.append(String.format("%02x", value & 0xff));
            }

            return hash.toString();

        } catch (JacksonException exception) {

            throw new IllegalStateException(
                    "Could not calculate request hash",
                    exception
            );

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }
    
}
