package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.util.HttpDamUtils;
import com.atex.onecms.content.ContentId;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public interface ContentPublisher {

    PublishResult publishContent(String json) throws ContentPublisherException;

    PublishResult publishContentUpdate(ContentId contentId, String json) throws ContentPublisherException;

    PublishResult unpublish(ContentId contentId, String json) throws ContentPublisherException;

    String publishBinary(InputStream is, String filePath, String mimeType) throws ContentPublisherException;

    ByteArrayOutputStream readBinary(String uri) throws ContentPublisherException;

    ContentId resolve(String externalId) throws ContentPublisherException;

    String getContent(ContentId contentId) throws ContentPublisherException;

    String getRemotePublicationUrl(ContentId sourceId, ContentId remoteId);

    HttpDamUtils.WebServiceResponse postContent(String json, String... path) throws ContentPublisherException;

    // --- Page publishing methods (used by DamPageResource) ---

    default HttpDamUtils.WebServiceResponse publishPage(String pageJson) throws ContentPublisherException {
        throw new UnsupportedOperationException("publishPage not implemented");
    }

    default HttpDamUtils.WebServiceResponse duplicatePage(String sourcePageId, String targetParentId,
                                                           String title, String description) throws ContentPublisherException {
        throw new UnsupportedOperationException("duplicatePage not implemented");
    }

    default HttpDamUtils.WebServiceResponse getContent(Function<Object, Object> pathBuilder,
                                                        UnaryOperator<Object> configurer) throws ContentPublisherException {
        throw new UnsupportedOperationException("getContent with path builder not implemented");
    }

    default String toRemoteContentId(String sourceId) { return sourceId; }
    default String fromRemoteContentId(String sourceId) { return sourceId; }
}
