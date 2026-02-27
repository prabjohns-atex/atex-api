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

### Dependency order (bottom → top)
```
polopoly → gong → adm-starterkit
                → adm-content-service
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
