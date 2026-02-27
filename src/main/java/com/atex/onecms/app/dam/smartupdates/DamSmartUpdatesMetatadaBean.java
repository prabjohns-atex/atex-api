package com.atex.onecms.app.dam.smartupdates;

import java.util.List;
import java.util.Map;

public class DamSmartUpdatesMetatadaBean {
    private List<String> id;
    private String field;
    private String value;
    private Map<String, Object> data;
    public List<String> getId() { return id; }
    public void setId(List<String> v) { this.id = v; }
    public String getField() { return field; }
    public void setField(String v) { this.field = v; }
    public String getValue() { return value; }
    public void setValue(String v) { this.value = v; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> v) { this.data = v; }
}
