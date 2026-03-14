# Desk API — Increment Details

Full per-increment documentation. Referenced from [CLAUDE.md](../CLAUDE.md).

## Increment 1 — OneCMS-Compatible REST Layer

Collapses the existing multi-layer stack (Desk UI → Jersey → CmClient → OneCMS Server → Couchbase) into a single Spring Boot API talking directly to the ADM Content Service MySQL database.

### Configuration
- **build.gradle** — MySQL (`mysql-connector-j`), Spring Data JPA, Spring Web MVC, Flyway
- **compose.yaml** — MySQL 8.0 container with `desk` database
- **application.properties** — `ddl-auto=none`, Flyway baseline-on-migrate
- **Database migrations** — Flyway-managed (see Increment 22)

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
| GET | `/content/contentid/{id}` | Versioned → 200 with ETag; unversioned → 303 redirect |
| GET | `/content/contentid/{id}/history` | Version history (unversioned ID only) |
| GET | `/content/externalid/{id}` | Resolve external ID → 303 redirect |
| GET | `/content/externalid/{id}/history` | Resolve → redirect to history |
| GET | `/content/view/{view}/contentid/{id}` | Resolve in specific view → 303 redirect |
| GET | `/content/view/{view}/externalid/{id}` | Resolve external ID in view → 303 redirect |
| POST | `/content` | Create → 201 + Location + ETag |
| PUT | `/content/contentid/{id}` | Update (new version) → 200 + ETag |
| DELETE | `/content/contentid/{id}` | Soft delete → 204 |

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

### Security Controller
`com.atex.desk.api.controller.SecurityController` at `/security`:

| Method | Path | Behaviour |
|--------|------|-----------|
| POST | `/security/token` | Login: `{username, password}` → JWT token + userId + expireTime |
| GET | `/security/token` | Validate: `X-Auth-Token` header → token info if valid |
| GET | `/security/oauth/url` | Get Cognito hosted UI login URL (query param: `callbackUrl`) |
| POST | `/security/oauth/callback` | Exchange OAuth code/token for desk-api JWT |

### User Storage
- `registeredusers` table: loginname (PK), passwordhash, regtime, isldapuser, isremoteuser, remoteserviceid, active
- Entity: `AppUser`, Repository: `AppUserRepository`
- Default seed: `sysadmin` / `sysadmin` (OLDSHA hash)
- Auth dispatch: local → `PasswordService`, LDAP → `LdapAuthService`, remote → `CognitoAuthService`

## Increment 3 — DamDataResource Migration

Migrates the full 68-endpoint `DamDataResource` (3000-line JAX-RS resource from `gong/desk`) into desk-api as a Spring MVC `@RestController`. Copies the original code and mechanically converts annotations, then wires to local services.

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

### Phase 1: OneCMS API Types (~40 classes)
Recreated the minimal OneCMS type hierarchy preserving **original package names** for backward compatibility.

**Package `com.atex.onecms.content`**: `ContentManager`, `ContentId`, `ContentVersionId`, `ContentResult<T>`, `ContentResultBuilder<T>`, `Content<T>`, `ContentWrite<T>`, `ContentWriteBuilder<T>`, `Subject`, `Status`, `DeleteResult`, `ContentHistory`, `ContentVersionInfo`, `ContentOperation`, `SetAliasOperation`, `IdUtil`, `FilesAspectBean`, `InsertionInfoAspectBean`

**Package `com.polopoly.cm.*`**: `ContentId` (numeric major/minor), `ExternalContentId`, `ContentIdFactory`, `PolicyCMServer`, `CmClient`, `CmClientBase`, `CMException`, `Caller`, `UserServer`, `Metadata`, `Dimension`

### Phase 2: LocalContentManager
`com.atex.desk.api.onecms.LocalContentManager` — implements `ContentManager`, delegates to `ContentService`.

### Phase 3: PolicyCMServer Facade + CmClient
`LocalPolicyCMServer`, `LocalCmClient`, `CallerContext` (@RequestScope), `LocalUserServer`

### Phase 4: DAM Utility Classes (~90 classes)
Copied from `gong/desk/module-desk` preserving original packages (`com.atex.onecms.app.dam.*`). Categories: Beans/DTOs, Workflow, Mark-as, Collections, Engagement, Publishing, Remote, Restrict, Utilities, Operations, Config, Camel, Solr, User, ACL, Wire images.

### Phase 5: DamDataResource Conversion
Annotation conversion (JAX-RS → Spring MVC), wiring changes (Jersey → Spring DI).

**New infrastructure:** `ContentApiException`, `ContentApiExceptionHandler`, `DamUserContext`

### Phase 6: Cleanup
Removed superseded controllers (`DamConfigController`, `DamSearchController`, `DamContentController`) and DTOs.

### Increment 3a — Solr Search Implementation
Full SolrJ 9.8.0 integration: `SolrService`, `SolrUtils`, `SolrQueryDecorator`, `DamMapping`, `SolrCoreMapper`, `QueryField`, `DamQueryAspectBean`

### Increment 3b — File Service
`FileService` interface, `LocalFileService` (filesystem-backed), `LocalHttpFileServiceClient`, `HttpDamUtils`, `FileServiceUtils`

### Increment 3c — Remaining Stub Implementations
`MarkAsUtils`, `SmartUpdatesMetadataUtils`, `DamEngagementUtils`, `ContentOperationUtils`, `CollectionAspectUtils`, `CamelApiWebClient`, `RemoteUtils`

### Increment 3d — Content Restriction
`RestrictContentService` — restricts/unrestricts content by modifying security parents, associated sites, and workflow statuses.

### Increment 3e — Publishing Pipeline
`DamRemoteImporter`, `DamBeanPublisher`, `DamPublisher`, `DamPublisherImpl`, `PublishingContextImpl`, `DamPublisherBuilder`, `RemoteConfigBean`, `RemotesConfiguration`, `DamPublisherFactory`

## Increment 4 — BeanMapper, HTTP Publisher, Permission Checking

### Permission Checking
`DamUserContext.havePermission()` — JWT scope-based: OWNER → all, strips `"21"` prefix, READ/WRITE scope matching.

### HTTP Publisher
`ContentPublisher`, `ContentAPIPublisher` (HTTP via HttpDamUtils), `DamBeanPublisherImpl`, `UserTokenStorage` (ConcurrentHashMap cache).

