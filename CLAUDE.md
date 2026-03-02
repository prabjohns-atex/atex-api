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
| GET | `/security/oauth/url` | Get Cognito hosted UI login URL (query param: `callbackUrl`) |
| POST | `/security/oauth/callback` | Exchange OAuth code/token for desk-api JWT |

### User Storage
- `registeredusers` table: loginname (PK), passwordhash, regtime, isldapuser, isremoteuser, remoteserviceid, active (see Increment 9 for full schema)
- Entity: `AppUser`, Repository: `AppUserRepository`
- Default seed: `sysadmin` / `sysadmin` (OLDSHA hash)
- Auth dispatch: local → `PasswordService`, LDAP → `LdapAuthService`, remote → `CognitoAuthService`

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
- `canRestrictContent(ContentId, Subject)` / `canUnrestrictContent(ContentId, Subject)` — permission checks
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

### Index Composer Pipeline

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
| `SolrService` | Added `indexBatch()` and `deleteBatch()`, refactored `index()`/`delete()` to delegate |
| `LocalContentManager` | Removed `ContentIndexer` field, constructor param, `indexAsync()` method and 3 call sites |
| `schema.sql` | Added `indexer_state` table DDL |
| `data.sql` | Seeded `solr` LIVE indexer row |
| `application.properties` | Added `desk.indexing.*` properties |
| `config/OpenApiConfig.java` | Added `"Reindex"` tag |

### Design Notes

- **Changelist upsert safety**: upserts by contentId (delete old row, insert new with higher `id`). Cursor always advances forward. Solr upserts are idempotent.
- **One reindex at a time**: worker picks oldest REQUESTED/RUNNING job. Additional requests queue.
- **Live indexing during reindex**: both threads run concurrently. Overlapping writes are harmless.
- **Failure recovery**: on Solr failure, cursors stay put — next tick retries. On instance crash, lease expires and another instance takes over.
- **DELETE semantics**: first DELETE on active job pauses it. Second DELETE on inactive job removes the row.

## Increment 16 — File Service Endpoints & Activity/Locking System

Exposes the existing filesystem-backed `FileService` (from Increment 3b) via REST endpoints, and implements the activity/content-locking system used by the editorial UI.

### Architecture

```
Desk UI / mytype-new
  ├─ /file/*          → FileController
  │    └─ FileService (LocalFileService, from Increment 3b)
  │         └─ Filesystem storage under ${desk.file-service.base-dir}
  │
  └─ /activities/*    → ActivityController
       └─ ActivityServiceSecured (@Component, auth wrapper)
            └─ ActivityService (@Service)
                 └─ ContentManager (stores activities as content objects)
                      └─ External ID alias: "activity:{contentId}"
```

### File Service REST API

`FileController` at `/file`:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/file/info/{space}/{host}/{path}` | File metadata as JSON (FileInfoDTO) |
| GET | `/file/metadata?uri={uri}` | File metadata by URI query param |
| POST | `/file/{space}` | Upload file (host defaults to caller login) |
| POST | `/file/{space}/{host}/{path}` | Upload file with explicit path |
| GET | `/file/{space}/{host}/{path}` | Download binary with streaming |
| DELETE | `/file/{space}/{host}/{path}` | Delete file |

Upload returns `201 Created` with `Location` header and `X-Original-Path` header. Download returns streaming binary with `Content-Type`, `Content-Length`, `Last-Modified`, and cache headers.

**`FileInfoDTO`** — Response DTO with uppercase `URI` field name (matches original Polopoly API that clients expect). Fields: `URI`, `length`, `creationTime`, `modifiedTime`, `accessTime`, `mimeType`, `originalPath`.

### Activity/Locking System

Content locking in the editorial UI uses activities to track which users have content open in which applications. When a user opens content for editing, the UI registers an activity; when they close it, the activity is removed. Other users see who has content locked.

#### Data Model

Activities are stored as content objects in the ContentManager (no dedicated DB table):

```
ActivityInfo (@AspectDefinition("atex.activity.Activities"))
  └─ users: Map<String, UserActivities>     (keyed by user login name)
       └─ applications: Map<String, ApplicationInfo>  (keyed by application ID)
            ├─ timestamp: long              (server-generated)
            ├─ activity: String             (e.g., "editing", "viewing")
            └─ params: Map<String, String>  (arbitrary metadata)
```

External ID pattern: `activity:{contentId}` — each content object has at most one ActivityInfo.

#### Classes

| Class | Package | Description |
|-------|---------|-------------|
| `Activity` | `c.a.o.ws.activity` | Input bean: `activity` + `params` |
| `ApplicationInfo` | `c.a.o.ws.activity` | Per-app entry: `timestamp`, `activity`, `params` |
| `UserActivities` | `c.a.o.ws.activity` | Per-user container keyed by application ID |
| `ActivityInfo` | `c.a.o.ws.activity` | Top-level `@AspectDefinition("atex.activity.Activities")` with `users` map. `isEmpty()` checks all nested maps |
| `ActivityException` | `c.a.o.ws.activity` | Exception with `Status` field for retry logic |
| `ActivityService` | `c.a.o.ws.activity` | `@Service`: CRUD via ContentManager with retry (10 tries, 10ms) for conflict resolution |
| `ActivityServiceSecured` | `c.a.o.ws.activity` | `@Component`: auth wrapper — write requires `subject.principalId == userName`, delete allows anyone (unlock) |
| `ActivityController` | `c.a.d.a.controller` | `@RestController` at `/activities` |

#### Activity REST API

`ActivityController` at `/activities`:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/{activityId}` | Get activity info for a content ID |
| PUT | `/{activityId}/{userId}/{applicationId}` | Add/update activity (server-generated timestamp) |
| DELETE | `/{activityId}/{userId}/{applicationId}` | Remove activity (unlock) |

