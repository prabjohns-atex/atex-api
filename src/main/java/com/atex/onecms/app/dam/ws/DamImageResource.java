package com.atex.onecms.app.dam.ws;

import com.atex.onecms.app.dam.operation.ContentOperationUtils;
import com.atex.onecms.app.dam.standard.aspects.OneImageBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileService;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/dam/image")
@Tag(name = "DAM Image")
public class DamImageResource {

    private static final Logger LOGGER = Logger.getLogger(DamImageResource.class.getName());

    private final ContentManager contentManager;
    private final FileService fileService;

    public DamImageResource(ContentManager contentManager, @Nullable FileService fileService) {
        this.contentManager = contentManager;
        this.fileService = fileService;
    }

    @PostMapping("create-print-variant/{id}")
    public ResponseEntity<String> createPrintVariantImage(HttpServletRequest request,
                                                           @PathVariable("id") String id) {
        DamUserContext ctx = DamUserContext.from(request);
        Subject subject = ctx.assertLoggedIn().getSubject();
        String loginName = ctx.getCaller().getLoginName();

        try {
            final ContentId originalId = IdUtil.fromString(id);

            ContentVersionId originalVid = contentManager.resolve(originalId, subject);
            if (originalVid == null) {
                throw ContentApiException.notFound(originalId);
            }
            @SuppressWarnings("unchecked")
            final ContentResult<Object> originalCr = contentManager.get(originalVid, null, Object.class, null, subject);
            if (!originalCr.getStatus().isSuccess()) {
                throw ContentApiException.error("Cannot get " + IdUtil.toVersionedIdString(originalVid),
                    originalCr.getStatus());
            }

            if (!(originalCr.getContent().getContentData() instanceof OneImageBean)) {
                throw ContentApiException.badRequest("Original content must be an image");
            }
            final OneImageBean originalImageBean = (OneImageBean) originalCr.getContent().getContentData();
            if (originalImageBean.isNoUseWeb()) {
                throw ContentApiException.badRequest("Original image cannot be a print variant");
            }

            final ContentOperationUtils utils = ContentOperationUtils.getInstance();
            utils.configure(contentManager, fileService);

            final UnaryOperator<ContentWriteBuilder<Object>> editOriginal = cwb -> {
                Object contentData = originalCr.getContent().getContentData();
                if (contentData instanceof OneImageBean bean) {
                    bean.setNoUsePrint(true);
                    cwb.mainAspectData(bean);
                }
                return cwb;
            };
            final UnaryOperator<ContentWriteBuilder<Object>> editDuplicated = cwb -> {
                Object contentData = cwb.buildCreate().getContentData();
                if (contentData instanceof OneImageBean duplicatedCw) {
                    duplicatedCw.setNoUseWeb(true);
                    duplicatedCw.setNoUsePrint(false);
                    cwb.mainAspectData(duplicatedCw);
                }
                return cwb;
            };

            final List<ContentId> duplicateContents = utils.duplicate(
                Collections.singletonList(originalId), subject, loginName,
                editOriginal, editDuplicated);
            if (duplicateContents.isEmpty()) {
                throw ContentApiException.badRequest("The image has not been duplicated!");
            }
            final ContentId duplicatedId = duplicateContents.get(0);
            LOGGER.info("Created print variant, original image " + IdUtil.toIdString(originalId)
                + " duplicated image " + IdUtil.toIdString(duplicatedId));
            final JsonObject json = new JsonObject();
            json.addProperty("id", IdUtil.toIdString(duplicatedId));
            return ResponseEntity.ok(json.toString());
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal(e.getMessage(), e);
        }
    }
}

