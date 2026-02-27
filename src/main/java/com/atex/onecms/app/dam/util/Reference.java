package com.atex.onecms.app.dam.util;

import java.io.Serializable;
import java.util.ArrayList;

public class Reference implements Serializable {
    private static final long serialVersionUID = 1385447152358774463L;
    private ArrayList<String> references;

    public Reference() {
        this.references = new ArrayList<>();
    }

    public ArrayList<String> getReferences() { return references; }
    public void setReferences(ArrayList<String> references) { this.references = references; }
}
