package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.util.HttpDamUtils;
import com.atex.onecms.content.ContentId;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

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
}
