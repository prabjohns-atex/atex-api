package com.atex.onecms.content;

import java.util.Map;

import com.atex.onecms.content.callback.CallbackException;
import com.atex.onecms.content.repository.ContentModifiedException;
import com.atex.onecms.content.repository.StorageException;

/**
 * Java interface to OneCMS content. The Content Manager provides access to
 * different representations of content based on a variant.
 */
public interface ContentManager {

    String SYSTEM_VIEW_PUBLIC = "p.public";
    String SYSTEM_VIEW_LATEST = "p.latest";
    String SYSTEM_VIEW_DELETE = "p.deleted";

    ContentVersionId resolve(ContentId id, Subject subject) throws StorageException;

    ContentVersionId resolve(ContentId id, String view, Subject subject) throws StorageException;

    ContentVersionId resolve(String externalId, Subject subject) throws StorageException;

    ContentVersionId resolve(String externalId, String view, Subject subject) throws StorageException;

    default ContentHistory getContentHistory(ContentId contentId, Subject subject)
            throws StorageException {
        return getContentHistory(contentId, subject, (GetOption) null);
    }

    ContentHistory getContentHistory(ContentId contentId, Subject subject,
                                     GetOption... options) throws StorageException;

    default <T> ContentResult<T> get(ContentVersionId contentId, String variant,
                                      Class<T> dataClass, Map<String, Object> params,
                                      Subject subject)
            throws ClassCastException, StorageException {
        return get(contentId, variant, dataClass, params, subject, (GetOption) null);
    }

    <T> ContentResult<T> get(ContentVersionId contentId, String variant,
                              Class<T> dataClass, Map<String, Object> params,
                              Subject subject, GetOption... options)
            throws ClassCastException, StorageException;

    default <T> ContentResult<T> get(ContentVersionId contentId, Class<T> dataClass,
                                      Subject subject)
            throws ClassCastException, StorageException {
        return get(contentId, null, dataClass, null, subject);
    }

    default <T> ContentResult<T> get(ContentVersionId contentId, Class<T> dataClass,
                                      Subject subject, GetOption... options)
            throws ClassCastException, StorageException {
        return get(contentId, null, dataClass, null, subject, options);
    }

    <IN, OUT> ContentResult<OUT> update(ContentId contentId, ContentWrite<IN> data,
                                         Subject subject)
            throws StorageException, ContentModifiedException, CallbackException;

    <IN, OUT> ContentResult<OUT> forceUpdate(ContentId contentId, ContentWrite<IN> data,
                                              Subject subject)
            throws StorageException, ContentModifiedException, CallbackException;

    <IN, OUT> ContentResult<OUT> create(ContentWrite<IN> content, Subject subject)
            throws IllegalArgumentException, StorageException, CallbackException;

    String getConfigId() throws StorageException;

    default <T> WorkspaceResult<T> getFromWorkspace(String workspaceId, ContentId contentId,
                                                     String variant, Map<String, Object> params,
                                                     Class<T> targetClass, Subject subject)
            throws ClassCastException, StorageException {
        return getFromWorkspace(workspaceId, contentId, variant, params, targetClass, subject,
                               (GetOption) null);
    }

    default <T> WorkspaceResult<T> getFromWorkspace(String workspaceId, ContentId contentId,
                                                     String variant, Map<String, Object> params,
                                                     Class<T> targetClass, Subject subject,
                                                     GetOption... options)
            throws ClassCastException, StorageException {
        throw new UnsupportedOperationException("Workspaces not yet supported");
    }

    default <T> WorkspaceResult<T> getFromWorkspace(String workspaceId, ContentId contentId,
                                                     Class<T> targetClass, Subject subject)
            throws ClassCastException, StorageException {
        return getFromWorkspace(workspaceId, contentId, null, null, targetClass, subject,
                               (GetOption) null);
    }

    default <T> WorkspaceResult<T> getFromWorkspace(String workspaceId, ContentId contentId,
                                                     Class<T> targetClass, Subject subject,
                                                     GetOption... options)
            throws ClassCastException, StorageException {
        return getFromWorkspace(workspaceId, contentId, null, null, targetClass, subject, options);
    }

    default <T> WorkspaceResult<T> updateOnWorkspace(String workspaceId, ContentId contentId,
                                                      ContentWrite<T> data, Subject subject)
            throws ClassCastException, StorageException, ContentModifiedException {
        throw new UnsupportedOperationException("Workspaces not yet supported");
    }

    default <T> WorkspaceResult<T> createOnWorkspace(String workspaceId, ContentWrite<T> content,
                                                      Subject subject)
            throws ContentModifiedException, StorageException {
        throw new UnsupportedOperationException("Workspaces not yet supported");
    }

    default DeleteResult delete(ContentId contentId, ContentVersionId latestVersion,
                                 Subject subject)
            throws ContentModifiedException, StorageException, CallbackException {
        return delete(contentId, latestVersion, subject, (DeleteOption) null);
    }

    DeleteResult delete(ContentId contentId, ContentVersionId latestVersion,
                         Subject subject, DeleteOption... options)
            throws ContentModifiedException, StorageException, CallbackException;

    default Status clearWorkspace(String workspaceId, Subject subject)
            throws StorageException, ContentModifiedException, CallbackException {
        throw new UnsupportedOperationException("Workspaces not yet supported");
    }

    default DeleteResult deleteFromWorkspace(String workspaceId, ContentId contentId,
                                              Subject subject)
            throws StorageException, ContentModifiedException, CallbackException {
        return deleteFromWorkspace(workspaceId, contentId, subject, (DeleteOption) null);
    }

    default DeleteResult deleteFromWorkspace(String workspaceId, ContentId contentId,
                                              Subject subject, DeleteOption... options)
            throws StorageException, ContentModifiedException, CallbackException {
        throw new UnsupportedOperationException("Workspaces not yet supported");
    }

    default <T> ContentResult<T> promote(String workspaceId, ContentId contentId,
                                          Subject subject)
            throws StorageException, ContentModifiedException, CallbackException {
        throw new UnsupportedOperationException("Workspaces not yet supported");
    }

    interface Option {}
    interface DeleteOption extends Option {}
    interface GetOption extends Option {}
}