Publishing flow: `DamPublisherFactory` → `DamPublisherBuilder` → `DamPublisherImpl` → `DamBeanPublisherImpl` → `ContentAPIPublisher` → Remote CMS

### BeanMapper (Content Export)
`BeanMapper.export()` — deep-copy via Gson, reset dates, set insertion info, record engagement.

## Increment 5 — PF4J Plugin System

PF4J 3.15.0 for runtime JAR plugins. Extension points: `DeskPreStoreHook` (modify content before persistence), `DeskContentComposer` (transform content for variants).

`PluginConfig`, `PluginProperties`, `PluginLoader`, hook execution in `LocalContentManager`.

## Increment 6 — Built-in PreStoreHooks + Index Composer Pipeline

### PreStoreHooks
| Hook | Behaviour |
|---|---|
| `OneContentPreStore` | Creation date, name, author |
| `HandleItemStatePreStore` | Spike/unspike |
| `OneImagePreStore` | Name from filename |
| `SecParentPreStoreHook` | Partition ↔ security parent sync |
| `SetStatusPreStoreHook` | Workflow status from operations |
| `OneWordCountPreStoreHook` | Word count |
| `OneCharCountPreStoreHook` | Char count |
| `AddEngagementPreStoreHook` | Engagement tracking |
| `DamAudioPreStoreHook` | Audio name and link |
| `CollectionPreStore` | Collection name and access |

### Hook Chain Order
- `*` (all): OneContentPreStore → HandleItemStatePreStore
- Image: OneImagePreStore → SecParent → SetStatus → AddEngagement
- Article: SecParent → SetStatus → WordCount → CharCount → AddEngagement
- Audio/Collection: similar chains

### Index Composer Pipeline
`ContentIndexer` → `DamIndexComposer` → `SolrService.index()` → Solr

## Increment 16 — File Service Endpoints & Activity/Locking System

### File Service REST API (`/file`)
Upload, download, metadata, delete endpoints. `FileInfoDTO` with uppercase `URI` field.

### Activity/Locking System (`/activities`)
Activities stored as content objects via ContentManager (external ID: `activity:{contentId}`).
`ActivityService` with retry logic (10 tries, 10ms). `ActivityServiceSecured` for auth.

## Increment 17 — Preview Endpoint

`POST /preview/contentid/{id}` and `POST /preview/externalid/{id}`. Dispatches by adapter class: `AceWebPreviewAdapter` or `WebPreviewAdapter`. `PreviewService` orchestrates.

## Increment 18 — Compatibility Test Suite & Fix Plan

`scripts/compat-test.py` — 16 comparison tests against reference OneCMS (localhost:38084/onecms).

7 issues found (P1-P3): redirect codes, If-Match enforcement, error format, default aspects, contentData defaults, principals format, changes feed format.

## Increment 18a — P1 Compatibility Fixes

1. **Error response format** — `ErrorResponseDto` reworked: `statusCode` (int × 100), `message`, `extraInfo`
2. **If-Match enforcement + 303 redirects** — Required for PUT/DELETE, validates against current version
3. **Default workflow status** — `SetStatusPreStoreHook` auto-creates `WFContentStatus`/`WebContentStatus` on new content

## Increment 18b — P2 Compatibility Fixes

1. **ContentData bean field enrichment** — `BeanTypeRegistry` maps 19 `_type` strings to bean classes. Gson round-trip in `OneContentPreStore` applies constructor defaults.
2. **Principals response format** — `UserMeDto` for `/me`, reworked `PrincipalDto` for list
3. **Hibernate CamelCase quoting** — Backtick quoting for camelCase table/column names

## Increment 19 — Full Content Data Bean Migration

Complete bean hierarchy from gong source. `OneContentBean` → `OneArchiveBean` → type-specific beans. 45 new files, 19 content types. `StructuredText` for rich text fields. Legacy `DamContentBean` tree kept for backward compatibility.

## Increment 20 — Solr Client Fix & Indexer Pause/Resume

- `Http2SolrClient` → `HttpSolrClient` (Jetty 11 vs 12 incompatibility)
- `IndexingHealthIndicator` for actuator
- `POST /admin/reindex/pause` and `/resume`

## Increment 21 — Legacy Polopoly Content Migration

`scripts/migrate-legacy.py` migrates legacy content with alias-based resolution (`policyId` alias). `ContentService.resolveWithFallback()` — 3-step: normal → policyId alias → externalId alias.

## Increment 22 — Flyway Database Migrations

V1 = legacy baseline (23 tables + seeds). V2 = desk-api additions (Java migration with conditional logic). Baseline-on-migrate for existing DBs.

New migrations: SQL in `src/main/resources/db/migration/`, Java in `com.atex.desk.api.migration`.

## Increment 23 — ContentService Functional Gap Fixes

11 gaps fixed: aspect MD5 reuse, optimistic locking at service layer, view exclusivity, alias CRUD completion, content existence check, validation, aliases in response, batch history views, resolution caching, hard delete (purge).

## Increment 24 — Admin UI

`dashboard.html` — 5 tabs: Overview, Indexing, Users & Groups, Configuration, API Explorer. Vanilla HTML/CSS/JS with auth.

## Increment 25 — Config Sync & JSON5 Parsing Fixes

3 config-sync.py fixes (json-ref paths, escape pairs, line continuations) + Json5Reader line continuation stripping.

## Increment 26 — ConfigurationDataBean Wrapping & Display Names

Config content wrapped in `ConfigurationDataBean` structure (`_type`, `json`, `name`, `dataType`, `dataValue`). `config-names.properties` for display names.

## Increment 27 — XML Component Config Extraction

`xml-component` extraction type in config-sync.py. `ConfigurationService.toContentResultAs()` for typed bean loading. `LocalContentManager` honors `dataClass` parameter.

## Increment 28 — Integration Test Suite with Testcontainers

50 tests across 9 classes. Singleton MySQL container via Testcontainers. JDK HttpClient for error status testing. `BaseIntegrationTest` shared base class.

```bash
JAVA_HOME=C:/Users/peter/.jdks/openjdk-25.0.2 ./gradlew test
JAVA_HOME=C:/Users/peter/.jdks/openjdk-25.0.2 ./gradlew test --tests "*ContentCrudIntegrationTest"
```

## Increment 29 — Template System, Config Profiles & Format-Aware Serving

### Config Metadata & Format System

**`ConfigMeta`** record (name, group, format) — extracted from `_meta` block in JSON5 config files. `group` organizes configs in admin UI. `format` controls API serialization format, matching Polopoly input-template names.

