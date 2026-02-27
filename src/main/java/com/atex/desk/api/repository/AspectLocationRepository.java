package com.atex.desk.api.repository;

import com.atex.desk.api.entity.AspectLocation;
import com.atex.desk.api.entity.AspectLocationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AspectLocationRepository extends JpaRepository<AspectLocation, AspectLocationId>
{
    List<AspectLocation> findByContentId(Integer contentId);
}
