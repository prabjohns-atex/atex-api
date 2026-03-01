package com.atex.desk.api.repository;

import com.atex.desk.api.entity.EventTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventTypeRepository extends JpaRepository<EventTypeEntity, Integer>
{
    Optional<EventTypeEntity> findByName(String name);
}
