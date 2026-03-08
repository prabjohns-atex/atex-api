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

### New Files (2)

| File | Description |
|------|-------------|
| `controller/TypeController.java` | `GET /content/type/{typeName}` endpoint |
| `service/TypeService.java` | Classpath scan, bean introspection, ModelTypeBean generation |

### Design Notes

- **Plugin override**: `TypeService.registerType()` allows plugins to override core type definitions at runtime
- **@AceAspect support**: Scanner also picks up `@AceAspect`-annotated beans (3 ACE metadata types)
- **Generic types**: Getter return types include generic parameters (e.g., `java.util.List<com.atex.onecms.content.ContentId>`) matching Polopoly's format
- **No database storage**: Types are generated from annotations at startup — no migration or content import needed