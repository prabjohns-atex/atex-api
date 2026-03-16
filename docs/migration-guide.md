# Migrating ADM Projects to desk-api

Guide for migrating projects built on adm-starterkit to run on desk-api.

---

## Background

The legacy stack runs each publisher as a full Polopoly/Gong deployment:

```
adm-starterkit (template)
  -> adm-mpp, adm-bt, adm-irishnews, ... (per-publisher instances)
     Each deploys: 7 WAR files + Solr + MySQL + Tomcat containers
```

desk-api replaces the entire server-side stack with a single Spring Boot application
plus lightweight sidecars (desk-image, image-metadata-service).

The goal: take a project's custom code and run it inside desk-api as a plugin,
eliminating the WAR-per-service architecture.

---

## What ADM Projects Customize

Based on analysis of adm-mpp (a large newspaper project with 160+ custom Java classes):

| Category | Typical Count | Example |
|---|---|---|
| Custom content beans | 10-16 classes | CustomArticleBean, CustomImageBean |
| PreStore lifecycle hooks | 10-15 classes | ContentAccessPreStoreHook, Article2ImageChannelPreStore |
| Solr index mappers | 2-5 classes | CustomContentToSolrJsonMapper |
| Camel ingest processors | 20-50 classes | AnsaParser, ReutersParser, AFPArticleParser |
| Publishing/export handlers | 3-5 classes | CustomAceArticleResourceHandler |
| REST endpoints | 1-3 classes | MyTypeResource (permissions/config) |
| Type converters | 3-5 classes | CustomEpochLongIsoStringConverter |
| Configuration files | 20-50 files | Solr schemas, ACT/DESK config, connection.properties |
| Content definitions | 50-100 XML files | Input templates, UI config, localization |
| JSLT/XSLT transforms | 5-10 files | Wire feed transformation rules |

---

## Migration Strategy: Two Approaches

### Approach A: Plugin JAR (recommended for most projects)

Package project customizations as a PF4J plugin JAR deployed to desk-api's `plugins/` directory.

```
desk-api/
  plugins/
    adm-mpp-plugin-1.0.jar    <-- all project-specific code
  config/
    project/                   <-- project config overrides
```

**Pros**: Clean separation, no desk-api source changes, hot-deployable, multi-tenant possible.
**Cons**: Limited to existing extension points (hooks, composers, types).

### Approach B: Embedded Dependency (for deeply customized projects)

Add desk-api as a Gradle/Maven dependency and build a project-specific Spring Boot app.

```
adm-mpp-api/
  build.gradle               <-- depends on desk-api
  src/main/java/
    com/atex/mpp/            <-- project-specific code as Spring components
  src/main/resources/
    config/project/           <-- project config overrides
```

**Pros**: Full Spring context access, can add REST controllers, custom beans, any extension.
**Cons**: Requires building a custom application, harder to upgrade desk-api.

### Recommendation

Use **Approach A (Plugin JAR)** for most projects. Only use Approach B if the project
needs custom REST endpoints or deep Spring integration that plugins cannot provide.

The plugin system needs a few enhancements first (see Phase 1 below).

---

## Phase 1: Extend desk-api Plugin Framework

Before projects can migrate, desk-api needs these additional extension points:

### 1.1 DeskIndexComposer (new extension point)

Allow plugins to add custom fields to Solr documents during indexing.

```java
public interface DeskIndexComposer extends ExtensionPoint {
    String[] contentTypes();  // which types this applies to
    void compose(ContentResult<Object> content,
                 Map<String, Object> solrFields);  // add fields to map
}
```

**Why**: Every ADM project has custom Solr field mappers (e.g., CustomContentToSolrJsonMapper)
that extract project-specific fields like `premiumtype_atex_desk_s`, `article_edition_atex_desk_s`.

**Integration**: DamIndexComposer calls all registered DeskIndexComposer plugins after
building the base document. Plugin fields are added to the same SolrInputDocument.

### 1.2 DeskContentValidator (new extension point)

Allow plugins to validate content before persistence (separate from transformation).

```java
public interface DeskContentValidator extends ExtensionPoint {
    String[] contentTypes();
    void validate(ContentWrite<Object> input,
                  Content<Object> existing) throws ValidationException;
}
```

**Why**: Some hooks are purely validation (ContentAccessPreStoreHook checks permissions,
not transforms). Separating validation from transformation makes intent clearer.

### 1.3 Plugin Spring Context Bridge

Allow plugin JARs to declare Spring-compatible components that desk-api discovers.

```java
// In plugin JAR
@DeskPlugin
public class MppPlugin extends Plugin {
    @Override
    public void start() {
        // Register custom beans, REST controllers, etc.
    }
}
```

**Alternative**: Scan plugin classloaders for `@RestController`, `@Service` annotations
and register them in the Spring context at startup.

**Why**: Projects like adm-mpp have custom REST endpoints (MyTypeResource) and services
that need Spring DI. Without this, Approach B is required for any project with custom endpoints.

### 1.4 DeskPublishHandler (new extension point)

Allow plugins to handle content export to external systems.

```java
public interface DeskPublishHandler extends ExtensionPoint {
    String[] contentTypes();
    PublishResult publish(ContentResult<Object> content,
                          String target,
                          Subject subject);
}
```

**Why**: Projects have custom ACE publishers, Hermes composers, and other export handlers.

### 1.5 Integration Extension Points (deferred to desk-integration)

Content integration (ingest + distribution) runs as a **separate project** (`desk-integration`)
that depends on desk-api as a library. Integration extension points live in desk-integration,
not in desk-api's PF4J plugin framework.