Uses Gson for JSON serialization (matching DamDataResource pattern).

#### DamDataResource Unlock Wiring

The `unlock` endpoint in `DamDataResource` (called via `sendBeacon` on page unload) was wired to use `ActivityServiceSecured.delete()`. Added `extractSubjectFromBody()` helper for sendBeacon unlock where the auth token is in the JSON body instead of the header.

### New Files (8)

| File | Description |
|------|-------------|
| `content/files/FileInfoDTO.java` | File metadata DTO with uppercase URI field |
| `controller/FileController.java` | `/file` REST controller (6 endpoints) |
| `ws/activity/Activity.java` | Activity input bean |
| `ws/activity/ApplicationInfo.java` | Per-app activity entry |
| `ws/activity/UserActivities.java` | Per-user activity container |
| `ws/activity/ActivityInfo.java` | Top-level activity content bean |
| `ws/activity/ActivityService.java` | Activity CRUD service with retry logic |
| `controller/ActivityController.java` | `/activities` REST controller (3 endpoints) |

### Modified Files (5)

| File | Change |
|------|--------|
| `ws/activity/ActivityException.java` | Replaced empty stub — added `Status` field and constructors |
| `ws/activity/ActivityServiceSecured.java` | Replaced empty stub — full auth wrapper with `assertWritePermission()` and `assertLoggedIn()` |
| `app/dam/ws/DamDataResource.java` | Added `ActivityServiceSecured` as 8th constructor param, wired `unlock` endpoint, added `extractSubjectFromBody()` |
| `auth/AuthConfig.java` | Added `/file/*` and `/activities/*` to auth filter URL patterns |
| `config/OpenApiConfig.java` | Added `"Files"` and `"Activities"` tags |

### Design Notes

- **No dedicated DB table**: Activities are stored as regular content objects via ContentManager, using external ID alias `activity:{contentId}`. This matches the original Polopoly implementation.
- **Retry logic**: `ActivityService.retry()` handles `Status.CONFLICT` (from concurrent updates or alias creation race) with 10 attempts at 10ms intervals.
- **Auth model**: Write (lock) requires matching user identity. Delete (unlock) allows any authenticated user — the UI needs to let admins force-unlock content.
- **Gson serialization**: ActivityController uses Gson (not Jackson) for JSON, matching the DamDataResource pattern and avoiding Jackson 3 annotation requirements on the activity beans.
- **AuthConfig patterns**: `/content/*`, `/dam/*`, `/principals/*`, `/admin/*`, `/search/*`, `/changes/*`, `/layout/*`, `/file/*`, `/activities/*`, `/preview/*`

## Increment 17 — Preview Endpoint

Adds `POST /preview/contentid/{id}` and `POST /preview/externalid/{id}` endpoints for generating web preview URLs. Used by the mytype-new frontend's web preview workflow to get a preview URL after staging edited content in a workspace.

### Architecture

```
POST /preview/contentid/{id}?channel=web&workspace=webpreview-{token}
  └─ PreviewController
       └─ PreviewService (@Service)
            ├─ DamPublisherFactory.createContext(contentId, caller) → RemoteConfigBean
            ├─ Read previewAdapterClassName + previewConfig from matched backend
            ├─ Dispatch by adapter class name:
            │    ├─ "AceWebPreviewAdapter" → acePreview()
            │    │    ├─ Push workspace drafts to remote via HttpDamUtils.callDataApiWs() PUT
            │    │    ├─ Call remote preview API POST {acePreviewBaseUrl}/preview/contentid/{id}
            │    │    └─ Return {previewUrl, contentId}
            │    └─ "WebPreviewAdapter" → webPreview()
            │         └─ Construct URL from {remoteApiUrl}{previewDispatcherUrl}{contentId}
            └─ Return JSON response
```

### Preview API Types (preserving original package)

**Package `com.atex.onecms.preview`**:
- `PreviewAdapter<CONFIG>` — interface: `preview(ContentVersionId, workspace, body, PreviewContext)`
- `PreviewContext<CONFIG>` — interface: `getChannelName()`, `getConfiguration()`, `getContentManager()`, `getUserName()`, `getSubject()`
- `PreviewContextImpl<CONFIG>` — simple implementation holding all fields

### ACE Preview Adapter

**`AceWebPreviewServiceChannelConfiguration`** — config POJO: `acePreviewBaseUrl`, `acePreviewVariant`, `acePreviewStatusList`. Parsed from the remotes configuration `previewConfig` object via Gson.

**`AceWebPreviewAdapter`** — implements `PreviewAdapter<AceWebPreviewServiceChannelConfiguration>`:
1. Resolves remote backend config via `DamPublisherFactory`
2. Gets auth token for remote backend via `HttpDamUtils.getAuthToken()`
3. Pushes all workspace drafts to remote via `PUT {remoteApiUrl}/content/workspace/{ws}/contentid/{id}`
4. Calls `POST {acePreviewBaseUrl}/preview/contentid/{id}?channel={ch}&workspace={ws}`
5. Parses response for `previewUrl` or `url` field
6. Falls back to `{acePreviewBaseUrl}/preview/{contentId}` on failure

