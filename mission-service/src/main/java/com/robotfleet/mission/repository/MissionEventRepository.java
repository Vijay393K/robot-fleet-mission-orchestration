
package com.robotfleet.mission.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import com.robotfleet.mission.entity.MissionEvent;

public interface MissionEventRepository
        extends Repository<MissionEvent, UUID> {

    <S extends MissionEvent> S save(S event);

    Page<MissionEvent> findByMissionIdOrderByChangedAtAscIdAsc(
            UUID missionId,
            Pageable pageable
    );
}
