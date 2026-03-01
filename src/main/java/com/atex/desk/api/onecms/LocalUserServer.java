package com.atex.desk.api.onecms;

import com.atex.desk.api.auth.PasswordService;
import com.atex.desk.api.entity.AppGroup;
import com.atex.desk.api.entity.AppGroupMember;
import com.atex.desk.api.entity.AppUser;
import com.atex.desk.api.repository.AppGroupMemberRepository;
import com.atex.desk.api.repository.AppGroupRepository;
import com.atex.desk.api.repository.AppUserRepository;
import com.polopoly.user.server.Caller;
import com.polopoly.user.server.Group;
import com.polopoly.user.server.GroupId;
import com.polopoly.user.server.UserServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Local UserServer implementation backed by JPA repositories.
 */
@Component
public class LocalUserServer implements UserServer {

    private static final Logger LOG = LoggerFactory.getLogger(LocalUserServer.class);

    private final AppUserRepository appUserRepository;
    private final AppGroupRepository appGroupRepository;
    private final AppGroupMemberRepository appGroupMemberRepository;
    private final PasswordService passwordService;

    public LocalUserServer(AppUserRepository appUserRepository,
                           AppGroupRepository appGroupRepository,
                           AppGroupMemberRepository appGroupMemberRepository,
                           PasswordService passwordService) {
        this.appUserRepository = appUserRepository;
        this.appGroupRepository = appGroupRepository;
        this.appGroupMemberRepository = appGroupMemberRepository;
        this.passwordService = passwordService;
    }

    @Override
    public Caller loginAndMerge(String loginName, String password, Caller caller) throws Exception {
        AppUser user = appUserRepository.findByLoginName(loginName).orElse(null);
        if (user == null) {
            throw new Exception("User not found: " + loginName);
        }
        if (!user.isActive()) {
            throw new Exception("User account is disabled: " + loginName);
        }
        if (user.isLdap()) {
            throw new Exception("LDAP authentication required for user: " + loginName);
        }
        if (user.isRemote()) {
            throw new Exception("Remote authentication required for user: " + loginName);
        }
        if (!passwordService.verify(password, user.getPasswordHash())) {
            throw new Exception("Invalid password for user: " + loginName);
        }

        // Update login stats
        user.setLastLoginTime((int) (System.currentTimeMillis() / 1000));
        user.setNumLogins(user.getNumLogins() + 1);
        appUserRepository.save(user);

        return new Caller(loginName);
    }

    @Override
    public String getUserIdByLoginName(String loginName) throws Exception {
        // In the Polopoly user model, loginName IS the user identifier
        return appUserRepository.findByLoginName(loginName)
            .map(AppUser::getLoginName)
            .orElse(null);
    }

    @Override
    public Group findGroup(GroupId groupId) throws Exception {
        return appGroupRepository.findById(groupId.getId())
            .map(g -> {
                List<AppGroupMember> members = appGroupMemberRepository.findByGroupId(g.getGroupId());
                Set<String> memberIds = new HashSet<>();
                for (AppGroupMember m : members) {
                    memberIds.add(m.getPrincipalId());
                }
                return (Group) new LocalGroup(new GroupId(g.getGroupId()), g.getName(), memberIds);
            })
            .orElse(null);
    }

    @Override
    public GroupId[] getAllGroups() throws Exception {
        List<AppGroup> groups = appGroupRepository.findAll();
        GroupId[] result = new GroupId[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            result[i] = new GroupId(groups.get(i).getGroupId());
        }
        return result;
    }

    @Override
    public GroupId[] findGroupsByMember(String principalId) throws Exception {
        List<AppGroupMember> memberships = appGroupMemberRepository.findByPrincipalId(principalId);
        GroupId[] result = new GroupId[memberships.size()];
        for (int i = 0; i < memberships.size(); i++) {
            result[i] = new GroupId(memberships.get(i).getGroupId());
        }
        return result;
    }
}
