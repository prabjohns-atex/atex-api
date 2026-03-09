mod config;
mod handler;
mod processing;
mod signing;
mod storage;

use axum::{
    Router,
    routing::get,
};
use clap::Parser;
use std::sync::Arc;
use tower_http::trace::TraceLayer;
use tracing_subscriber::{EnvFilter, fmt};

#[derive(Parser)]
#[command(name = "desk-image", about = "Image processing sidecar for desk-api")]
struct Cli {
    /// Path to config file
    #[arg(short, long, default_value = "config.toml")]
    config: String,
}

pub struct AppState {
    pub config: config::Config,
    pub storage: storage::StorageBackend,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("desk_image=info".parse()?))
        .init();

    let cli = Cli::parse();
    let mut config = config::Config::load(&cli.config)?;

    // Environment variable overrides (for container deployment)
    if let Ok(v) = std::env::var("DESK_IMAGE_HOST") { config.server.host = v; }
    if let Ok(v) = std::env::var("DESK_IMAGE_PORT") { config.server.port = v.parse().unwrap_or(config.server.port); }
    if let Ok(v) = std::env::var("DESK_IMAGE_SECRET") { config.signing.secret = v; }
    if let Ok(v) = std::env::var("DESK_IMAGE_STORAGE_BACKEND") { config.storage.backend = v; }
    if let Ok(v) = std::env::var("DESK_IMAGE_LOCAL_DIR") {
        config.storage.local.get_or_insert_with(|| config::LocalStorageConfig { base_dir: String::new() }).base_dir = v;
    }
    if let Ok(v) = std::env::var("DESK_IMAGE_S3_REGION") {
        if let Some(s3) = config.storage.s3.as_mut() { s3.region = v; }
    }
    if let Ok(v) = std::env::var("DESK_IMAGE_S3_ENDPOINT") {
        if let Some(s3) = config.storage.s3.as_mut() { s3.endpoint = Some(v); }
    }
    if let Ok(v) = std::env::var("DESK_IMAGE_S3_BUCKET") {
        if let Some(s3) = config.storage.s3.as_mut() { s3.bucket_content = v; }
    }
    if let Ok(v) = std::env::var("DESK_IMAGE_S3_ACCESS_KEY") {
        if let Some(s3) = config.storage.s3.as_mut() { s3.access_key = Some(v); }
    }
    if let Ok(v) = std::env::var("DESK_IMAGE_S3_SECRET_KEY") {
        if let Some(s3) = config.storage.s3.as_mut() { s3.secret_key = Some(v); }
    }

    let bind_addr = format!("{}:{}", config.server.host, config.server.port);
    tracing::info!("Starting desk-image on {}", bind_addr);

    let storage = storage::StorageBackend::new(&config.storage).await?;

    let state = Arc::new(AppState {
        config,
        storage,
    });

    let app = Router::new()
        .route("/health", get(|| async { "ok" }))
        // /image/{file_uri}/{filename}?w=&h=&m=&f=&q=&c=&fp=&rot=&flipv=&fliph=&sig=
        .route("/image/{*path}", get(handler::handle_image))
        .layer(TraceLayer::new_for_http())
        .with_state(state);

    let listener = tokio::net::TcpListener::bind(&bind_addr).await?;
    tracing::info!("Listening on {}", bind_addr);
    axum::serve(listener, app).await?;

    Ok(())
}
