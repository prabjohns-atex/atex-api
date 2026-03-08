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
    pub cache: Option<processing::ImageCache>,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("desk_image=info".parse()?))
        .init();

    let cli = Cli::parse();
    let config = config::Config::load(&cli.config)?;

    let bind_addr = format!("{}:{}", config.server.host, config.server.port);
    tracing::info!("Starting desk-image on {}", bind_addr);

    let storage = storage::StorageBackend::new(&config.storage).await?;

    let cache = if config.cache.enabled {
        tracing::info!(
            "Image cache enabled: max_entries={}, max_size={}MB",
            config.cache.max_entries,
            config.cache.max_size_bytes / 1024 / 1024
        );
        Some(processing::ImageCache::new(
            config.cache.max_entries,
            config.cache.max_size_bytes,
        ))
    } else {
        None
    };

    let state = Arc::new(AppState {
        config,
        storage,
        cache,
    });

    let app = Router::new()
        .route("/health", get(|| async { "ok" }))
        // /image/{file_uri}/{filename}?w=&h=&m=&f=&q=&c=&fp=&rot=&flipv=&fliph=&sig=
        .route("/image/*path", get(handler::handle_image))
        .layer(TraceLayer::new_for_http())
        .with_state(state);

    let listener = tokio::net::TcpListener::bind(&bind_addr).await?;
    tracing::info!("Listening on {}", bind_addr);
    axum::serve(listener, app).await?;

    Ok(())
}
