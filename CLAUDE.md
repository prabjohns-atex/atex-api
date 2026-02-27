# Desk API

## Project Overview
New Spring Boot 4.0.3 API project (Java 25) for porting features from the existing Atex/Polopoly platform.

- **Group**: `com.atex.cloud`
- **Package**: `com.atex.desk.api`
- **Database**: MySQL Server (via Docker Compose)
- **ORM**: Hibernate 7.2.4 with JPA
- **Frameworks**: Spring Web MVC, Spring REST Client
- **Build**: Gradle, GraalVM native image support

## Source Codebases (porting references)
All paths are relative to this project's parent directory (`../`).

| Codebase | Path                     | Description                                                              |
|---|--------------------------|--------------------------------------------------------------------------|
| Polopoly | `../polopoly`            | Core platform code — foundational layer                                  |
| Gong | `../gong`                | Next layer of core code built on Polopoly — projects are built from this |
| Gong | `../gong/desk`           | Submodule containing the DESK specific functionality                     |
| ADM Content Service | `../adm-content-service` | Content API definition used by newer clients to read the database        |
| ADM Starterkit | `../adm-starterkit`      | Project framework / starter template — top of the dependency tree        |
| OneCMS Lib | `../onecms-lib`          | Shared JS/Java library — core OneCMS content API client services and UI widgets |
| OneCMS Widgets | `../onecms-widgets`      | Additional UI widgets — reference for service APIs they consume                  |
| Reporter Tool | `../reporter-tool`       | Atex Contributor Tool (ACT) — legacy AngularJS editorial SPA                    |

### Dependency order (bottom → top)
```
polopoly → gong → adm-starterkit
                → adm-content-service
onecms-lib (shared library used by reporter-tool / desk UI)
reporter-tool (legacy editorial UI)
```

## Porting Strategy
1. Understand domain model, entities, and database schema from the existing codebases
2. Understand the content API surface from `adm-content-service`
3. Port features incrementally: entities → repositories → services → controllers
4. Adapt for Spring Boot 4 / Hibernate 7 / Java 25 conventions

## Increment 1 — OneCMS-Compatible REST Layer

Collapses the existing multi-layer stack (Desk UI → Jersey → CmClient → OneCMS Server → Couchbase) into a single Spring Boot API talking directly to the ADM Content Service MySQL database.

### Configuration
- **build.gradle** — MySQL (`mysql-connector-j`), Spring Data JPA, Spring Web MVC
- **compose.yaml** — MySQL 8.0 container with `adm` database
- **application.properties** — `ddl-auto=none`, `sql.init.mode=always`
- **schema.sql** — Full MySQL DDL from ADM Content Service (16 tables)
- **data.sql** — Default seed data (idtypes, attributes, views, aliases, eventtypes)

### Entity Layer (12 entities)
`com.atex.desk.api.entity` — JPA mappings for all ADM Content Service tables:
- `IdType`, `ContentId`, `ContentVersion`, `Content`, `Aspect`, `AspectLocation`, `View`, `ContentView`, `Alias`, `ContentAlias`, `Attribute`, `ContentAttribute`
- Composite PK classes (`AspectLocationId`, `ContentViewId`, `ContentAliasId`, `ContentAttributeId`) for multi-column keys

### Repository Layer (10 repositories)
`com.atex.desk.api.repository` — Spring Data JPA with key queries:
- Version resolution by idtype+id, view lookups, alias resolution via JPQL joins

### DTO Layer (7 DTOs)
`com.atex.desk.api.dto` — Matches OneCMS REST API JSON format:
- `ContentResultDto` — id, version, aspects map, meta
- `AspectDto` — name, version, data map
- `ContentWriteDto`, `ContentHistoryDto`, `ContentVersionInfoDto`, `MetaDto`, `ErrorResponseDto`

### Service Layer
`com.atex.desk.api.service.ContentService` — Core operations:
- **ID parsing** — `delegationId:key` (unversioned) / `delegationId:key:version` (versioned)
- **Resolve** — unversioned ID → latest version via view assignment (`p.latest`)
- **External ID resolution** — alias lookup → content ID
- **Get content** — version → content entry → aspects via aspectslocations join
- **Create** — new content ID + version + content entry + aspects + assign `p.latest` view
- **Update** — new version + content entry + aspects + reassign `p.latest` view
- **Delete** — soft delete via view reassignment (`p.latest` → `p.deleted`)
- **History** — all versions with their view assignments

### Controller
`com.atex.desk.api.controller.ContentController` at `/content`:

| Method | Path | Behaviour |
|--------|------|-----------|
| GET | `/content/contentid/{id}` | Versioned → 200 with ETag; unversioned → 302 redirect |
| GET | `/content/contentid/{id}/history` | Version history (unversioned ID only) |
| GET | `/content/externalid/{id}` | Resolve external ID → 302 redirect |
| GET | `/content/externalid/{id}/history` | Resolve → redirect to history |
| GET | `/content/view/{view}/contentid/{id}` | Resolve in specific view → 302 redirect |
| GET | `/content/view/{view}/externalid/{id}` | Resolve external ID in view → 302 redirect |
| POST | `/content` | Create → 201 + Location + ETag |
| PUT | `/content/contentid/{id}` | Update (new version) → 200 + ETag |
| DELETE | `/content/contentid/{id}` | Soft delete → 204 |

Auth: `X-AUTH-TOKEN` header (JWT, enforced by `AuthFilter`).

### Content ID Format
- **Unversioned**: `delegationId:key` (e.g., `onecms:some-uuid`)
- **Versioned**: `delegationId:key:version` (e.g., `onecms:some-uuid:version-uuid`)
- `delegationId` maps to `idtype.name` in DB (`onecms` or `draft`)
- `key` maps to `id.id` (VARCHAR 255 PK) in DB

### Spring Boot 4 / Jackson 3 Notes
- Jackson 3.0 packages: `tools.jackson.core` / `tools.jackson.databind` (not `com.fasterxml.jackson`)
- Exception class: `tools.jackson.core.JacksonException` (not `JsonProcessingException`)

### ID Generation
`com.atex.desk.api.service.IdGenerator` — Camflake algorithm (Twitter Snowflake variant):
- Content IDs: 41-bit time + 24-bit sequence + 32-bit random → base-36 (~19 chars)
- Versions: 41-bit time + 14-bit sequence → base-36 (~10 chars)
- Base time: 2019-06-01T00:00:00Z

## Increment 2 — JWT Authentication

### Architecture
- **Algorithm**: RS256 (RSA + SHA-256, 2048-bit keys)
- **Library**: JJWT 0.12.6 with Gson backend (not Jackson, to avoid Jackson 2 vs 3 conflict)
- **Header**: `X-Auth-Token` (case-insensitive)
- **Claims**: `sub` (user ID), `scp` (permissions: READ,WRITE,OWNER), `aud` (instance ID), `iss`, `exp`, `jti`

### Auth Layer
`com.atex.desk.api.auth`:
- `TokenProperties` — `@ConfigurationProperties(prefix = "desk.auth")`: enabled, RSA keys, instanceId, clockSkew, maxLifetime
- `TokenService` — JWT decode/validate/create with RSA keys; auto-generates ephemeral keys + dev token if none configured
- `AuthFilter` — Jakarta servlet filter on `/content/*`: validates `X-Auth-Token`, sets user attribute on request
- `AuthConfig` — Registers filter via `FilterRegistrationBean`
- `DecodedToken` — Record: subject, impersonating, permissions, targets, expiration
- `InvalidTokenException` — Thrown on invalid/expired tokens

### Security Controller
`com.atex.desk.api.controller.SecurityController` at `/security`:

| Method | Path | Behaviour |
|--------|------|-----------|
| POST | `/security/token` | Login: `{username, password}` → JWT token + userId + expireTime |
| GET | `/security/token` | Validate: `X-Auth-Token` header → token info if valid |

### User Storage
- `users` table: userid (auto PK), username (unique), password_hash (SHA-256), created_at
- Entity: `AppUser`, Repository: `AppUserRepository`
- Default seed: `sysadmin` / `sysadmin`

### Configuration (`application.properties`)
```properties
desk.auth.enabled=true
desk.auth.instance-id=desk-api-dev
desk.auth.clock-skew=30
desk.auth.max-lifetime=86400
desk.auth.private-key=   # base64 PKCS8 — leave empty to auto-generate
desk.auth.public-key=    # base64 X509
```

## Increment 3 — DamDataResource Migration

Migrates the full 68-endpoint `DamDataResource` (3000-line JAX-RS resource from `gong/desk`) into desk-api as a Spring MVC `@RestController`. Rather than rewriting all endpoints, the approach copies the original code and mechanically converts annotations, then wires to local services.

### Architecture

```
Desk UI
  ↓
desk-api (Spring Boot 4)
  ├─ DamDataResource (@RestController at /dam/content) — converted from JAX-RS
  │    ├─ LocalContentManager (implements ContentManager → JPA repos)
  │    ├─ LocalPolicyCMServer (implements PolicyCMServer → ContentManager)
  │    ├─ LocalCmClient (wraps PolicyCMServer + UserServer)
  │    └─ CallerContext (request-scoped, replaces SetCurrentCaller)
  ├─ ContentController (/content/*) — OneCMS-compatible CRUD
  ├─ SecurityController (/security/*) — JWT auth
  └─ PrincipalsController (/principals/*) — user management
```