**`ConfigEntry`** record (data, meta, contentList) — adds `contentList` field extracted from `_contentList` arrays in catalog JSON5 files.

**Format-aware serialization** in `ConfigurationService.toContentResultDto()`:
- `atex.onecms.Template.it` → `OneCMSTemplateBean`: `{_type, data: "<stringified JSON>"}` — template JSON as a string in `data` field
- `atex.onecms.TemplateList.it` → `OneCMSTemplateListBean`: `{_type, templateList: [...]}` — list of template external ID references
- Default → `ConfigurationDataBean`: `{_type, json, name, dataType, dataValue}` — stringified JSON wrapping

### Template Files (49 new)

Added 49 template JSON5 files in `config/defaults/form-templates/`, synced from `gong/onecms-common/content/src/main/content/onecms-templates/` via `json-ref|gong-onecms-common` entries in `config-mapping.txt`. Each has `"format": "atex.onecms.Template.it"` in `_meta`.

Template types: article (8 variants), image (6), audio (2), video (3), collection (3), page (8), graphic (2), folder (3), document (2), embed (1), tweet (1), instagram (1), author (1), bulkimages (1), liveblog (3), changeStatus (1), changeFolios/Puffs (2).

8 template XML filenames don't match their internal `<externalid>` — canonical external IDs from XML used in config-mapping.txt.

### Template Catalogs (3 new)

Manually created JSON5 catalog files with `"format": "atex.onecms.TemplateList.it"` and `_contentList` arrays:
- `p.onecms.DamTemplateList.json5` — 43 template references (main DAM catalog)
- `p.onecms.DamCreateContentTemplateList.json5` — 11 create-new-content templates
- `atex.onecms.ContributorToolTemplateList.json5` — 11 contributor tool templates

### Profile Configuration Endpoint

`ProfileConfigurationController` at `/configuration/profile`:
- `GET /configuration/profile?profile={name}` — returns merged config for a named profile
- Loads profile definition from `atex.configuration.desk.profiles`, extracts config keys, merges all referenced configs into a single response

### Config Group Metadata

All ~130 existing config JSON5 files updated with `_meta.group` field for admin UI categorization. Groups: Desk Configuration, Form, Permission, Result Set, Localization, Chains, Routes, Processors, Widgets, Workflow, Mark As, Publish, Templates, etc.

### Admin UI Config Tab

`dashboard.html` config tab updated with group-based filtering tabs.

### config-sync.py Changes

- Added `gong-onecms-common` source base for template content
- `inject_meta()` accepts `fmt` parameter for format field
- Auto-detects `form-templates` dest subdir → sets `atex.onecms.Template.it` format

### Modified Files

| File | Change |
|------|--------|
| `config/ConfigMeta.java` | **New** — record with name, group, format; helper methods `isTemplateFormat()`, `isTemplateListFormat()` |
| `config/ConfigEntry.java` | **New** — record with data, meta, contentList |
| `config/ConfigurationService.java` | Format-aware `toContentResultDto()`, `_contentList` extraction in `parseJson()`, `ConfigEntry`/`ConfigMeta` integration |
| `controller/ConfigurationController.java` | Name/group in admin config list |
| `controller/ProfileConfigurationController.java` | **New** — `/configuration/profile` endpoint |
| `auth/AuthConfig.java` | Added `/configuration/*` to auth filter |
| `scripts/config-sync.py` | `gong-onecms-common` source, format injection, `_meta.group` |
| `scripts/config-mapping.txt` | 49 new `json-ref\|gong-onecms-common` template entries |
| `static/dashboard.html` | Config group tabs |
| All ~130 JSON5 config files | Added `_meta.group` field |
| 49 new template JSON5 files | Template content with `atex.onecms.Template.it` format |
| 3 new catalog JSON5 files | Template lists with `atex.onecms.TemplateList.it` format |

## Increment 30 — Runtime Bug Fixes (Config Serving, Auth Expiry, Gson/Java 25)

Three runtime bugs discovered during mytype-new integration testing.

### Fix 1: Missing aspects.put in ConfigurationService

**Problem:** After the format-aware refactoring (Increment 29), `toContentResultDto()` created and populated a `contentData` AspectDto but never added it to the `aspects` map. Config content API responses had empty aspects, breaking mytype-new's `extractConfigJson()` which checks `aspects/contentData/data/json`.

**Root cause:** The line `aspects.put("contentData", contentData)` was accidentally removed during the format-aware serialization refactoring.

**Fix:** Restored the missing `aspects.put("contentData", contentData)` call in `ConfigurationService.toContentResultDto()`.

### Fix 2: Token Expiry Too Short

**Problem:** mytype-new kept getting auth expired errors. Tokens were expiring after 5 minutes.

**Root cause:** `SecurityController.DEFAULT_EXPIRATION` was set to `Duration.ofMinutes(5)` instead of matching the configured `desk.auth.max-lifetime=86400` (24 hours). mytype-new has no token refresh logic — it stores the token in localStorage/sessionStorage and uses it until it expires.

**Fix:** Changed `DEFAULT_EXPIRATION` from `Duration.ofMinutes(5)` to `Duration.ofHours(24)`.

### Fix 3: Gson InaccessibleObjectException on Java 25

**Problem:** `DamDataResource.solrquery()` crashed with `InaccessibleObjectException: Unable to make field private int java.text.SimpleDateFormat.serialVersionOnStream accessible: module java.base does not "opens java.text" to unnamed module`. Gson tried to reflectively access `SimpleDateFormat` internals blocked by Java 25's module system.

**Root cause:** `QueryField` had an unused instance field `private final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd")`. This was dead code in the original gong source too — declared but never referenced. Gson attempted to serialize/deserialize it when processing `DamQueryAspectBean` (which contains `List<QueryField>`).

**Fix:** Removed the unused `FORMATTER` field entirely from `QueryField`.

### Modified Files (3)

| File | Change |
|------|--------|
| `config/ConfigurationService.java` | Restored missing `aspects.put("contentData", contentData)` |
| `controller/SecurityController.java` | `DEFAULT_EXPIRATION`: `Duration.ofMinutes(5)` → `Duration.ofHours(24)` |
| `dam/util/QueryField.java` | Removed unused `SimpleDateFormat FORMATTER` field |

### Design Notes

