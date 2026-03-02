package com.atex.onecms.app.dam.standard.aspects;

import java.util.Date;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;

@AspectDefinition(DamShapeDBAspectBean.ASPECT_NAME)
public class DamShapeDBAspectBean extends OneContentBean {

    public static final String OBJECT_TYPE = "shapeDb";
    public static final String ASPECT_NAME = "atex.dam.standard.ShapeDB";

    private String shapeDb;
    private String lastUpdate;

    public DamShapeDBAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
    }

    public String getShapeDb() { return shapeDb; }
    public void setShapeDb(final String shapeDb) { this.shapeDb = shapeDb; }
    public String getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(final String lastUpdate) { this.lastUpdate = lastUpdate; }
}

