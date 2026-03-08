# Desk API

## Project Overview
Spring Boot 4.0.3 API (Java 25) porting features from the Atex/Polopoly platform.

- **Group**: `com.atex.cloud` / **Package**: `com.atex.desk.api`
- **Database**: MySQL 8.0 (port 33306, database `desk`)
- **ORM**: Hibernate 7.2.4 with JPA, Flyway migrations
- **Build**: Gradle, GraalVM native image support
- **Main class**: `DeskApiApplication`

## Key Technical Notes
- **Jackson 3.0** in Spring Boot 4: packages `tools.jackson.core` / `tools.jackson.databind` (NOT `com.fasterxml.jackson`)
- **Exception**: `tools.jackson.core.JacksonException` (NOT `JsonProcessingException`)
- **DamDataResource uses Gson** internally (not Jackson) — DAM beans are Gson-compatible
- **JJWT uses Gson backend** (not Jackson, to avoid Jackson 2 vs 3 conflict)
- **SolrJ**: `HttpSolrClient` (NOT `Http2SolrClient` — Jetty 11 vs 12 incompatibility)
- **Hibernate naming**: CamelCase MySQL tables need backtick quoting in `@Table`/`@Column` annotations
- **Java 25 module system**: Gson cannot reflectively access JDK internal classes (`SimpleDateFormat`, etc.). Don't add JDK types as instance fields on Gson-serialized beans — use `transient` or remove
- **JDK path**: `C:\Users\peter\.jdks\openjdk-25.0.2` (JAVA_HOME must be set for Gradle)

## Source Codebases (porting references)

| Codebase | Path | Description |
|---|---|---|
| Polopoly | `../polopoly` | Core platform — foundational layer |
| Gong | `../gong` | Next core layer; `gong/desk` = DESK-specific code |
| ADM Content Service | `../adm-content-service` | Content API definition |
| ADM Starterkit | `../adm-starterkit` | Project framework / starter template |
| OneCMS Lib | `../onecms-lib` | Shared JS/Java library |
| OneCMS Widgets | `../onecms-widgets` | UI widgets — reference for service APIs |
| Reporter Tool | `../reporter-tool` | Legacy AngularJS editorial SPA |
| mytype-new | `../mytype-new` | New reference client replacing DESK UI |

Dependency order: `polopoly → gong → adm-starterkit / adm-content-service`

## Architecture

```
desk-api (Spring Boot 4)
  ├─ ContentController (/content/*)         — OneCMS-compatible CRUD (Inc 1, 18a, 23)
  ├─ ContentResolveController (/content/*)  — External ID & view resolution (Inc 32)
  ├─ TypeController (/content/type/*)       — Content type schemas (Inc 31)
  ├─ DamDataResource (/dam/content)         — 68 endpoints from gong/desk (Inc 3)
  ├─ SecurityController (/security/*)       — JWT auth (Inc 2)
  ├─ PrincipalsController (/principals/*)   — User management (Inc 18b)
  ├─ FileController (/file/*)               — File upload/download (Inc 16)
  ├─ FileDeliveryController (/filedelivery) — Public file serving with Range (Inc 32)
  ├─ ActivityController (/activities/*)      — Content locking (Inc 16)
  ├─ SearchController (/search/*)           — Solr search (Inc 10)
  ├─ PreviewController (/preview/*)         — Web preview (Inc 17)
  ├─ WorkspaceController (/workspace/*)     — Draft workspaces
  ├─ ChangesController (/changes/*)         — Change feed
  ├─ ConfigurationController (/admin/config) — Config CRUD
  ├─ ReindexController (/admin/reindex)     — Solr indexing management (Inc 6, 20)
  └─ dashboard.html                         — Admin UI (Inc 24)
```

