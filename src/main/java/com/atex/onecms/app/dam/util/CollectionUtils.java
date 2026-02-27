package com.atex.onecms.app.dam.util;

import java.util.List;

public class CollectionUtils {

    public static <T> boolean notNull(List<T> list) {
        return list != null && !list.isEmpty();
    }
}
