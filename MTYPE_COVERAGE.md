# mytype-new Endpoint Coverage

Analysis of all HTTP API endpoints called by `mytype-new` frontend vs desk-api implementation status.

## Implemented

| Endpoint Group | Paths | Desk-API Increment |
|---------------|-------|-------------------|
| Content CRUD | `/content/contentid/*`, `/content/externalid/*` | 1 |
| Security/Auth | `/security/token`, `/security/oauth/*` | 2 |
| DAM Operations | `/dam/content/*` (publish, trash, duplicate, restrict, solrquery, export, etc.) | 3, 3a–3e, 4 |
| Search | `/search/{core}/select`, `/dam/search/{core}/select` | 10 |
| Principals | `/principals/users/*`, `/principals/groups/*` | 9 |
| Configuration Admin | `/admin/config/*` | 7 |
| Changes Feed | `/changes` | 12 |
| Layout Proxy | `/layout/handj/*` (print, poll, workflows, permissions, shapes, preview) | 14 |
| Print Pages | `/dam/print-page/*` (publications, editions, zones, sections) | 11 |
| Social | `/dam/content/social/accounts` | 11 (stub) |
| DAM UI | `/dam/ui/system/versions` | 11 |
| Locales | `/dam/locales/{app}/locale/{file}` | 11 |
| Image | `/dam/image/create-print-variant/{id}` | 11 |
| Tag Manager | `/dam/tagmanager/*` | 11 (stub) |
| PDF Tool | `/dam/pdftool/*` | 11 (stub) |
| Scheduling | `/dam/scheduling/*` | 11 (stub) |
| Statistics | `/dam/content/statistics/*` | 11 |
| Page Publishing | `/dam/page/*` | 11 |
| File Service | `/file/*` (upload, download, metadata, delete) | 16 |
| Activities/Locking | `/activities/*` (get, lock, unlock) | 16 |

## Not Yet Implemented

| Priority | Endpoint Group | Paths | Description |
|----------|---------------|-------|-------------|
| **MEDIUM** | Workspace/Preview | `/content/workspace/*`, `/preview/*` | Editorial preview workflow (stage, render, clear) |
| **LOW** | Audio AI | `/dam/audioai/*` | TTS/audio search |
| **LOW** | Config Profile | `/configuration/profile/desk` | User profile configuration |
| **LOW** | Permissions Config | `/dam/mytype/permissions` | User permissions configuration |

## Detailed Endpoint Listing (mytype-new)

### Content Management
- `GET /content/contentid/{id}` — get content by ID
- `GET /content/externalid/{externalId}` — get content by external ID
- `GET /content/{kind}/{id}` — get content by kind (contentid/externalid)
- `GET /content/type/{typeName}` — get content type schema
- `GET /content/{id}/history` — get content version history
- `POST /content` — create new content
- `PUT /content/contentid/{id}` — update content
- `DELETE /content/contentid/{id}` — delete content

### DAM Content Operations
- `POST /dam/content/publish?contentId={id}` — publish
- `POST /dam/content/unpublish/{id}` — unpublish
- `POST /dam/content/trash/{id}` — move to trash
- `POST /dam/content/untrash/{id}` — restore from trash
- `POST /dam/content/archive/{id}` — archive
- `POST /dam/content/duplicate` — duplicate (POST with contentId array)
- `GET /dam/content/export?contentId={id}&appType={type}&domain={domain}` — export/engage
- `POST /dam/content/clearengage/{id}` — clear engagement
- `POST /dam/content/restrict/{id}` — restrict
- `POST /dam/content/unrestrict/{id}` — unrestrict
- `POST /dam/content/solrquery` — Solr query

### Layout/HandJ (all proxied to backend servers)
- `GET /layout/handj/preview-url/{id}` — preview image
- `GET /layout/handj/{type}/{id}` — layout GET
- `GET /layout/handj/{type}/{subtype}/{id}` — layout GET with subtype
- `POST /layout/handj/{type}/{id}` — layout POST
- `POST /layout/handj/{type}/{subtype}/{id}` — layout POST with subtype
- Content-specific: `content/print`, `content/permission`, `content/poll`, `content/workflows`, `content/getElementList`, `content/shapes`, `content/shape`, `content/item`, `content/operation`, `content/linkToPrint`, `content/unlinkItem`, `content/unload`, `content/pageOp`, `content/uiQuery`, `content/restore/article`, `layout/print`

### Search
- `GET /search/{core}/select?{queryString}` — Solr GET
- `POST /search/{core}/select` — Solr POST (form or JSON)

### Security & Principals
- `POST /security/token` — login
- `GET /principals/users/me` — current user
- `GET /principals/users/{userId}` — user by ID
- `GET /principals/users` — list users
- `GET /dam/users` — list users (DAM endpoint)

### File Service (Increment 16)
- `GET /file/info/{space}/{host}/{path}` — file metadata as JSON
- `GET /file/metadata?uri={uri}` — file metadata by URI
- `POST /file/{space}` — upload file (anonymous host)
- `POST /file/{space}/{host}/{path}` — upload file with explicit path
- `GET /file/{space}/{host}/{path}` — download binary
- `DELETE /file/{space}/{host}/{path}` — delete file

### Activities/Locking (Increment 16)
- `GET /activities/{contentId}` — get locks on content
- `PUT /activities/{contentId}/{principalId}/{appId}` — acquire lock
- `DELETE /activities/{contentId}/{principalId}/{appId}` — release lock

### Workspace/Preview (NOT IMPLEMENTED)
- `DELETE /content/workspace/{workspaceName}` — clear workspace
- `PUT /content/workspace/{workspaceName}/contentid/{contentId}` — stage content
- `GET /content/workspace/{workspaceName}/contentid/{contentId}` — get workspace content
- `POST /preview/contentid/{contentId}?channel=web&workspace={name}` — render preview