### Implementation Phases

The migration was done in 6 phases, each verified by `./gradlew compileJava` or `bootRun`.

#### Phase 1: OneCMS API Types (~40 classes)

Recreated the minimal OneCMS type hierarchy preserving **original package names** for backward compatibility. These are value classes and interfaces — no implementation logic.

**Package `com.atex.onecms.content`** (from polopoly/core/data-api-api):
- `ContentManager` — interface with 7 core methods + workspace stubs (default → UnsupportedOperationException)
- `ContentId`, `ContentVersionId` — immutable ID types
- `ContentResult<T>`, `ContentResultBuilder<T>` — result wrappers with status
- `Content<T>` — read result with aspects map
- `ContentWrite<T>`, `ContentWriteBuilder<T>` — write input with operations
- `Subject` — principal + credentials, `NOBODY_CALLER` constant
- `Status` — enum: OK, CREATED, NOT_FOUND, CONFLICT, etc.
- `DeleteResult`, `ContentHistory`, `ContentVersionInfo`, `ContentOperation`, `SetAliasOperation`
- `IdUtil` — static utility: fromString, toIdString
- `FilesAspectBean`, `InsertionInfoAspectBean`, `RepositoryClient`, `StorageException`

**Package `com.atex.onecms.content.aspects`**: `Aspect<T>`, `AspectDefinition`

**Package `com.polopoly.cm.*`** (minimal Polopoly subset):
- `ContentId` (Polopoly, numeric major/minor), `ExternalContentId`, `ContentIdFactory`, `ContentReference`
- `PolicyCMServer` — interface with 8 methods DamDataResource calls (others → UnsupportedOperationException)
- `CmClient`, `CmClientBase`, `CMException`, `UserData`, `HttpFileServiceClient`
- `Caller`, `UserServer`, `Group`, `GroupId`
- `Metadata`, `Dimension`

**Package `com.polopoly.application.*`**: `Application`, `ApplicationComponent`, `ApplicationServletUtil` (stubs)

**Other**: `Preconditions` (com.atex.common.base), `Pair` (com.atex.common.collections), `StringUtil` (com.polopoly.util)

**Key decision**: For large interfaces (PolicyCMServer has 79+ methods from ReadOnlyCMServer), only the methods DamDataResource actually calls are implemented; others get `default throw new UnsupportedOperationException()`.

#### Phase 2: LocalContentManager

`com.atex.desk.api.onecms.LocalContentManager` — implements `ContentManager`, delegates to existing `ContentService`.

| ContentManager method | Implementation |
|---|---|
| `resolve(id, subject)` | Delegates to `ContentService.resolve()` |
| `get(versionId, dataClass, aspects, subject)` | Delegates to `ContentService.getContent()`, converts to `ContentResult<T>` |
| `create(contentWrite, subject)` | Converts `ContentWrite<T>` → `ContentWriteDto`, delegates to `ContentService.createContent()` |
| `update(id, contentWrite, subject)` | Converts and delegates to `ContentService.updateContent()` |
| `delete(id, subject)` | Delegates to `ContentService.deleteContent()` |
| `getContentHistory(id, subject)` | Delegates to `ContentService.getHistory()` |
| Workspace methods | `throw new UnsupportedOperationException("Workspaces not yet supported")` |

**Generic `<T>` handling**: When `dataClass` is `Object.class` (90%+ of calls), aspect data is returned as `Map<String, Object>`. When a specific class is requested, `objectMapper.convertValue()` converts it.

#### Phase 3: PolicyCMServer Facade + CmClient

Three Spring `@Component` classes:

**`LocalPolicyCMServer`** — implements 8 PolicyCMServer methods:
- `getPolicy()` → resolves via ContentManager
- `findContentIdByExternalId()` → resolves external ID
- `getCurrentCaller()` / `setCurrentCaller()` → delegates to CallerContext
- `contentExists()` → resolve and check non-null

**`LocalCmClient`** — `getPolicyCMServer()` + `getUserServer()`

**`CallerContext`** — `@Component @RequestScope`, holds `principalId`. Replaces the Polopoly `SetCurrentCaller` try-with-resources pattern.

**`LocalUserServer`** — `getUserIdByLoginName()` delegates to `AppUserRepository`

#### Phase 4: DAM Utility Classes (~90 classes)

Copied from `gong/desk/module-desk` preserving original packages (`com.atex.onecms.app.dam.*`). Created as stubs initially, then fleshed out methods as needed by DamDataResource in Phase 5.

**Categories:**
| Category | Package | Example classes |
|---|---|---|
| Beans/DTOs | `com.atex.onecms.app.dam.standard.aspects` | DamArticleAspectBean, DamCollectionAspectBean, DamImageAspectBean, OneArticleBean, OneImageBean |
| Workflow | `com.atex.onecms.app.dam.workflow` | WFStatusBean, WFStatusListBean |
| Mark-as | `com.atex.onecms.app.dam.markas` | DamMarkAsBean |
| Collections | `com.atex.onecms.app.dam.collection.aspect` | CollectionAspect |
| Engagement | `com.atex.onecms.app.dam.engagement` | EngagementAspect, EngagementDesc, EngagementElement |
| Publishing | `com.atex.onecms.app.dam.publish` | DamPublisher, DamPublisherFactory, DamPublisherConfiguration, PublishingContext |
| Remote | `com.atex.onecms.app.dam.remote` | RemoteUtils, RemoteContentRefBean, RemotesConfiguration |
| Restrict | `com.atex.onecms.app.dam.restrict` | RestrictContentService |
| Utilities | `com.atex.onecms.app.dam.util` | DamUtils, DamMapping, MarkAsUtils, CollectionUtils, JsonUtil, HttpDamUtils |
| Operations | `com.atex.onecms.app.dam.operation` | ContentOperationUtils |
| Config | `com.atex.onecms.app.dam` | DeskConfig, DeskConfigLoader |
| Camel | `com.atex.onecms.app.dam.camel.client` | CamelApiWebClient, CamelEngine |
| Solr | `com.atex.onecms.app.dam.util` | SolrService, SolrPrintPageService, SolrUtils |
| User | `com.atex.onecms.app.user` | UserDataBean |
| ACL | `com.atex.onecms.app.dam.acl` | AclBean |
| Wire images | `com.atex.onecms.app.dam.wire` | DamWireImageAspectBean, DamWireImageWrapperPolicyBean |

**Stub strategy**: Classes were created as minimal compilable stubs (correct fields, empty method bodies returning null/defaults). When Phase 5 compilation revealed missing methods, they were added incrementally. Many methods remain stubs — they compile and can be implemented later as endpoints are activated.

**Gradle dependencies added:**
```groovy
implementation 'com.google.code.gson:gson:2.11.0'
implementation 'com.google.guava:guava:33.4.0-jre'
implementation 'org.apache.httpcomponents:httpclient:4.5.14'
implementation 'org.apache.pdfbox:pdfbox:2.0.32'
implementation 'org.apache.solr:solr-solrj:9.8.0'
```

#### Phase 5: DamDataResource Conversion

**Source**: `gong/desk/module-desk/.../DamDataResource.java` (3096 lines, 68 endpoints)
**Target**: `com.atex.onecms.app.dam.ws.DamDataResource` — `@RestController` at `/dam/content`

##### Annotation Conversion (mechanical)
| JAX-RS | Spring MVC |
|---|---|
| `@Path("/dam/content")` | `@RequestMapping("/dam/content")` |
| `@GET` / `@POST` / `@PUT` | `@GetMapping` / `@PostMapping` / `@PutMapping` |
| `@Path("/sub")` on method | Path in `@GetMapping("/sub")` |
| `@PathParam("id")` | `@PathVariable("id")` |
| `@QueryParam("q")` | `@RequestParam("q")` |
| `@DefaultValue("x")` | `@RequestParam(defaultValue = "x")` |
| `@HeaderParam("X-Auth-Token")` | `@RequestHeader("X-Auth-Token")` |
| `@Produces/@Consumes` | Removed (Spring defaults) |
| `Response` return type | `ResponseEntity<?>` |
| `Response.ok(entity).build()` | `ResponseEntity.ok(entity)` |
| `Response.status(404).build()` | `ResponseEntity.status(HttpStatus.NOT_FOUND).build()` |

##### Wiring Changes
| Old (Jersey) | New (Spring) |
|---|---|
| `@Context WebServiceUtil` | Constructor-injected `ContentManager` |
| `@Context SearchServiceUtil` | Constructor-injected `SolrService` (stub) |
| `@Context UserServiceUtil` | Constructor-injected `AppUserRepository` |
| `@Context ErrorFactory` | Throw `ContentApiException` |
| `getCMServer()` | `this.policyCMServer` (field) |
| `getCmClient()` | `this.cmClient` (field) |

