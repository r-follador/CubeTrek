package com.cubetrek.upload.garminconnect;

import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.exception.OAuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GarminActivityDownloader {
    private final String consumerKey;
    private final String consumerSecret;
    private final Set<String> allowedHosts;

    public GarminActivityDownloader(@Value("${garmin.consumer.key}") String consumerKey,
                                    @Value("${garmin.consumer.secret}") String consumerSecret,
                                    @Value("${garmin.callback.allowed-hosts:healthapi.garmin.com,apis.garmin.com}") String allowedHosts) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.allowedHosts = Arrays.stream(allowedHosts.split(",")).map(String::trim)
                .map(host -> host.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public URI validate(String callback) throws IOException {
        try {
            URI uri = URI.create(callback);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IOException("Garmin callback destination is not allowed");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid Garmin callback URL");
        }
    }

    public byte[] download(String callback, String bearer, String legacyToken, String legacySecret) throws IOException {
        HttpURLConnection request = openConnection(validate(callback));
        try {
            request.setInstanceFollowRedirects(false);
            request.setConnectTimeout(30_000);
            request.setReadTimeout(30_000);
            if (bearer != null) {
                request.setRequestProperty("Authorization", "Bearer " + bearer);
            } else {
                DefaultOAuthConsumer consumer = new DefaultOAuthConsumer(consumerKey, consumerSecret);
                consumer.setTokenWithSecret(legacyToken, legacySecret);
                consumer.sign(request);
            }
            int status = request.getResponseCode();
            if (status < 200 || status >= 300) throw new DownloadException(status);
            try (InputStream stream = request.getInputStream()) {
                byte[] data = stream.readNBytes(10_000_001);
                if (data.length > 10_000_000) throw new DownloadException(413);
                return data;
            }
        } catch (OAuthException e) {
            throw new IOException("Could not sign Garmin download");
        } finally {
            request.disconnect();
        }
    }

    HttpURLConnection openConnection(URI uri) throws IOException {
        return (HttpURLConnection) uri.toURL().openConnection();
    }

    static class DownloadException extends IOException {
        private final int status;
        DownloadException(int status) { super("Garmin callback returned HTTP " + status); this.status = status; }
        boolean retryable() { return status == 429 || status >= 500; }
    }
}
