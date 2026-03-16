package com.atex.desk.integration.feed;

import com.atex.desk.integration.config.IntegrationProperties;
import com.atex.onecms.app.dam.standard.aspects.OneArticleBean;
import com.atex.onecms.app.dam.standard.aspects.OneImageBean;
import com.atex.onecms.content.ContentFileInfo;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.FilesAspectBean;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.files.FileInfo;
import com.atex.onecms.content.files.FileService;
import com.atex.onecms.content.metadata.MetadataInfo;
import com.atex.onecms.image.ImageInfoAspectBean;
import com.atex.plugins.structured.text.StructuredText;
import com.polopoly.metadata.Dimension;
import com.polopoly.metadata.Entity;
import com.polopoly.metadata.Metadata;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates CMS content from parsed wire feed data.
 * Replaces the content creation logic from AgencyFeedProcessor and ArticlesObjectGenerator.
 *
 * <p>Handles both article and image creation with proper aspect composition:
 * contentData, p.InsertionInfo, atex.Metadata, atex.Files, atex.Image.
 */
@Service
public class FeedContentCreator {

    private static final Logger LOG = Logger.getLogger(FeedContentCreator.class.getName());
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);
    private static final String SCHEME_TMP = "tmp";
    private static final String DIMENSION_TAG = "dimension.Tag";
    private static final String DIMENSION_LOCATION = "dimension.Location";

    private final ContentManager contentManager;
    private final FileService fileService;
    private final IntegrationProperties properties;

    public FeedContentCreator(ContentManager contentManager,
                               FileService fileService,
                               IntegrationProperties properties) {
        this.contentManager = contentManager;
        this.fileService = fileService;
        this.properties = properties;
    }

    /**
     * Create an article from parsed wire data.
     *
     * @return the content ID of the created article
     */
    public ContentId createArticle(WireArticle article, String securityParent) {
        OneArticleBean bean = new OneArticleBean();
        bean.setHeadline(new StructuredText(article.getHeadline()));
        if (article.getLead() != null) {
            bean.setLead(new StructuredText(article.getLead()));
        }
        if (article.getBody() != null) {
            bean.setBody(new StructuredText(article.getBody()));
        }
        bean.setSource(article.getSource());
        bean.setAuthor(article.getAuthor());
        bean.setByline(article.getByline());
        if (article.getPriority() != null) {
            try { bean.setPriority(Integer.parseInt(article.getPriority())); }
            catch (NumberFormatException ignored) {}
        }
        bean.setInputTemplate(OneArticleBean.INPUT_TEMPLATE_WIRE);

        ContentWriteBuilder<OneArticleBean> cwb = new ContentWriteBuilder<>();
        cwb.mainAspectData(bean);
        cwb.type(OneArticleBean.ASPECT_NAME);

        // p.InsertionInfo
        ContentId parentId = resolveSecurityParent(
            securityParent != null ? securityParent : properties.getDefaultSecurityParent());
        if (parentId != null) {
            cwb.aspect("p.InsertionInfo", new InsertionInfoAspectBean(parentId));
        }

        // atex.Metadata
        cwb.aspect("atex.Metadata", buildMetadata(article.getTags(), article.getLocations()));

        ContentWrite<OneArticleBean> content = cwb.buildCreate();
        ContentResult<OneArticleBean> cr = contentManager.create(content, SYSTEM_SUBJECT);
        if (!cr.getStatus().isOk()) {
            LOG.warning("Failed to create article: " + cr.getStatus());
            return null;
        }

        LOG.info("Created wire article: " + cr.getContentId().getContentId()
            + " headline=" + article.getHeadline());
        return cr.getContentId().getContentId();
    }

    /**
     * Create an image from parsed wire data, uploading the image file to storage.
     *
     * @param image parsed image metadata
     * @param imageFile the actual image file on disk
     * @param securityParent security parent external ID (null for default)
     * @return the content ID of the created image, or null on failure
     */
    public ContentId createImage(WireImage image, File imageFile, String securityParent) {
        try {
            // Upload file to storage
            String mimeType = detectMimeType(imageFile);
            FileInfo fInfo;
            try (var bis = new BufferedInputStream(new FileInputStream(imageFile))) {
                fInfo = fileService.uploadFile(SCHEME_TMP, null,
                    imageFile.getName(), bis, mimeType, SYSTEM_SUBJECT);
            }

            // Build bean
            OneImageBean bean = new OneImageBean();
            bean.setCaption(image.getHeadline());
            if (image.getCaption() != null) {
                bean.setDescription(image.getCaption());
            }
            bean.setByline(image.getByline());
            bean.setCredit(image.getCredit());
            bean.setSource(image.getSource());
            bean.setWidth(image.getWidth());
            bean.setHeight(image.getHeight());
            bean.setInputTemplate(OneImageBean.INPUT_TEMPLATE_WIRE);

            ContentWriteBuilder<OneImageBean> cwb = new ContentWriteBuilder<>();
            cwb.mainAspectData(bean);
            cwb.type(OneImageBean.ASPECT_NAME);

            // atex.Files
            FilesAspectBean filesBean = new FilesAspectBean();
            ContentFileInfo cfi = new ContentFileInfo(fInfo.getOriginalPath(), fInfo.getUri());
            HashMap<String, ContentFileInfo> files = new HashMap<>();
            files.put(fInfo.getOriginalPath(), cfi);
            filesBean.setFiles(files);
            cwb.aspect("atex.Files", filesBean);

            // atex.Image
            ImageInfoAspectBean imageInfo = new ImageInfoAspectBean();
            imageInfo.setHeight(image.getHeight());
            imageInfo.setWidth(image.getWidth());
            imageInfo.setFilePath(fInfo.getOriginalPath());
            cwb.aspect("atex.Image", imageInfo);

            // p.InsertionInfo
            ContentId parentId = resolveSecurityParent(
                securityParent != null ? securityParent : properties.getDefaultSecurityParent());
            if (parentId != null) {
                cwb.aspect("p.InsertionInfo", new InsertionInfoAspectBean(parentId));
            }

            // atex.Metadata
            cwb.aspect("atex.Metadata", buildMetadata(image.getTags(), List.of()));

            ContentWrite<OneImageBean> content = cwb.buildCreate();
            ContentResult<OneImageBean> cr = contentManager.create(content, SYSTEM_SUBJECT);
            if (!cr.getStatus().isOk()) {
                LOG.warning("Failed to create image: " + cr.getStatus());
                return null;
            }

            LOG.info("Created wire image: " + cr.getContentId().getContentId()
                + " file=" + imageFile.getName());
            return cr.getContentId().getContentId();

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to create image from " + imageFile, e);
            return null;
        }
    }

    private MetadataInfo buildMetadata(List<String> tags, List<String> locations) {
        MetadataInfo info = new MetadataInfo();
        Set<String> taxonomyIds = new HashSet<>();
        taxonomyIds.add("p.StandardCategorization");
        info.setTaxonomyIds(taxonomyIds);

        Metadata metadata = new Metadata();
        if (tags != null && !tags.isEmpty()) {
            metadata.addDimension(createDimension(DIMENSION_TAG, tags));
        }
        if (locations != null && !locations.isEmpty()) {
            metadata.addDimension(createDimension(DIMENSION_LOCATION, locations));
        }
        info.setMetadata(metadata);
        return info;
    }

    private Dimension createDimension(String id, List<String> entityNames) {
        List<Entity> entities = new ArrayList<>();
        for (String name : entityNames) {
            entities.add(new Entity(name, name));
        }
        return new Dimension(id, id, false, entities);
    }

    private ContentId resolveSecurityParent(String externalId) {
        try {
            ContentVersionId vid = contentManager.resolve(externalId, SYSTEM_SUBJECT);
            return vid != null ? vid.getContentId() : null;
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not resolve security parent: " + externalId, e);
            return null;
        }
    }

    private String detectMimeType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".tiff") || name.endsWith(".tif")) return "image/tiff";
        if (name.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
