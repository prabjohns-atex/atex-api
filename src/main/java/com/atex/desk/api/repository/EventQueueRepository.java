package com.atex.desk.api.repository;

import com.atex.desk.api.entity.EventQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventQueueRepository extends JpaRepository<EventQueueEntry, Integer>
{
}
