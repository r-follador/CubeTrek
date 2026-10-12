package com.cubetrek.upload.garminconnect;

import com.cubetrek.ExceptionHandling;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.database.UserThirdpartyConnectRepository;
import com.cubetrek.database.Users;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GarminAuthorizationTest {
    private final GarminconnectAuthSession session = new GarminconnectAuthSession();
    private final GarminApiClient api = mock(GarminApiClient.class);
    private final GarminConnectionService connections = mock(GarminConnectionService.class);
    private final Users user = new Users();
    private final Authentication auth = mock(Authentication.class);

    private GarminconnectController controller(boolean oauth2) {
        ReflectionTestUtils.setField(user, "id", 42L);
        when(auth.getPrincipal()).thenReturn(user);
        return new GarminconnectController("key", "secret", "https://cubetrek.test/", oauth2, session, api, connections);
    }

    @Test
    void oauth1CallbackFetchesAndSavesIdBeforeCompletingConnection() throws Exception {
        var controller = controller(false);
        OAuthConsumer consumer = mock(OAuthConsumer.class);
        OAuthProvider provider = mock(OAuthProvider.class);
        when(consumer.getToken()).thenReturn("request", "access");
        when(consumer.getTokenSecret()).thenReturn("secret");
        when(api.userIdOAuth1("access", "secret")).thenReturn("stable-id");
        session.beginOAuth1(42L, consumer, provider);

        assertEquals("redirect:/profile", controller.connectToGarmin2(auth, "request", "verifier", null, null, null));
        var order = inOrder(provider, api, connections);
        order.verify(provider).retrieveAccessToken(consumer, "verifier");
        order.verify(api).userIdOAuth1("access", "secret");
        order.verify(connections).linkOAuth1(user, "stable-id", "access", "secret");
        assertThrows(ExceptionHandling.UnnamedException.class,
                () -> controller.connectToGarmin2(auth, "request", "verifier", null, null, null));
        verifyNoMoreInteractions(api, connections);
    }

    @Test
    void oauth1RejectsMismatchedRequestTokenBeforeExchange() {
        var controller = controller(false);
        OAuthConsumer consumer = mock(OAuthConsumer.class);
        OAuthProvider provider = mock(OAuthProvider.class);
        when(consumer.getToken()).thenReturn("expected");
        session.beginOAuth1(42L, consumer, provider);
        assertThrows(ExceptionHandling.UnnamedException.class,
                () -> controller.connectToGarmin2(auth, "other", "verifier", null, null, null));
        verifyNoInteractions(provider, api, connections);
    }

    @Test
    void oauth2RedirectCarriesPkceAndRandomState() throws Exception {
        var controller = controller(true);
        when(api.oauth2ClientId()).thenReturn("client");
        String redirect = controller.connectToGarmin(auth);
        var pending = (GarminconnectAuthSession.Pending) ReflectionTestUtils.getField(session, "pending");
        assertNotNull(pending);
        assertEquals(43, pending.verifier().length());
        assertEquals(43, pending.state().length());
        assertNotEquals(pending.state(), pending.verifier());
        assertTrue(redirect.startsWith("redirect:https://connect.garmin.com/oauth2Confirm?"));
        assertTrue(redirect.contains("code_challenge_method=S256"));
        assertTrue(redirect.contains("code_challenge=" + pending.challenge()));
        assertTrue(redirect.contains("state=" + pending.state()));
        assertFalse(redirect.contains(pending.verifier()));
    }

    @Test
    void oauth2CallbackSavesIdentityAndHonorsDeniedActivityPermission() throws Exception {
        var controller = controller(true);
        var pending = session.beginOAuth2(42L, "https://cubetrek.test/profile/connectGarmin-step2");
        GarminTokens tokens = new GarminTokens("access", "refresh", Instant.now().plusSeconds(3600), null);
        when(api.exchangeCode("code", pending.verifier(), pending.redirectUri())).thenReturn(tokens);
        when(api.userId("access")).thenReturn("stable-id");
        when(api.activityExportAllowed("access")).thenReturn(false);

        assertEquals("redirect:/profile", controller.connectToGarmin2(auth, null, null, "code", pending.state(), null));
        verify(connections).linkOAuth2(user, "stable-id", tokens, false);
        assertThrows(ExceptionHandling.UnnamedException.class,
                () -> controller.connectToGarmin2(auth, null, null, "code", pending.state(), null));
        verify(api, times(1)).exchangeCode(anyString(), anyString(), anyString());
    }

    @Test
    void oauth2RejectsInvalidStateBeforeAnyApiCall() {
        var controller = controller(true);
        session.beginOAuth2(42L, "https://cubetrek.test/callback");
        assertThrows(ExceptionHandling.UnnamedException.class,
                () -> controller.connectToGarmin2(auth, null, null, "code", "wrong", null));
        verifyNoInteractions(api, connections);
    }

    @Test
    void sessionRejectsOtherUserAndExpiredAuthorization() {
        var pending = session.beginOAuth2(42L, "https://cubetrek.test/callback");
        assertThrows(IllegalArgumentException.class, () -> session.consumeOAuth2(43L, pending.state()));
        ReflectionTestUtils.setField(session, "pending", new GarminconnectAuthSession.Pending(42L,
                Instant.now().minusSeconds(601), null, null, "state", "verifier", "callback"));
        assertThrows(IllegalArgumentException.class, () -> session.consumeOAuth2(42L, "state"));
    }

    @Test
    void pkceMatchesRfc7636Vector() {
        var pending = new GarminconnectAuthSession.Pending(42L, Instant.now(), null, null, "state",
                "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", "callback");
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pending.challenge());
    }

    @Test
    void oauth1RelinkClearsOldOauth2StateAndReplacesIdentity() {
        var repository = mock(UserThirdpartyConnectRepository.class);
        var service = new GarminConnectionService(repository);
        ReflectionTestUtils.setField(user, "id", 42L);
        var existing = new UserThirdpartyConnect();
        existing.setId(7L);
        existing.setGarminUserid("old-id");
        existing.setGarminOauth2AccessToken("old-access");
        existing.setGarminOauth2RefreshToken("old-refresh");
        existing.setGarminOauth2MigrationCompletedAt(Instant.now());
        existing.setGarminOauth2ReconnectRequired(true);
        when(repository.lockByUserId(42L)).thenReturn(existing);
        service.linkOAuth1(user, "new-id", "new-access", "new-secret");
        assertEquals("new-id", existing.getGarminUserid());
        assertEquals("new-access", existing.getGarminUseraccesstoken());
        assertNull(existing.getGarminOauth2AccessToken());
        assertNull(existing.getGarminOauth2RefreshToken());
        assertNull(existing.getGarminOauth2MigrationCompletedAt());
        assertFalse(existing.isGarminOauth2ReconnectRequired());
        assertTrue(existing.isGarminEnabled());
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void linkingRefusesIdentityOwnedByAnotherCubetrekUser() {
        var repository = mock(UserThirdpartyConnectRepository.class);
        var other = new UserThirdpartyConnect();
        other.setId(8L);
        when(repository.findByGarminUserid("taken")).thenReturn(other);
        assertThrows(ExceptionHandling.UnnamedException.class,
                () -> new GarminConnectionService(repository).linkOAuth1(user, "taken", "token", "secret"));
        verify(repository, never()).saveAndFlush(any());
    }
}
