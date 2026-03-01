package com.atex.desk.api.repository;

import com.atex.desk.api.entity.AppGroupMember;
import com.atex.desk.api.entity.AppGroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppGroupMemberRepository extends JpaRepository<AppGroupMember, AppGroupMemberId>
{
    List<AppGroupMember> findByGroupId(Integer groupId);

    List<AppGroupMember> findByPrincipalId(String principalId);

    void deleteByGroupIdAndPrincipalId(Integer groupId, String principalId);
}