##### New Infrastructure Classes

**`ContentApiException`** — RuntimeException with `HttpStatus` + `detailCode`. Factory methods: `badRequest()`, `notFound()`, `forbidden()`, `internal()`. Replaces JAX-RS `ErrorFactory`.

**`ContentApiExceptionHandler`** — `@RestControllerAdvice` catching `ContentApiException` → JSON `{error, status, detailCode}`. Logs 5xx at SEVERE, others at FINE.

**`DamUserContext`** — Replaces JAX-RS `@AuthUser UserContext` injection. Reads auth from request attributes set by `AuthFilter` (`desk.auth.user`, `desk.auth.token`). Methods: `isLoggedIn()`, `assertLoggedIn()`, `getSubject()`, `getCaller()`, `getAuthToken()`.

##### Endpoint Status

Most endpoints are fully converted. Complex endpoints requiring deep integration are stubs returning `NOT_IMPLEMENTED`:
- `create-page`, `sendcontent`, PDF merge

`export` and `createcontent` were implemented in Increment 4 via `BeanMapper`.

##### Internal JSON Handling

DamDataResource uses **Gson** internally (not Jackson) for JSON parsing, matching the original implementation. Both Gson and Jackson 3 coexist without conflict.

#### Phase 6: Cleanup

Removed superseded controllers and DTOs that were replaced by DamDataResource endpoints:

**Deleted controllers:**
- `DamConfigController` — superseded by DamDataResource `configuration/*` endpoints
- `DamSearchController` — superseded by DamDataResource `solrquery`, `queryUpdate`, `eventid`, `autocomplete`, `terms`, `find/*`, `references`, `related`
- `DamContentController` — superseded by DamDataResource `contentid/*`, `resolve/*`, `metadata/markas`, `update`, `duplicate`, `publish`, `unpublish`, etc.

**Deleted DTOs:**
- `MarkAsRequestDto` — replaced by `DamMarkAsBean`
- `DuplicateRequestDto` — replaced by Gson-based JSON parsing
- `QueryUpdateRequestDto` — replaced by Gson-based JSON parsing

**Kept:** `ContentController`, `SecurityController`, `PrincipalsController`, `ContentService`, all entities, all repositories.

### Auth Configuration

`AuthConfig` registers `AuthFilter` on `/content/*`, `/dam/*`, `/principals/*` patterns. The filter sets request attributes that `DamUserContext.from(request)` reads.

### Increment 3a — Solr Search Implementation

Replaced all search-related stubs with real SolrJ 9.8.0 integration.

**New files:**
- `com.polopoly.search.solr.SolrConfiguration` — Config holder extending SolrServerUrl with core + auth
- `com.atex.onecms.app.dam.util.Reference` — POJO holding ArrayList<String> of content ID strings for search results
- `com.atex.onecms.app.dam.standard.aspects.OneContentBean` — Base class for DamQueryAspectBean with common metadata fields
- `com.atex.onecms.app.dam.solr.SolrQueryUtils` — Resolves `{externalId/xxx}` templates in Solr query strings via ContentManager

**Rewritten stubs:**
- `SolrService` — Full SolrJ implementation with `Http2SolrClient`. Methods: autocomplete, terms, recordCount, query, querySorted, resources, related, jobs, mlt, getLatestEventId, getHighlightMap, size. Uses `client.query(collection, query)` two-arg API for SolrJ 9.x. JSON serialization via Gson.
- `SolrUtils` — Static utility holding Solr server URL and core name configuration
- `SolrQueryDecorator` — Decorates SolrQuery with working sites filter on `page_ss` field
- `DamMapping` — Complete 26 bidirectional field mappings (UI field ↔ Solr field)
- `SolrCoreMapper` — Changed from interface to concrete class with Map-based mappings and prefix fallback
- `SolrCoreMapperFactory` — Static `create()` methods returning SolrCoreMapper instances
- `QueryField` (357 lines) — Full query builder with `processQuery()` and `processSubQuery()`. Handles types: STRING, LIST, BOOLEAN, DATE, ENGAGE, NUM-RANGE, GEOLOCATION
- `DamQueryAspectBean` (800 lines) — Full query bean with 39 Solr field constants, 60+ search parameters, two `updateSolrQueryString()` variants (Hermes-style with block joins, budget-style with status label→ID mapping and timezone-aware date queries)

**DamDataResource endpoints wired:** `solrquery`, `eventid`, `queryUpdate`, `autocomplete`, `terms`, `references`, `related`, `jobs`, `mlt`, `configuration/solrserverinfo`, `configuration/search/{type}`, `find/{type}`

### Increment 3b — File Service

Implemented filesystem-backed file service infrastructure.

**New files:**
- `com.atex.onecms.content.files.FileService` — Core interface (8 methods: getFile, uploadFile, removeFile, moveFile, listFiles, getFileInfo, getFileInfos). URI schemes: `content://` and `tmp://`
- `com.atex.onecms.content.files.FileInfo` — File metadata: uri, mimeType, checksum (MD5), length, timestamps
- `com.atex.desk.api.onecms.LocalFileService` — `@Service` filesystem-backed implementation. Stores files under `${desk.file-service.base-dir:./files}`, organized by space/host. Path traversal prevention, UUID filenames, MD5 checksums.
- `com.atex.desk.api.onecms.LocalHttpFileServiceClient` — `@Component` wrapping LocalFileService, provides `getFileService()` and `getFileServiceBaseUrl()`

**Rewritten stubs:**
- `HttpDamUtils` — Full Apache HttpClient implementation: `readBinaryContentFromURL`, `sendStreamToFileService`, `sendBinaryToFileService`, `callDataApiWs` (general REST calls with ETag/auth support). Configurable timeouts (20s connect, 60s socket).
- `FileServiceUtils` — Utilities: `transformFileUri`, `downloadFiles`, `write` (text/binary), `createTempFolder`, `getMimeType`
- `HttpFileServiceClient` — Added `getFileService()` method to interface

### Increment 3c — Remaining Stub Implementations

Implemented all remaining utility classes and endpoint stubs that had real logic in the originals.

**Utility classes rewritten:**
| Class | Implementation |
|---|---|
| `MarkAsUtils` | `updateDimension()` replaces entities, `getDimensionId()` prefixes with `dimension.markas-`, `createDimension/Metadata/MetadataInfo` factories |
| `SmartUpdatesMetadataUtils` | `updateDimension()` appends entities with dedup check, `createDimension` strips `dimension.` prefix for name |
| `DamEngagementUtils` | Full `addEngagement()` + `updateEngagement()` — reads EngagementAspect from ContentManager, modifies, writes back. Attribute merging by name on update. Also `getEngagement()` for reading. |
| `DamEngagement` | `getEngagementId()` resolves EngagementAspect, extracts ContentId from first engagement's `appPk`. `findEngagement()` searches by appType. |
| `ContentOperationUtils` | `duplicate()` clones content via ContentManager read+create. `copyContent()` returns source→copy ID mapping. |
| `CollectionAspectUtils` | Proper merge logic — detects no-changes (returns null), updates contentIds and name |
| `DamWebServiceUtil` | `createEventFromJson()` uses Gson deserialization, added `parseJsonInputStream()` |
| `CamelApiWebClient` | Full HTTP implementation: `addCamelRoute()`, `stopCamelRoute()`, `getCamelRoutes()` via `HttpDamUtils.callDataApiWs()` |
| `RemoteUtils` | Added `callRemoteWs(method, url, auth)` for HTTP proxy calls |

**DamDataResource endpoints implemented:**
| Endpoint | Implementation |
|---|---|
| `sked/aspect` | Reads template from classpath, parses as JSON, returns serialized |
| `proxy` | Delegates to `RemoteUtils.callRemoteWs()` via `PublishingContext` |
| `changePassword` | SHA-256 hash via `AppUserRepository` for logged-in user |
| `changeUserPwd` | Same hash logic for specified userId (admin operation) |
| `getpage` | Solr query with `hermesPk_ss:"{pageid}"` filter |
| `users` | Returns all users from `AppUserRepository.findAll()`, cached via Guava `OBJECT_CACHE` |
| `groups` | Returns cached empty groups array (group storage not yet implemented) |
| `addCamelRoute` / `stopCamelRoute` | Delegate to `CamelApiWebClient` HTTP methods |

**Other changes:**
- `Dimension` — Added `addEntities(Entity...)` varargs method
- `DamDataResource` constructor — Added `AppUserRepository` as 6th dependency

### Increment 3d — Content Restriction

Full implementation of `RestrictContentService` — restricts/unrestricts content by modifying security parents, associated sites, and workflow statuses.

