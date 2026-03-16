package com.atex.desk.integration.distribution;

import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.FilesAspectBean;
import com.atex.onecms.content.ContentFileInfo;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileService;
import jakarta.activation.DataSource;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Distributes content via email.
 * Replaces the legacy {@code MailAttacherProcessor} and {@code MailLinkerProcessor} Camel processors.
 *
 * <p>Sends content files as email attachments or links, using the route's configured
 * SMTP settings (host, to/from addresses, subject, template).
 */
@Component
public class EmailHandler implements DistributionHandler {

    private static final Logger LOG = Logger.getLogger(EmailHandler.class.getName());
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final JavaMailSender mailSender;
    private final FileService fileService;

    public EmailHandler(JavaMailSender mailSender, FileService fileService) {
        this.mailSender = mailSender;
        this.fileService = fileService;
    }

    @Override
    public String[] contentTypes() {
        return new String[]{"*"};
    }

    @Override
    public void distribute(ContentResult<Object> content, DistributionRoute route)
            throws DistributionException {
        if (!route.isEmail()) return;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(route.getToAddress().split("[,;]"));
            helper.setFrom(route.getFromAddress());
            helper.setSubject(route.getEmailSubject() != null
                ? route.getEmailSubject()
                : "Content: " + content.getContentId().getContentId());

            // Build body
            Object data = content.getContent().getContentData();
            String body = buildEmailBody(data, route);
            helper.setText(body, true);

            // Attach files if configured
            if (route.isContentAttached()) {
                attachFiles(content, helper);
            }

            mailSender.send(message);
            LOG.info("Email sent for content " + content.getContentId().getContentId()
                + " to " + route.getToAddress());

        } catch (Exception e) {
            throw new DistributionException("Email distribution failed for route " + route.getName(), e);
        }
    }

    private String buildEmailBody(Object data, DistributionRoute route) {
        // Simple default body; template rendering can be extended later
        if (data == null) return "<p>Content distributed.</p>";
        return "<p>" + data.toString() + "</p>";
    }

    private void attachFiles(ContentResult<Object> content, MimeMessageHelper helper) {
        try {
            FilesAspectBean filesAspect = (FilesAspectBean)
                content.getContent().getAspectData("atex.Files");
            if (filesAspect == null || filesAspect.getFiles() == null) return;

            for (Map.Entry<String, ContentFileInfo> entry : filesAspect.getFiles().entrySet()) {
                ContentFileInfo cfi = entry.getValue();
                try (InputStream is = fileService.getFile(cfi.getFileUri(), SYSTEM_SUBJECT)) {
                    if (is == null) continue;
                    byte[] bytes = is.readAllBytes();
                    DataSource ds = new ByteArrayDataSource(bytes, "application/octet-stream");
                    helper.addAttachment(cfi.getFilePath(), ds);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Could not attach file: " + cfi.getFilePath(), e);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error attaching files", e);
        }
    }
}