### PreviewService

`com.atex.desk.api.preview.PreviewService` — `@Service`:
- Constructor: `ContentManager`, `@Nullable DamPublisherFactory`, `WorkspaceStorage`
- Dispatches by `previewAdapterClassName` string match:
  - Contains `"AceWebPreviewAdapter"` → creates `AceWebPreviewAdapter`, parses config via Gson
  - Contains `"WebPreviewAdapter"` → reads `previewDispatcherUrl` from previewConfig, constructs URL
  - Null/empty → falls back to web preview
  - Unknown → returns 501 error

### PreviewController

`com.atex.desk.api.controller.PreviewController` at `/preview`:

| Method | Path | Query Params | Description |
|--------|------|-------------|-------------|
| POST | `/contentid/{contentId}` | `channel` (required), `workspace` (optional) | Preview by content ID |
| POST | `/externalid/{id}` | `channel` (required), `workspace` (optional) | Preview by external ID |

Both endpoints: auth via `AuthFilter.USER_ATTRIBUTE`, resolve content version (workspace-aware), parse JSON body via Gson, delegate to `PreviewService`, return `application/json`.

### New Files (7)

| File | Description |
|------|-------------|
| `com/atex/onecms/preview/PreviewAdapter.java` | Generic adapter interface |
| `com/atex/onecms/preview/PreviewContext.java` | Context interface for adapters |
| `com/atex/onecms/preview/PreviewContextImpl.java` | Simple context implementation |
| `com/atex/onecms/ace/AceWebPreviewServiceChannelConfiguration.java` | ACE preview config POJO (replaced stub) |
| `com/atex/onecms/ace/AceWebPreviewAdapter.java` | ACE preview adapter (replaced stub) |
| `com/atex/desk/api/preview/PreviewService.java` | Preview orchestration service |
| `com/atex/desk/api/controller/PreviewController.java` | `/preview` REST controller |

### Modified Files (2)

| File | Change |
|------|--------|
| `auth/AuthConfig.java` | Added `/preview/*` to auth filter URL patterns |
| `config/OpenApiConfig.java` | Added `"Preview"` tag |

## Increment 18 — Compatibility Test Suite & Fix Plan

### Compatibility Test Script

`scripts/compat-test.py` — Python test suite exercising core API endpoints on both desk-api (localhost:8081) and reference OneCMS (localhost:38084/onecms), comparing responses.

**Usage:**
```bash
python scripts/compat-test.py                    # Compare both servers
python scripts/compat-test.py --desk-only        # Single-server CRUD cycle
python scripts/compat-test.py --ref-only         # Single-server CRUD cycle
python scripts/compat-test.py --verbose           # Show full response bodies
python scripts/compat-test.py --test <substring>  # Filter tests
```

**16 comparison tests:** login, validate token, create article, get versioned, get unversioned redirect, update, history, external ID resolve, config content, search, DAM content, DAM configuration, principals /me, workspace CRUD, changes feed, delete.

**Key discovery:** Reference server requires `If-Match: "quoted-etag"` header for PUT and DELETE operations. Returns 400 without it.

### Compatibility Issues Found (desk-api vs reference OneCMS)

#### Issue 1: Redirect Status Code 302 vs 303

**Reference:** Returns `303 See Other` with JSON body for unversioned content and external ID resolution:
```json
{"statusCode":"30300","message":"Symbolic version resolved","location":"/onecms/content/contentid/..."}
```

**desk-api:** Returns `302 Found` with `Location` header only (no JSON body).

**Fix:** Change `HttpStatus.FOUND` (302) to `HttpStatus.SEE_OTHER` (303) in `ContentController` for all redirect endpoints (5 locations). Add JSON body to redirect responses matching reference format.

**Files:** `ContentController.java` (lines 101, 198, 222, 252, 280)

#### Issue 2: If-Match Header Not Enforced

**Reference:** Returns `400` with `{"statusCode":40000,"message":"Tried to update content with an If-Match header not corresponding to this content."}` when If-Match is missing on PUT. Returns `{"statusCode":40000,"message":"Deleting requires an If-Match header"}` when missing on DELETE. ETag must be quoted: `If-Match: "onecms:id:version"`.

**desk-api:** Accepts `If-Match` as optional parameter but **ignores it entirely** — never validates, never rejects missing values. No optimistic locking.

**Fix:**
1. `ContentController.updateContent()` — make `If-Match` required, validate against current version, return 400 if missing, 409 if stale
2. `ContentController.deleteContent()` — same: require `If-Match`, validate, return 400 if missing
3. `ContentService` — add version-aware resolve method to check If-Match against `p.latest` view

**Files:** `ContentController.java`, `ContentService.java`

#### Issue 3: Error Response Format Mismatch

**Reference** error format:
```json
{"extraInfo":{},"statusCode":40400,"message":"NOT_FOUND"}
```

**desk-api** error format:
```json
{"detailCode":"NOT_FOUND","name":"Content not found"}
```

**Fix:** Change `ErrorResponseDto` to match reference format: rename `detailCode`→`statusCode` (as integer), `name`→`message`, add `extraInfo` object field. Update all error construction sites across controllers.

**Files:** `ErrorResponseDto.java`, all controllers that construct `ErrorResponseDto`

