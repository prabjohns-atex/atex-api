package com.atex.desk.integration.distribution;

/**
 * Configuration for a content distribution route.
 * Ported from {@code DamSendContentConfiguration} in gong/desk.
 *
 * <p>Defines how content is delivered to an external system: via file transfer (FTP/SFTP),
 * email (SMTP), or a custom handler.
 */
public class DistributionRoute {

    /** Service type: file-transfer, email, or custom handler class. */
    private String service;
    private String name;
    private String protocol; // ftp, sftp, smtp, local
    private String host;
    private int port;
    private String username;
    private String password;

    // File transfer
    private String localFolder;
    private String remoteFolder;

    // Email
    private String toAddress;
    private String fromAddress;
    private String emailSubject;
    private String emailTemplate;
    private boolean contentAttached;
    private boolean contentZipped;

    // Content filtering
    private String variant;
    private String webStatus;
    private String printStatus;
    private String defaultAction;
    private String options;

    // Flags
    private boolean enabled = true;
    private boolean debug;

    // Getters/setters

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getLocalFolder() { return localFolder; }
    public void setLocalFolder(String localFolder) { this.localFolder = localFolder; }

    public String getRemoteFolder() { return remoteFolder; }
    public void setRemoteFolder(String remoteFolder) { this.remoteFolder = remoteFolder; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getEmailSubject() { return emailSubject; }
    public void setEmailSubject(String emailSubject) { this.emailSubject = emailSubject; }

    public String getEmailTemplate() { return emailTemplate; }
    public void setEmailTemplate(String emailTemplate) { this.emailTemplate = emailTemplate; }

    public boolean isContentAttached() { return contentAttached; }
    public void setContentAttached(boolean contentAttached) { this.contentAttached = contentAttached; }

    public boolean isContentZipped() { return contentZipped; }
    public void setContentZipped(boolean contentZipped) { this.contentZipped = contentZipped; }

    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }

    public String getWebStatus() { return webStatus; }
    public void setWebStatus(String webStatus) { this.webStatus = webStatus; }

    public String getPrintStatus() { return printStatus; }
    public void setPrintStatus(String printStatus) { this.printStatus = printStatus; }

    public String getDefaultAction() { return defaultAction; }
    public void setDefaultAction(String defaultAction) { this.defaultAction = defaultAction; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }

    public boolean isFileTransfer() {
        return "file-transfer".equals(service);
    }

    public boolean isEmail() {
        return "email".equals(service);
    }
}