See [Phase 5](#phase-5-migrate-content-integration-ingest--distribution) for the full architecture.

**No extension point needed in desk-api itself** — desk-integration has its own Spring context
and can define its own extension mechanisms (e.g., pluggable parsers, custom route builders).

---

## Phase 2: Migrate Custom Content Types

### 2.1 Custom Bean Classes

ADM projects extend base beans with project-specific fields:

```java
// Legacy (adm-mpp)
@AspectDefinition(aspectName = "atex.onecms.article",
                  policyType = "atex.DamNewArticlePolicy")
public class CustomArticleBean extends OneArticleBean {
    private String customContentType;
    private String channel;
    private String edition;
    // ... 20+ custom fields
}
```

**Migration**: Package in plugin JAR under `com.atex.plugins.*` package.
TypeService already scans this package for `@AspectDefinition` annotations.

```java
// Plugin JAR (desk-api)
@AspectDefinition(aspectName = "atex.onecms.article")
public class CustomArticleBean extends OneArticleBean {
    // Same fields, same annotations
    // No code changes needed
}
```

**Action**: TypeService.registerType() override — last registration wins.
Plugin beans replace core beans for the same aspect name.

### 2.2 BeanTypeRegistry Extension

The plugin's custom beans need to be registered in BeanTypeRegistry
so that `_type` string mapping works for Gson deserialization.

**Implementation**: Add a `DeskBeanTypeProvider` extension point, or have PluginLoader
scan plugin classes for `@AspectDefinition` and auto-register.

### 2.3 Type Converters (Dozer replacements)

Legacy projects use Dozer converters for field mapping. desk-api uses Gson.

**Migration**: Replace Dozer converters with Gson TypeAdapters or custom serializers
registered via the plugin. Most converters are simple date/string formatting.

---

## Phase 3: Migrate Lifecycle Hooks

### 3.1 PreStore Hooks

Plugins implement `LifecyclePreStore<Object, Object>` — the same interface used by
built-in hooks. This means plugins can extend built-in hook classes directly.

```java
// Legacy (adm-mpp)
public class CustomOneContentPreStoreHook
    extends OneContentPreStore {
    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input,
            Content<Object> existing, LifecycleContextPreStore<Object> ctx)
            throws CallbackException {
        // Call super to keep original logic, then add custom behavior
        input = super.preStore(input, existing, ctx);
        // ... custom logic ...
        return input;
    }
}

// Plugin JAR (desk-api) — same class, same interface, just add @Extension
@Extension
public class CustomOneContentPreStoreHook extends OneContentPreStore {
    @Override
    public String[] contentTypes() { return new String[]{"*"}; }

    @Override
    public ContentWrite<Object> preStore(ContentWrite<Object> input,
            Content<Object> existing, LifecycleContextPreStore<Object> ctx)
            throws CallbackException {
        input = super.preStore(input, existing, ctx);
        // ... custom logic ...
        return input;
    }
}
```

**Key points**:
- Plugins use `LifecyclePreStore` directly (same as built-in hooks, not a separate interface)
- `LifecycleContextPreStore` provides `getContentManager()`, `getSubject()`, `getFileService()`
- Override `contentTypes()` to declare which content types the hook applies to

### 3.2 Replacing Built-in Hooks

**Automatic (via inheritance)**: When a plugin hook extends a built-in hook class,
the built-in is automatically removed from the chain. The plugin can call
`super.preStore()` to extend, or override completely to redefine.

```java
// Plugin extends OneContentPreStore — built-in is automatically replaced
@Extension
public class CustomOneContentPreStoreHook extends OneContentPreStore {
    // super.preStore() available if you want to keep original logic
}
```

**Explicit (via `@Replaces`)**: For cases where inheritance isn't practical —
e.g., replacing multiple hooks with a single combined hook:

```java
@Extension
@Replaces({SecParentPreStoreHook.class, SetStatusPreStoreHook.class})
public class CombinedSecStatusHook implements LifecyclePreStore<Object, Object> {
    public String[] contentTypes() { return new String[]{"atex.onecms.article"}; }
    public ContentWrite<Object> preStore(ContentWrite<Object> input,
            Content<Object> existing, LifecycleContextPreStore<Object> ctx)
            throws CallbackException {
        // Combined logic replacing both hooks
    }
}
```

**Without extending/`@Replaces`**: plugin hooks are appended after built-in hooks (both run).

**Startup logging** confirms replacements:
```
Plugin CustomOneContentPreStoreHook extends OneContentPreStore — replaced 1 built-in registration(s)
```

### 3.3 Hook Ordering

Legacy hook ordering is defined in Spring/Polopoly configuration.
desk-api hooks run in registration order: built-in first, then plugins.

**Migration**: If ordering matters, add an `@Order` annotation or `int priority()`
method to `DeskPreStoreHook`.

### 3.4 Configuration-Driven Hooks

Some hooks (ContentAccessPreStoreHook) load handler classes from configuration.
These need access to desk-api's ConfigurationService.

**Migration**: Pass ConfigurationService to hooks, or allow hooks to declare
dependencies via a plugin context object.

---

## Phase 4: Migrate Solr Indexing

### 4.1 Custom Index Mappers

Legacy mappers implement `ContentToSolrJsonMapper`:

```java
// Legacy
public class CustomContentToSolrJsonMapper extends OneContentToSolrJsonMapper {
    protected void addCustomFields(SolrInputDocument doc, OneContentBean bean) {
        if (bean instanceof CustomArticleBean custom) {
            doc.addField("premiumtype_atex_desk_s", custom.getPremiumType());
            doc.addField("article_edition_atex_desk_s", custom.getEdition());
        }
    }
}
```

**Migration** (using new DeskIndexComposer):

```java
@Extension
public class MppIndexComposer implements DeskIndexComposer {
    public String[] contentTypes() { return new String[]{"atex.onecms.article"}; }
    public void compose(ContentResult<Object> content, Map<String, Object> fields) {
        Object data = content.getContent().getContentData();
        if (data instanceof CustomArticleBean custom) {
            fields.put("premiumtype_atex_desk_s", custom.getPremiumType());
            fields.put("article_edition_atex_desk_s", custom.getEdition());
        }
    }
}
```

### 4.2 Solr Schema

Custom Solr field definitions go in project configuration:

```
config/project/solr/onecms/schema-overlay.xml
```

desk-api's SolrService applies schema overlays on core creation.
Or: use dynamic fields (`*_atex_desk_s`, `*_atex_desk_ss`) which require no schema changes.

---

## Phase 5: Migrate Content Integration (Ingest + Distribution)

### Legacy Architecture

The current integration server is a **Polopoly-embedded Camel 2.24 application** deployed as a WAR
inside a Tomcat container. It:

- Runs inside the Polopoly Application Framework (not standalone)
- Connects to CMServer directly (in-process RMI, not HTTP)
- Uses `ContentManager.create()` for content creation (same API as desk-api's LocalContentManager)
- Monitors local filesystem directories for wire feed files
- Runs ~40 Camel routes defined in `applicationContext.xml`
- Uses Quartz2 for scheduled tasks (web status updates, purging, cache invalidation)
- Dynamically loads routes from CMS content (`dam.customRoutes`)
- Publishes events to Kafka/EventBridge

**Key dependencies**: Camel 2.24 (core, spring, jms, mail, http, kafka, zipfile, quartz2, saxon, exec),
Polopoly Application Framework, Spring 5, JSLT, Saxon, Google HTTP Client.

### Architecture Decision: Separate Project, Shared Library

`desk-integration` is a **separate Spring Boot application** in its own project/repo that depends on
desk-api as a compile-time library. It runs as a separate container alongside desk-api.

```
desk-integration (separate container)
  → depends on desk-api JAR (compile dependency)
  → has direct in-process access to ContentManager, beans, hooks
  → adds Camel 4 or simple scheduled polling on top
  → no HTTP overhead for bulk ingest
```

**Why not same codebase ("integration mode")?**
Camel 4 + wire feed dependencies (JSLT, Saxon, mail, Kafka, JMS) add ~30+ MB of JARs and
lifecycle complexity. 90% of desk-api deployments don't run ingest — forcing them to carry
these dependencies wastes resources and increases attack surface.

**Why not HTTP-only (separate service calling REST API)?**
Bulk ingest (100s of articles/images per batch) would have significant HTTP overhead.
Wire feed processors need direct `ContentManager.create()` access to trigger the full
hook chain (PreStore hooks, indexing, engagement tracking). Re-implementing that over
HTTP would be fragile and slow.

**Why separate project depending on desk-api?**
- Processors need `ContentManager`, bean classes, hooks — all already in desk-api
- Compile dependency gives direct in-process access, same as legacy CMServer integration
- Separate container: doesn't bloat desk-api, scales independently, can restart without affecting API
- Same Java 25, same bean classes (CustomArticleBean, etc.) — no serialization boundary
- Clean dependency direction: `desk-integration → desk-api` (one way)

### desk-api Module Structure (future)

Initially, desk-integration depends on the desk-api bootJar directly, component-scanning
the service/entity packages it needs. When this gets unwieldy, extract a library module:

```
desk-api/
  desk-core/            ← entities, services, beans, ContentManager, hooks, indexing
    build.gradle        ← java-library plugin
  desk-web/             ← controllers, filters, REST endpoints, auth
    build.gradle        ← spring-boot plugin, depends on desk-core

desk-integration/
  build.gradle          ← spring-boot plugin, depends on desk-core + Camel 4
  src/main/java/
    com/atex/integration/
      DeskIntegrationApplication.java  ← @SpringBootApplication
      config/
        IntegrationProperties.java     ← feed directories, schedules, feature flags
      route/
        WireFeedRouteBuilder.java    ← Camel 4 routes (or Spring @Scheduled)
        ScheduledTasks.java          ← Quartz/scheduled maintenance tasks
      processor/
        ArticleProcessor.java        ← wire article parsing + ContentManager.create()
        ImageProcessor.java          ← wire image handling
        VideoProcessor.java          ← wire video handling
      parser/
        AFPArticleParser.java        ← AFP XML parsing (plain Java, no Camel)
        ReutersParser.java           ← Reuters XML parsing
        APParser.java                ← AP wire format parsing
        AnsaParser.java              ← ANSA feed parsing
        PTIParser.java               ← PTI text format parsing
      util/
        WireContentMapper.java       ← maps parsed content → bean + aspects
        WireImageDownloader.java     ← fetches images from wire URLs
        StructuredTextBuilder.java   ← builds StructuredText from wire content
```

### Camel 4 vs Simple Polling

Most legacy Camel routes are simple directory watchers:

```
file:{{desk.base}}/TXT_REUTERS → articleProcessor('REUTERS')
file:{{desk.foto.base}}/IMG_AP → imageProcessor('AP')
```

This can be implemented without Camel at all using Spring's `@Scheduled` + `WatchService`
or even basic directory polling. The actual parsing logic (AFPArticleParser, ReutersParser, etc.)
is plain Java — the Camel parts are thin wrappers.

**Use Camel 4 if**: you need complex routing (JMS, Kafka, mail endpoints), error handling
with dead letter channels, dynamic route loading from config, or Quartz scheduling.

**Use simple polling if**: feeds are file-based directory watching only. Simpler to maintain,
fewer dependencies, faster startup.

**Recommendation**: Start with simple Spring `@Scheduled` polling. Add Camel 4 only if
routing complexity demands it. The parser classes work either way.

### Migration Steps

#### 5.1 Create desk-integration project

```groovy
// desk-integration/build.gradle
plugins {
    id 'org.springframework.boot' version '4.0.3'
    id 'java'
}

dependencies {
    implementation files('../desk-api/build/libs/desk-api-0.0.1-SNAPSHOT.jar')
    // Or when desk-core is extracted:
    // implementation project(':desk-core')

    // Only if using Camel 4:
    // implementation 'org.apache.camel.springboot:camel-spring-boot-starter:4.x'
    // implementation 'org.apache.camel:camel-file:4.x'
}
```

#### 5.2 Port processors (Camel 2 → plain Java)

Legacy processors extend Camel's `Processor` interface and receive `Exchange` objects.
The actual work is plain Java: parse XML/text, build beans, call ContentManager.

```java
// Legacy (Camel 2, in server-integration WAR)
public class ArticleProcessor extends BaseFeedProcessor implements Processor {
    public void process(Exchange exchange) throws Exception {
        File file = exchange.getIn().getBody(File.class);
        ContentManager cm = getContentManager();  // from Polopoly Application
        // ... parse file, create content via cm.create()
    }
}

// desk-integration (plain Java, no Camel dependency needed)
@Service
public class ArticleProcessor {
    private final ContentManager contentManager;  // injected from desk-api

    public void process(Path file, String source) {
        // Same parsing logic, same ContentManager.create() calls
        // Bean classes (CustomArticleBean etc.) come from desk-api dependency
    }
}
```

#### 5.3 Port wire feed parsers

Parsers are already plain Java (no Camel dependency). Copy and adapt:

| Legacy Class | desk-integration Class | Changes Needed |
|---|---|---|
| AFPArticleParser | AFPArticleParser | None (plain XML parsing) |
| ReutersParser | ReutersParser | None (plain XML parsing) |
| APParser | APParser | None (plain text parsing) |
| AnsaParser | AnsaParser | None (plain XML parsing) |
| PTIParser | PTIParser | None (plain text parsing) |
| FIEGParser | FIEGParser | None (plain XML parsing) |

#### 5.4 Port directory watching

```java
// desk-integration: simple scheduled polling
@Component
public class WireFeedPoller {
    private final ArticleProcessor articleProcessor;
    private final ImageProcessor imageProcessor;
    private final IntegrationProperties props;

    @Scheduled(fixedDelay = 5000)  // poll every 5 seconds
    public void pollArticleFeeds() {
        for (var feed : props.getArticleFeeds()) {
            Path dir = feed.getDirectory();   // e.g., /data/TXT_REUTERS
            String source = feed.getSource(); // e.g., "REUTERS"
            try (var files = Files.list(dir).filter(Files::isRegularFile)) {
                files.forEach(f -> {
                    try {
                        articleProcessor.process(f, source);
                        Files.move(f, feed.getProcessedDir().resolve(f.getFileName()));
                    } catch (Exception e) {
                        Files.move(f, feed.getErrorDir().resolve(f.getFileName()));
                        log.error("Failed to process " + f, e);
                    }
                });
            }
        }
    }
}
```

#### 5.5 Port scheduled tasks

Legacy Quartz2 tasks → Spring `@Scheduled`:

| Legacy Route | desk-integration | Purpose |
|---|---|---|
| quartz2://webStatusUpdate | @Scheduled(cron="0 */5 * * * *") | Update web content status |
| quartz2://tagJob | @Scheduled(cron="0 0 2 * * *") | Tag maintenance |
| quartz2://purge | @Scheduled(cron="0 0 3 * * *") | Content purge |
| quartz2://nginxCache | @Scheduled(cron="0 */10 * * * *") | Cache invalidation |

#### 5.6 Configuration

```yaml
# desk-integration application.yml
spring:
  profiles:
    active: ingest
  datasource:
    url: jdbc:mysql://mysql:33306/desk   # same DB as desk-api

desk:
  integration:
    enabled: true
    feeds:
      - source: REUTERS
        type: article
        directory: /data/TXT_REUTERS
        processed-dir: /data/processed/TXT_REUTERS
        error-dir: /data/error/TXT_REUTERS
      - source: AP
        type: image
        directory: /data/IMG_AP
        processed-dir: /data/processed/IMG_AP
        error-dir: /data/error/IMG_AP
    schedules:
      web-status-update: "0 */5 * * * *"
      tag-job: "0 0 2 * * *"
      purge: "0 0 3 * * *"
```

### Outbound Integration (Send/Distribute)

desk-integration also handles the outbound side currently stubbed in desk-api:

- **sendcontent**: Content distribution to external systems (currently stub in DamDataResource)
- **assigncontent**: Content assignment workflows
- **Kafka/EventBridge**: Publishing change events to message brokers
- **NGINX cache invalidation**: Scheduled cache purge notifications
- **ACE publishing**: Content export to ACE instances

These use the same `ContentManager` to read content and the same bean classes
for serialization — another reason the shared library dependency is the right approach.

### Deployment

```yaml
# compose.yaml (add to existing desk-api compose)
services:
  desk-integration:
    image: eclipse-temurin:25-jre
    volumes:
      - ./desk-integration/build/libs/:/app/
      - wire-feeds:/data          # shared volume for wire feed files
    command: ["java", "-jar", "/app/desk-integration-0.0.1-SNAPSHOT.jar"]
    depends_on:
      - mysql
      - solr
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:33306/desk
      - DESK_SOLR_URL=http://solr:8983/solr
```

Both desk-api and desk-integration connect to the same MySQL and Solr instances.
desk-integration creates content directly via `ContentManager` (shared code from desk-api JAR),
which writes to the same `adm_changelist` table that SolrIndexProcessor polls.

### Project-Specific Ingest Configuration

Each publisher has different wire feeds enabled. This is handled via configuration,
not code — the parsers are the same, only the feed list differs:

| Project | Active Feeds |
|---|---|
| adm-mpp | Reuters, AFP, AP, ANSA, FIEG |
| adm-allied | Reuters, AP, AAP, RNZ (unique) |
| adm-irishnews | Reuters, AP |
| adm-nem-aus | Reuters, AP, AAP |
| adm-nw | Reuters, AFP |

Only adm-allied has truly unique parsers (AAP, RNZ). All other projects use
the standard parser set from adm-starterkit.

### Migration Timeline

| Step | Effort | Prerequisite |
|---|---|---|
| 5.1 Create desk-integration project shell | Small | None |
| 5.2 Port BaseFeedProcessor + ArticleProcessor | Medium | desk-api JAR dependency working |
| 5.3 Port 6 wire feed parsers | Small | Plain Java, no dependencies |
| 5.4 Port directory polling (Spring @Scheduled) | Small | Step 5.2 |
| 5.5 Port scheduled tasks | Small | Step 5.2 |
| 5.6 Port project-unique parsers (AAP, RNZ) | Small | Step 5.3 |
| Future: Extract desk-core library module | Medium | When maintaining bootJar dependency gets painful |
| Future: Add Camel 4 if routing complexity grows | Medium | Only if needed |

---

## Phase 6: Migrate Configuration

### 6.1 Project Config Overrides

Legacy: `config/common/*.json`, `config/{env}/*.properties`
desk-api: `config/project/*.json5` (loaded from classpath or plugin JARs)

**Migration**:
1. Copy JSON configs to `config/project/` in plugin JAR or desk-api resources
2. Convert XML configs to JSON5 format
3. Environment-specific overrides via Spring profiles or DB config tier

### 6.2 Content Definitions (input templates)

Legacy: `.content` XML files defining UI structure, field visibility, defaults.
desk-api: Configuration system already handles template definitions
(`OneCMSTemplateBean`, `OneCMSTemplateListBean`).

**Migration**: Convert `.content` XML to JSON5 config format.
Script: extend `scripts/config-sync.py` to handle project-specific XML.

### 6.3 Localization

Legacy: `locale-{lang}.json` files in content packages.
desk-api: Served via DamLocaleResource from config system.

**Migration**: Copy locale JSON files to `config/project/locales/`.

---

## Migration Checklist (per project)

### Pre-Migration

- [ ] Inventory all custom Java classes by category
- [ ] Identify which extension points each class needs
- [ ] Check for Spring DI dependencies (requires Approach B if heavy)
- [ ] List all custom Solr schema fields
- [ ] List all custom configuration files
- [ ] List all wire feed integrations

### Plugin Development

- [ ] Create plugin project (Maven or Gradle) with PF4J dependency
- [ ] Port custom bean classes (usually copy-paste, same annotations)
- [ ] Port PreStore hooks to DeskPreStoreHook interface
- [ ] Port index mappers to DeskIndexComposer interface
- [ ] Port configuration files to JSON5 format
- [ ] Port localization files

### Testing

- [ ] Unit test each hook independently
- [ ] Integration test: create content with custom fields via REST API
- [ ] Integration test: verify custom Solr fields are indexed
- [ ] Integration test: verify hook chain order (wildcard, type, plugin)
- [ ] Compat test: compare desk-api + plugin vs legacy stack responses

### Deployment

- [ ] Build plugin JAR
- [ ] Deploy to desk-api `plugins/` directory
- [ ] Deploy project config to `config/project/`
- [ ] Verify startup (plugin loaded, hooks registered, types registered)
- [ ] Run smoke tests against running instance

---

## Example: Migrating adm-mpp

### What moves to plugin JAR

```
adm-mpp-plugin/
  src/main/java/
    com/atex/plugins/mpp/
      MppPlugin.java                        (PF4J plugin descriptor)
      beans/
        CustomArticleBean.java              (extends OneArticleBean)
        CustomImageBean.java                (extends OneImageBean)
        ... 14 more bean classes
      hooks/
        ContentAccessPreStoreHook.java      (DeskPreStoreHook)
        CustomOneContentPreStoreHook.java   (DeskPreStoreHook)
        ... 13 more hooks
      indexing/
        MppIndexComposer.java              (DeskIndexComposer)
      publishing/
        MppAcePublishHandler.java          (DeskPublishHandler)
  src/main/resources/
    config/project/
      desk-configuration.json5             (project config)
      act-configurations.json5             (editor config)
    META-INF/
      extensions.idx                       (PF4J extension index)
```

### What stays separate

```
mpp-ingest-service/                       (separate container)
  processors/
    ansa.js, reuters.js, afp.js, ...      (wire feed parsers)
  index.js                                (scheduler/watcher)
```

### What is no longer needed

- 7 WAR modules (server-onecms, server-solr-indexer, webapp-dispatcher, etc.)
- Polopoly/Gong deployment infrastructure
- Tomcat containers
- WAR overlay mechanism
- custom-adm-imports / custom-adm-dependencies POMs

---

## Implementation Priority

| Phase | Effort | Prerequisite | Enables |
|---|---|---|---|
| 1.1 DeskIndexComposer | Small (1 interface + DamIndexComposer wiring) | None | Custom Solr fields |
| 1.2 DeskContentValidator | Small (1 interface + hook chain integration) | None | Validation hooks |
| 2.1 Custom bean classes | None (already works) | None | Custom content types |
| 3.1 PreStore hook migration | None (DeskPreStoreHook exists) | None | Lifecycle hooks |
| 4.1 Custom index mappers | Small | Phase 1.1 | Project-specific search |
| 6.1 Project config | None (config system exists) | None | Project settings |
| 1.4 DeskPublishHandler | Medium | None | Export/publishing |
| 1.3 Plugin Spring bridge | Medium-Large | None | Custom REST endpoints |
| 5 desk-integration | Large (separate project) | desk-api JAR | Wire feeds, distribution, scheduled tasks |

---

## Architecture After Migration

```
Docker Compose:
  desk-api          (Spring Boot + project plugin JAR)
  desk-image        (Rust image sidecar)
  image-metadata    (Node.js EXIF/IPTC extraction)
  mysql             (shared database)
  solr              (search index)
  mpp-ingest        (optional: wire feed processor)
  next-web-mpp      (Next.js frontend)

vs. Legacy (per publisher):
  server-onecms     (Tomcat WAR)
  server-solr-indexer (Tomcat WAR)
  server-integration (Tomcat WAR)
  server-content-hub (Tomcat WAR)
  webapp-dispatcher  (Tomcat WAR)
  webapp-polopoly    (Tomcat WAR)
  webapp-image-service (Tomcat WAR)
  mysql
  solr
  next-web-mpp
```

Reduction: 7 Tomcat WARs + 3 services -> 3-4 lightweight containers.

---

## Multi-Project Strategy (20+ publishers)

With ~20 projects built on adm-starterkit, the repo and build strategy matters.

### Option 1: Monorepo with Plugin Modules (recommended)

All project plugins live in a single repo alongside desk-api:

```
desk-api/
  src/main/java/...                  (core desk-api)
  plugins/
    mpp/                             (adm-mpp plugin source)
      src/main/java/...
      build.gradle
    bt/                              (adm-bt plugin source)
      src/main/java/...
      build.gradle
    irishnews/                       (adm-irishnews plugin source)
      ...
  build.gradle                       (multi-project Gradle build)
```

**Pros**:
- Single build verifies all plugins compile against current desk-api
- Breaking changes in desk-api are caught immediately across all 20 projects
- Shared CI/CD pipeline
- Atomic upgrades (update desk-api + all plugins in one commit)
- Easier to extract shared code into desk-api core when patterns emerge
- One place to search/refactor across all projects

**Cons**:
- Larger repo
- All projects coupled to same desk-api version (but this is actually desirable)
- Need per-project build targets (`./gradlew :plugins:mpp:build`)

**Build structure** (Gradle multi-project):
```groovy
// settings.gradle
include 'plugins:mpp', 'plugins:bt', 'plugins:irishnews', ...

// plugins/mpp/build.gradle
plugins { id 'java-library' }
dependencies {
    compileOnly project(':')  // desk-api as compile-only dependency
    compileOnly 'org.pf4j:pf4j:3.15.0'
}
jar {
    manifest {
        attributes 'Plugin-Id': 'adm-mpp'
        attributes 'Plugin-Version': '1.0.0'
    }
}
```

### Option 2: Separate Plugin Repos

Each project keeps its own repo, desk-api published as a Maven artifact:

```
desk-api/          (repo 1 - published to Maven)
adm-mpp-plugin/    (repo 2 - depends on desk-api artifact)
adm-bt-plugin/     (repo 3 - depends on desk-api artifact)
...
```

**Pros**:
- Independent release cycles per project
- Smaller repos
- Teams can work independently

**Cons**:
- desk-api changes can break plugins silently (discovered at deploy time)
- Must publish desk-api to a Maven repo on every change
- Harder to refactor across projects
- 20 separate CI pipelines
- Version matrix nightmare (which desk-api version works with which plugin version?)

### Option 3: Hybrid (desk-api core + project monorepo)

desk-api stays in its own repo. All project plugins in a separate monorepo:

```
desk-api/                (repo 1)
adm-plugins/             (repo 2)
  plugins/mpp/
  plugins/bt/
  plugins/irishnews/
  ...
```

**Pros**: Separates platform from projects.
**Cons**: Still need to coordinate desk-api version across plugin builds.

### Recommendation: Option 1 (Monorepo)

For 20 projects with shared base code, monorepo wins:

1. **Most ADM projects share 70-80% of their custom code** (same hook patterns,
   same bean extensions, same Solr mappers). A monorepo makes it easy to extract
   shared code into desk-api core or a shared plugin library.

2. **desk-api is actively evolving**. Having all plugins in the same repo means
   you can update an interface and fix all 20 callers in the same PR.

3. **The custom code per project is small** (30-100 Java classes). 20 projects
   at ~50 classes each = ~1000 files. This is manageable in a single repo.

4. **Deployment is per-project anyway**. Each publisher gets its own Docker Compose
   with the desk-api image + that project's plugin JAR. The monorepo just builds
   all plugin JARs; each deployment picks the one it needs.

### Shared Plugin Library

With 20 projects, common patterns should be extracted into a shared base plugin:

```
plugins/
  shared/                            (shared plugin library)
    src/main/java/
      com/atex/plugins/shared/
        hooks/
          CommonContentAccessHook.java
          CommonSecParentHook.java
        indexing/
          CommonIndexComposer.java
        beans/
          CommonArticleFields.java   (mixin interface for shared fields)
  mpp/                               (depends on shared)
  bt/                                (depends on shared)
```

This avoids duplicating the same hooks across 20 projects.
Projects override or extend shared hooks only where they differ.

### Deployment Model

Each publisher deployment:

```yaml
# docker-compose.mpp.yaml
services:
  desk-api:
    image: desk-api:latest
    volumes:
      - ./plugins/adm-mpp-plugin.jar:/app/plugins/adm-mpp-plugin.jar
      - ./config/mpp/:/app/config/project/
    environment:
      - DESK_PLUGINS_ENABLED=true
      - SPRING_PROFILES_ACTIVE=mpp
```

The desk-api Docker image is the same for all 20 projects.
Only the plugin JAR and config directory differ per deployment.

### Migration Order

Migrate projects in waves based on complexity:

| Wave | Projects | Complexity | Notes |
|---|---|---|---|
| 1 (pilot) | 1-2 simple projects | Low | Minimal custom hooks, few wire feeds |
| 2 | 5-8 standard projects | Medium | Standard hook set, 1-2 wire feeds |
| 3 | 5-8 complex projects | High | Custom REST endpoints, many wire feeds |
| 4 | Remaining | Varies | Print-heavy, deep Camel integration |

Each wave validates the plugin framework and adds missing extension points
before the next wave begins.

### Shared Code Extraction Strategy

As projects migrate, track which classes appear in multiple plugins:

```
If 3+ projects have the same hook -> extract to shared plugin
If a hook pattern is universal -> promote to desk-api core
If a bean field appears in 5+ projects -> add to base OneArticleBean
```

This organic extraction is easier in a monorepo where you can grep
across all projects and refactor in a single commit.

---

## Cross-Project Code Analysis

Analysis of adm-starterkit (baseline) and 7 real projects (adm-mpp, adm-allied, adm-irishnews,
adm-mhie-pa, adm-mytype, adm-nem-aus, adm-nw). Total: ~1,141 Java files across 8 codebases.

### Summary

| Category | Classes | Action |
|---|---|---|
| Identical across ALL projects | ~120 classes | Move to desk-api core |
| Identical across MOST projects | ~20 classes | Move to desk-api core (configurable) |
| Truly project-unique | 5-25 per project | Stays in project plugin |

**Key finding**: 95%+ of "custom" code is copy-pasted from adm-starterkit with zero modifications.
Only 5-25 classes per project are genuinely unique. This means plugin JARs will be very small.

### Tier 1: Move to desk-api Core (identical everywhere)

These classes exist in ALL 7 projects with identical or near-identical source code.
They should be built into desk-api itself, not duplicated in plugins.

#### Custom Bean Classes (10 classes)

| Class | Extends | Package |
|---|---|---|
| CustomArticleBean | OneArticleBean | `onecms.custom` |
| CustomImageBean | OneImageBean | `onecms.custom` |
| CustomVideoBean | OneVideoBean | `onecms.custom` |
| CustomAudioBean | OneAudioBean | `onecms.custom` |
| CustomDocumentBean | OneDocumentBean | `onecms.custom` |
| CustomGraphicBean | OneGraphicBean | `onecms.custom` |
| CustomCollectionBean | DamCollectionAspectBean | `onecms.custom` |
| CustomPageBean | PageBean | `onecms.custom` |
| CustomEventBean | OneEventBean | `onecms.custom` |
| CustomLiveBlogArticleBean | OneArticleBean | `onecms.custom` |

These add fields like `customContentType`, `channel`, `edition`, `premiumType`, `metaTitle`,
`metaDescription`, `canonicalUrl`, `noindex`, `redirectUrl`, `relatedArticles`, etc.

#### ACE Bean Classes (4 classes)

| Class | Package |
|---|---|
| CustomAceBean | `onecms.custom.ace` |
| CustomAceEventBean | `onecms.custom.ace` |
| CustomAceVideoBean | `onecms.custom.ace` |
| CustomAceImageGallary | `onecms.custom.ace` |

#### Lifecycle Hooks (10 classes)

| Class | Scope | Purpose |
|---|---|---|
| ContentAccessPreStoreHook | `*` | Loads handler classes from config, delegates |
| ContentAccessCreateHandler | | Default create handler |
| ContentAccessUpdateHandler | | Default update handler |
| CustomOneContentPreStoreHook | `*` | Custom content preprocessing |
| CustomSecParentPreStoreHook | varies | Security parent override |
| CustomSetStatusPreStoreHook | varies | Workflow status logic |
| CustomCollectionPreStore | collection | Collection-specific logic |
| Article2ImageChannelPreStore | article | Copies article channel to embedded images |
| MetadataCoercingPreStoreHook | `*` | Normalizes/coerces metadata tags |
| VideoPreStore | video | Video-specific preprocessing |

#### Solr Indexing (2 classes)

| Class | Purpose |
|---|---|
| CustomContentToSolrJsonMapper | Adds custom fields (premiumType, edition, channel, etc.) to Solr docs |
| CustomWebContentStatusToSolrJsonMapper | Maps web content status fields |

#### Dailymotion Integration (13 classes)

| Class | Purpose |
|---|---|
| DailymotionService | API client |
| DailymotionVideoUploader | Upload handler |
| DailymotionTokenManager | OAuth token management |
| DailymotionProperties | Configuration |
| DailymotionPreStore | PreStore hook for video upload |
| DailymotionVideoInfo | Video metadata DTO |
| + 7 more support classes | Error handling, config, utilities |

#### Wire Feed Processing (Camel, ~30 classes)

All Camel processors and wire parsers are identical across projects:

| Category | Classes | Examples |
|---|---|---|
| Wire parsers | ~10 | AFPArticleParser, APParser, ReutersParser, PTIParser, AnsaParser, FIEGParser |
| Camel processors | ~10 | ContentPreProcessor, ContentPostProcessor, ImageProcessor, WireMetadataProcessor |
| Transform utilities | ~5 | WireContentMapper, StructuredTextBuilder, WireImageDownloader |
| Camel routes | ~5 | WireIngestRouteBuilder, ScheduledPollRoute |

**Note**: Wire feed processing is recommended as a separate service (see Phase 5),
but these classes form the shared library for that service.

#### Hermes/HSeries Module (~15 classes)

| Category | Classes |
|---|---|
| Composers | HSeriesComposer, HermesArticleComposer, HermesImageComposer |
| Loaders | HermesContentLoader, HSeriesContentLoader |
| Index mappers | HermesIndexMapper, HSeriesFieldMapper |
| Bordero | BorderoService, BorderoBuilder, BorderoEntry |
| Utilities | HermesUtils, HSeriesConfig, PrintPageUtils |

#### REST & Configuration (3 classes)

| Class | Purpose |
|---|---|
| MyTypeResource | REST endpoint for permissions/config queries |
| MyTypeConfiguration | Configuration provider |
| CollectionUtils | Collection content utilities |

#### Utility Classes (~15 classes)

| Class | Purpose |
|---|---|
| ContentPropertyUtils | Content field extraction helpers |
| JsonUtils | JSON manipulation utilities |
| ProcessorUtils | Content processing helpers |
| WebStatusUtils | Web status field helpers |
| PublishingUtils | Publishing pipeline helpers |
| CustomEpochLongIsoStringConverter | Date format converter |
| CustomMapOfMapsConverter | Nested map converter |
| DamStatusUtils | DAM status helpers |
| DistributionListProcessor | Distribution list processing |
| EngagementUtils | Engagement tracking |
| SecurityContextUtils | Auth context helpers |
| + ~5 more | Various utilities |

#### Configuration Files (identical across all)

All projects share the same set of configuration files (desk config, ACT config,
Solr schemas, locale files). These are already handled by desk-api's config system.

### Tier 2: Configurable Core (same pattern, different values)

These classes exist in most/all projects with the same structure but project-specific values
(e.g., different field names, different handler lists). They should move to desk-api core
with configuration-driven behavior.

| Class | What Varies | Configuration Approach |
|---|---|---|
| ContentAccessPreStoreHook handlers | Handler class list | Config property: `desk.hooks.content-access.handlers` |
| CustomSecParentPreStoreHook | Security parent IDs | Config property: `desk.hooks.sec-parent.defaults` |
| CustomSetStatusPreStoreHook | Status field mappings | Config property: `desk.hooks.set-status.mappings` |
| CustomContentToSolrJsonMapper | Extra Solr field list | Config property: `desk.indexing.custom-fields` |
| MyTypeResource | Permission rules | Config property: `desk.mytype.permissions` |
| Dailymotion* | API credentials, enabled/disabled | Config properties: `desk.dailymotion.*` |

### Tier 3: Project-Unique Code (stays in plugin)

These classes exist in only one or two projects and represent genuinely unique functionality.

#### adm-mpp (~25 unique classes)

| Category | Classes | Purpose |
|---|---|---|
| Custom publishing | CustomBeanMapper, CustomDamDataResource | Override publish behavior |
| ACE preview | AcePreviewBeanPublisher | Custom ACE preview rendering |
| Auto-publish | AutoPublishProcessor | Scheduled auto-publishing |
| Fast JSON ingest | FastJsonToDeskProcessor | High-speed bulk import |
| Image tools | AtexImageMetadataTool | Custom EXIF processing |
| Generic content | GenericArticle, GenericParser | Flexible content types |
| ACE customizations | CustomAceBeanPublisher, CustomAceModulePublisher | ACE export |
| User utilities | UserUtils | Project-specific user logic |
| Color categorization | ColorCategorizationConfigProvider, PseriesSectionMapper | Print layout |
| Config cache | ConfigurationCache | Custom caching layer |

#### adm-allied (~10 unique classes)

| Category | Classes | Purpose |
|---|---|---|
| AAP wire feed | AAPClient, AAPImporter, AAPParser | Australian wire service |
| RNZ import | RNZImporter, RNZRssParser, RNZContentMapper | Radio NZ content |
| SCC images | SCCImageProcessor, SCCImageTransformer | Image processing |
| Reuters GraphQL | ReutersGraphQLClient | GraphQL-based Reuters feed |
| Housekeeping | ImageHouseKeeping | Image cleanup jobs |
| Custom hooks | CustomOneImagePreStore, ImageFirmaPreStore | Image-specific hooks |
| Custom beans | CustomAceAuthorBean, CustomAceImageBean, CustomAuthorBean | Extended beans |

#### adm-mytype / adm-nem-aus (~8 unique classes)

| Category | Classes | Purpose |
|---|---|---|
| Property bag | PropertyBagConfiguration, PropertyBagIndexComposer | Dynamic field indexing |
| Schema | SchemaField | Field schema definitions |
| Permissions | MyTypePermissions, MyTypePermissionsValidator | Fine-grained permissions |
| Conditions | JsonConditionEvaluator | Dynamic content rules |

#### adm-irishnews (~5 unique classes)

| Category | Classes | Purpose |
|---|---|---|
| Custom publisher | IrishNewsPublisherProcessor | Custom publish pipeline |
| Legacy hooks | LifecycleScripts | Legacy script hooks |
| Custom queries | QueryProcessor | Project-specific search |

#### adm-mhie-pa / adm-nw (~5 unique classes)

| Category | Classes | Purpose |
|---|---|---|
| Migration bridge | DeskBridgePropertyPlaceholderConfigurer | Legacy config bridge |
| Migration processor | MigrationServerToDeskProcessor | Content migration from old CMS |
| Custom migration | CustomMigrationServerToDeskProcessor | Project-specific migration |

### Impact on Plugin Size

After moving Tier 1 and Tier 2 code to desk-api core:

| Project | Total Java Files | Unique (plugin) | Plugin Size |
|---|---|---|---|
| adm-mpp | 198 | ~25 | ~13% |
| adm-allied | 179 | ~10 | ~6% |
| adm-irishnews | 162 | ~5 | ~3% |
| adm-mhie-pa | 122 | ~5 | ~4% |
| adm-mytype | 111 | ~8 | ~7% |
| adm-nem-aus | 154 | ~8 | ~5% |
| adm-nw | 145 | ~5 | ~3% |

**Average plugin size: ~6% of original project code.** The rest moves to desk-api core
or is configuration that's already handled by the config system.

### Migration Priority (revised based on analysis)

| Priority | Action | Effort | Impact |
|---|---|---|---|
| 1 | Port 10 Custom*Bean classes to desk-api core | Medium | Eliminates ~10 classes from every plugin |
| 2 | Port 10 lifecycle hooks to desk-api core | Medium | Eliminates ~10 classes from every plugin |
| 3 | Port Solr mappers to desk-api core (configurable) | Small | Eliminates 2 classes from every plugin |
| 4 | Port utility classes to desk-api core | Small | Eliminates ~15 classes from every plugin |
| 5 | Port Dailymotion integration (feature-flagged) | Medium | Eliminates 13 classes from every plugin |
| 6 | Port Hermes/HSeries module (feature-flagged) | Medium | Eliminates 15 classes from every plugin |
| 7 | Wire feed service (separate container) | Large | Eliminates 30 classes from every plugin |
| 8 | Port MyTypeResource to desk-api core | Small | Eliminates REST endpoint duplication |

After priorities 1-4, most projects will have **zero or near-zero plugin code**.
Only adm-mpp (custom publishing), adm-allied (AAP/RNZ feeds), and adm-mytype
(property bag system) need meaningful plugin JARs.
