package com.atex.onecms.app.dam.ws;

import com.atex.onecms.app.dam.ayrshare.AyrShareService;
import com.atex.onecms.app.dam.ayrshare.AyrShareService.SocialAccountResponse;
import com.atex.onecms.content.ContentManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

@RestController
@RequestMapping("/dam/content/social")
@Tag(name = "DAM Social")
public class SocialResource {

    private static final Logger LOGGER = Logger.getLogger(SocialResource.class.getName());
    private static final Gson GSON = new GsonBuilder().create();
    private static final AtomicReference<AyrShareService> ayrShareService = new AtomicReference<>();

    private final ContentManager contentManager;

    public SocialResource(ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    @GetMapping("accounts")
    public ResponseEntity<String> getSocialAccounts(HttpServletRequest request) {
        DamUserContext.from(request).assertLoggedIn();
        try {
            final AyrShareService service = getAyrShareService();
            final SocialAccountResponse response = service.getActiveAccounts();
            return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(GSON.toJson(response));
        } catch (Exception e) {
            throw ContentApiException.internal("Error fetching social accounts", e);
        }
    }

    private AyrShareService getAyrShareService() {
        if (ayrShareService.get() == null) {
            ayrShareService.set(new AyrShareService(contentManager));
        }
        return ayrShareService.get();
    }
}

