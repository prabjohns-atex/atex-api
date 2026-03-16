package com.atex.desk.integration.distribution;

import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.files.FileInfo;
import com.atex.onecms.content.files.FileService;
import com.atex.onecms.content.FilesAspectBean;
import com.atex.onecms.content.ContentFileInfo;
import com.atex.onecms.content.Subject;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Distributes content files via FTP/SFTP.
 * Replaces the legacy {@code FileTransferProcessor} Camel processor.
 *
 * <p>Exports content files from the file service to a local staging directory,
 * then transfers them to the remote server via FTP or SFTP.
 */
@Component
public class FileTransferHandler implements DistributionHandler {

    private static final Logger LOG = Logger.getLogger(FileTransferHandler.class.getName());
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final FileService fileService;

    public FileTransferHandler(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public String[] contentTypes() {
        return new String[]{"*"};
    }

    @Override
    public void distribute(ContentResult<Object> content, DistributionRoute route)
            throws DistributionException {
        if (!route.isFileTransfer()) return;

        try {
            // Export files from content to local staging
            Path localDir = Path.of(route.getLocalFolder());
            Files.createDirectories(localDir);

            FilesAspectBean filesAspect = (FilesAspectBean)
                content.getContent().getAspectData("atex.Files");
            if (filesAspect == null || filesAspect.getFiles() == null) {
                LOG.fine("No files to transfer for content " + content.getContentId());
                return;
            }

            for (Map.Entry<String, ContentFileInfo> entry : filesAspect.getFiles().entrySet()) {
                ContentFileInfo cfi = entry.getValue();
                Path localFile = exportToLocal(cfi, localDir);

                if (localFile != null) {
                    transferFile(localFile, route);
                    Files.deleteIfExists(localFile);
                }
            }

        } catch (IOException e) {
            throw new DistributionException("File transfer failed for route " + route.getName(), e);
        }
    }

    private Path exportToLocal(ContentFileInfo cfi, Path localDir) throws IOException {
        try {
            InputStream is = fileService.getFile(cfi.getFileUri(), SYSTEM_SUBJECT);
            if (is == null) return null;

            Path localFile = localDir.resolve(cfi.getFilePath());
            Files.createDirectories(localFile.getParent());
            try (is; var out = new FileOutputStream(localFile.toFile())) {
                is.transferTo(out);
            }
            return localFile;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not export file: " + cfi.getFilePath(), e);
            return null;
        }
    }

    private void transferFile(Path localFile, DistributionRoute route) throws IOException {
        String protocol = route.getProtocol();

        if ("ftp".equals(protocol) || "ftps".equals(protocol)) {
            transferViaFtp(localFile, route);
        } else if ("sftp".equals(protocol)) {
            transferViaSftp(localFile, route);
        } else if ("local".equals(protocol)) {
            // Simple local copy
            Path target = Path.of(route.getRemoteFolder()).resolve(localFile.getFileName());
            Files.createDirectories(target.getParent());
            Files.copy(localFile, target);
        } else {
            LOG.warning("Unsupported transfer protocol: " + protocol);
        }
    }

    private void transferViaFtp(Path localFile, DistributionRoute route) throws IOException {
        FTPClient ftp = "ftps".equals(route.getProtocol()) ? new FTPSClient() : new FTPClient();
        try {
            ftp.connect(route.getHost(), route.getPort() > 0 ? route.getPort() : 21);
            ftp.login(route.getUsername(), route.getPassword());
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ftp.enterLocalPassiveMode();

            if (route.getRemoteFolder() != null && !route.getRemoteFolder().isEmpty()) {
                ftp.changeWorkingDirectory(route.getRemoteFolder());
            }

            try (InputStream is = Files.newInputStream(localFile)) {
                boolean ok = ftp.storeFile(localFile.getFileName().toString(), is);
                if (!ok) {
                    throw new IOException("FTP store failed: " + ftp.getReplyString());
                }
            }

            LOG.info("FTP transferred: " + localFile.getFileName()
                + " to " + route.getHost() + ":" + route.getRemoteFolder());

        } finally {
            if (ftp.isConnected()) {
                ftp.logout();
                ftp.disconnect();
            }
        }
    }

    private void transferViaSftp(Path localFile, DistributionRoute route) throws IOException {
        try {
            var jsch = new com.jcraft.jsch.JSch();
            var session = jsch.getSession(route.getUsername(), route.getHost(),
                route.getPort() > 0 ? route.getPort() : 22);
            session.setPassword(route.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30_000);

            var channel = (com.jcraft.jsch.ChannelSftp) session.openChannel("sftp");
            channel.connect(30_000);

            if (route.getRemoteFolder() != null && !route.getRemoteFolder().isEmpty()) {
                channel.cd(route.getRemoteFolder());
            }

            channel.put(localFile.toString(), localFile.getFileName().toString());

            LOG.info("SFTP transferred: " + localFile.getFileName()
                + " to " + route.getHost() + ":" + route.getRemoteFolder());

            channel.disconnect();
            session.disconnect();

        } catch (com.jcraft.jsch.JSchException | com.jcraft.jsch.SftpException e) {
            throw new IOException("SFTP transfer failed", e);
        }
    }
}
