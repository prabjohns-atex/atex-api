package com.atex.desk.api.plugin;

import com.atex.onecms.content.ContentResult;
import org.pf4j.ExtensionPoint;

import java.util.Map;

/**
 * PF4J extension point for content composers.
 * Plugin authors implement this interface to transform content for a specific variant.
 */
public interface DeskContentComposer extends ExtensionPoint {

    /**
     * The variant name this composer handles.
     */
    String variant();

    /**
     * Transform content for the requested variant.
     *
     * @param source the original content result
     * @param params optional parameters for the composition
     * @return the transformed content result
     */
    ContentResult<Object> compose(ContentResult<Object> source,
                                   Map<String, Object> params);
}
