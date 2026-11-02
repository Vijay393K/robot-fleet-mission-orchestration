package com.robotfleet.robot_fleet.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.robotfleet.robot_fleet.MissionEvent;

public interface MissionEventRepository
        extends Repository<MissionEvent, UUID> {

    <S extends MissionEvent> S save(S event);

    List<MissionEvent> findByMissionIdOrderByChangedAtAsc(UUID missionId);
}