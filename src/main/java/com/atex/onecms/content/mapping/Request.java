package com.atex.onecms.content.mapping;

import com.atex.onecms.content.ContentManager.GetOption;
import com.atex.onecms.content.Subject;

import java.util.Map;

/**
 * Information about the request passed to a ContentComposer.
 */
public interface Request {

    /**
     * @return map of parameter name to value, where any multivalued values are collections.
     */
    Map<String, Object> getRequestParameters();

    /**
     * @return the subject of the request.
     */
    Subject getSubject();

    /**
     * Return the GetOption that have been used for the current request.
     *
     * @return an array of options that may be null.
     */
    GetOption[] getOptions();
}