- **No `--add-opens` JVM flag**: Rather than adding `--add-opens java.base/java.text=ALL-UNNAMED` to work around the module system, the dead field was simply removed — cleaner and avoids masking future issues
- **Token expiry alignment**: The `desk.auth.max-lifetime` property (86400s) controls the maximum allowed lifetime at validation time. `DEFAULT_EXPIRATION` controls the actual expiry set when creating tokens. Both should match.

## Increment 31 — Type Service & Aspect Name Fixes

Implements `GET /content/type/{typeName}` for mytype-new's type schema system, and fixes incorrect aspect names.

### Type Service

**Problem:** mytype-new calls `GET /onecms/content/type/{typeName}` to get `ModelTypeBean` schemas describing bean fields and Java types. This is used to detect `StructuredText` fields and coerce values to correct Java types on content create/update. No endpoint existed in desk-api.

**Solution:** New `TypeService` scans `@AspectDefinition` and `@AceAspect` annotations at startup via Spring's classpath scanner, introspects bean fields via `java.beans.Introspector`, and generates `ModelTypeBean` responses matching the Polopoly format.

### TypeController (`GET /content/type/{typeName}`)

Separate controller at `/content/type` to keep `ContentController` focused. Returns:
```json
{
  "_type": "com.polopoly.model.ModelTypeBean",
  "typeName": "atex.onecms.article",
  "typeClass": "bean",
  "generator": "desk-api",
  "attributes": [
    {"name": "headline", "typeName": "com.atex.plugins.structured.text.StructuredText", "modifiers": 3},
    {"name": "images", "typeName": "java.util.List<com.atex.onecms.content.ContentId>", "modifiers": 3},
    {"name": "p.beanClass", "typeName": "java.lang.String", "modifiers": 9, "value": "com.atex.onecms.app.dam.standard.aspects.OneArticleBean"},
    {"name": "p.publicInterfaces", "typeName": "java.lang.String", "modifiers": 9, "value": "..."},
    {"name": "p.mt", "typeName": "java.lang.String", "modifiers": 9, "value": "atex.onecms.article"},
    {"name": "_data", "typeName": "java.lang.String", "modifiers": 257}
  ],
  "beanClass": "com.atex.onecms.app.dam.standard.aspects.OneArticleBean",
  "addAll": "true"
}
```

Modifier constants (from `com.polopoly.model.ModelTypeFieldData`):
- `MOD_READ = 1`, `MOD_WRITE = 2`, `MOD_STATIC = 8`, `MOD_TRANSIENT = 256`
- Normal RW fields: `3` (READ|WRITE), metadata: `9` (READ|STATIC), `_data`: `257` (READ|TRANSIENT)

Response is cached (`Cache-Control: public, max-age=3600`).

### Aspect Name Fixes (verified against gong source)

| Bean | Old (wrong) | Correct (gong) |
|------|-------------|-----------------|
| `DamContentAccessAspectBean` | `atex.dam.contentAccess` | `atex.dam.standard.ContentAccess` |
| `DamPubleventsAspectBean` | `damPubleventsAspect` | `atex.dam.standard.Publevents` |
| `WFStatusListBean` | `dam.wfstatuslist.d` | `atex.WFStatusList` |
| `CollectionAspect` | `collectionAspect` | `atex.collection` |
| `EngagementAspect` | `atex.dam.engagement` | `engagementAspect` |

### Added Missing @AspectDefinition (8 beans)

`WFContentStatusAspectBean`, `WebContentStatusAspectBean`, `WFStatusListBean`, `CollectionAspect`, `DamContentAccessAspectBean`, `DamPubleventsAspectBean`, `PrestigeItemStateAspectBean`, `AliasesAspectBean`

### Ported Missing Aspect Beans (5 + 6 supporting types)

mytype-new queries 17 type names. Five were missing from desk-api:

| Bean | Aspect Name | Source |
|------|-------------|--------|
| `ImageInfoAspectBean` | `atex.Image` | polopoly/core/image-service |
| `ImageEditInfoAspectBean` | `atex.ImageEditInfo` | polopoly/core/image-service |
| `MetadataTagsAspectBean` | `atex.ImageMetadata` | polopoly/core/image-service |
| `ContentAccessBean` | `p.ContentAccess` | polopoly/core/data-api-api |
| `PlanningAspectBean` | `atex.Planning` | gong/onecms-common/beans |

Supporting image types ported: `Rectangle`, `AspectRatio`, `ImageFormat`, `CropInfo`, `Pixelation`, `FocalPoint` (all in `c.a.onecms.image`).

### New Files

| File | Description |
|------|-------------|
| `controller/TypeController.java` | `GET /content/type/{typeName}` endpoint |
| `service/TypeService.java` | Classpath scan, bean introspection, ModelTypeBean generation |
| `c.a.onecms.image.*` | 8 image classes (3 aspect beans + 5 supporting types) |
| `c.a.onecms.content.ContentAccessBean` | Content access control bean |
| `c.a.onecms.app.dam.planning.PlanningAspectBean` | Editorial planning bean |

### Tests (18)

`TypeServiceIntegrationTest` — covers all 17 type names queried by mytype-new:
- Schema structure (\_type, typeName, typeClass, beanClass, addAll, attributes)
- StructuredText field detection (headline, lead, body)
- Field type accuracy (String, boolean, int, List<ContentId>)
- Metadata attributes (p.beanClass, p.mt, \_data with correct modifiers)
- Boolean `is*` methods (flipVertical, flipHorizontal)
- 404 for unknown types, Cache-Control header

### Design Notes

- **Plugin override**: `TypeService.registerType()` allows plugins to override core type definitions at runtime
- **@AceAspect support**: Scanner also picks up `@AceAspect`-annotated beans (3 ACE metadata types)
- **Generic types**: Getter return types include generic parameters (e.g., `java.util.List<com.atex.onecms.content.ContentId>`) matching Polopoly's format
- **No database storage**: Types are generated from annotations at startup — no migration or content import needed

## Increment 32 — File Service Backends, File Delivery & Controller Refactoring

Adds pluggable file storage backends (local/S3/hybrid), a public file delivery endpoint with HTTP Range request support, and extracts resolution endpoints from ContentController.

### File Service Backends
`com.atex.desk.api.file` — Three backend implementations selectable via `desk.file-service.backend` property:

