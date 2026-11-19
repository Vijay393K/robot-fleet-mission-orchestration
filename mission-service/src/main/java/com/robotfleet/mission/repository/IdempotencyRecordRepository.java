
package com.robotfleet.mission.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.robotfleet.mission.entity.IdempotencyRecord;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByOperationScopeAndIdempotencyKey(
            String operationScope,
            String idempotencyKey
    );

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records (
                id,
                operation_scope,
                idempotency_key,
                request_hash
            )
            VALUES (
                :id,
                :operationScope,
                :idempotencyKey,
                :requestHash
            )
            ON CONFLICT (operation_scope, idempotency_key)
            DO NOTHING
            """, nativeQuery = true)
    int createIfAbsent(
            @Param("id") UUID id,
            @Param("operationScope") String operationScope,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash
    );
}
