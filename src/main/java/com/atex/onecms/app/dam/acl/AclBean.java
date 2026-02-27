package com.atex.onecms.app.dam.acl;
import java.util.List;
public class AclBean {
    private String id;
    private String permission;
    private List<String> read;
    private List<String> write;
    private boolean decorate = true;
    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getPermission() { return permission; }
    public void setPermission(String v) { this.permission = v; }
    public List<String> getRead() { return read; }
    public void setRead(List<String> v) { this.read = v; }
    public List<String> getWrite() { return write; }
    public void setWrite(List<String> v) { this.write = v; }
    public boolean isDecorate() { return decorate; }
    public void setDecorate(boolean v) { this.decorate = v; }
}
