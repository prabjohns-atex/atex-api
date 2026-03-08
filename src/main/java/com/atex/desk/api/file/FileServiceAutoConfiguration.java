package com.atex.desk.api.file;

import com.atex.desk.api.config.FileServiceProperties;
import com.atex.desk.api.onecms.LocalFileService;
import com.atex.onecms.content.files.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for the file service backend.
 *
 * Selects the appropriate FileService implementation based on
 * the desk.file-service.backend property:
 * - "local" (default): filesystem storage only
 * - "s3": S3 storage only
 * - "hybrid": delegates by URI scheme, S3 for content, local for tmp
 */
@Configuration
@EnableConfigurationProperties(FileServiceProperties.class)
public class FileServiceAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(FileServiceAutoConfiguration.class);

    @Bean
    @Primary
    public FileService fileService(FileServiceProperties props) {
        String backend = props.getBackend();
        LOG.info("Configuring file service backend: {}", backend);

        return switch (backend) {
            case "s3" -> {
                S3FileService s3 = new S3FileService(props.getS3());
                yield s3;
            }
            case "hybrid" -> {
                LocalFileService local = new LocalFileService(props.getBaseDir());
                S3FileService s3 = new S3FileService(props.getS3());
                yield new DelegatingFileService(local, s3);
            }
            default -> {
                // "local" or anything else
                yield new LocalFileService(props.getBaseDir());
            }
        };
    }
}