### Core Layers
| Layer | Package | Key Classes |
|---|---|---|
| Entities (12) | `c.a.desk.api.entity` | `IdType`, `ContentId`, `ContentVersion`, `Content`, `Aspect`, `AspectLocation`, `View`, `ContentView`, `Alias`, `ContentAlias` |
| Repositories (10) | `c.a.desk.api.repository` | Spring Data JPA with JPQL queries |
| DTOs (7) | `c.a.desk.api.dto` | `ContentResultDto`, `AspectDto`, `ContentWriteDto`, `MetaDto`, `ErrorResponseDto` |
| Service | `c.a.desk.api.service` | `ContentService` (CRUD, resolve, alias, purge), `IdGenerator` (Camflake), `TypeService` (type schema from annotations) |
| OneCMS Compat | `c.a.desk.api.onecms` | `LocalContentManager`, `LocalPolicyCMServer`, `LocalCmClient`, `LocalFileService` |
| Auth | `c.a.desk.api.auth` | `TokenService`, `AuthFilter`, `AuthConfig` (RS256 JWT) |
| Plugins | `c.a.desk.api.plugin` | PF4J 3.15.0, `DeskPreStoreHook`, `DeskContentComposer` |
| Indexing | `c.a.desk.api.indexing` | `ContentIndexer`, `DamIndexComposer`, `SolrIndexProcessor` |
| File Service | `c.a.desk.api.file` | `FileServiceAutoConfiguration`, `S3FileService`, `DelegatingFileService` (local/S3/hybrid) |
| DAM Classes | `c.a.onecms.app.dam.*` | ~90 classes preserving original packages from gong/desk |

### Content ID Format
- **Unversioned**: `delegationId:key` (e.g., `onecms:some-uuid`)
- **Versioned**: `delegationId:key:version` (e.g., `onecms:some-uuid:version-uuid`)
- `delegationId` maps to `idtype.name` in DB; `key` maps to `id.id` (VARCHAR 255)
- Legacy `policy:X.Y` IDs resolve via `policyId` alias fallback

### Auth
- `X-Auth-Token` header (JWT RS256), claims: `sub`, `scp` (READ,WRITE,OWNER), `aud`, `exp`
- Filter URL patterns: `/content/*`, `/dam/*`, `/principals/*`, `/admin/*`, `/search/*`, `/changes/*`, `/layout/*`, `/file/*`, `/activities/*`, `/preview/*`
- Permission checking: OWNER → all; strips `"21"` ACL prefix; READ/WRITE scope matching

### OneCMS Compatibility
- Error format: `{"extraInfo":{},"statusCode":40400,"message":"NOT_FOUND"}` (HTTP status × 100)
- Redirects: 303 See Other with JSON body `{"statusCode":"30300","message":"...","location":"..."}`
- If-Match required for PUT/DELETE (quoted ETag: `"onecms:id:version"`)
- Content creation auto-creates: `contentData`, `atex.WFContentStatus`, `atex.WebContentStatus`, `p.InsertionInfo`, `atex.Metadata`
- `BeanTypeRegistry` maps 19 `_type` strings to bean classes for field defaults via Gson round-trip

### PreStoreHook Chain (Increment 6)
Registered by `BuiltInHookRegistrar`, wildcard `*` hooks run first:
- `*`: OneContentPreStore → HandleItemStatePreStore
- Image: OneImagePreStore → SecParent → SetStatus → AddEngagement
- Article: SecParent → SetStatus → WordCount → CharCount → AddEngagement
- Audio/Collection: similar chains
- PF4J plugin hooks appended after built-ins

### Bean Hierarchy (Increment 19)
```
OneContentBean → OneArchiveBean → OneArticleBean / OneImageBean / DamCollectionAspectBean / ...
DamContentBean (legacy, deprecated) → DamArchiveAspectBean → DamArticleAspectBean / ...
```
`StructuredText` for rich text fields (headline, lead, body). 19 content types registered.

### Database Migrations (Increment 22)
Flyway: V1 = legacy baseline (23 tables), V2 = desk-api additions (Java migration).
New migrations: SQL in `src/main/resources/db/migration/`, Java in `c.a.desk.api.migration`.

