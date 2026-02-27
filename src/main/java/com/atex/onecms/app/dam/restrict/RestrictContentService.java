package com.atex.onecms.app.dam.restrict;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.atex.onecms.app.dam.restrict.RestrictContentConfiguration.Configuration;
import com.atex.onecms.app.dam.util.ResolveUtil;
import com.atex.onecms.app.dam.workflow.AbstractStatusAspectBean;
import com.atex.onecms.app.dam.workflow.WFContentStatusAspectBean;
import com.atex.onecms.app.dam.workflow.WFStatusBean;
import com.atex.onecms.app.dam.workflow.WFStatusUtils;
import com.atex.onecms.app.dam.workflow.WebContentStatusAspectBean;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.ContentResult;
import com.atex.onecms.content.ContentResultBuilder;
import com.atex.onecms.content.ContentVersionId;
import com.atex.onecms.content.ContentWrite;
import com.atex.onecms.content.ContentWriteBuilder;
import com.atex.onecms.content.IdUtil;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.Status;
import com.atex.onecms.content.Subject;
import com.atex.onecms.content.repository.ContentModifiedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestrictContentService {
    private static final Logger LOG = LoggerFactory.getLogger(RestrictContentService.class);

    private final ContentManager contentManager;
    private final WFStatusUtils statusUtils;
    private final ResolveUtil resolveUtil;

    public RestrictContentService(ContentManager contentManager) {
        this.contentManager = contentManager;
        this.statusUtils = new WFStatusUtils(contentManager);
        this.resolveUtil = new ResolveUtil(contentManager);
    }

    public RestrictContentConfiguration getConfiguration() {
        return RestrictContentConfiguration.fetch(contentManager, Subject.of("98"));
    }

    public ContentResult<Object> restrictContent(ContentId contentId, Subject subject)
            throws ContentModifiedException {
        LOG.info("Restricting content with id: {}", IdUtil.toIdString(contentId));
        return processContent(contentId, c -> c::getRestrict, subject);
    }

    public ContentResult<Object> unRestrictContent(ContentId contentId, Subject subject)
            throws ContentModifiedException {
        LOG.info("Unrestricting content with id: {}", IdUtil.toIdString(contentId));
        return processContent(contentId, c -> c::getUnRestrict, subject);
    }

    public <T> ContentWrite<T> applyRestrictContent(ContentWrite<T> cw) {
        return applyProcessContent(cw, c -> c::getRestrict);
    }

    public <T> ContentWrite<T> applyUnRestrictContent(ContentWrite<T> cw) {
        return applyProcessContent(cw, c -> c::getUnRestrict);
    }

    public boolean canRestrictContent(ContentId contentId, Subject subject) {
        return canProcessContent(contentId, c -> c::getRestrict, subject);
    }

    public boolean canUnRestrictContent(ContentId contentId, Subject subject) {
        return canProcessContent(contentId, c -> c::getUnRestrict, subject);
    }

    // === Private implementation ===

    private <T> ContentWrite<T> applyProcessContent(ContentWrite<T> cw,
            Function<RestrictContentConfiguration, Function<String, Optional<Configuration>>> resolver) {
        RestrictContentConfiguration config = getConfiguration();
        Optional<Configuration> restrictConfig = getConfigurationFromWrite(cw, resolver.apply(config));
        if (restrictConfig.isPresent()) {
            return updateContentWrite(cw, restrictConfig.get()).build();
        }
        return cw;
    }

    private boolean canProcessContent(ContentId contentId,
            Function<RestrictContentConfiguration, Function<String, Optional<Configuration>>> resolver,
            Subject subject) {
        ContentResult<Object> cr = getContent(contentId, subject);
        if (!cr.getStatus().isSuccess()) return false;
        RestrictContentConfiguration config = getConfiguration();
        return getConfigurationFromResult(cr, resolver.apply(config)).isPresent();
    }

    private ContentResult<Object> processContent(ContentId contentId,
            Function<RestrictContentConfiguration, Function<String, Optional<Configuration>>> resolver,
            Subject subject) throws ContentModifiedException {
        ContentResult<Object> cr = getContent(contentId, subject);
        if (!cr.getStatus().isSuccess()) return cr;

        RestrictContentConfiguration config = getConfiguration();
        Optional<Configuration> restrictConfig = getConfigurationFromResult(cr, resolver.apply(config));
        if (restrictConfig.isEmpty()) {
            return ContentResult.<Object>status(Status.PRECONDITION_FAILED).build();
        }
        return updateContent(cr, restrictConfig.get(), subject);
    }

    private <T> Optional<Configuration> getConfigurationFromWrite(ContentWrite<T> cw,
            Function<String, Optional<Configuration>> resolver) {
        InsertionInfoAspectBean insBean = cw.getAspect(InsertionInfoAspectBean.ASPECT_NAME, InsertionInfoAspectBean.class);
        if (insBean == null) return Optional.empty();
        return Stream.of(insBean.getSecurityParentId(), parseContentId(insBean.getInsertParentId()))
            .filter(Objects::nonNull)
            .map(resolveUtil::getExternalId)
            .filter(s -> s != null && !s.isEmpty())
            .map(resolver)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    private Optional<Configuration> getConfigurationFromResult(ContentResult<Object> cr,
            Function<String, Optional<Configuration>> resolver) {
        InsertionInfoAspectBean insBean = cr.getContent().getAspectData(InsertionInfoAspectBean.ASPECT_NAME);
        if (insBean == null) return Optional.empty();
        return Stream.of(insBean.getSecurityParentId(), parseContentId(insBean.getInsertParentId()))
            .filter(Objects::nonNull)
            .map(resolveUtil::getExternalId)
            .filter(s -> s != null && !s.isEmpty())
            .map(resolver)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    private ContentId parseContentId(String idStr) {
        if (idStr == null || idStr.isEmpty()) return null;
        try {
            return IdUtil.fromString(idStr);
        } catch (Exception e) {
            return null;
        }
    }

    private ContentResult<Object> getContent(ContentId contentId, Subject subject) {
        ContentVersionId versionId = contentManager.resolve(contentId, subject);
        if (versionId == null) {
            LOG.warn("Cannot resolve: {}", IdUtil.toIdString(contentId));
            return ContentResult.<Object>status(Status.NOT_FOUND).build();
        }
        ContentResult<Object> cr = contentManager.get(versionId, Object.class, subject);
        if (!cr.getStatus().isSuccess()) return cr;

        InsertionInfoAspectBean insBean = cr.getContent().getAspectData(InsertionInfoAspectBean.ASPECT_NAME);
        if (insBean == null) {
            LOG.warn("Missing InsertionInfoAspectBean for: {}", IdUtil.toIdString(contentId));
            return ContentResult.<Object>status(Status.PRECONDITION_FAILED).build();
        }
        return cr;
    }

    private ContentResult<Object> updateContent(ContentResult<Object> source, Configuration config,
            Subject subject) throws ContentModifiedException {
        ContentWriteBuilder<Object> cwb = updateContentWrite(source, config);
        return contentManager.update(source.getContentId().getContentId(), cwb.buildUpdate(), subject);
    }

    private <T> ContentWriteBuilder<T> updateContentWrite(ContentResult<T> source, Configuration config) {
        return updateContentWrite(
            ContentWriteBuilder.from(source),
            () -> source.getContent().getAspectData(InsertionInfoAspectBean.ASPECT_NAME),
            () -> source.getContent().getAspectData(WFContentStatusAspectBean.ASPECT_NAME),
            () -> source.getContent().getAspectData(WebContentStatusAspectBean.ASPECT_NAME),
            config
        );
    }

    private <T> ContentWriteBuilder<T> updateContentWrite(ContentWrite<T> cw, Configuration config) {
        return updateContentWrite(
            ContentWriteBuilder.from(cw),
            () -> cw.getAspect(InsertionInfoAspectBean.ASPECT_NAME, InsertionInfoAspectBean.class),
            () -> cw.getAspect(WFContentStatusAspectBean.ASPECT_NAME, WFContentStatusAspectBean.class),
            () -> cw.getAspect(WebContentStatusAspectBean.ASPECT_NAME, WebContentStatusAspectBean.class),
            config
        );
    }

    @SuppressWarnings("unchecked")
    private <T> ContentWriteBuilder<T> updateContentWrite(ContentWriteBuilder<T> cwb,
            Supplier<Object> insSupplier, Supplier<Object> printStatusSupplier,
            Supplier<Object> webStatusSupplier, Configuration config) {

        InsertionInfoAspectBean rawInsBean = castOrNull(insSupplier.get(), InsertionInfoAspectBean.class);
        final InsertionInfoAspectBean insBean = rawInsBean != null ? rawInsBean : new InsertionInfoAspectBean();

        // Check if insertion parent equals security parent
        boolean changeInsertParentId = Optional.ofNullable(insBean.getInsertParentId())
            .map(id -> id.equals(insBean.getSecurityParentIdString()))
            .orElse(config.isForceChangeInsertionParentId());

        // Change security parent
        resolveId(config.getSecurityParentId()).ifPresent(insBean::setSecurityParentId);

        // Change insertion parent if needed
        if (changeInsertParentId) {
            resolveId(config.getSecurityParentId()).ifPresent(id ->
                insBean.setInsertParentId(IdUtil.toIdString(id)));
        }

        // Update associated sites
        // Note: InsertionInfoAspectBean.associatedSites is List<String>, not List<ContentId>
        // We store the ID string representation
        List<String> sites = insBean.getAssociatedSites();
        if (sites == null) sites = new ArrayList<>();
        if (config.getAssociatedSiteId() != null && !config.getAssociatedSiteId().isEmpty()) {
            ContentVersionId resolved = resolveUtil.resolveExternalId(config.getAssociatedSiteId());
            if (resolved != null) {
                String siteIdStr = IdUtil.toIdString(resolved.getContentId());
                if (!sites.contains(siteIdStr)) {
                    sites.add(siteIdStr);
                }
            }
        }
        if (!sites.isEmpty()) {
            insBean.setAssociatedSites(sites);
        }
        cwb.aspect(InsertionInfoAspectBean.ASPECT_NAME, insBean);

        // Update print status
        updateStatusAspect(config.getPrintStatusId(), statusUtils::getStatusById,
            () -> castOrNull(printStatusSupplier.get(), WFContentStatusAspectBean.class),
            WFContentStatusAspectBean::new)
            .ifPresent(s -> cwb.aspect(WFContentStatusAspectBean.ASPECT_NAME, s));

        // Update web status
        updateStatusAspect(config.getWebStatusId(), statusUtils::getWebStatusById,
            () -> castOrNull(webStatusSupplier.get(), WebContentStatusAspectBean.class),
            WebContentStatusAspectBean::new)
            .ifPresent(s -> cwb.aspect(WebContentStatusAspectBean.ASPECT_NAME, s));

        return cwb;
    }

    private <A extends AbstractStatusAspectBean> Optional<A> updateStatusAspect(String statusId,
            Function<String, WFStatusBean> statusSupplier, Supplier<A> aspectSupplier,
            Supplier<A> newAspectSupplier) {
        if (statusId == null || statusId.isEmpty()) return Optional.empty();
        WFStatusBean status = statusSupplier.apply(statusId);
        if (status == null) return Optional.empty();
        A aspect = aspectSupplier.get();
        if (aspect == null) aspect = newAspectSupplier.get();
        aspect.setStatus(status);
        return Optional.of(aspect);
    }

    private Optional<ContentId> resolveId(String externalId) {
        if (externalId == null || externalId.isEmpty()) return Optional.empty();
        ContentVersionId vid = resolveUtil.resolveExternalId(externalId);
        return vid == null ? Optional.empty() : Optional.of(vid.getContentId());
    }

    @SuppressWarnings("unchecked")
    private <T> T castOrNull(Object obj, Class<T> clazz) {
        if (obj == null) return null;
        if (clazz.isInstance(obj)) return clazz.cast(obj);
        // If it's a Map (from JSON deserialization), we can't easily cast it
        return null;
    }
}