- **`LocalFileService`** (default) — Filesystem-backed storage under `desk.file-service.base-dir`. Refactored from `@Service` to config-driven instantiation via constructor.
- **`S3FileService`** — Full Amazon S3 implementation (AWS SDK v2). Supports one-bucket mode (prefix separation) and two-bucket mode. Date-based S3 key prefixes, metadata storage, Range request support, MD5 checksums.
- **`DelegatingFileService`** — Hybrid routing by URI scheme (`content://` → S3, `tmp://` → local). Used when `backend=hybrid`.
- **`FileServiceAutoConfiguration`** — Spring `@Configuration` bean factory, selects backend via switch on `desk.file-service.backend` property.
- **`FileServiceProperties`** — `@ConfigurationProperties(prefix = "desk.file-service")` with S3 sub-properties (region, endpoint, credentials, buckets, one-bucket mode, multipart threshold).

### File Delivery Controller
`FileDeliveryController` at `/filedelivery` — Public endpoint (no auth required), ported from Polopoly's filedelivery-service WAR:

- `GET /filedelivery/{id}/{path}` — Deliver file by content ID (short form)
- `GET /filedelivery/contentid/{id}/{path}` — Deliver file by content ID (long form)
- `GET /filedelivery/externalid/{externalId}/{path}` — Deliver file by external ID
- `GET /filedelivery` — Ping

Features:
- HTTP Range request support (RFC 7233): partial, open-ended, suffix ranges
- `If-Range` conditional range support
- ETag, Accept-Ranges, Cache-Control headers
- Resolves file URIs from content's `files` aspect
- Fallback to first file if requested path not found
- StreamingResponseBody for efficient large file delivery

### ContentController Refactoring
Extracted 5 endpoints into `ContentResolveController` to reduce ContentController from 687 → ~520 lines:

- `GET /content/externalid/{id}` — Resolve external ID with config fallback
- `GET /content/contentid/externalid/{externalId}` — URL-decoded path variant
- `GET /content/externalid/{id}/history` — History redirect by external ID
- `GET /content/view/{view}/contentid/{id}` — View-based resolution
- `GET /content/view/{view}/externalid/{id}` — View + external ID resolution

ContentController delegates to `ContentResolveController` for the `externalid/` path in `getContent()`.

### Configuration
`application.properties` additions:
```properties
desk.file-service.base-dir=./files
desk.file-service.backend=local
desk.file-service.s3.enabled=false
desk.file-service.s3.region=eu-west-1
```

`build.gradle`: Added `software.amazon.awssdk:s3` dependency.

### Tests (19 new tests)
`FileDeliveryIntegrationTest` — 11 tests:
- Full file delivery by content ID (short + long form)
- 404 for non-existent content
- Response headers (Accept-Ranges, ETag, Cache-Control, Content-Length)
- Range requests: partial (`bytes=0-9`), open-ended (`bytes=10-`), suffix (`bytes=-5`)
- If-Range with matching/non-matching ETag
- Ping endpoint, no-auth-required verification

`FileServiceIntegrationTest` — 8 new tests (total 14):
- Auth enforcement: upload/download/delete without token → 401 with WWW-Authenticate
- 404 for non-existent file GET and info
- Location header and response fields on upload
- Cache headers on download
- Anonymous upload endpoint

### Design Notes
- **No auth on `/filedelivery`**: Matches Polopoly's public file serving pattern — files are accessible without tokens
- **ETag generation**: Uses `fileUri.hashCode()` — lightweight, sufficient for cache invalidation
- **S3 ported from Polopoly**: `FileServiceS3` reference in `polopoly/public-artifacts/file-storage-server`
- **DelegatingFileService simplified**: Hardcoded two-backend (local + S3) instead of Polopoly's pluggable `FileServiceDelegator` interface — sufficient for current needs

---

## Increment 33: Image Service with Rust Sidecar

### Goal
Replace Polopoly's Java-based image processing (ImageService, imgscalr, ImageIO) with a high-performance Rust sidecar. desk-api resolves content IDs and redirects to the Rust service, which reads directly from storage — zero image bytes flow through Java.

### Architecture
```
Client → desk-api (resolve + sign + 302) → desk-image (Rust) → S3/filesystem
```

### Java Side (desk-api)

**ImageController** (`c.a.desk.api.controller.ImageController`):
- `GET /image/{id}/{filename}` — image by unversioned content ID
- `GET /image/contentid/{id}/{filename}` — image by versioned content ID
- `GET /image/original/{id}/{filename}` — download original (auth required, no processing)
- `GET /image/original/{id}` — download original without filename
- `GET /image/{scheme}/{host}/{path}` — process image by file URI (public, no HMAC)
- Resolves content ID → extracts `atex.Image` filePath + `atex.ImageEditInfo` (rotation, flip, crops, focal point)
- Builds HMAC-SHA256 signed redirect URL to Rust sidecar
- Conditional on `desk.image-service.enabled=true`

**ImageServiceProperties** (`c.a.desk.api.config.ImageServiceProperties`):
- `desk.image-service.enabled` — enable/disable (default: false)
- `desk.image-service.url` — Rust sidecar base URL
- `desk.image-service.secret` — shared HMAC secret
- `desk.image-service.signature-length` — hex chars (default: 7, matches Polopoly)
- `desk.image-service.redirect` — redirect vs proxy mode
- `desk.image-service.cache-max-age` — Cache-Control seconds

### Rust Side (desk-image/)

**Standalone HTTP service** (axum framework):
- `GET /image/{file_uri}/{filename}?w=&h=&m=&q=&c=&rot=&flipv=&fliph=&fp=&sig`
- HMAC-SHA256 signature validation (shared secret with desk-api)
- Direct S3/filesystem read — no data through Java
- Image processing: resize (fit/fill), crop, rotate, flip, focal point, quality, format conversion
- `image` + `fast_image_resize` crates (SIMD-accelerated, 5-10x faster than Java ImageIO)
- LRU cache for processed images (configurable size/entries)
- TOML configuration (`config.toml`)

**Operations supported** (matching Polopoly ImageService):
| Param | Description |
|-------|-------------|
| `w` | Width in pixels |
| `h` | Height in pixels |
| `m` | Resize mode: FIT (default) or FILL |
| `q` | JPEG quality (0.0-1.0) |
| `c` | Crop rectangle: x,y,w,h |
| `rot` | Rotation: 90, 180, 270 |
| `flipv` | Vertical flip |
| `fliph` | Horizontal flip |
| `fp` | Focal point: x,y,zoom (auto-crop centering) |

**Signing format** (Polopoly-compatible):
- Key: `$p$w$h$m$...` (sorted param names prefixed with $)
- Value: first 7 hex chars of HMAC-SHA256(secret, path + concatenated values)