#### Issue 4: Missing Default Aspects on Content Creation

**Reference** creates 6 aspects for a new article:
1. `contentData` — main bean with enriched fields
2. `atex.WFContentStatus` — default workflow status (e.g., "PRIVATE")
3. `p.InsertionInfo` — security parent, associated sites
4. `art.Inbox` — `{showInInbox: true}` **(legacy, ignored)**
5. `atex.WebContentStatus` — default web status (e.g., "Draft")
6. `atex.Metadata` — partition dimension, taxonomy IDs

**desk-api** creates only `contentData` (plus `p.InsertionInfo` and `p.Metadata` from SecParentPreStoreHook if partition is configured). Missing: `atex.WFContentStatus`, `atex.WebContentStatus`.

**Root cause:** `SetStatusPreStoreHook` only acts when an explicit `SetStatusOperation` is in the request operations list. It should also set a **default status** on new content when no operation is present.

**Fix:**
1. `SetStatusPreStoreHook` — on create (existing content is null), if no `SetStatusOperation` present, auto-create `WFContentStatusAspectBean` and `WebContentStatusAspectBean` with the first status from the workflow status list config (`dam.wfstatuslist.d` / `dam.webstatuslist.d`)
2. `OneContentPreStore` — ensure `atex.Metadata` is always created on new content with partition dimension from insertion info

**Note:** `art.Inbox` is legacy and not needed — skip `InboxPreStoreHook`.

**Files:** `SetStatusPreStoreHook.java`, `OneContentPreStore.java`, `BuiltInHookRegistrar.java`

#### Issue 5: ContentData Bean Field Enrichment

**Reference** contentData for a new article includes many computed/default fields:
```
objectType: "article", inputTemplate: "p.NosqlArticle", contentType: "parentDocument",
priority: 3, related: [], creationdate: "epoch", words: N, chars: N,
propertyBag: {custom_type: "java.util.HashMap", custom: {_type: "java.util.HashMap"}},
wordCount: N, depth: N.NN, depthWithUm: "N.NN cm", commonContentIds: [],
showTopMediaOnWeb: true, publishingTime: 0, publishingUpdateTime: 0,
premiumContent: false, rating: 0, publicationDate: epoch, hold: false,
printFirst: false, initialisedFromPrint: false, premiumType: "inherit",
pushNotification: false, noIndex: false, showAuthorOnWeb: false, markForArchive: false
```

**desk-api** only returns fields that were explicitly set in the request (headline, lead, body, _type) plus fields set by hooks (creationdate, words, chars, author).

**Root cause:** When content is created via `ContentManager` with `Object.class`, the data is a `Map<String, Object>` — the `OneArticleBean` constructor (which sets defaults like `priority=3`, `objectType="article"`) is never called.

**Fix:** In `OneContentPreStore`, after setting creationdate/author/name, detect the `_type` field and apply bean-class defaults. For `atex.onecms.article`: set `objectType`, `inputTemplate`, `contentType`, `priority`, `propertyBag`, and boolean defaults. This could use a type→defaults registry or Gson round-trip through the bean class.

**Files:** `OneContentPreStore.java`, possibly `OneArticleBean.java` (add static defaults method)

#### Issue 6: Principals Response Format Mismatch

**Reference** `GET /principals/users/me`:
```json
{
  "loginName": "sysadmin", "firstName": "System", "lastName": "Administrator",
  "id": 98, "userId": "98", "cmUser": true, "ldapUser": false, "remoteUser": false,
  "groups": [{"type":"group","id":"group:7","name":"...","principalId":"group:7"}],
  "userData": {"relations": {}},
  "homeDepartmentId": "policy:2.0",
  "workingSites": ["policy:2.248","policy:2.220"]
}
```

**Reference** `GET /principals/users` (list):
```json
[{"ldapUser":false,"remoteUser":false,"cmUser":true,"type":"user","id":"1","name":"webmaster","principalId":"1"}]
```

**desk-api** `GET /principals/users/me`:
```json
{"userId":"sysadmin","username":"sysadmin","createdAt":"..."}
```

**desk-api** `GET /principals/users`:
```json
[{"userId":"sysadmin","username":"sysadmin","createdAt":"..."}]
```

**Mismatches:**
- `/me` missing: `loginName`, `firstName`, `lastName`, `id` (numeric), `cmUser`, `ldapUser`, `remoteUser`, `groups` (as objects), `userData`, `homeDepartmentId`, `workingSites`
- `/users` list uses wrong field names: should be `type`, `id` (numeric), `name`, `principalId`, `cmUser`, `ldapUser`, `remoteUser`
- `PrincipalDto` fields don't match either response format

**Fix:**
1. Create `UserMeDto` for the `/me` response with all fields from reference
2. Rework `PrincipalDto` to match list format: `type`, `id`, `name`, `principalId`, `cmUser`, `ldapUser`, `remoteUser`
3. Expose `isLdapUser` and `isRemoteUser` from `AppUser` entity (already in DB)
4. `groups` for `/me`: look up group memberships and return as objects `{type, id, name, principalId}`
5. `workingSites`, `homeDepartmentId` — need new DB columns or content-based storage (defer or stub with empty values)

**Files:** new `UserMeDto.java`, `PrincipalDto.java` (rework), `PrincipalsController.java`

#### Issue 7: Changes Feed Empty Response

**Reference** returns minimal `{"runTime": epoch}` when no events match.

