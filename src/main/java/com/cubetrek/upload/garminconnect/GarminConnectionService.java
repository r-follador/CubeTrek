package com.cubetrek.upload.garminconnect;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class GarminConnectionService {
    private final UserThirdpartyConnectRepository repository;

    public GarminConnectionService(UserThirdpartyConnectRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void linkOAuth1(Users user, String userId, String accessToken, String secret) {
        UserThirdpartyConnect connection = connectionFor(user, userId);
        GarminTokens.clear(connection);
        connection.setGarminUserid(userId);
        connection.setGarminUseraccesstoken(accessToken);
        connection.setGarminUseraccesstokenSecret(secret);
        connection.setGarminEnabled(true);
        repository.saveAndFlush(connection);
    }

    @Transactional
    public void linkOAuth2(Users user, String userId, GarminTokens tokens, boolean activityExportAllowed) {
        UserThirdpartyConnect connection = connectionFor(user, userId);
        GarminTokens.clear(connection);
        connection.setGarminUserid(userId);
        connection.setGarminUseraccesstoken(null);
        connection.setGarminUseraccesstokenSecret(null);
        tokens.applyTo(connection);
        connection.setGarminEnabled(activityExportAllowed);
        repository.saveAndFlush(connection);
    }

    private UserThirdpartyConnect connectionFor(Users user, String userId) {
        UserThirdpartyConnect connection = repository.lockByUserId(user.getId());
        UserThirdpartyConnect other = repository.findByGarminUserid(userId);
        if (other != null && (connection == null || !Objects.equals(other.getId(), connection.getId()))) {
            throw new ExceptionHandling.UnnamedException("Failed", "This Garmin Account is already linked to another CubeTrek account");
        }
        if (connection == null) {
            connection = new UserThirdpartyConnect();
            connection.setUser(user);
        }
        return connection;
    }
}
