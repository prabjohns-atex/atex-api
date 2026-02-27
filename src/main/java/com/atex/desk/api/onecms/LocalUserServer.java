package com.atex.desk.api.onecms;

import com.atex.desk.api.entity.AppUser;
import com.atex.desk.api.repository.AppUserRepository;
import com.polopoly.user.server.Caller;
import com.polopoly.user.server.Group;
import com.polopoly.user.server.GroupId;
import com.polopoly.user.server.UserServer;
import org.springframework.stereotype.Component;

/**
 * Local UserServer implementation backed by AppUserRepository.
 */
@Component
public class LocalUserServer implements UserServer {

    private final AppUserRepository appUserRepository;

    public LocalUserServer(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public Caller loginAndMerge(String loginName, String password, Caller caller) throws Exception {
        // Login is handled by SecurityController/TokenService
        return new Caller(loginName);
    }

    @Override
    public String getUserIdByLoginName(String loginName) throws Exception {
        return appUserRepository.findByUsername(loginName)
            .map(u -> String.valueOf(u.getUserId()))
            .orElse(null);
    }

    @Override
    public Group findGroup(GroupId groupId) throws Exception {
        // Groups not yet implemented
        return null;
    }
}