**desk-api** returns full structure `{"runTime":..., "maxCommitId":0, "numFound":0, "size":0, "events":[]}`.

**Impact:** Low — the extra fields with 0/empty values shouldn't break clients. But for strict compatibility, `ChangeFeedDto` should use `@JsonInclude(JsonInclude.Include.NON_DEFAULT)` or similar to omit zero-valued fields.

**Files:** `ChangeFeedDto.java`

### Priority Order

| Priority | Issue | Impact | Effort |
|----------|-------|--------|--------|
| **P1** | Issue 4: Missing default aspects | Clients crash expecting WFContentStatus | Medium |
| **P1** | Issue 2: If-Match enforcement | Data integrity — concurrent edits not detected | Medium |
| **P1** | Issue 3: Error response format | All clients parsing errors break | Low |
| **P2** | Issue 6: Principals format | mytype-new UI expects reference format | Medium |
| **P2** | Issue 5: ContentData defaults | UI displays empty fields, search indexing misses data | Medium |
| **P3** | Issue 1: 302→303 redirect | Most HTTP clients follow both — low breakage | Low |
| **P3** | Issue 7: Changes empty response | Extra fields in response shouldn't break clients | Low |

## Increment 18a — P1 Compatibility Fixes

Implements the three P1 (critical) fixes identified by the compatibility test suite (`scripts/compat-test.py`).

### Fix 1: Error Response Format

**Problem:** desk-api returned `{"detailCode":"NOT_FOUND","name":"Content not found"}` but reference OneCMS returns `{"extraInfo":{},"statusCode":40400,"message":"NOT_FOUND"}`.

**Changes:**
- `ErrorResponseDto` — Reworked fields: `statusCode` (int, HTTP status × 100), `message` (String), `extraInfo` (Map, always `{}`). Added `HttpStatus` convenience constructor.
- `ContentApiExceptionHandler` — Updated to emit `extraInfo`, `statusCode`, `message` matching reference format.
- All 39 `new ErrorResponseDto(...)` call sites across 6 controllers updated to use `HttpStatus` enum constructor.

**Files modified:** `ErrorResponseDto.java`, `ContentApiExceptionHandler.java`, `ContentController.java`, `SecurityController.java`, `WorkspaceController.java`, `PrincipalsController.java`, `ConfigurationController.java`, `ChangesController.java`

### Fix 2: If-Match Enforcement + 303 Redirects

**Problem:** Reference requires quoted `If-Match: "onecms:id:version"` header for PUT and DELETE, returns 400 without it. desk-api accepted but ignored If-Match. Also, reference uses 303 (not 302) for redirects with a JSON body.

**Changes:**
- `ContentService.getCurrentVersion()` — New method resolving current `p.latest` versioned ID for ETag validation.
- `ContentController.updateContent()` — Returns 400 if `If-Match` missing or doesn't match current version. Error messages match reference exactly.
- `ContentController.deleteContent()` — Same: requires `If-Match`, validates against current version.
- `ContentController.redirect()` — New helper. All 5 redirect locations changed from `302 FOUND` to `303 SEE_OTHER` with JSON body `{"statusCode":"30300","message":"Symbolic version resolved","location":"..."}`.
- `ContentController.stripETagQuotes()` — Strips surrounding quotes from If-Match header values.

**Files modified:** `ContentService.java`, `ContentController.java`

### Fix 3: Default Workflow Status Aspects on Create

**Problem:** When creating content without explicit `SetStatusOperation`, reference auto-creates `atex.WFContentStatus` and `atex.WebContentStatus`. desk-api created neither, causing client crashes expecting these aspects.

**Changes:**
- `SetStatusPreStoreHook` — For new content (existing==null) without an explicit `SetStatusOperation`, auto-creates `WFContentStatusAspectBean` and `WebContentStatusAspectBean` using the first status from workflow config (`dam.wfstatuslist.d` / `dam.webstatuslist.d`). Skips if status aspects already present.
- `OneContentPreStore` — On create, now always initializes both `InsertionInfoAspectBean` and `MetadataInfo` (`atex.Metadata`) if missing. Previously only created `InsertionInfoAspectBean` conditionally and didn't create `MetadataInfo` at all.

**Files modified:** `SetStatusPreStoreHook.java`, `OneContentPreStore.java`

### Remaining Compatibility Issues (P2/P3)

| Priority | Issue | Status |
|----------|-------|--------|
| **P2** | Issue 6: Principals response format mismatch | Fixed in 18b |
| **P2** | Issue 5: ContentData bean field defaults | Fixed in 18b |
| **P3** | Issue 7: Changes feed empty response format | Not yet fixed |

## Increment 18b — P2 Compatibility Fixes

Implements the two P2 fixes identified by the compatibility test suite: contentData bean field enrichment and principals response format.

### Fix 1: ContentData Bean Field Enrichment (Issue 5)

**Problem:** When content arrives via REST API as `Map<String, Object>`, `OneContentPreStore` checks `instanceof OneContentBean` and returns early — the bean constructors that set defaults (objectType, inputTemplate, priority, contentType, etc.) are never called. The reference server auto-populates these fields.

**Approach:** Gson round-trip in `OneContentPreStore` converts `Map<String, Object>` → typed bean class → merged result. This applies constructor/field-initializer defaults from the bean class, then overlays user-provided values on top.

