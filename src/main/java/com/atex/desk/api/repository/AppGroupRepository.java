package com.atex.desk.api.repository;

import com.atex.desk.api.entity.AppGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppGroupRepository extends JpaRepository<AppGroup, Integer>
{
    Optional<AppGroup> findByName(String name);

    List<AppGroup> findByLdapGroupDn(String ldapGroupDn);

    List<AppGroup> findByRemoteGroupDn(String remoteGroupDn);
}
