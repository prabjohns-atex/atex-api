package com.atex.desk.api.repository;

import com.atex.desk.api.entity.AspectLocation;
import com.atex.desk.api.entity.AspectLocationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AspectLocationRepository extends JpaRepository<AspectLocation, AspectLocationId>
{
    List<AspectLocation> findByContentId(Integer contentId);

    @Modifying
    void deleteByContentId(Integer contentId);

    /**
     * Count how many content entries reference a given aspect.
     * Used during purge to check if an aspect is shared before deleting.
     */
    long countByAspectId(Integer aspectId);
}
