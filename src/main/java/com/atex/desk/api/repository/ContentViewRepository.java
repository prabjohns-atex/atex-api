package com.atex.desk.api.repository;

import com.atex.desk.api.entity.ContentView;
import com.atex.desk.api.entity.ContentViewId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentViewRepository extends JpaRepository<ContentView, ContentViewId>
{
    List<ContentView> findByVersionId(Integer versionId);

    Optional<ContentView> findByVersionIdAndViewId(Integer versionId, Integer viewId);

    void deleteByVersionIdAndViewId(Integer versionId, Integer viewId);
}
