package com.atex.desk.api.repository;

import com.atex.desk.api.entity.ContentView;
import com.atex.desk.api.entity.ContentViewId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContentViewRepository extends JpaRepository<ContentView, ContentViewId>
{
    List<ContentView> findByVersionId(Integer versionId);

    List<ContentView> findByVersionIdIn(List<Integer> versionIds);

    Optional<ContentView> findByVersionIdAndViewId(Integer versionId, Integer viewId);

    void deleteByVersionIdAndViewId(Integer versionId, Integer viewId);

    /**
     * Remove a view from all versions of a content item except the specified version.
     * Used for exclusive view assignment (Gap 3).
     */
    @Modifying
    @Query("""
        DELETE FROM ContentView cv
        WHERE cv.viewId = :viewId
        AND cv.versionId IN (
            SELECT v.versionId FROM ContentVersion v
            WHERE v.idtype = :idtype AND v.id = :contentKey
            AND v.versionId <> :excludeVersionId
        )
        """)
    void removeViewFromOtherVersions(Integer viewId, Integer idtype, String contentKey,
                                      Integer excludeVersionId);
}
