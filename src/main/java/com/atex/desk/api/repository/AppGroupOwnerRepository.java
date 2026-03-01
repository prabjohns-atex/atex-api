package com.atex.desk.api.repository;

import com.atex.desk.api.entity.AppGroupOwner;
import com.atex.desk.api.entity.AppGroupOwnerId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppGroupOwnerRepository extends JpaRepository<AppGroupOwner, AppGroupOwnerId>
{
    List<AppGroupOwner> findByGroupId(Integer groupId);

    List<AppGroupOwner> findByPrincipalId(String principalId);

    void deleteByGroupIdAndPrincipalId(Integer groupId, String principalId);
}
