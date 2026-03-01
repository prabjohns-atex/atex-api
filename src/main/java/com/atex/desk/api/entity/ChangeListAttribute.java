package com.atex.desk.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "changelistattributes")
@IdClass(ChangeListAttributeId.class)
public class ChangeListAttribute
{
    @Id
    private Integer id;

    @Id
    @Column(name = "attrid")
    private Integer attrId;

    @Column(name = "str_value", length = 255)
    private String strValue;

    @Column(name = "num_value")
    private Integer numValue;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getAttrId() { return attrId; }
    public void setAttrId(Integer attrId) { this.attrId = attrId; }

    public String getStrValue() { return strValue; }
    public void setStrValue(String strValue) { this.strValue = strValue; }

    public Integer getNumValue() { return numValue; }
    public void setNumValue(Integer numValue) { this.numValue = numValue; }
}