**New classes created:**
| Class | Package | Description |
|---|---|---|
| `AbstractStatusAspectBean` | `c.a.o.a.d.workflow` | Base class for workflow status beans (status + comment) |
| `WFContentStatusAspectBean` | `c.a.o.a.d.workflow` | Print/content workflow status aspect (`atex.WFContentStatus`) |
| `WebContentStatusAspectBean` | `c.a.o.a.d.workflow` | Web workflow status aspect (`atex.WebContentStatus`) |
| `WFStatusUtils` | `c.a.o.a.d.workflow` | Resolves WFStatusBean by ID from status list content (`dam.wfstatuslist.d`, `dam.webstatuslist.d`) |
| `ResolveUtil` | `c.a.o.a.d.util` | Resolves content external IDs with ConcurrentHashMap caching |
| `RestrictContentConfiguration` | `c.a.o.a.d.restrict` | Config loaded from `atex.configuration.desk.restrictContent` — maps security parent IDs to restrict/unrestrict configurations |

**RestrictContentService methods:**
- `restrictContent(ContentId, Subject)` — updates InsertionInfoAspectBean (security parent, insert parent, associated sites) + WFContentStatusAspectBean + WebContentStatusAspectBean
- `unRestrictContent(ContentId, Subject)` — reverse operation using unrestrict config
- `canRestrictContent(ContentId, Subject)` / `canUnRestrictContent(ContentId, Subject)` — permission checks
- `applyRestrictContent(ContentWrite)` / `applyUnRestrictContent(ContentWrite)` — apply restrictions to a ContentWrite before creation

### Increment 3e — Publishing Pipeline

Full publishing infrastructure: interfaces, implementations, configuration, and factory.

**New interfaces:**
| Interface | Description |
|---|---|
| `DamRemoteImporter` | Base for remote content import: `importContent()`, `getContent()`, `getContentReference()` |
| `DamBeanPublisher` | Extends `DamRemoteImporter`: `publish()`, `unpublish()`, `getRemotePublicationUrl()`, `createModulePublisher()`, `createModuleImporter()` |
| `DamPublisher` | Extends `DamRemoteImporter`: full publishing interface with converter support, delegating to `DamBeanPublisher` |
| `DamPublishConverter` | Content conversion during publish: `test(contentData)` + `convert(contentId)` |
| `PublishingContext` | Publishing context: `getRemoteConfiguration()`, `getPublishConfiguration()`, `getCaller()`, `getSubject()` |

**New implementations:**
| Class | Description |
|---|---|
| `DamPublisherImpl` | Full DamPublisher — resolves content, applies converter chain, delegates to DamBeanPublisher |
| `PublishingContextImpl` | Immutable context holding RemoteConfigBean, DamPublisherConfiguration, source ID, workspace ID, Caller |
| `DamPublisherBuilder` | Fluent builder for DamPublisher instances |

**Configuration classes:**
| Class | Description |
|---|---|
| `RemoteConfigBean` | Remote publishing target config: API URLs, credentials, module classes, preview settings, domain overrides, engagement config |
| `RemoteConfigRuleBean` | Publish rule: matches users/groups/securityParentIds to a backend |
| `DamStatusConfiguration` | Base config for status handling during publish (status attributes, content/web status by workflow) |
| `DamPublisherConfiguration` | Extends DamStatusConfiguration: image quality/width, security parent, distributed lock, non-publishable statuses. Fetched from `com.atex.onecms.dam.beanPublisher.Configuration` |
| `RemotesConfiguration` | Top-level config: map of RemoteConfigBean, publish rules, defaults |
| `RemotesConfigurationFactory` | Factory parsing remotes JSON from `com.atex.onecms.dam.remotes.Configuration` |

**Utility classes rewritten:**
| Class | Implementation |
|---|---|
| `DomainOverrider` | URL domain replacement using regex + RemoteConfigBean domain overrides |
| `PublicationUrlJsonParser` | Gson-based: `getUrl()` extracts from JSON string/object, `toUrl()` wraps in `{"url":"..."}` |
| `LockPathCreator` | `createPublishLockPath()` → `/atex/onecms/desk/publish/locks/{contentId}` |
| `DistributionListUtil` | Email list expansion from `dam.distributionlist.conf` content, with 1-minute cache |
| `LockServiceUtil` | Static lock utility with graceful fallback when no LockService configured |

**DamPublisherFactory** (`@Component`):
- Constructor-injected `ContentManager`
- `createContext(ContentId, Caller)` — loads RemotesConfiguration, matches rules, resolves publish config
- `createContext(String backendId, Caller)` — direct backend lookup
- `create(PublishingContext)` — builds DamPublisher via DamPublisherBuilder
- `getBackendIdFromApiDomain(String)` — reverse lookup backend by API URL

## Increment 4 — BeanMapper, HTTP Publisher, Permission Checking

Implements the three major stubs remaining from Increment 3: JWT-based permission checking, HTTP content publishing to remote CMS, and content export via BeanMapper.

### Permission Checking

**`DamUserContext.havePermission()`** — replaced stub (always true) with JWT scope-based checking:
- `OWNER` scope → all permissions granted
- Strips `"21"` decoration prefix before matching (Polopoly ACL convention)
- `READ` permission → requires `READ` scope
- `WRITE` / `CREATE` permission → requires `WRITE` scope
- Content-level ACLs deferred (contentId parameter ignored)

**`AclBean`** — added `decorate` boolean field (default true). When true, permission endpoint prefixes permission string with `"21"`.

**`DamDataResource` permission endpoint** — updated to parse contentId via `ContentIdFactory.createContentId()`, apply decoration, and pass to `havePermission()`.

### HTTP Publisher

Full publishing pipeline from local content to remote CMS via HTTP.

**New classes:**
| Class | Package | Description |
|---|---|---|
| `ContentPublisher` | `c.a.o.a.d.publish` | Interface: 9 methods (publishContent, publishContentUpdate, unpublish, publishBinary, readBinary, resolve, getContent, getRemotePublicationUrl, postContent) |
| `ContentAPIPublisher` | `c.a.o.a.d.publish` | HTTP implementation using `HttpDamUtils` + Spring `UriComponentsBuilder`. Retry logic (2 attempts), token renewal on auth failure |
| `DamBeanPublisherImpl` | `c.a.o.a.d.publish` | Concrete `DamBeanPublisher`: resolve source → check engagement for existing remote ID → serialize via Gson → HTTP push → record engagement |
| `PublishResult` | `c.a.o.a.d.publish` | Value class: id + fileUri |
| `UserTokenStorage` | `c.a.o.a.d.publish` | ConcurrentHashMap token cache (max 50 entries, 5-minute TTL) |

**Modified classes:**
| Class | Change |
|---|---|
| `ContentPublisherException` | Changed from `Exception` to `RuntimeException` |
| `ContentPublisherNotFoundException` | Added cause constructor |
| `ModulePublisher` | Replaced stub — constructor takes `RemoteConfigBean`, creates `ContentAPIPublisher` instances |
| `DamPublisherBuilder` | `build()` now wires `DamBeanPublisherImpl` when remote config with API URL is available |

**Publishing flow:**
```
DamPublisherFactory.create(contentId, caller)
  → DamPublisherBuilder.build()
    → DamPublisherImpl (converter chain)
      → DamBeanPublisherImpl (serialize + HTTP)
        → ModulePublisher.createContentPublisher(username)
          → ContentAPIPublisher (HTTP calls via HttpDamUtils)
            → Remote CMS REST API
```

### BeanMapper (Content Export)

**New classes:**
| Class | Package | Description |
|---|---|---|
| `BeanMapperConfiguration` | `c.a.o.a.d.cfg` | Config from `com.atex.onecms.beanmapper.Configuration`: enabled, handlers, excludeAspects, excludeDimensions |

**`BeanMapper.export()`** implementation:
1. Resolve + fetch source content via ContentManager
2. Deep-copy main bean via `Gson.fromJson(Gson.toJson(bean))` (no XML config needed)
3. Reset creation date for `OneContentBean` subtypes
4. Set `InsertionInfoAspectBean` with security parent from destination domain
5. Deep-copy all aspects via Gson, apply `BeanMapperConfiguration` filtering (exclude aspects/dimensions)
6. Remove engagement aspect (new content should not inherit engagements)
7. Create exported content via `ContentManager.create()`
8. Record export engagement on source content
9. Return new content ID string

**DamDataResource endpoints wired:**
- `export` — delegates to `BeanMapper.export()` with query params
- `createcontent` — parses JSON body for contentId + params, delegates to `BeanMapper.export()`

### Still Not Implemented

**501 NOT_IMPLEMENTED endpoints** (require substantial infrastructure not yet available):

| Endpoint | Reason |
|---|---|
| `create-page` | Needs deep PolicyCMServer page hierarchy (PagePolicy, lock/commit) |
| `sendcontent` | Needs CamelEngine route matching + file service integration |
| `assigncontent` | Needs DamAssignContentService + DamSendContentPolicy |
| `mergeMultiplePdf` | Needs PDFBox integration |
| `collectionpreview` | Needs ACE Web Preview |

**Remaining stubs** (compile but have no real logic):

| Category | Classes | Status |
|---|---|---|
| Module import | `ModuleImporter` | Empty stub — need remote CMS HTTP import logic |
| Solr print pages | `SolrPrintPageService` | Empty stub |
| Collection gallery | `CollectionToGallery` | Empty stub |
| Send content | `SendContentHandlerFinder`, `DamSendContentPolicy` | Empty stubs |
| Camel engine | `CamelEngine` | Empty stub (route management) |
| Workspace/draft | ContentManager workspace methods | Throw UnsupportedOperationException |
| Group storage | `LocalUserServer.findGroup()` | Returns null |

