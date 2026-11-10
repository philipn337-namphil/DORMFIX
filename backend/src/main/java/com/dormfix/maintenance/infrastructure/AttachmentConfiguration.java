package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.application.AttachmentPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AttachmentProperties.class)
class AttachmentConfiguration {
    @Bean
    AttachmentPolicy attachmentPolicy(AttachmentProperties properties) {
        return new AttachmentPolicy(properties.maxFileSizeBytes(),
                properties.allowedContentTypes(), properties.maxAttachmentsPerRequest(),
                properties.uploadUrlLifetime(), properties.downloadUrlLifetime());
    }
}
