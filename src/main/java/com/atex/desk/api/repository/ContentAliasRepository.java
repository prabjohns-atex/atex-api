package com.atex.desk.api.repository;

import com.atex.desk.api.entity.ContentAlias;
import com.atex.desk.api.entity.ContentAliasId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContentAliasRepository extends JpaRepository<ContentAlias, ContentAliasId>
{
    List<ContentAlias> findByIdtypeAndId(Integer idtype, String id);

    @Query("""
        SELECT ca FROM ContentAlias ca
        JOIN Alias a ON ca.aliasId = a.aliasId
        WHERE a.name = :aliasName AND ca.value = :value
        """)
    Optional<ContentAlias> findByAliasNameAndValue(String aliasName, String value);

    @Modifying
    @Query("""
        DELETE FROM ContentAlias ca
        WHERE ca.idtype = :idtype AND ca.id = :id AND ca.aliasId = :aliasId
        """)
    void deleteByIdtypeAndIdAndAliasId(Integer idtype, String id, Integer aliasId);

    @Modifying
    @Query("""
        DELETE FROM ContentAlias ca
        WHERE ca.idtype = :idtype AND ca.id = :id
        """)
    void deleteByIdtypeAndId(Integer idtype, String id);
}
