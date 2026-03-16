package com.atex.desk.integration.schedule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NGINX cache invalidation via PURGE requests.
 * Ported from adm-starterkit NginxCacheHandler.
 *
 * <p>Sends HTTP PURGE requests to NGINX cache when content is updated
 * or deleted, to ensure the CDN/cache serves fresh content.
 */
@Component
@ConditionalOnProperty(name = "desk.integration.nginx-cache.enabled", havingValue = "true")
public class NginxCacheInvalidator implements ChangeProcessor.ChangeHandler {

    private static final Logger LOG = Logger.getLogger(NginxCacheInvalidator.class.getName());

    private final HttpClient httpClient;

    @Value("${desk.integration.nginx-cache.purge-url:http://localhost:80}")
    private String purgeBaseUrl;

    @Value("${desk.integration.nginx-cache.purge-paths:/content/,/api/}")
    private String purgePaths;

    public NginxCacheInvalidator() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Override
    public boolean accepts(ChangeProcessor.ChangeEvent event) {
        return event.isUpdate() || event.isDelete();
    }

    @Override
    public void handle(ChangeProcessor.ChangeEvent event) {
        for (String path : purgePaths.split(",")) {
            String purgeUrl = purgeBaseUrl + path.trim() + event.contentId();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(purgeUrl))
                    .method("PURGE", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();

                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
                LOG.fine(() -> "NGINX purge " + purgeUrl + " -> " + response.statusCode());

            } catch (Exception e) {
                LOG.log(Level.FINE, "NGINX purge failed for " + purgeUrl, e);
            }
        }
    }
}
