package com.atex.onecms.app.dam.standard.aspects;

import java.util.Map;

public interface PropertyBag {
    Map<String, Map<String, String>> getPropertyBag();
    void setPropertyBag(Map<String, Map<String, String>> propertyBag);
}