## Increment 5 — PF4J Plugin System

Adds a modular plugin architecture using PF4J 3.15.0 for loading external JAR plugins at runtime. Plugins can extend content lifecycle behavior without modifying desk-api core.

### Architecture

```
desk-api startup
  ├─ PluginConfig (@Configuration)
  │    └─ JarPluginManager → loads + starts JARs from plugins/ directory
  ├─ BuiltInHookRegistrar (@PostConstruct) — registers built-in hooks first
  └─ PluginLoader (@PostConstruct, @DependsOn("builtInHookRegistrar"))
       ├─ discovers DeskPreStoreHook extensions → registers with LocalContentManager
       └─ discovers DeskContentComposer extensions → logged (not yet wired)
```

### Extension Points

**`DeskPreStoreHook`** (`extends ExtensionPoint`) — modify content before persistence:
- `String[] contentTypes()` — content types this hook applies to
- `ContentWrite<Object> preStore(input, existing, contentManager, subject)` — transform content write; throw `CallbackException` to abort

**`DeskContentComposer`** (`extends ExtensionPoint`) — transform content for variants:
- `String variant()` — variant name this composer handles
- `ContentResult<Object> compose(source, params)` — transform content result
- Discovered at startup but not yet exposed via REST API

### Plugin Infrastructure

| Class | Package | Description |
|---|---|---|
| `PluginConfig` | `c.a.d.a.plugin` | `@Configuration`: creates `JarPluginManager` bean (or no-op when disabled), auto-creates plugins directory |
| `PluginProperties` | `c.a.d.a.plugin` | `@ConfigurationProperties(prefix = "desk.plugins")`: `enabled` (default true), `directory` (default "plugins") |
| `PluginLoader` | `c.a.d.a.plugin` | `@Component @DependsOn("builtInHookRegistrar")`: discovers PF4J extensions, adapts `DeskPreStoreHook` to `LifecyclePreStore` interface, registers with `LocalContentManager` |
| `DeskPreStoreHook` | `c.a.d.a.plugin` | PF4J `ExtensionPoint` interface for pre-store hooks |
| `DeskContentComposer` | `c.a.d.a.plugin` | PF4J `ExtensionPoint` interface for content composers |

### Hook Execution in LocalContentManager

`LocalContentManager` maintains a `Map<String, List<LifecyclePreStore<?, ?>>>` keyed by content type. The `registerPreStoreHook(contentType, hook)` method appends to this map. During `create()` and `update()`, `runPreStoreHooks()` chains all matching hooks in registration order, passing each a `LifecycleContextPreStore` with `ContentManager`, `Subject`, and optional `FileService`.

### Configuration

```properties
desk.plugins.enabled=true
desk.plugins.directory=plugins
```

**Gradle dependency:** `implementation 'org.pf4j:pf4j:3.15.0'`

## Increment 6 — Built-in PreStoreHooks + Index Composer Pipeline

Ports the built-in PreStoreHooks and post-store index composition from the original gong/onecms-common codebase into desk-api. These are core behaviors every installation needs — the PF4J plugin system (Increment 5) handles customer-specific additions on top.

The original system maps content types to ordered hook chains via XML config (`callbacks.xml`). In desk-api, `BuiltInHookRegistrar` programmatically registers hooks in the correct order at startup.

### Architecture

```
LocalContentManager
  ├─ create()/update() → runPreStoreHooks()
  │    ├─ Wildcard hooks (all types):
  │    │    ├─ OneContentPreStore         (creation date, name, author)
  │    │    └─ HandleItemStatePreStore    (spike/unspike)
  │    ├─ Image types:
  │    │    ├─ OneImagePreStore           (metadata extraction)
  │    │    ├─ SecParentPreStoreHook      (partition sync)
  │    │    ├─ SetStatusPreStoreHook      (workflow status)
  │    │    └─ AddEngagementPreStoreHook  (engagement tracking)
  │    ├─ Article types:
  │    │    ├─ SecParentPreStoreHook
  │    │    ├─ SetStatusPreStoreHook
  │    │    ├─ OneWordCountPreStoreHook   (word count)
  │    │    ├─ OneCharCountPreStoreHook   (char count)
  │    │    └─ AddEngagementPreStoreHook
  │    ├─ Audio/Collection types: (similar chains)
  │    └─ [PF4J plugin hooks]            (appended after built-ins)
  │
  ├─ create()/update() → ContentIndexer.index()  [post-store]
  │    └─ DamIndexComposer → SolrService.index() → Solr
  │
  └─ delete() → ContentIndexer.delete()
       └─ SolrService.delete() → Solr
```

### Bean Inheritance Fix (prerequisite)

The original OneCMS beans have an inheritance chain that hooks depend on (`instanceof OneContentBean` checks). Fixed the desk-api stubs to match:

| Bean | Change |
|---|---|
| `OneArticleBean` | Now `extends OneContentBean`; added `headline`, `lead`, `body` |
| `OneImageBean` | Now `extends OneContentBean`; added `description`, `caption`, `title`, `byline`, `credit`, `width`, `height` |
| `DamCollectionAspectBean` | Now `extends OneContentBean`; added `headline`, `description`, `getContentIdList()` helper |
| `DamArticleAspectBean` | Simplified to `extends OneArticleBean` (inherits all fields) |
| `DamImageAspectBean` | Simplified to `extends OneImageBean` (inherits all fields) |
| `DamAudioAspectBean` | New class `extends OneContentBean`; `audioLink`, `duration`, `description` |

### Phase 1: Infrastructure

**New classes:**

| Class | Package | Description |
|---|---|---|
| `CachingFetcher` | `c.a.o.content` | Per-request cache wrapping `ContentManager` with HashMap-based caching for `get()` and `resolve()` operations. Factory: `CachingFetcher.create(contentManager, subject)` |
| `SetStatusOperation` | `c.a.o.content` | `implements ContentOperation`: `statusId`, `comment`, `attributes` map |
| `AddEngagement` | `c.a.o.content` | `implements ContentOperation`: `sourceId`, `EngagementDesc`, `addOriginalContent` flag |
| `PrestigeItemStateAspectBean` | `c.a.o.a.d.standard.aspects` | Item state aspect: `ItemState` enum (PRODUCTION, SPIKED), `previousSecParent` |
| `DamContentAccessAspectBean` | `c.a.o.a.d.standard.aspects` | Content access control: `creator`, `assignees` list |
| `OneWordCountPreStoreConfig` | `c.a.o.a.d.lifecycle.wordcount` | Config bean with `fields` list (default: `["body"]`) |
| `OneCharCountPreStoreConfig` | `c.a.o.a.d.lifecycle.charcount` | Config bean with `fields` list (default: `["body"]`) |
| `PartitionProperties` | `c.a.d.a.plugin` | `@ConfigurationProperties(prefix = "desk.partitions")`: `Map<String, String> mapping` (partition ID → security parent external ID) with bidirectional lookup |

**Modified classes:**

| Class | Change |
|---|---|
| `LifecycleContextPreStore` | Added optional `FileService` field with second constructor |
| `LocalContentManager` | Added `FileService` + `ContentIndexer` constructor params (both `@Nullable`); `runPreStoreHooks()` merges wildcard `"*"` hooks with type-specific hooks; post-store `indexAsync()` and `delete()` call `ContentIndexer` |

### Phase 2: PreStoreHook Implementations

All hooks implement `LifecyclePreStore<Object, ?>` and are plain classes (not Spring components) — instantiated by `BuiltInHookRegistrar`.

| Hook | Package | Behaviour |
|---|---|---|
| `OneContentPreStore` | `c.a.o.a.d.lifecycle.onecontent` | Sets creation date if null, derives name from headline/title/caption, sets author from subject |
| `HandleItemStatePreStore` | `c.a.o.a.d.lifecycle.handleItemState` | SPIKED → moves to trash security parent + "trash" partition; PRODUCTION → restores previous security parent |
| `OneImagePreStore` | `c.a.o.a.d.lifecycle.onecontent` | Sets name from filename; stub for image metadata service extraction (configurable via `desk.image-metadata-service.*`) |
| `SecParentPreStoreHook` | `c.a.o.a.d.lifecycle.partition` | Bidirectional partition ↔ security parent sync using `PartitionProperties` mapping |
| `SetStatusPreStoreHook` | `c.a.o.a.d.lifecycle.status` | Processes `SetStatusOperation` from ContentWrite operations; resolves status via `WFStatusUtils`; updates `WFContentStatusAspectBean` + `WebContentStatusAspectBean` |
| `OneWordCountPreStoreHook` | `c.a.o.a.d.lifecycle.wordcount` | Strips HTML, counts words via `split("\\s+")`, sets `bean.setWords()` |
| `OneCharCountPreStoreHook` | `c.a.o.a.d.lifecycle.charcount` | Strips HTML, counts characters, sets `bean.setChars()` |
| `AddEngagementPreStoreHook` | `c.a.o.a.d.lifecycle.engagement` | Processes `AddEngagement` operations via `DamEngagementUtils` |
| `DamAudioPreStoreHook` | `c.a.o.a.d.lifecycle.audio` | Sets name and `audioLink` from `FilesAspectBean` file path if empty |
| `CollectionPreStore` | `c.a.o.a.d.lifecycle.collection` | Sets name from headline, initializes `DamContentAccessAspectBean` for new collections |

