package com.atex.plugins.layout;

public class LayoutServerConfigurationBean {
    private String layoutServer;
    private String printServer;
    private String layoutNewsRoomServer;
    private int timeout = 360;
    private int poolSize = 32;
    private String slowUrlRegex;
    private String vSlowUrlRegex;

    @Override
    public String toString() {
        return "LayoutServerConfigurationBean{" +
                "layoutServer='" + layoutServer + '\'' +
                ", printServer='" + printServer + '\'' +
                ", layoutNewsRoomServer='" + layoutNewsRoomServer + '\'' +
                ", timeout=" + timeout +
                ", poolSize=" + poolSize +
                ", slowUrlRegex='" + slowUrlRegex + '\'' +
                ", vSlowUrlRegex='" + vSlowUrlRegex + '\'' +
                '}';
    }

    public String getLayoutNewsRoomServer() { return layoutNewsRoomServer; }
    public void setLayoutNewsRoomServer(String layoutNewsRoomServer) { this.layoutNewsRoomServer = layoutNewsRoomServer; }

    public String getLayoutServer() { return layoutServer; }
    public void setLayoutServer(String layoutServer) { this.layoutServer = layoutServer; }

    public String getPrintServer() { return printServer; }
    public void setPrintServer(String printServer) { this.printServer = printServer; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }

    public String getSlowUrlRegex() { return slowUrlRegex; }
    public void setSlowUrlRegex(String slowUrls) { this.slowUrlRegex = slowUrls; }

    public String getvSlowUrlRegex() { return vSlowUrlRegex; }
    public void setvSlowUrlRegex(String vSlowUrlRegex) { this.vSlowUrlRegex = vSlowUrlRegex; }
}