**New file: `BeanTypeRegistry.java`** (`com.atex.onecms.app.dam.standard.aspects`)

Static registry mapping 19 `_type` strings to bean classes:

| `_type` | Bean Class |
|---------|------------|
| `atex.onecms.article` | `OneArticleBean` |
| `atex.onecms.image` | `OneImageBean` |
| `atex.dam.standard.Audio` | `DamAudioAspectBean` |
| `atex.dam.standard.Collection` | `DamCollectionAspectBean` |
| `atex.dam.standard.Video` | `DamVideoAspectBean` |
| `atex.dam.standard.Graphic` | `DamGraphicAspectBean` |
| `atex.dam.standard.Page` | `DamPageAspectBean` |
| `atex.dam.standard.Document` | `DamDocumentAspectBean` |
| `atex.dam.standard.Embed` | `DamEmbedAspectBean` |
| `atex.dam.standard.Folder` | `DamFolderAspectBean` |
| `atex.dam.standard.Tweet` | `DamTweetAspectBean` |
| `atex.dam.standard.Instagram` | `DamInstagramAspectBean` |
| `atex.dam.standard.ShapeDB` | `DamShapeDBAspectBean` |
| `atex.dam.standard.NewslistItem` | `DamNewsListItemAspectBean` |
| `atex.dam.standard.BulkImages` | `DamBulkImagesBean` |
| `atex.dam.standard.AutoPage` | `DamAutoPageAspectBean` |
| `atex.dam.standard.LiveBlog.article` | `LiveBlogArticleBean` |
| `atex.dam.standard.LiveBlog.event` | `LiveBlogEventBean` |

Note: Legacy deprecated types (`DamArticleAspectBean`, `DamImageAspectBean`, `DamWireArticleAspectBean`, `DamWireImageAspectBean`) extend `DamContentBean` (not `OneContentBean`) and are excluded.

**Modified: `OneContentPreStore.java`**

Before the `instanceof OneContentBean` check, added Map→bean conversion:
1. If data is a `Map`, extract `_type` field and look up in `BeanTypeRegistry`
2. Create bean with defaults via `Gson.fromJson("{}", beanClass)` (triggers constructor)
3. Serialize defaults to `JsonObject`, overlay user-provided fields on top
4. Deserialize merged result back to typed bean
5. Replace `input` with new `ContentWrite` containing the typed bean

This ensures downstream hooks see a typed bean with all defaults populated.

### Fix 2: Principals Response Format (Issue 6)

**Problem:** desk-api returned `{userId, username, createdAt}` for both `/principals/users/me` and `/principals/users` list. Reference returns different formats for each.

**New files:**

| File | Description |
|------|-------------|
| `dto/UserMeDto.java` | Detailed `/me` response: `loginName`, `firstName`, `lastName`, `id` (numeric), `userId`, `cmUser`, `ldapUser`, `remoteUser`, `groups` (as `GroupRefDto` objects), `userData` (`{relations: {}}`), `homeDepartmentId` (null), `workingSites` (empty list) |
| `dto/GroupRefDto.java` | Group reference within user response: `type` ("group"), `id` ("group:{groupId}"), `name`, `principalId` |

**Modified files:**

| File | Change |
|------|--------|
| `dto/PrincipalDto.java` | Reworked to match reference list format: `type` ("user"), `id` (numeric string), `name` (loginName), `principalId`, `cmUser`, `ldapUser`, `remoteUser`. Removed old fields: `userId`, `username`, `createdAt`, `groupList` |
| `entity/AppUser.java` | Added `isCmUser()` convenience method (`!isLdap() && !isRemote()`) |
| `controller/PrincipalsController.java` | `/users/me` and `/users/{userId}` return `UserMeDto` with group membership lookups. `/users` list returns new `PrincipalDto` format. Added `toUserMeDto()` helper with group resolution via `groupMemberRepository` + `groupRepository`. Numeric IDs generated via `Math.abs(loginName.hashCode())` as stable substitute for sequential DB IDs. |

### Remaining Compatibility Issues (P3)

| Priority | Issue | Status |
|----------|-------|--------|
| **P3** | Issue 7: Changes feed empty response format | Not yet fixed |

## Increment 19 — Full Content Data Bean Migration

Ports the complete content data bean hierarchy from gong source to desk-api, replacing minimal stubs with full API-compatible beans. All beans retain their original packages (`com.atex.onecms.app.dam.standard.aspects`, `com.atex.onecms.app.dam`, `com.atex.onecms.app.dam.types`, etc.) for binary compatibility with existing serialized content.

### Problem

The existing desk-api beans (`OneArticleBean`, `OneImageBean`, etc.) were minimal stubs with 3-10 fields each. The original gong beans have 50+ fields per type. When content with all fields is deserialized into these stubs and re-saved, **unmapped fields are silently dropped** — a data loss risk. Additionally, the bean hierarchy was flat (`OneContentBean` → `OneArticleBean`) when it should have an intermediate `OneArchiveBean` class.

### Bean Hierarchy (after migration)

