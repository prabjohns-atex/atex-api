use crate::config::StorageConfig;
use anyhow::Result;
use aws_sdk_s3::Client as S3Client;
use std::path::PathBuf;
use tokio::io::AsyncReadExt;

/// Unified storage backend for reading image files.
pub enum StorageBackend {
    Local {
        base_dir: PathBuf,
    },
    S3 {
        client: S3Client,
        bucket_content: String,
        bucket_tmp: String,
        one_bucket_mode: bool,
        content_prefix: String,
        tmp_prefix: String,
    },
}

impl StorageBackend {
    pub async fn new(config: &StorageConfig) -> Result<Self> {
        match config.backend.as_str() {
            "s3" => {
                let s3_config = config
                    .s3
                    .as_ref()
                    .ok_or_else(|| anyhow::anyhow!("S3 storage config missing"))?;

                let mut aws_config = aws_config::defaults(aws_config::BehaviorVersion::latest())
                    .region(aws_config::Region::new(s3_config.region.clone()));

                if let Some(endpoint) = &s3_config.endpoint {
                    aws_config = aws_config.endpoint_url(endpoint);
                }

                if let (Some(ak), Some(sk)) = (&s3_config.access_key, &s3_config.secret_key) {
                    aws_config = aws_config.credentials_provider(
                        aws_sdk_s3::config::Credentials::new(ak, sk, None, None, "config"),
                    );
                }

                let sdk_config = aws_config.load().await;
                let mut s3_builder = aws_sdk_s3::config::Builder::from(&sdk_config);
                if s3_config.endpoint.is_some() {
                    s3_builder = s3_builder.force_path_style(true);
                }
                let client = S3Client::from_conf(s3_builder.build());

                Ok(StorageBackend::S3 {
                    client,
                    bucket_content: s3_config.bucket_content.clone(),
                    bucket_tmp: s3_config.bucket_tmp.clone(),
                    one_bucket_mode: s3_config.one_bucket_mode,
                    content_prefix: s3_config.content_prefix.clone(),
                    tmp_prefix: s3_config.tmp_prefix.clone(),
                })
            }
            _ => {
                let base_dir = config
                    .local
                    .as_ref()
                    .map(|l| PathBuf::from(&l.base_dir))
                    .unwrap_or_else(|| PathBuf::from("./files"));
                tracing::info!("Local storage: base_dir={}", base_dir.display());
                Ok(StorageBackend::Local { base_dir })
            }
        }
    }

    /// Read file bytes from storage.
    /// `file_uri` format: "{space}/{host}/{path}" (e.g., "content/host/2024/01/photo.jpg")
    pub async fn get_file(&self, file_uri: &str) -> Result<Vec<u8>> {
        match self {
            StorageBackend::Local { base_dir } => {
                let file_path = base_dir.join(file_uri);
                tracing::debug!("Reading local file: {}", file_path.display());
                let mut file = tokio::fs::File::open(&file_path).await.map_err(|e| {
                    anyhow::anyhow!("File not found: {} ({})", file_path.display(), e)
                })?;
                let mut buf = Vec::new();
                file.read_to_end(&mut buf).await?;
                Ok(buf)
            }
            StorageBackend::S3 {
                client,
                bucket_content,
                bucket_tmp,
                one_bucket_mode,
                content_prefix,
                tmp_prefix,
            } => {
                // Parse space from URI: "content/host/path" or "tmp/host/path"
                let (space, rest) = file_uri
                    .split_once('/')
                    .ok_or_else(|| anyhow::anyhow!("Invalid file URI: {}", file_uri))?;

                let (bucket, key) = if *one_bucket_mode {
                    let prefix = match space {
                        "content" => content_prefix.as_str(),
                        "tmp" => tmp_prefix.as_str(),
                        _ => content_prefix.as_str(),
                    };
                    (bucket_content.as_str(), format!("{}/{}", prefix, rest))
                } else {
                    let bucket = match space {
                        "content" => bucket_content.as_str(),
                        "tmp" => bucket_tmp.as_str(),
                        _ => bucket_content.as_str(),
                    };
                    (bucket, rest.to_string())
                };

                tracing::debug!("Reading S3 object: s3://{}/{}", bucket, key);
                let resp = client
                    .get_object()
                    .bucket(bucket)
                    .key(&key)
                    .send()
                    .await
                    .map_err(|e| anyhow::anyhow!("S3 get failed for {}: {}", file_uri, e))?;

                let bytes = resp.body.collect().await?.into_bytes().to_vec();
                Ok(bytes)
            }
        }
    }
}
