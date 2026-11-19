
package com.robotfleet.robot_fleet.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.robotfleet.robot_fleet.entity.IdempotencyRecord;

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
                request_hash,
                created_at
            )
            VALUES (
                :id,
                :scope,
                :key,
                :hash,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (operation_scope, idempotency_key)
            DO NOTHING
            """, nativeQuery = true)
    int createIfAbsent(
            @Param("id") UUID id,
            @Param("scope") String scope,
            @Param("key") String key,
            @Param("hash") String hash
    );
}
