package com.atex.desk.api.repository;

import com.atex.desk.api.entity.ChangeListEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ChangeListRepository extends JpaRepository<ChangeListEntry, Integer>
{
    @Query("SELECT MAX(c.id) FROM ChangeListEntry c")
    Optional<Integer> findMaxId();

    Optional<ChangeListEntry> findByContentid(String contentid);

    void deleteByContentid(String contentid);
}