### Phase 3: Hook Registration

**`BuiltInHookRegistrar`** (`com.atex.desk.api.plugin`) — `@Component` with `@PostConstruct` that registers all built-in hooks with `LocalContentManager` in the correct order, matching the original `callbacks.xml` configuration.

**Content type → hook chain mapping:**

| Content Type | Hook Chain (in order) |
|---|---|
| `*` (wildcard, all types) | OneContentPreStore → HandleItemStatePreStore |
| Image (`atex.dam.standard.Image`, `DamImageAspectBean`, `OneImageBean`) | OneImagePreStore → SecParentPreStoreHook → SetStatusPreStoreHook → AddEngagementPreStoreHook |
| WireImage (`atex.dam.standard.WireImage`) | OneImagePreStore → SecParentPreStoreHook → SetStatusPreStoreHook |
| Article (`atex.dam.standard.Article`, `DamArticleAspectBean`, `OneArticleBean`) | SecParentPreStoreHook → SetStatusPreStoreHook → OneWordCountPreStoreHook → OneCharCountPreStoreHook → AddEngagementPreStoreHook |
| WireArticle (`atex.dam.standard.WireArticle`) | SecParentPreStoreHook → SetStatusPreStoreHook → OneWordCountPreStoreHook → OneCharCountPreStoreHook |
| Audio (`DamAudioAspectBean`) | DamAudioPreStoreHook → SecParentPreStoreHook → SetStatusPreStoreHook → AddEngagementPreStoreHook |
| Collection (`DamCollectionAspectBean`) | CollectionPreStore → SecParentPreStoreHook → SetStatusPreStoreHook → AddEngagementPreStoreHook |

Hooks registered for both original Polopoly type names (`atex.dam.standard.*`) and Java class-based type names (`com.atex.onecms.app.dam.standard.aspects.*`).

**Ordering guarantee**: `@DependsOn("builtInHookRegistrar")` on `PluginLoader` ensures built-in hooks register first, PF4J plugin hooks append after.

**Wildcard merging**: `LocalContentManager.runPreStoreHooks()` merges hooks from `"*"` key first, then appends type-specific hooks. All hooks chain in registration order.

### Phase 4: Index Composer Pipeline

Post-store indexing that converts content to Solr documents and pushes to Solr after create/update/delete.

**New classes:**

| Class | Package | Description |
|---|---|---|
| `ContentIndexer` | `c.a.d.a.indexing` | `@Component`: receives pre-built `ContentResult` from `LocalContentManager` (avoids circular dependency), composes Solr JSON via `DamIndexComposer`, pushes to `SolrService`. `@Nullable SolrService` — silently skips if Solr not configured. Methods: `index(ContentResult, ContentVersionId)`, `delete(ContentId)` |
| `DamIndexComposer` | `c.a.d.a.indexing` | `@Component`: combines IndexComposer + DamIndexComposer + SystemFieldComposer into one class. Produces Solr JSON with: system fields (`id`, `version`, `type`, timestamps), aspect fields (auto-detected Solr type suffixes: `_b`, `_l`, `_dt`, `_ss`, `_t`), hierarchy fields (`page_ss`), DAM-specific fields (`originalCreationTime_dt`, full-text fields) |
| `SolrConfig` | `c.a.d.a.config` | `@Configuration`: provides `SolrService` bean via `@ConditionalOnProperty(name = "desk.solr-url")` |

**Modified classes:**

