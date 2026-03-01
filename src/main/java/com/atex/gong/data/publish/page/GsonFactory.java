package com.atex.gong.data.publish.page;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Gson factory for page data serialization.
 */
public final class GsonFactory {
    private GsonFactory() {}

    public static Gson create() {
        return new GsonBuilder().create();
    }
}

