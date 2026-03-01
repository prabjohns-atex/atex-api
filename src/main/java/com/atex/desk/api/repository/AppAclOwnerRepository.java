package com.atex.desk.api.repository;

import com.atex.desk.api.entity.AppAclOwner;
import com.atex.desk.api.entity.AppAclOwnerId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppAclOwnerRepository extends JpaRepository<AppAclOwner, AppAclOwnerId>
{
    List<AppAclOwner> findByAclId(Integer aclId);

    List<AppAclOwner> findByPrincipalId(String principalId);
}
