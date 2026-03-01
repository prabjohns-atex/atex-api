package com.atex.onecms.app.dam.ws;

import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileInfo;
import com.atex.onecms.content.files.FileService;
import com.atex.tools.pdfconverter.AtexPdfium;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/dam/pdftool")
@Tag(name = "DAM PDF Tool")
public class DamPdfToolResource {

    private static final Logger LOGGER = Logger.getLogger(DamPdfToolResource.class.getName());
    private static final Pattern DOUBLE_SLASH_PATTERN = Pattern.compile("/*/");
    private static final String SCHEME_TMP = "tmp";
    private static final String CONTENT_TYPE_IMAGE_JPEG = "image/jpeg";

    private final FileService fileService;

    public DamPdfToolResource(@Nullable FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/convertFile", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertFile(HttpServletRequest request,
                                               @RequestParam("file") String file,
                                               @RequestParam("out") String out) {
        DamUserContext.from(request).assertLoggedIn();

        java.nio.file.Path path = Paths.get(file);
        if (Files.notExists(path)) throw ContentApiException.notFound("File not found: " + file);
        if (!Files.isRegularFile(path)) throw ContentApiException.badRequest("Not a file: " + file);

        try {
            List<String> paths = AtexPdfium.convertPdf(file, out);
            JsonObject json = new JsonObject();
            JsonArray array = new JsonArray();
            paths.stream().map(JsonPrimitive::new).forEach(array::add);
            json.add("files", array);
            return ResponseEntity.ok(json.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal("Error converting PDF: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "/convert", produces = "image/jpeg")
    public ResponseEntity<byte[]> convertPdfToImage(HttpServletRequest request,
                                                     @RequestParam("file") String file) {
        DamUserContext.from(request).assertLoggedIn();

        java.nio.file.Path path = Paths.get(file);
        if (Files.notExists(path)) throw ContentApiException.notFound("File not found: " + file);
        if (!Files.isRegularFile(path)) throw ContentApiException.badRequest("Not a file: " + file);

        try {
            java.nio.file.Path pdfium = Files.createTempDirectory("pdfconvert");
            try {
                List<String> paths = AtexPdfium.convertPdf(file, pdfium.toString());
                if (!paths.isEmpty()) {
                    java.nio.file.Path imagePath = Paths.get(paths.get(0));
                    byte[] imageData = Files.readAllBytes(imagePath);
                    Files.deleteIfExists(imagePath);
                    try (InputStream is = new ByteArrayInputStream(imageData)) {
                        BufferedImage image = ImageIO.read(is);
                        return ResponseEntity.ok()
                            .header("X-image-width", String.valueOf(image.getWidth()))
                            .header("X-image-height", String.valueOf(image.getHeight()))
                            .header("X-file-size", String.valueOf(imageData.length))
                            .body(imageData);
                    }
                }
                throw ContentApiException.badRequest("The pdf cannot be converted");
            } finally {
                Files.deleteIfExists(pdfium);
            }
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal("Error converting PDF: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "/convert/{scheme}/{host}/{path:.*}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertPdfAndUpload(HttpServletRequest request,
                                                       @PathVariable("scheme") String scheme,
                                                       @PathVariable("host") String host,
                                                       @PathVariable("path") String filePath) {
        DamUserContext ctx = DamUserContext.from(request);
        Subject subject = ctx.assertLoggedIn().getSubject();

        if (fileService == null) throw ContentApiException.internal("FileService not available");

        String fileUri;
        try {
            fileUri = new URI(scheme, host, "/" + filePath, null).toString();
        } catch (Exception e) {
            throw ContentApiException.badRequest(String.format("Invalid file URI: '%s://%s/%s'", scheme, host, filePath));
        }

        FileInfo fileInfo = fileService.getFileInfo(fileUri, subject);
        if (fileInfo == null) throw ContentApiException.notFound("Cannot find " + fileUri);

        try {
            java.nio.file.Path tempDirectory = Files.createTempDirectory("pdfconvert");
            java.nio.file.Path pdfTempFile = Files.createTempFile(tempDirectory, "pdf", ".pdf");
            try {
                try (InputStream is = fileService.getFile(fileUri, subject)) {
                    Files.copy(is, pdfTempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                List<String> paths = AtexPdfium.convertPdf(pdfTempFile.toString(), tempDirectory.toString());
                if (paths.isEmpty()) throw ContentApiException.badRequest("No images have been generated");

                JsonArray filesArray = new JsonArray();
                for (String pathName : paths) {
                    java.nio.file.Path imagePath = Paths.get(pathName);
                    String imageName = imagePath.getFileName().toString();
                    FileInfo imageFileInfo;
                    try (InputStream is = Files.newInputStream(imagePath)) {
                        imageFileInfo = fileService.uploadFile(SCHEME_TMP, null,
                            cleanPath(imageName), is, CONTENT_TYPE_IMAGE_JPEG, subject);
                    } finally {
                        Files.deleteIfExists(imagePath);
                    }
                    JsonObject json = new JsonObject();
                    json.addProperty("uri", imageFileInfo.getUri());
                    json.addProperty("name", imageFileInfo.getOriginalPath());
                    filesArray.add(json);
                }
                return ResponseEntity.ok(filesArray.toString());
            } finally {
                Files.deleteIfExists(pdfTempFile);
                Files.deleteIfExists(tempDirectory);
            }
        } catch (ContentApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw ContentApiException.internal("Error converting " + fileUri + ": " + e.getMessage(), e);
        }
    }

    private String cleanPath(String path) {
        if (path == null || path.isEmpty()) return null;
        String cleanPath = DOUBLE_SLASH_PATTERN.matcher(path).replaceAll("/");
        if (cleanPath.startsWith("/")) return cleanPath.substring(1);
        return cleanPath;
    }
}