```
IDamBean (interface, com.atex.onecms.app.dam)
├─ OneContentBean (com.atex.onecms.app.dam.standard.aspects)
│   implements IDamBean, PropertyBag
│   Fields: _type, objectType, inputTemplate, securityParentId, contentType, name,
│           creationdate, author, words, chars, subject, newsId, source, section,
│           markForArchive, aceSlugInfo, aceMigrationInfo, propertyBag
│   ├─ OneArchiveBean
│   │   Fields: lastPublication, lastEdition, lastPage, lastPubdate, lastPagelevel,
│   │           lastSection, domain, legacyid, legacyUrl, archiveComment, related
│   │   ├─ OneArticleBean (50+ fields, StructuredText headline/lead/body/subTitle/caption/teaserTitle)
│   │   │   implements RelatedSectionsAware, PublicationLinkSupport, PushNotificationSupport,
│   │   │             PremiumTypeSupport, MinorChangeSupport, PublishUpdatedTimeAware, AuthorsSupport
│   │   │   └─ LiveBlogArticleBean
│   │   ├─ OneImageBean (20+ fields: rights, webStatement, alternativeText, useWatermark, etc.)
│   │   ├─ DamCollectionAspectBean (25+ fields, 7 interfaces)
│   │   ├─ DamAudioAspectBean (20+ fields, 9 interfaces)
│   │   ├─ DamVideoAspectBean (25+ fields, 9 interfaces)
│   │   ├─ DamGraphicAspectBean
│   │   ├─ DamPageAspectBean
│   │   └─ DamBulkImagesBean
│   ├─ DamFolderAspectBean (18 fields, smart folder query support)
│   ├─ DamDocumentAspectBean (8 fields, 3 interfaces)
│   ├─ DamEmbedAspectBean
│   ├─ DamTweetAspectBean
│   ├─ DamInstagramAspectBean (12 fields)
│   ├─ DamAutoPageAspectBean
│   ├─ DamShapeDBAspectBean
│   ├─ DamNewsListItemAspectBean (25+ fields)
│   └─ LiveBlogEventBean
├─ DamContentBean (legacy, com.atex.onecms.app.dam) @Deprecated
│   implements IDamBean
│   ├─ DamArchiveAspectBean @Deprecated
│   │   implements IDamArchiveAspectBean
│   │   ├─ DamArticleAspectBean @Deprecated
│   │   ├─ DamImageAspectBean @Deprecated
│   │   └─ DamContentShareAspectBean @Deprecated
│   ├─ DamWireArticleAspectBean @Deprecated
│   └─ DamWireImageAspectBean @Deprecated
```

### Supporting Types Created

| Package | Class | Source |
|---------|-------|--------|
| `c.a.plugins.structured.text` | `StructuredText` | structured-text symlink |
| `c.a.plugins.structured.text` | `Note` | structured-text symlink |
| `c.a.onecms` | `GeoLocation` | gong/common-beans |
| `c.a.o.app.dam` | `IDamBean` | gong/onecms-common/beans |
| `c.a.o.app.dam` | `DamContentBean` | gong/desk/module-desk |
| `c.a.o.app.dam` | `DamAssigneeBean` | gong/onecms-common/beans |
| `c.a.o.app.dam.types` | `TimeState` | gong/onecms-common/beans |
| `c.a.o.app.dam.types` | `AceSlugInfo` | gong/onecms-common/beans |
| `c.a.o.app.dam.types` | `AceMigrationInfo` | gong/onecms-common/beans |
| `c.a.o.app.dam.types` | `AceWebMetadata` | gong/onecms-common/beans |
| `c.a.o.app.dam.types` | `SocialPostingAccount` | gong/onecms-common/beans |
| `c.a.o.app.dam.types` | `SocialPostingPlatform` | gong/onecms-common/beans |
| `c.a.o.app.dam.twitter` | `TweetMediaEntity` | gong/onecms-common/beans |
| `c.a.o.app.dam.twitter` | `TweetUrlEntity` | gong/onecms-common/beans |
| `c.a.o.ace.annotations` | `AceAspect` | stub annotation |

### Interfaces Created (all in `c.a.o.app.dam.standard.aspects`)

| Interface | Methods |
|-----------|---------|
| `PropertyBag` | `getPropertyBag()`, `setPropertyBag()` |
| `MinorChangeSupport` | `isMinorChange()`, `setMinorChange()` |
| `PushNotificationSupport` | `isPushNotification()`, `setPushNotification()` |
| `PremiumTypeSupport` | `getPremiumType()`, `setPremiumType()` + `DEFAULT_PREMIUM_TYPE="inherit"` |
| `AuthorsSupport` | `getAuthors()`, `setAuthors()` |
| `RelatedSectionsAware` | `getRelatedSections()`, `setRelatedSections()` |
| `PublishUpdatedTimeAware` | `getPublishingUpdateTime()`, `setPublishingUpdateTime()` |
| `DigitalPublishingTimeAware` | `getDigitalPublishingTime()`, `setDigitalPublishingTime()` |
| `TimeStateAware` | `getTimeState()`, `setTimeState()` |
| `TranscribeSupport` | `getTranscriptionText()`, `setTranscriptionText()`, `isTranscribeFlag()`, `setTranscribeFlag()` |
| `BinaryUrlSupport` | `getBinaryUrl()`, `setBinaryUrl()` |
| `PostPublishUpdate` | `postPublishUpdate()` |
| `IDamArchiveAspectBean` | extends `IDamBean`, archive field accessors |

### New Files (45)

