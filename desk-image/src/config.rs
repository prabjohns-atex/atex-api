use serde::Deserialize;
use std::path::Path;

#[derive(Debug, Deserialize, Clone)]
pub struct Config {
    pub server: ServerConfig,
    pub signing: SigningConfig,
    pub storage: StorageConfig,
    pub processing: ProcessingConfig,
}

#[derive(Debug, Deserialize, Clone)]
pub struct ServerConfig {
    pub host: String,
    pub port: u16,
}

#[derive(Debug, Deserialize, Clone)]
pub struct SigningConfig {
    pub secret: String,
    pub signature_length: usize,
}

#[derive(Debug, Deserialize, Clone)]
pub struct StorageConfig {
    pub backend: String,
    pub local: Option<LocalStorageConfig>,
    pub s3: Option<S3StorageConfig>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct LocalStorageConfig {
    pub base_dir: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct S3StorageConfig {
    pub region: String,
    pub endpoint: Option<String>,
    pub bucket_content: String,
    pub bucket_tmp: String,
    pub one_bucket_mode: bool,
    pub content_prefix: String,
    pub tmp_prefix: String,
    pub access_key: Option<String>,
    pub secret_key: Option<String>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct ProcessingConfig {
    pub max_width: u32,
    pub max_height: u32,
    pub default_quality: f32,
    pub strip_metadata: bool,
}

impl Config {
    pub fn load(path: &str) -> anyhow::Result<Self> {
        let content = std::fs::read_to_string(Path::new(path))
            .map_err(|e| anyhow::anyhow!("Failed to read config file '{}': {}", path, e))?;
        let config: Config = toml::from_str(&content)
            .map_err(|e| anyhow::anyhow!("Failed to parse config file '{}': {}", path, e))?;
        Ok(config)
    }
}
