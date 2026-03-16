# Desk API -- Implementation Status

Last updated: 2026-03-16 (Increment 38b).

---

## Endpoint Coverage

34 REST controllers, 100+ endpoints. All endpoints required by mytype-new are implemented except workspace/preview.

| Controller | Path | Endpoints | Status |
|---|---|---|---|
| ContentController | /content/* | CRUD, aliases, history | Complete |
| ContentResolveController | /content/* | External ID and view resolution | Complete |
| TypeController | /content/type/* | Content type schemas | Complete |
| DamDataResource | /dam/content/* | 74 endpoints | 5 stubbed (501) |
| SearchController | /search/{core}/select | Solr query proxy | Complete |
| DamSearchController | /dam/search/{core}/select | Remote Solr proxy | Complete |
| SecurityController | /security/* | JWT auth, OAuth | Complete |
| PrincipalsController | /principals/* | Users/groups (11 endpoints) | Complete |
| FileController | /file/* | Upload/download/metadata | Complete |
| ImageController | /image/* | Image service (Rust sidecar) | Complete |
| FileDeliveryController | /filedelivery/* | Public file serving with Range | Complete |
| ActivityController | /activities/* | Content locking | Complete |
| PreviewController | /preview/* | Web preview | Complete |
| WorkspaceController | /workspace/* | Draft workspaces | Stub (throws UOE) |
| ChangesController | /changes/* | Change feed | Complete |
| ConfigurationController | /admin/config/* | Config CRUD | Complete |
| ProfileConfigurationController | /configuration/profile | Profile-specific config | Complete |
| CacheController | /admin/cache/* | Cache stats and clearing | Complete |
| ReindexController | /admin/reindex/* | Solr indexing management | Complete |
| RequestMetricsController | /admin/requests/* | Live request metrics | Complete |
| MetadataController | /metadata/* | Taxonomy/tag operations | 4 endpoints (see gaps) |
| DamUIResource | /dam/ui/* | UI config | Complete |
| DamImageResource | /dam/image/* | Image manipulation | Complete |
| DamTagManagerResource | /dam/tagmanager/* | Tag management | Stub |
| DamPrintPageResource | /dam/printpage/* | Print page queries | Complete |
| DamPdfToolResource | /dam/pdf/* | PDF utilities | Stub |
| DamPageResource | /dam/page/* | Page operations | Complete |
| DamSchedulingResource | /dam/scheduling/* | Scheduling | Stub |
| DamLocaleResource | /dam/locale/* | Locale/i18n | Complete |
| StatisticsResource | /statistics/* | Analytics | Complete |
| SocialResource | /social/* | Social media | Stub |
| LayoutResource | /layout/* | Layout plugin (proxy) | Complete |
| DashboardController | /admin/dashboard | Admin UI | Complete |

---

## 501 Stubs (intentional, out-of-scope)

| Endpoint | Reason |
|---|---|
| POST /dam/content/create-page | Needs PolicyCMServer page hierarchy |
| PUT /dam/content/sendcontent | Needs CamelEngine routes (returns 200 no-op) |
| POST /dam/content/assigncontent | Needs DamAssignContentService (returns 200 no-op) |
| POST /dam/content/collectionpreview | Needs ACE Web Preview |
| POST /dam/content/mergeMultiplePdf | Needs PDFBox |

---

## Stub Service Classes

| Class | Package | Notes |
|---|---|---|
| ModuleImporter | dam.importer | Empty class |
| CollectionToGallery | dam.collection | Empty class |
| CamelEngine | dam.camel | Empty class |
| TagManagerService | dam.tagmanager | All methods return empty |
| PublicationsService | dam.publication | All methods return empty |
| SolrPrintPageService | dam.solr | All methods return empty |
| AtexPdfium | tools.pdfconverter | Throws UnsupportedOperationException |
| AyrShareService | dam.ayrshare | Returns empty SocialAccountResponse |
| DamPublishingScheduleUtils | dam.publish | Throws UnsupportedOperationException |

---

## UnsupportedOperationException Stubs

| Class | Methods | Notes |
|---|---|---|
| ContentPublisher | publishPage, duplicatePage | Page publishing not needed |
| ContentManager | 6 workspace methods | Workspaces not yet supported |
| Group | addMember, removeMember | Group membership mutation |
| UserServer | findGroupsByMember | Reverse group lookup |

---

## TODO Markers (2)

| File | Line | Note |
|---|---|---|
| DamDataResource.java | 347 | Lock service integration (locking works via ActivityController) |
| DamBeanPublisherImpl.java | 150 | Full import logic for remote-to-local content sync |

---

## Metadata Service Gaps

4 endpoints implemented matching mytype-new contract. Reference has additional endpoints not called by mytype-new.

### Implemented

| Endpoint | Notes |
|---|---|
| GET /metadata/structure/{id} | Solr-backed + content fallback |
| GET /metadata/complete/{dim}/{prefix} | Solr faceted autocomplete |
| POST /metadata/annotate | Text-to-tag matching via Solr |
| POST /metadata/lookup | Entity resolution via content manager |

### Not Implemented (reference-only)

| Endpoint | Notes |
|---|---|
| GET /metadata/structure (no args) | Returns all taxonomy dimensions |
| GET /metadata/structure/{taxonomy}/{id} | Entity within specific taxonomy |
| POST /metadata/structure/{parentId} | Create entity in enumerable dimension |

### Simplifications vs Reference

- Autocomplete: Simple prefix query vs hierarchical path matching with locale support
- Annotate: Solr text-matching vs Polopoly taxonomy provider engine
- Entity resolution: Resolves by tagId-{id} but does not enrich with content data
- Entity class: Missing attributes, childrenOmitted, equals/hashCode
- Dimension class: Missing localizations field
- API contract: JSON bodies vs reference text/plain bodies (intentional for mytype-new)

---

## Sidecar Services

### image-metadata-service (Node.js)

Separate container at ../image-metadata-service. Extracts EXIF/IPTC metadata from uploaded images.
Replaces the old Java-based image metadata extraction that ran inside a Tomcat container in the legacy stack.
Called by OneImagePreStore during content creation/update. Configured via desk.image-metadata-service.* properties.

### desk-image (Rust)

Image resizing/transformation sidecar. Runs alongside desk-api in Docker Compose.
Shares the adm-mpp_file-data volume (read-only). Configured via compose.yaml.

---

## mytype-new Coverage

### Implemented

| Feature | Paths | Status |
|---|---|---|
| Content CRUD | /content/contentid/*, /content/externalid/*, /content/type/* | Complete |
| Security/Auth | /security/token, /security/oauth/* | Complete |
| DAM Operations | /dam/content/* (publish, trash, duplicate, restrict, etc.) | Complete |
| Search | /search/{core}/select, /dam/search/{core}/select | Complete |
| Principals | /principals/users/*, /principals/groups/* | Complete |
| Configuration | /admin/config/*, /configuration/profile | Complete |
| Changes Feed | /changes | Complete |
| Layout Proxy | /layout/handj/* | Complete |
| Print Pages | /dam/print-page/* | Complete |
| File Service | /file/* | Complete |
| Activities/Locking | /activities/* | Complete |
| Metadata/Tags | /metadata/* | Complete (4 endpoints) |
| Social/TagMgr/PDF/Scheduling | Various /dam/* | Stubs (return empty) |

### Not Implemented

| Priority | Feature | Paths |
|---|---|---|
| MEDIUM | Workspace/Preview | /content/workspace/*, /preview/* |
| LOW | Audio AI | /dam/audioai/* |
| LOW | Permissions Config | /dam/mytype/permissions |

---

## Compat Issues (reference server differences)

| Issue | Priority | Notes |
|---|---|---|
| Principals /users list format | P2 | Reference: {type, id, name, principalId, cmUser} -- desk-api differs |
| contentData default fields | P2 | Reference auto-sets objectType, priority, propertyBag etc. |
| 302 vs 303 redirects | P3 | Reference uses 303 See Other in some places |
| Changes feed empty response | P3 | Response format mismatch on empty changelist |

---

## Previously Flagged -- Now Verified Complete

All items from the original gap analysis (March 1) have been verified:

| Item | Status |
|---|---|
| AppAclEntry composite keys | @IdClass with findByAclId, findByPrincipalId |
| Post-store indexing | Decoupled: SolrIndexProcessor polls adm_changelist on schedule |
| Hook merging (wildcard + type) | Wildcard first, then type-specific, then PF4J plugins |
| ConfigService 5-tier precedence | Product -> Flavor -> Project -> Plugin -> DB |
| LDAP group cache | ConcurrentHashMap + timestamp-based staleness |
| Cognito token renewal | exchangeCodeForToken + CognitoTokenVerifier |
| PolicyCMServer ID handling | Both ExternalContentId and numeric Polopoly IDs |
| SetStatus pre-store hook | Extracts operation, delegates to WFStatusUtils |
| AddEngagement pre-store hook | Detects operation, delegates to DamEngagementUtils |
| Partition bidirectional mapping | Forward + reverse lookup in PartitionProperties |
| Solr field type detection | All suffixes: _b, _l, _dt, _ss, _t |
| Distribution list cache | DistributionListUtil with 1-minute ConcurrentHashMap TTL |
| ContentIndexer circular dep | No @Autowired ContentManager -- receives data via changelist |

---

## File Counts

- 106 desk-api classes (com.atex.desk.api)
- 175 OneCMS/DAM classes (ported from gong/polopoly)
- 30 Polopoly compatibility classes
- 201 config files (187 defaults + 14 flavors)
- 74 DamDataResource endpoints (5 stubbed)
- 115+ integration tests

---

## ADM Project Migration

See [docs/migration-guide.md](docs/migration-guide.md) for the full migration plan.

**Key finding from cross-project analysis** (7 projects scanned):
- 95%+ of code across all ADM projects is identical (copied from adm-starterkit)
- ~120 classes should move to desk-api core (custom beans, hooks, Solr mappers, utilities)
- Only 5-25 classes per project are truly unique (average plugin = ~6% of original codebase)
- After porting core code, most projects need zero or near-zero plugin code

## desk-integration Module

Gradle submodule (`desk-integration/`) — separate Spring Boot app for content ingest, distribution,
and scheduled processing. Depends on desk-api as a library (plain jar).

| Component | Classes | Replaces |
|---|---|---|
| Wire feed framework | WireArticleParser, WireImageParser, FeedPoller, FeedContentCreator | Camel file routes, BaseFeedProcessor, AgencyFeedProcessor |
| Wire parsers | AFPArticleParser, APArticleParser, ReutersArticleParser, PTIArticleParser, AbstractXmlParser | Legacy agency parsers from adm-starterkit |
| Wire utilities | WireUtils | WireUtils from gong/desk (follow-up threading) |
| Image metadata | ImageMetadataExtractor, CustomMetadataTags | BaseImageProcessor.extract() from gong/desk |
| Scheduled jobs | WebStatusScheduler, PurgeScheduler, ChangeProcessor | Quartz2 routes, CustomPurgeProcessor |
| Change handlers | InboxChangeHandler, PublishChangeHandler, NginxCacheInvalidator | Inbox processing, publish triggers, NGINX cache purge |
| Publishing pipeline | PublishingService, RemoteContentPublisher, PublishingConfig | DamPublisher → BeanPublisher → ContentPublisher from gong/desk |
| Distribution | DistributionService, FileTransferHandler, EmailHandler | CamelEngine, SendContentHandler, FileTransferProcessor |