### Files Created
- `src/main/java/.../controller/ImageController.java` — Java redirect controller
- `src/main/java/.../config/ImageServiceProperties.java` — Configuration
- `desk-image/Cargo.toml` — Rust project manifest
- `desk-image/config.toml` — Default configuration
- `desk-image/src/main.rs` — Entry point, router setup
- `desk-image/src/config.rs` — TOML config loading
- `desk-image/src/handler.rs` — Request handler, param parsing
- `desk-image/src/processing.rs` — Image operations, LRU cache
- `desk-image/src/signing.rs` — HMAC validation
- `desk-image/src/storage.rs` — S3/local file reader

### Files Modified
- `DeskApiApplication.java` — added ImageServiceProperties
- `AuthConfig.java` — added `/image/*` to auth filter
- `application.properties` — added `desk.image-service.*` config
- `CLAUDE.md` — added ImageController to architecture

### Design Notes
- **Zero Java image bytes**: desk-api only does content resolution + URL signing, then 302 redirects. Image data flows storage → Rust → client (1 hop)
- **Polopoly HMAC compatibility**: Same algorithm (HmacSHA256), same key format ($p$w$h...), same signature length (7 hex chars)
- **Edit info baked in**: Rotation, flip, focal point from `atex.ImageEditInfo` aspect are injected into the redirect URL params
- **Format crops**: When `f=2x1` is requested, desk-api looks up the crop rectangle from `ImageEditInfoAspectBean.crops` map and adds it as `c=x,y,w,h`
- **Conditional activation**: Controller only registers when `desk.image-service.enabled=true` — no impact on existing deployments
- **Rust toolchain required**: `cargo build --release` in `desk-image/` to produce the binary

### Docker Container Support
- `desk-image/Dockerfile` — multi-stage build: `rust:1-bookworm` → `debian:bookworm-slim` (~161MB)
- Dependency caching: dummy `main.rs` compiles deps first, then real source replaces it
- `compose.yaml` — `desk-image` service alongside MySQL, port 8090, volume mount for local files
- **Environment variable overrides** for container config:
  - `DESK_IMAGE_HOST`, `DESK_IMAGE_PORT`, `DESK_IMAGE_SECRET`
  - `DESK_IMAGE_STORAGE_BACKEND`, `DESK_IMAGE_LOCAL_DIR`
  - `DESK_IMAGE_S3_REGION`, `DESK_IMAGE_S3_ENDPOINT`, `DESK_IMAGE_S3_BUCKET`, `DESK_IMAGE_S3_ACCESS_KEY`, `DESK_IMAGE_S3_SECRET_KEY`
  - `DESK_IMAGE_CACHE_ENABLED`, `DESK_IMAGE_CACHE_MAX_ENTRIES`, `DESK_IMAGE_CACHE_MAX_SIZE`

### Review Against Polopoly Reference
Compared against `polopoly/plugins/image-plugin` and `gong/desk` image handling:

**Additional features implemented after review:**
- SVG passthrough — serve raw SVGs without processing (detected by extension or XML/SVG headers)
- Aspect ratio param (`a=16:9`) — crops to target ratio with focal point centering
- `ow`/`oh` params — original image dimensions passed from desk-api to sidecar
- `X-Original-Image-Width/Height` and `X-Rendered-Image-Width/Height` response headers
- ETag headers for cache validation
- File-service scheme paths (`/image/content/...`, `/image/tmp/...`, `/image/s3/...`)

**Known gaps (lower priority):**
- Pixelation support (rarely used editorial feature)
- Workspace parameter for draft images
- Accept header content negotiation (WebP auto-conversion)
- CDN proxy redirect (OneCMSContentProxyInfo)
- Timeout/queue management for processing pipeline
- Metrics/observability endpoints

### Integration Tests (10 tests)
`ImageServiceIntegrationTest` extends `BaseIntegrationTest`:
- Auth required (401 without token)
- 404 for unknown content (unversioned, versioned, original with/without filename)
- 302 redirect with correct sidecar URL, query params (w, ow, oh), and HMAC signature
- 302 redirect for versioned content ID (`/contentid/` path)
- Original image redirect to file service (`/file/` path)
- 404 when content exists but has no `atex.Image` aspect
- Edit info passthrough (rotation, flip in redirect URL)

## Increment 34: Auth compatibility, aspect contentId format, compat-test expansion

### Auth response compatibility
- **`userId` field**: Changed from login name (`"sysadmin"`) to numeric `principalId` (`"98"`) to match reference server
- **`renewTime` field**: Now populated as `expireTime - 12 hours` (reference uses `expireTime - sessionKeyRenewalTime`)
- Applied to all auth endpoints: `POST /security/token`, `GET /security/token`, `POST /security/oauth/callback`
- Fallback: if `principalId` is NULL in DB, falls back to login name

### Aspect contentId format fix (cross-system DB compatibility)
- **Bug**: `aspects.contentid` stored a random Camflake ID (e.g., `1hopxmd0zf8v2kq8yie`)
- **Fix**: Now stores versioned content ID string `"onecms:key:version"` matching reference format
- **Why**: Reference's `CmsAspectInfoMapper` calls `CmsIdUtil.fromVersionedString()` which expects exactly 3 colon-separated parts — bare IDs caused `IllegalArgumentException`
- Critical for shared-DB deployments where both desk-api and reference OneCMS read the same content

### Compat-test expansion (`scripts/compat-test.py`)
- Added workflow rule: every new/modified endpoint must get a compat-test comparison test
- **New tests**: file upload (with 2-10MB sample images), image content creation (tmp→content commit verification), activities (locking), image service redirect, file download
- **Performance monitoring**: per-test timing, `--iterations N` for benchmarking, summary table with Avg/Min/Max/P50/P95
- **Sample images**: downloaded from learningcontainer.com (2MB, 5MB, 10MB), cached on disk
- **smoke-test.sh**: Added `_type` fields to `atex.Image` and `atex.Files` aspects for reference server compatibility

### Aspect contentId format (bug fix)
- **Original change**: aspect `contentid` changed from random Camflake to `delegation:key:version`
- **Bug**: all aspects of the same content version got the same `contentid`, violating the `aspects_contentid_UNIQUE` constraint
- **Fix**: each aspect gets `delegation:key:uniqueCamflakeId` — unique per row, still 3-part format for `CmsIdUtil.fromVersionedString()` compatibility
- **Reference format confirmed**: each aspect `version` field has its own unique content ID (verified from live reference JSON)