### Site Structure Variant
`GET /content/contentid/{id}?variant=atex.onecms.structure&excludedSites=...` builds a recursive site tree.
- **Beans**: `PageBean`, `SiteBean`, `SiteStructureBean` in `c.a.onecms.app.siteengine` (ported from polopoly/gong, using OneCMS `ContentId` not Polopoly)
- **Service**: `SiteStructureService` in `c.a.desk.api.site` (ported from `SiteStructureUtils` in gong/site)
- **ID resolution**: supports `delegationId:key`, versioned `a:b:c`, and external IDs (no colons)
- **excludedSites**: comma-separated IDs/external IDs filtered from tree (with parent chain walk)
- **Response**: `ContentResultDto` format with `contentData.data.children` array and synthesized `atex.Aliases` aspect from `meta.aliases`

### Type Service (Increment 31)
`GET /content/type/{typeName}` returns `ModelTypeBean` JSON with field introspection.
- **TypeService**: scans `@AspectDefinition` and `@AceAspect` annotations at startup, introspects bean fields
- **TypeController**: separate controller at `/content/type`, returns cached responses (1hr)
- **Plugin override**: `TypeService.registerType()` allows plugins to replace core type definitions
- **Format**: `{_type, typeName, typeClass:"bean", attributes:[{name, typeName, modifiers}], beanClass, addAll:"true"}`

### Publishing Pipeline
`DamPublisherFactory` → `DamPublisherBuilder` → `DamPublisherImpl` → `DamBeanPublisherImpl` → `ContentAPIPublisher` → Remote CMS HTTP

### Configuration System
Config files in `src/main/resources/config/`, synced via `scripts/config-sync.py` from source XML.
3-tier: product (file) → project (file) → live (DB override). `ConfigMeta` (name, group, format) and `ConfigEntry` (data, meta, contentList) records.

**Format-aware serialization** via `_meta.format` field (matches Polopoly input-template names):
- `atex.onecms.Template.it` → `OneCMSTemplateBean` format: `{data: "<stringified JSON>"}`
- `atex.onecms.TemplateList.it` → `OneCMSTemplateListBean` format: `{templateList: [...]}`
- Default → `ConfigurationDataBean` wrapping: `{json: "<stringified>", name, dataType, dataValue}`

**Template catalog**: `p.onecms.DamTemplateList` → 43 template refs; crawled by mytype-new's `damTemplateCatalogService.ts`.

**Profile endpoint**: `GET /configuration/profile?profile={name}` serves merged config for a named profile.

## Tests

```bash
# Integration tests (requires Docker)
JAVA_HOME=C:/Users/peter/.jdks/openjdk-25.0.2 ./gradlew test

# Specific test class
JAVA_HOME=C:/Users/peter/.jdks/openjdk-25.0.2 ./gradlew test --tests "*ContentCrudIntegrationTest"

# Compatibility tests against reference server
python scripts/compat-test.py --desk-only
```

74 integration tests (Testcontainers MySQL): Security, ContentCrud, ContentVersioning, Alias, FileService, Principals, Workspace, ErrorResponse, SiteStructure, TypeService.

## Remaining Stubs / Not Implemented
- 501 endpoints: `create-page`, `sendcontent`, `assigncontent`, `mergeMultiplePdf`, `collectionpreview`
- Empty stubs: `ModuleImporter`, `CollectionToGallery`, `CamelEngine`, `SolrPrintPageService`
- Service stubs: `AyrShareService`, `TagManagerService`, `PublicationsService`, `AtexPdfium`
- P3 compat issue: Changes feed empty response format

## Workflow Rules (mandatory after every increment)
1. **Review against reference**: verify the implementation against the original source (polopoly/gong) to ensure no features, fields, or edge cases were missed
2. **Update/add tests**: ensure integration tests cover the new functionality, including edge cases found during review
3. **Update docs**: update CLAUDE.md summary and append details to `docs/increments.md`
4. **Commit and push**: stage relevant files, commit with descriptive message, push to remote

These steps are automatic — do not wait to be asked.

## Detailed Documentation
- **[docs/increments.md](docs/increments.md)** — Full per-increment implementation details
- **MEMORY.md** files in `.claude/projects/` — Cross-session notes