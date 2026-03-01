package com.atex.gong.data.publish.page;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Page data model for page publishing.
 */
public class Page {
    private String name;
    private String externalId;
    private String aceId;
    private List<Container> containers = new ArrayList<>();

    public Page() {}

    public Page(Page other) {
        this.name = other.name;
        this.externalId = other.externalId;
        this.aceId = other.aceId;
        this.containers = new ArrayList<>(other.containers);
    }

    public static Page from(JsonElement json) {
        return GsonFactory.create().fromJson(json, Page.class);
    }

    public JsonElement toJson() {
        return GsonFactory.create().toJsonTree(this);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getAceId() { return aceId; }
    public void setAceId(String aceId) { this.aceId = aceId; }
    public List<Container> getContainers() { return containers; }
    public void setContainers(List<Container> containers) { this.containers = containers; }
}