| File | Description |
|------|-------------|
| `plugins/structured/text/StructuredText.java` | Text with editorial notes |
| `plugins/structured/text/Note.java` | Single editorial note |
| `onecms/GeoLocation.java` | Lat/long/label POJO |
| `dam/IDamBean.java` | Base DAM bean interface |
| `dam/DamContentBean.java` | Legacy base class (deprecated) |
| `dam/DamAssigneeBean.java` | Assignee POJO |
| `dam/types/TimeState.java` | On/off time POJO |
| `dam/types/AceSlugInfo.java` | ACE slug info |
| `dam/types/AceMigrationInfo.java` | ACE migration info |
| `dam/types/AceWebMetadata.java` | ACE web metadata |
| `dam/types/SocialPostingAccount.java` | Social posting account |
| `dam/types/SocialPostingPlatform.java` | Social posting platform |
| `dam/twitter/TweetMediaEntity.java` | Tweet media entity |
| `dam/twitter/TweetUrlEntity.java` | Tweet URL entity |
| `ace/annotations/AceAspect.java` | Stub annotation |
| `aspects/OneArchiveBean.java` | Archive intermediate class |
| `aspects/PropertyBag.java` | Interface |
| `aspects/MinorChangeSupport.java` | Interface |
| `aspects/PushNotificationSupport.java` | Interface |
| `aspects/PremiumTypeSupport.java` | Interface |
| `aspects/AuthorsSupport.java` | Interface |
| `aspects/RelatedSectionsAware.java` | Interface |
| `aspects/PublishUpdatedTimeAware.java` | Interface |
| `aspects/DigitalPublishingTimeAware.java` | Interface |
| `aspects/TimeStateAware.java` | Interface |
| `aspects/TranscribeSupport.java` | Interface |
| `aspects/BinaryUrlSupport.java` | Interface |
| `aspects/PostPublishUpdate.java` | Interface |
| `aspects/IDamArchiveAspectBean.java` | Legacy archive interface |
| `aspects/DamArchiveAspectBean.java` | Legacy archive class (deprecated) |
| `aspects/AudioAI.java` | Audio AI settings POJO |
| `aspects/LogicalPage.java` | Print logical page POJO |
| `aspects/DamVideoAspectBean.java` | Video bean (25+ fields) |
| `aspects/DamGraphicAspectBean.java` | Graphic bean |
| `aspects/DamDocumentAspectBean.java` | Document bean |
| `aspects/DamEmbedAspectBean.java` | Embed bean |
| `aspects/DamTweetAspectBean.java` | Tweet bean |
| `aspects/DamInstagramAspectBean.java` | Instagram bean |
| `aspects/DamFolderAspectBean.java` | Folder/smart folder bean |
| `aspects/DamPageAspectBean.java` | Page bean |
| `aspects/DamAutoPageAspectBean.java` | Auto-page bean |
| `aspects/DamBulkImagesBean.java` | Bulk images bean |
| `aspects/DamCollectionLinkAspectBean.java` | Collection link bean |
| `aspects/DamShapeDBAspectBean.java` | ShapeDB bean |
| `aspects/DamNewsListItemAspectBean.java` | News list item bean |
| `aspects/LiveBlogArticleBean.java` | Live blog article |
| `aspects/LiveBlogEventBean.java` | Live blog event |
| `aspects/PrintPageAspectBean.java` | Print page bean |
| `aspects/PrintDeadlineAspectBean.java` | Print deadline bean |
| `aspects/SocialPostingAspect.java` | Social posting aspect |
| `aspects/DamWireArticleAspectBean.java` | Wire article (deprecated) |
| `aspects/DamContentShareAspectBean.java` | Content share (deprecated) |

### Hook Updates (3)

| File | Change |
|------|--------|
| `lifecycle/wordcount/OneWordCountPreStoreHook.java` | `bean.getBody()` returns `StructuredText` → added `extractText()` helper |
| `lifecycle/charcount/OneCharCountPreStoreHook.java` | Same: extract `.getText()` from `StructuredText` body |
| `lifecycle/onecontent/OneContentPreStore.java` | `deriveName()` now handles `StructuredText` via `asString()` helper |

### Caller Updates (1)

| File | Change |
|------|--------|
| `lifecycle/collection/CollectionPreStore.java` | `getContentIds()` → `getContents()` (`List<ContentId>`) |

### Design Notes

- **StructuredText vs String**: `OneArticleBean.headline`, `lead`, `body`, `subTitle`, `caption`, `teaserTitle` are now `StructuredText` (matching gong original). This preserves editorial notes embedded in rich text. Gson handles deserialization of both `{"text":"...","notes":{}}` objects and plain strings.
- **Legacy `Dam*AspectBean` hierarchy**: The deprecated `DamContentBean` → `DamArchiveAspectBean` → `DamArticleAspectBean`/`DamImageAspectBean` tree is separate from the `OneContentBean` tree. Both exist for backward compatibility with content created under different schemas.
- **No `@AspectStorage` annotation**: The original beans used `@AspectStorage(ignoreFields=...)` for field filtering during persistence. desk-api stores all fields as JSON, so this annotation is unnecessary.
- **`@AceAspect` stub**: Created as a minimal `@Retention(RUNTIME)` annotation in `com.atex.onecms.ace.annotations` — required by `AceSlugInfo` and `AceMigrationInfo` for compatibility but not used at runtime.
- **`QueryField` data-only port**: `DamFolderAspectBean` references `QueryField` from `com.atex.onecms.app.dam.util`. This class already existed in desk-api. The Solr query-building methods (which depend on Polopoly classes) are omitted; only the data fields for JSON serialization are retained.

