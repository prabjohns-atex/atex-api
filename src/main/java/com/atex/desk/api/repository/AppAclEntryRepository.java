package com.atex.desk.api.repository;

import com.atex.desk.api.entity.AppAclEntry;
import com.atex.desk.api.entity.AppAclEntryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppAclEntryRepository extends JpaRepository<AppAclEntry, AppAclEntryId>
{
    List<AppAclEntry> findByAclId(Integer aclId);

    List<AppAclEntry> findByPrincipalId(String principalId);
}