| Class | Change |
|---|---|
| `SolrService` | Added `index(String collection, JsonObject solrDoc)` — converts JSON to `SolrInputDocument`, adds + commits. Added `delete(String collection, String id)` — deletes by ID + commits. Added private `jsonToSolrDoc()` helper |
| `LocalContentManager` | After `create()`/`update()`: calls `ContentIndexer.index()` with the already-built `ContentResult` (fire-and-forget, logs errors but doesn't fail the content operation). After `delete()`: calls `ContentIndexer.delete()` |

**Circular dependency resolution**: `ContentIndexer` does NOT depend on `ContentManager`. Instead, `LocalContentManager` passes the already-fetched `ContentResult` directly to `ContentIndexer.index()`, breaking the cycle.

### Configuration

```properties
# Partition → security parent mapping (for SecParentPreStoreHook)
# desk.partitions.mapping.default=site.default.d

# Content indexing (requires desk.solr-url to be set)
desk.indexing.enabled=true

# Image metadata service (for OneImagePreStore)
desk.image-metadata-service.url=
desk.image-metadata-service.enabled=false
```

**Gradle dependency added:** `implementation 'org.jsoup:jsoup:1.18.3'`

### Files Summary

**New files (22):**

| File | Description |
|---|---|
| `content/CachingFetcher.java` | Per-request content cache |
| `content/SetStatusOperation.java` | Status change operation |
| `content/AddEngagement.java` | Engagement add operation |
| `standard/aspects/PrestigeItemStateAspectBean.java` | Spike/unspike state |
| `standard/aspects/DamContentAccessAspectBean.java` | Content access control |
| `standard/aspects/DamAudioAspectBean.java` | Audio content bean |
| `lifecycle/wordcount/OneWordCountPreStoreHook.java` | Word counter |
| `lifecycle/wordcount/OneWordCountPreStoreConfig.java` | Word counter config |
| `lifecycle/charcount/OneCharCountPreStoreHook.java` | Char counter |
| `lifecycle/charcount/OneCharCountPreStoreConfig.java` | Char counter config |
| `lifecycle/audio/DamAudioPreStoreHook.java` | Audio name/link setter |
| `lifecycle/status/SetStatusPreStoreHook.java` | Workflow status handler |
| `lifecycle/engagement/AddEngagementPreStoreHook.java` | Engagement tracking |
| `lifecycle/onecontent/OneContentPreStore.java` | Content initialization |
| `lifecycle/onecontent/OneImagePreStore.java` | Image metadata extraction |
| `lifecycle/partition/SecParentPreStoreHook.java` | Partition ↔ security parent |
| `lifecycle/handleItemState/HandleItemStatePreStore.java` | Spike/unspike |
| `lifecycle/collection/CollectionPreStore.java` | Collection item processing |
| `plugin/BuiltInHookRegistrar.java` | Registers hooks per content type |
| `plugin/PartitionProperties.java` | Partition config properties |
| `indexing/ContentIndexer.java` | Post-store Solr indexing |
| `indexing/DamIndexComposer.java` | Content → Solr JSON |

**Modified files (8):**

| File | Change |
|---|---|
| `OneArticleBean.java` | Extend `OneContentBean`, add missing fields |
| `OneImageBean.java` | Extend `OneContentBean`, add missing fields |
| `DamCollectionAspectBean.java` | Extend `OneContentBean`, add missing fields |
| `DamArticleAspectBean.java` | Simplified to extend `OneArticleBean` |
| `DamImageAspectBean.java` | Simplified to extend `OneImageBean` |
| `LifecycleContextPreStore.java` | Add optional `FileService` field |
| `LocalContentManager.java` | Wildcard hook matching, post-store indexing, `FileService` + `ContentIndexer` injection |
| `SolrService.java` | Add `index()` and `delete()` methods |

## Increment 7 — Resource-Based Configuration System

Replaces database-stored configuration with a 5-tier resource-based system. All existing consumers that call `ContentManager.resolve(externalId)` → `ContentManager.get(vid)` work without code changes.

### Architecture

```
Configuration Lookup (precedence order):

1. DATABASE (live)     — config modified via UI at runtime
2. PLUGIN (classpath)  — config/project/*.json inside PF4J plugin JARs
3. PROJECT (classpath) — classpath:config/project/{id}.json
4. FLAVOR (classpath)  — classpath:config/flavor/{hseries|pseries}/{id}.json
5. PRODUCT (classpath) — classpath:config/defaults/{id}.json

                        ┌──────────────────────┐
  WFStatusUtils ──────→ │                      │
  RemotesConfigFactory → │  LocalContentManager │
  RestrictContentCfg ──→ │    resolve(extId)    │──→ ConfigurationService
  BeanMapperConfig ────→ │    get(versionId)    │     ├─ check DB (live)
  DamDataResource ─────→ │                      │     ├─ check plugin resources
  ContentController ───→ │                      │     ├─ check project resources
                        └──────────────────────┘     ├─ check flavor overlay
                                                     └─ check product defaults
```

Projects are expected to be PF4J plugins — a JAR containing `config/project/*.json` overrides plus any custom `DeskPreStoreHook` or `DeskContentComposer` extensions. The plugin classloader is scanned at startup so project config files are automatically discovered.

### Flavor System (hseries / pseries)

The original adm-starterkit has two flavors controlled by a Maven build property (`-Ddesk=hseries` or `-Ddesk=pseries`). Only ~22 files differ between them (menus, toolbars, widgets, search forms, permissions, print templates). In desk-api, flavors are handled at runtime via `desk.config.flavor`:

- `config/flavor/hseries/` — only the files that differ for H-Series integration
- `config/flavor/pseries/` — only the files that differ for P-Series integration
- Files NOT present in the flavor directory fall through to product defaults
- A system doesn't change flavors at runtime — this is a deployment-time setting

### Config Interception

**`LocalContentManager.resolve(externalId)`**: tries DB first; if not found and `ConfigurationService.isConfigId(externalId)`, returns a synthetic `ContentVersionId` with delegation ID `"config"`.

**`LocalContentManager.get(vid)`**: if `vid.getDelegationId() == "config"`, returns config from `ConfigurationService.toContentResult()` instead of hitting DB.

**`ContentController.getContentByExternalId()`**: if `ContentService.resolveExternalId()` returns empty and `ConfigurationService.isConfigId(id)`, returns the config directly as `ContentResultDto` (no redirect).

### File Format

All config files use the **`.json5`** extension. This allows comments (`//`, `/* */`), trailing commas, and other JSON5 syntax without IDE validation errors. `ConfigurationService` scans for `.json5` first, then `.json` as a fallback (for backwards compatibility with plugins using `.json`).

The `parseJson()` method tries standard JSON parsing first; if that fails, it falls back to `Json5Reader.toJson()` which strips comments/trailing commas and uses Gson's lenient mode. This means both strict JSON and JSON5 content work regardless of file extension.

### Resource File Layout

```
src/main/resources/config/
  defaults/                              ← product defaults (187 files, shipped with desk-api)
    audioai/
      com.atex.onecms.audioAI.Configuration.json5
    ayrshare/
      com.atex.onecms.ayrshare.Configuration.json5
    camel-routes/
      dam.camelroutes.conf.json5
    cfg/
      com.atex.onecms.beanmapper.Configuration.json5
      dam.thirdpartiescfg.conf.json5
    chains-list/
      dam.chains.list.conf.json5
      chain.configuration.*.conf.json5    (5 chain configs)
    desk-configuration/
      atex.configuration.desk.*.json5     (~17 desk UI configs)
      form/
        atex.configuration.desk.*.json5   (7 form configs)
      permission/
        atex.configuration.desk.disable-*.json5  (~26 permission rules)
      result-set/
        atex.configuration.desk.*.json5   (7 result-set configs)
    desk-system-configuration/
      atex.configuration.desk-system-configuration.json5
    distributionlist/
      dam.distributionlist.conf.json5
    editor-configuration/
      atex.configuration.act.*.json5       (5 ACT editor configs)
      com.atex.onecms.texteditor.EditorConfigurationContent.json5
      permission/
        atex.configuration.act.disable-publish-rules.json5
    export/
      dam.export.conf.json5
    form-templates/
      dam*Template*.json5                  (38 form definitions from desk)
      atex.onecms.Template-*.json5         (7 from content/common)
    gallery-configurations/
      dam.galleryconfigurations.conf.json5
    image-service/
      com.atex.onecms.image.ImageFormatConfigurationContent.json5
    importer/
      com.atex.onecms.dam.beanImporter.Configuration.json5
    index/
      desk.content.indexing.indexComposer.json5
      desk.content.indexing.liveblog.indexComposer.json5
    localization/
      desk/
        atex.onecms.desk.locale.{lang}.json5  (10 languages)
      locale/
        atex.onecms.custom.locale.{lang}.json5 (10 languages)
    mail-service/
      com.atex.onecms.mail-service.Configuration.json5
    markas/
      dam.colorcoding.conf.json5
      dam.iconcoding.conf.json5
      dam.multiiconcoding.conf.json5
    partition/
      dam.partitions.conf.json5
    permissions/
      com.atex.onecms.permission.PermissionConfigurationContent.json5
    processors-list/
      dam.processors.list.conf.json5
      processor.configuration.*.conf.json5 (7 processor configs)
    publish/
      com.atex.onecms.dam.beanPublisher.Configuration.json5
      com.atex.onecms.ace.publish.Configuration.json5
      publish.page.configuration.json5
    remotes/
      com.atex.onecms.dam.remotes.Configuration.json5
    reset-password/
      com.atex.onecms.reset-password.Configuration.json5
    restrictContent/
      atex.configuration.desk.restrictContent.json5
    routes-list/
      dam.routes.list.conf.json5
      route.configuration.*.conf.json5    (6 route configs)
    sendcontent/
      dam.sendcontent.conf.json5
    settings/
      com.atex.onecms.general.default.Configuration.json5
    smartupdates/
      dam.smartupdates.conf.json5
    tag-manager/
      atex.configuration.tag-manager-configuration.json5
    widgets-configuration/
      configuration.widget.*.json5         (9 widget configs)
    workflow/
      dam.wfstatuslist.d.json5            ← empty default: {"statuses": []}
      dam.webstatuslist.d.json5           ← empty default: {"statuses": []}
  flavor/                                 ← flavor-specific overrides (only files that differ)
    hseries/
      .gitkeep
    pseries/                              ← 14 P-Series override files
      desk-configuration/
        atex.configuration.desk.copyfit-configurations.json5
        atex.configuration.desk.menus.json5
        atex.configuration.desk.toolbars.json5
        atex.configuration.desk.widgets.json5
        form/
          atex.configuration.desk.search-forms.json5
          atex.configuration.desk.smart-folder-filters.json5
        permission/
          atex.configuration.desk.disable-queues-panel-rules.json5
        result-set/
          atex.configuration.desk.thumbnail-list.json5
      editor-configuration/
        atex.configuration.act.toolbar-configurations.json5
        permission/
          atex.configuration.act.disable-publish-rules.json5
      form-templates/
        atex.onecms.Template-atex.onecms.article.print.json5
        atex.onecms.Template-atex.onecms.article.print.preview.json5
        atex.onecms.Template-atex.onecms.article.production.dam.preview.json5
      widgets-configuration/
        configuration.widget.activePreview.json5
  project/                                ← project overrides (empty by default)
    .gitkeep
```

External ID = filename without `.json5` extension. Subdirectories are for organization only.

**201 config files total**: 187 product defaults (from gong/desk + gong/content/common) + 14 P-Series flavor overrides (from adm-starterkit).

### Config Sync Scripts

Python scripts in `scripts/` automate re-syncing config files from the original source codebases.

**`scripts/config-mapping.txt`** — Pipe-delimited mapping file (201 entries) tracing every config file back to its source. Format:
```
type|source_base|source_path|dest_tier|dest_subdir|external_id
```

Columns:
- `type`: `xml-cdata` (extract JSON from XML component CDATA), `json-ref` (XML references separate JSON file), `json` (direct copy), `manual` (no source)
- `source_base`: `gong-desk`, `gong-common`, `adm-starterkit-pseries`, `none`
- `source_path`: relative path within source_base
- `dest_tier`: `defaults` or `flavor/pseries`
- `dest_subdir`: subdirectory under the tier
- `external_id`: output filename without `.json5`

To add a new config: append a line to this file and run the sync script.

**`scripts/config-sync.py`** — Reads the mapping and extracts/copies configs:
```bash
python scripts/config-sync.py              # Dry run (show what would change)
python scripts/config-sync.py --apply      # Actually write files
python scripts/config-sync.py --check      # Exit 1 if out of sync (CI-friendly)
python scripts/config-sync.py --verbose    # Show all entries including unchanged
python scripts/config-sync.py --filter X   # Only process entries matching X
```

Handles three XML component patterns (`data/value`, `contentData/contentData`, `model/pojo`), file-reference resolution (locale `.json5` files, template JSON via `file=` attributes), and direct JSON copies. Idempotent — re-running after `--apply` shows `201 unchanged, 0 errors`.

Source base directories (relative to project parent):
| Shorthand | Path |
|---|---|
| `gong-desk` | `../gong/desk/content/src/main/content` |
| `gong-common` | `../gong/content/common/src/main/content` |
| `adm-starterkit` | `../adm-starterkit/content/custom-content/src/main/content` |
| `adm-starterkit-pseries` | `../adm-starterkit/content/custom-pseries-content/src/main/content` |

### Configuration Admin REST API

`ConfigurationController` at `/admin/config` (auth required via `AuthFilter`):

| Method | Path | Behaviour |
|--------|------|-----------|
| GET | `/admin/config` | List all known config IDs with source tier (product/project/plugin/live) |
| GET | `/admin/config/{externalId}` | Get effective config JSON (merged precedence) |
| GET | `/admin/config/{externalId}/sources` | Get all tiers separately (for diff/audit) |
| PUT | `/admin/config/{externalId}` | Save live override to DB |
| DELETE | `/admin/config/{externalId}` | Delete live override (reverts to resource defaults) |
| GET | `/admin/config/export` | Export all live (DB) overrides as a JSON bundle |
| GET | `/admin/config/patch` | Unified diff of DB overrides against resource defaults (text/plain) |

### Configuration (`application.properties`)

```properties
desk.config.enabled=true
# Flavor overlay: hseries or pseries (empty = no flavor overlay)
desk.config.flavor=
```

### New Files

| File | Description |
|---|---|
| `config/ConfigProperties.java` | `@ConfigurationProperties(prefix = "desk.config")`: `enabled` (boolean), `flavor` (String) |
| `config/ConfigurationService.java` | 5-tier config lookup, classpath + plugin scanning, cache, synthetic ContentResult |
| `config/Json5Reader.java` | JSON5 → JSON converter (strip comments, trailing commas, Gson lenient) |
| `controller/ConfigurationController.java` | Admin REST API for config management |
| `resources/config/defaults/**/*.json5` | 187 product default config files (migrated from gong/desk + gong/content/common XML) |
| `resources/config/flavor/pseries/**/*.json5` | 14 P-Series flavor override files (from adm-starterkit) |
| `resources/config/flavor/hseries/.gitkeep` | H-Series flavor directory (empty — H-Series is the default) |
| `resources/config/project/.gitkeep` | Empty project override directory |
| `scripts/config-mapping.txt` | Source mapping file (201 entries): source path → dest path + extraction type |
| `scripts/config-sync.py` | Python sync script: reads mapping, extracts JSON from XML, copies to config dirs |

### Modified Files

| File | Change |
|---|---|
| `LocalContentManager.java` | Add `ConfigurationService` injection; intercept `resolve(externalId)` and `get()` for synthetic config IDs |
| `ContentController.java` | Add `ConfigurationService` injection; fallback in `getContentByExternalId()` |
| `DemoApplication.java` | Add `ConfigProperties.class` to `@EnableConfigurationProperties` |
| `AuthConfig.java` | Add `/admin/*` to auth filter URL patterns |
| `application.properties` | Add `desk.config.enabled=true`, `desk.config.flavor=` |

## Increment 7.1 — SpringDoc OpenAPI Generation

Replaces the hand-authored `static/openapi.yaml` (1830 lines) and manually-configured Swagger UI WebJar with SpringDoc-generated OpenAPI from annotations. The Polopoly Swagger 2 annotations (`@ApiOperation`, `@ApiModelProperty`) are mapped to OpenAPI 3 equivalents (`@Operation`, `@Schema`).

### Architecture

```
SpringDoc OpenAPI 3.0.1
  ├─ Auto-generates /v3/api-docs JSON from controller annotations
  ├─ Bundles Swagger UI at /swagger-ui.html (no manual WebJar config)
  └─ spring-boot-jackson2 bridge (swagger-core needs Jackson 2)

Annotation mapping (Polopoly → desk-api):
  @Api(tags)                → @Tag(name) on class
  @ApiOperation(value)      → @Operation(summary)
  @ApiParam("desc")         → @Parameter(description)
  @ApiResponse(code, ...)   → @ApiResponse(responseCode, ...)
  @ApiModel(description)    → @Schema(description) on class
  @ApiModelProperty("desc") → @Schema(description) on field
  @ApiImplicitParam(X-Auth) → Global security scheme in OpenApiConfig
```

### Dependencies

**Removed:**
- `org.webjars:swagger-ui:5.21.0`
- `org.webjars:webjars-locator-core:0.59`

**Added:**
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1` — auto-generates spec, bundles Swagger UI
- `org.springframework.boot:spring-boot-jackson2` — bridge for swagger-core's Jackson 2 dependency

### Configuration (`application.properties`)

```properties
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.persist-authorization=true
springdoc.swagger-ui.deep-linking=true
springdoc.swagger-ui.operations-sorter=alpha
springdoc.swagger-ui.tags-sorter=alpha
springdoc.packages-to-scan=com.atex.desk.api.controller,com.atex.onecms.app.dam.ws
```

No auth config changes needed — `/v3/*` and `/swagger-ui/**` are outside the AuthFilter URL patterns.

### OpenApiConfig

`com.atex.desk.api.config.OpenApiConfig` — `@Configuration` providing an `OpenAPI` bean:
- **Info**: title "Desk API", version "0.0.1-SNAPSHOT"
- **Server**: `http://localhost:8081`
- **Security scheme**: `authToken` — apiKey in header `X-Auth-Token`
- **Global security requirement**: `authToken` applied to all endpoints by default
- **Tag definitions** (7 tags with descriptions):

| Tag | Description |
|-----|-------------|
| Authentication | Acquire, validate and manage authentication tokens |
| Content | Create, get, update and delete content |
| Workspace | Create, get, update and delete content in workspaces |
| Principals | User and group management |
| Configuration | Resource-based configuration admin |
| Dashboard | System status and endpoint listing |
| DAM | DAM content operations, search, publishing and configuration |

### DTO Annotations

All DTOs annotated with `@Schema` (class-level description + field-level descriptions):

| DTO | Schema description |
|-----|-------------------|
| `ContentResultDto` | The content model when fetching a content |
| `ContentWriteDto` | The content model used for create and update operations |
| `AspectDto` | An aspect, the data field contains a _type field with the aspect type |
| `MetaDto` | Metadata information about a content |
| `ContentHistoryDto` | Content version history |
| `ContentVersionInfoDto` | Content version info |
| `ErrorResponseDto` | Error response with status code and message |
| `CredentialsDto` | Login credentials |
| `TokenResponseDto` | Authentication token response |
| `PrincipalDto` | User principal info |
| `WorkspaceInfoDto` | Workspace information |

### Controller Annotations

| Controller | Tag | Annotations |
|------------|-----|-------------|
| `SecurityController` | Authentication | `@Operation` + `@ApiResponse` on both methods; `@SecurityRequirements` (no auth) on login |
| `ContentController` | Content | `@Operation` + `@ApiResponse` + `@Parameter` on all 9 methods |
| `WorkspaceController` | Workspace | `@Operation` + `@ApiResponse` + `@Parameter` on all 7 methods |
| `PrincipalsController` | Principals | `@Operation` on all 4 methods |
| `ConfigurationController` | Configuration | `@Operation` + `@Parameter` on all 7 methods |
| `DashboardController` | Dashboard | `@Operation` + `@SecurityRequirements` (no auth) on both methods |
| `DamDataResource` | DAM | `@Tag` on class; `@Hidden` on 5 stub endpoints |

**Unauthenticated endpoints** use `@SecurityRequirements` (standalone annotation, not `@Operation(security = {})` which SpringDoc 3.0.1 ignores) to override the global security requirement.

**DamDataResource hidden endpoints** (501 NOT_IMPLEMENTED stubs):
- `createPage`, `sendContent`, `assignContent`, `collectionPreview`, `mergeMultiplePdf`

**Note**: DamDataResource endpoints only appear in the spec when all its runtime dependencies are satisfied (PolicyCMServer, CmClient, DamPublisherFactory, etc.).

### WebConfig Changes

- **Removed** `addResourceHandlers` override (SpringDoc serves its own Swagger UI)
- **Updated** swagger-ui redirect: `/swagger-ui` → `/swagger-ui.html` (SpringDoc's canonical entry point)
- **Kept** `/dashboard` → `/dashboard.html` redirect

### DashboardController Fix

Added `@Qualifier("requestMappingHandlerMapping")` to disambiguate between Spring MVC's `requestMappingHandlerMapping` and actuator's `controllerEndpointHandlerMapping` (exposed by SpringDoc's additional web MVC configuration).

### Dashboard Links

Updated `static/dashboard.html` header links:
- Swagger UI: `/swagger-ui/index.html` → `/swagger-ui.html`
- OpenAPI Spec: `/openapi.yaml` → `/v3/api-docs`

### New Files

| File | Description |
|------|-------------|
| `config/OpenApiConfig.java` | `OpenAPI` bean with security scheme, tags, API info |

### Deleted Files

| File | Reason |
|------|--------|
| `static/openapi.yaml` | Replaced by auto-generation from annotations |
| `static/swagger-ui/index.html` | Replaced by SpringDoc's built-in Swagger UI |

### Modified Files

| File | Change |
|------|--------|
| `build.gradle` | Replace webjar deps with `springdoc-openapi-starter-webmvc-ui` + `spring-boot-jackson2` |
| `application.properties` | Add SpringDoc config properties |
| `config/WebConfig.java` | Remove resource handler, update redirect to `/swagger-ui.html` |
| `controller/SecurityController.java` | `@Tag`, `@Operation`, `@ApiResponse`, `@SecurityRequirements` |
| `controller/ContentController.java` | `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter` |
| `controller/WorkspaceController.java` | `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter` |
| `controller/PrincipalsController.java` | `@Tag`, `@Operation` |
| `controller/ConfigurationController.java` | `@Tag`, `@Operation`, `@Parameter` |
| `controller/DashboardController.java` | `@Tag`, `@Operation`, `@SecurityRequirements`, `@Qualifier` |
| `DamDataResource.java` | `@Tag(name = "DAM")`, `@Hidden` on 5 stubs |
| `dto/*.java` (11 files) | `@Schema` annotations on classes and fields |
| `static/dashboard.html` | Update swagger/api-docs links |
