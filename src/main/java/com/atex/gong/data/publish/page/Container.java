package com.atex.gong.data.publish.page;

import java.util.ArrayList;
import java.util.List;

public class Container {
    private String name;
    private List<Article> articles = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Article> getArticles() { return articles; }
    public void setArticles(List<Article> articles) { this.articles = articles; }

    @Override
    public String toString() { return "Container{name='" + name + "', articles=" + articles.size() + "}"; }
}
