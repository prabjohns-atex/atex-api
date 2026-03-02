package com.atex.onecms.app.dam.standard.aspects;

import com.atex.onecms.content.aspects.annotations.AspectDefinition;
import java.util.Date;

@AspectDefinition(DamAutoPageAspectBean.ASPECT_NAME)
public class DamAutoPageAspectBean extends OneContentBean {

    public static final String OBJECT_TYPE = "autoPage";
    public static final String ASPECT_NAME = "atex.dam.standard.AutoPage";

    private String shapeDbHash;

    public DamAutoPageAspectBean() {
        super.setObjectType(OBJECT_TYPE);
        super.set_type(ASPECT_NAME);
        super.setCreationdate(new Date());
    }

    public String getShapeDbHash() { return shapeDbHash; }
    public void setShapeDbHash(final String shapeDbHash) { this.shapeDbHash = shapeDbHash; }
}

