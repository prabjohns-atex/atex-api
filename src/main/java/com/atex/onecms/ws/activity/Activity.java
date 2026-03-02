package com.atex.onecms.ws.activity;

import java.util.HashMap;
import java.util.Map;

/**
 * Input bean for activity registration.
 */
public class Activity {

    private String activity;
    private Map<String, String> params = new HashMap<>();

    public Activity() {}

    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }

    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) { this.params = params; }
}