### Reference format findings (from live content JSON)
- `_type` values use **aspect names** (e.g. `"atex.Image"`, `"atex.Files"`) — NOT full Java class names
- `atex.Image` is a **separate aspect** from contentData (not embedded despite `storeWithMainAspect=true`)
- `atex.Image.filePath` is just the filename; full `content://` URI only in `atex.Files`
- `width`/`height` duplicated in both `contentData.data` and `atex.Image.data`

### Files changed
- `SecurityController.java` — userId→principalId, renewTime, RENEWAL_WINDOW constant
- `ContentService.java` — aspect contentId: `delegation:key:uniqueId` (unique per aspect row)
- `ActivityServiceSecured.java` — accept principalId for ownership check
- `LocalContentManager.java` — commitTemporaryFiles (tmp→content on create/update)
- `LocalFileService.java` — path normalization
- `SecurityIntegrationTest.java` — updated assertions for new response format
- `scripts/compat-test.py` — 5 new comparison tests, performance monitoring, bug fixes (login before filtered tests, correct `_type` values, Accept header for JSON)
- `scripts/smoke-test.sh` — added `_type` on atex.Image/atex.Files aspects
- `compose.yaml` — desk-api container, shared file volume, removed cache config
- `desk-image/` — removed LRU cache layer
- `CLAUDE.md` — Docker Compose docs, compat-test workflow rule

## Increment 35 — Global Object Cache Service

### Summary
Introduced a centralized `ObjectCacheService` for application-wide caching with per-type configurable TTL and max size. Replaces inline caching in `PrincipalsController` and provides a single place for future cache busting.

### Design
- **`ObjectCacheService`** (`c.a.desk.api.service`): Spring `@Service`, named caches with configurable TTL and max size
- LRU eviction via access-ordered `LinkedHashMap` with `removeEldestEntry`
- Passive TTL expiration on `get()`
- Thread-safe via `ConcurrentHashMap` + `Collections.synchronizedMap`
- API: `configure(name, ttl, maxSize)`, `get(name, key)`, `put(name, key, value)`, `evict(name, key)`, `clear(name)`, `clearAll()`, `getStats()`
- `CacheStats` record for monitoring: `totalEntries`, `activeEntries`, `maxSize`, `ttlMs`

### Cache endpoint
- **`CacheController`** at `/admin/cache`:
  - `GET /admin/cache` — cache statistics for all registered caches
  - `DELETE /admin/cache` — clear all caches
  - `DELETE /admin/cache/{cacheName}` — clear a specific cache
- Protected by existing `/admin/*` auth filter

### Dashboard integration
- Added **Object Cache** card to Overview tab showing per-cache stats (active/max entries, TTL)
- Per-cache "Clear" button and global "Clear All Caches" button
- Stats auto-refresh with overview tab (every 30s)

### PrincipalsController refactoring
- Removed inline `CachedUserMe` record and `ConcurrentHashMap` cache
- Injects `ObjectCacheService`, configures `"userMe"` cache via `@PostConstruct` (5 min TTL, 200 entries)
- Kept N+1 fix: batch `findAllById` for group lookups instead of per-group queries

### Files changed
- `ObjectCacheService.java` — new global cache service
- `CacheController.java` — new admin REST endpoint for cache management
- `PrincipalsController.java` — refactored to use ObjectCacheService
- `dashboard.html` — cache stats card with clear buttons

## Increment 35b — Search Permission Filter Fix

### Problem
Search with `permission=write` (used by mytype-new) returned 0 results. The old filter used a positive match
(`content_writers_ss:"sysadmin"`) which required the field to exist and match — but most Solr documents
don't have `content_writers_ss` at all (they're public content).

### Solution — Double-negation pattern (matching reference ContentAccessDecorator)
Changed `LocalSearchClient` to use the same double-negation filter as the reference:
```
-(content_private_b:* -content_private_b:false -content_writers_ss:(98 OR group\:7 OR group\:all))
```
This means: exclude documents that are private AND where the user is NOT in the permission list.
Public documents (no `content_private_b` field, or `content_private_b:false`) pass through automatically.

### Principal resolution
- Maps JWT `loginName` → numeric `principalId` via `AppUserRepository`
- Resolves group memberships via `AppGroupMemberRepository.findByPrincipalId()`
- Formats groups as `group:X` (matching reference `GroupId.PREFIX_STRING`)
- Adds `group:all` (matching reference `AllPrincipalsId.ID_STRING`)
- Escapes Solr special chars via `ClientUtils.escapeQueryChars()` (`:` in `group:X` → `group\:X`)
- Results cached in `ObjectCacheService` ("searchPrincipals", 5 min TTL, 200 entries)

### Files changed
- `LocalSearchClient.java` — double-negation permission filter, principal resolution with caching
- `scripts/compat-test.py` — added `test_search_permission` for `permission=write` queries

## Increment 35c — Request Metrics Dashboard

### Feature
Added live request metrics capture and visualization to the admin dashboard for debugging API requests.

### Backend
- **`RequestMetricsService`** — ring buffer (500 entries) capturing every HTTP request: method, URI, query string, status, duration, user, content type, response size. Per-URI aggregate stats (count, avg, max, last) with pattern normalization (collapses UUIDs and content IDs).
- **`RequestMetricsFilter`** — servlet filter at order -1 (before auth), captures timing for all non-static requests. Excludes actuator, swagger, static assets.
- **`RequestMetricsController`** at `/admin/requests` — `GET` returns recent requests (up to 500), `GET /stats` returns per-URI aggregates, `DELETE` clears all metrics.

### Dashboard
- New **Requests** tab with two sections:
  - **URI Stats** — aggregated table showing request pattern, count, avg/max/last latency
  - **Recent Requests** — live scrolling table with method badges, status code badges (color-coded 2xx/3xx/4xx/5xx), duration highlighting (green <50ms, amber <200ms, red >200ms), user, and click-to-expand detail panel showing query parameters
- Text filter for quick search across method, URI, status, user
- Auto-refreshes every 3 seconds when active
- Clear all button to reset metrics

### Files changed
- `RequestMetricsService.java` — new service with ring buffer and aggregate stats
- `RequestMetricsFilter.java` — new servlet filter for timing capture
- `RequestMetricsController.java` — new admin REST controller
- `AuthConfig.java` — registers RequestMetricsFilter at order -1
- `dashboard.html` — new Requests tab with live metrics visualization

## Increment 35d — Search Serialization Fix + Parallel Content Inlining

### Search JSON serialization bug
`SearchServiceUtil.createChild` was appending child elements to their parent BEFORE populating them with values.
The JSON factory's `extractValue()` creates copies, so parents received empty `{}` for every doc field and nested structure.

**Root cause**: The `ChildFactoryJSON.appendChild(parent, child)` calls `extractValue(child)` which strips metadata
and returns a NEW object. But `createChild` was calling `appendChild(parent, child)` before setting values on the child.

**Fix**: Reordered all `createChild` branches to populate children fully before appending to parent:
- NamedList, SolrDocumentList, Map, List, Object[] — populate contents first, then `appendChild`
- Primitives (String, Integer, Long, etc.) — set `_value` first, then `appendChild`

### Parallel content inlining (variant=list)
`inlineContentData` was resolving content for each search doc sequentially (2 DB queries per doc).
With 50 rows: ~15ms × 50 = 783ms.

**Fix**: Uses Java 25 virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) to resolve all docs concurrently.
Result: 783ms → ~120ms for 50 rows with `variant=list`.

### Response size tracking
`RequestMetricsFilter` now wraps the response with `CountingResponseWrapper` that counts bytes written through
both `ServletOutputStream` and `PrintWriter`, giving accurate response sizes in the dashboard.

### Files changed
- `SearchServiceUtil.java` — fixed child-before-parent ordering for all container/primitive types
- `SearchController.java` — parallel content inlining via virtual threads
- `RequestMetricsFilter.java` — `CountingResponseWrapper` for response size tracking, excluded dashboard polling endpoints
- `RequestMetricsService.java` — changed `responseSize` from `Long` to `long`
- `scripts/compat-test.py` — added `test_search_variant_list` for variant=list with 50 rows

## Increment 36 — Content Operations, Changelist Migration, Metadata Service

### Content Operations (trash/untrash/archive)
Ported from gong/desk. Three new endpoints on `DamDataResource`:
- `POST /dam/content/trash/{id}` — sets item state to SPIKED via `PrestigeItemStateAspectBean`
- `POST /dam/content/untrash/{id}` — sets item state back to PRODUCTION
- `POST /dam/content/archive/{id}` — sets `dimension.partition` to `archive` in `p.Metadata`

These trigger the existing `HandleItemStatePreStore` hook chain which handles security parent moves automatically.

### mytype-new Routing Fixes
- `POST /dam/content/unpublish/{id}` — path-variable variant (mytype-new sends contentId in path, not query param)
- `POST /dam/content/duplicate` — accepts JSON array `[contentId]` (mytype-new format; existing `PUT` with `{entries:[...]}` preserved)
- `POST /dam/content/clearengage/{id}` — path-variable variant delegating to existing query-param endpoint

### Changelist Migration (changelist → adm_changelist)
desk-api and the reference adm-content-service share the same MySQL database. The reference uses `adm_changelist` (V2 with denormalized columns); desk-api had its own `changelist` (V1). Migrated to use the shared table:
- `ChangeListEntry.java` — `@Table(name = "adm_changelist")`, added 5 `attr_*` columns (insertParentId, securityParentId, objectType, inputTemplate, partition)
- `ChangeListService.recordEvent()` — populates denormalized columns via `populateDenormalizedAttrs()` (objectType, inputTemplate, insertParentId, securityParentId, partition); partition is lowercased per reference behavior
- `ChangeListService.queryChanges()` — reads from denormalized `attr_*` columns directly (no more JOIN to `changelistattributes` for objectType/partition filters)
- `V1__baseline.sql` — `changelist` table replaced with `adm_changelist` including denormalized columns
- Indexer (`SolrIndexProcessor`) uses JPQL so automatically picks up the table change

### Metadata Service (new)
4 endpoints for taxonomy/tag operations used by mytype-new's pTags widget:

| Method | Path | Behaviour |
|--------|------|-----------|
| GET | `/metadata/structure/{id}?depth=N` | Taxonomy/dimension structure — queries Solr for tags in dimension |
| GET | `/metadata/complete/{dim}/{prefix}?format=json` | Autocomplete — Solr faceted search on `tags_autocomplete_{dim}` field |
| POST | `/metadata/annotate` | Text annotation — searches for matching tag names in text |
| POST | `/metadata/lookup` | Content-backed entity resolution — resolves entity IDs via content manager |

**Architecture**:
- `MetadataController` at `/metadata` — REST endpoints, Gson serialization with `LOWER_CASE_WITH_DASHES` field naming
- `MetadataService` — Solr-backed taxonomy queries, faceted autocomplete, annotation, entity resolution
- `DamTag`, `DamTagMetadata` — ported from gong/onecms-common (content aspect `atex.onecms.metadata.Tag`)
- `Entity` — enhanced with `entities` (children) and `localizations` fields for hierarchical taxonomy support
- Auth filter updated to include `/metadata/*`
- `SolrService` is `@Nullable` — metadata endpoints degrade gracefully without Solr

### Image Metadata Service (Docker)
Added `image-metadata` container to `compose.yaml`:
- Pre-built image: `docker-registry.atex.com/atex/image-metadata-service:2.0.0`
- Node.js service wrapping exiftool for EXIF/IPTC/XMP extraction and injection
- `OneImagePreStore` updated to call `GET /extractor/image/{fileUri}` and populate `MetadataTagsAspectBean`
- desk-api environment: `DESK_IMAGE_METADATA_SERVICE_URL`, `DESK_IMAGE_METADATA_SERVICE_ENABLED`

### Files changed
- `DamDataResource.java` — trash/untrash/archive/clearengage/unpublish/{id}/duplicate POST endpoints
- `ChangeListEntry.java` — table + denormalized columns
- `ChangeListService.java` — denormalized writes + reads
- `V1__baseline.sql` — adm_changelist DDL
- `MetadataController.java` — new controller (4 endpoints)
- `MetadataService.java` — new service
- `DamTag.java`, `DamTagMetadata.java` — new beans (ported)
- `Entity.java` — enhanced with entities/localizations
- `AuthConfig.java` — added /metadata/* to auth filter
- `OneImagePreStore.java` — metadata extraction via HTTP
- `compose.yaml` — image-metadata container + desk-api config
- `ContentOpsIntegrationTest.java` — new (10 tests for trash/untrash/archive/clearengage)