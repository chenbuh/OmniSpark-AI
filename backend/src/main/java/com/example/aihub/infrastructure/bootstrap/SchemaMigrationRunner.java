package com.example.aihub.infrastructure.bootstrap;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;

@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaMigrationRunner implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        ensureColumn(
                "quota_record",
                "created_at",
                "ALTER TABLE `quota_record` ADD COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP"
        );
        ensureColumn(
                "quota_record",
                "updated_at",
                "ALTER TABLE `quota_record` ADD COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
        );
        ensureColumn("style_card", "negative_prompt", "ALTER TABLE `style_card` ADD COLUMN `negative_prompt` text DEFAULT NULL AFTER `content`");
        ensureColumn("style_card", "model_name", "ALTER TABLE `style_card` ADD COLUMN `model_name` varchar(128) DEFAULT NULL AFTER `negative_prompt`");
        ensureColumn("style_card", "provider_id", "ALTER TABLE `style_card` ADD COLUMN `provider_id` bigint DEFAULT NULL AFTER `model_name`");
        ensureColumn("style_card", "ref_asset_id", "ALTER TABLE `style_card` ADD COLUMN `ref_asset_id` bigint DEFAULT NULL AFTER `provider_id`");
        ensureColumn("style_card", "cfg", "ALTER TABLE `style_card` ADD COLUMN `cfg` double DEFAULT NULL AFTER `ref_asset_id`");
        ensureColumn("style_card", "steps", "ALTER TABLE `style_card` ADD COLUMN `steps` int DEFAULT NULL AFTER `cfg`");
        ensureColumn("style_card", "size", "ALTER TABLE `style_card` ADD COLUMN `size` varchar(32) DEFAULT NULL AFTER `steps`");
        ensureColumn("style_card", "params_json", "ALTER TABLE `style_card` ADD COLUMN `params_json` json DEFAULT NULL AFTER `size`");
        ensureColumn("prompt_template", "negative_prompt", "ALTER TABLE `prompt_template` ADD COLUMN `negative_prompt` text DEFAULT NULL AFTER `content`");
        ensureColumn("prompt_template", "model_name", "ALTER TABLE `prompt_template` ADD COLUMN `model_name` varchar(128) DEFAULT NULL AFTER `negative_prompt`");
        ensureColumn("prompt_template", "user_id", "ALTER TABLE `prompt_template` ADD COLUMN `user_id` bigint DEFAULT NULL AFTER `project_id`");
        ensureColumn("prompt_template", "username", "ALTER TABLE `prompt_template` ADD COLUMN `username` varchar(64) DEFAULT NULL AFTER `user_id`");
        ensureColumn("prompt_template", "nickname", "ALTER TABLE `prompt_template` ADD COLUMN `nickname` varchar(64) DEFAULT NULL AFTER `username`");
        ensureColumn("prompt_template", "avatar", "ALTER TABLE `prompt_template` ADD COLUMN `avatar` varchar(512) DEFAULT NULL AFTER `nickname`");
        ensureColumn("prompt_template", "likes_count", "ALTER TABLE `prompt_template` ADD COLUMN `likes_count` int NOT NULL DEFAULT 0 AFTER `tag`");
        ensureColumn("prompt_template", "comments_count", "ALTER TABLE `prompt_template` ADD COLUMN `comments_count` int NOT NULL DEFAULT 0 AFTER `likes_count`");
        ensureColumn("style_card", "user_id", "ALTER TABLE `style_card` ADD COLUMN `user_id` bigint DEFAULT NULL AFTER `project_id`");
        ensureColumn("style_card", "username", "ALTER TABLE `style_card` ADD COLUMN `username` varchar(64) DEFAULT NULL AFTER `user_id`");
        ensureColumn("style_card", "nickname", "ALTER TABLE `style_card` ADD COLUMN `nickname` varchar(64) DEFAULT NULL AFTER `username`");
        ensureColumn("style_card", "avatar", "ALTER TABLE `style_card` ADD COLUMN `avatar` varchar(512) DEFAULT NULL AFTER `nickname`");
        ensureColumn("style_card", "likes_count", "ALTER TABLE `style_card` ADD COLUMN `likes_count` int NOT NULL DEFAULT 0 AFTER `tag`");
        ensureColumn("style_card", "comments_count", "ALTER TABLE `style_card` ADD COLUMN `comments_count` int NOT NULL DEFAULT 0 AFTER `likes_count`");
        ensureColumn("community_post", "nickname", "ALTER TABLE `community_post` ADD COLUMN `nickname` varchar(64) DEFAULT NULL AFTER `username`");
        ensureColumn("community_post", "avatar", "ALTER TABLE `community_post` ADD COLUMN `avatar` varchar(512) DEFAULT NULL AFTER `nickname`");
        ensureColumn("community_post", "comments_count", "ALTER TABLE `community_post` ADD COLUMN `comments_count` int NOT NULL DEFAULT 0 AFTER `likes_count`");
        ensureTable("public_content_like", """
                CREATE TABLE IF NOT EXISTS `public_content_like` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `resource_type` varchar(32) NOT NULL,
                  `resource_id` bigint NOT NULL,
                  `user_id` bigint NOT NULL,
                  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_pcl_resource_user` (`resource_type`,`resource_id`,`user_id`),
                  KEY `idx_pcl_resource` (`resource_type`,`resource_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        ensureTable("public_content_comment", """
                CREATE TABLE IF NOT EXISTS `public_content_comment` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `resource_type` varchar(32) NOT NULL,
                  `resource_id` bigint NOT NULL,
                  `parent_id` bigint DEFAULT NULL,
                  `user_id` bigint NOT NULL,
                  `username` varchar(64) DEFAULT NULL,
                  `nickname` varchar(64) DEFAULT NULL,
                  `avatar` varchar(512) DEFAULT NULL,
                  `reply_to_user_id` bigint DEFAULT NULL,
                  `reply_to_username` varchar(64) DEFAULT NULL,
                  `reply_to_nickname` varchar(64) DEFAULT NULL,
                  `content` text NOT NULL,
                  `status` tinyint NOT NULL DEFAULT 1,
                  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  KEY `idx_pcc_resource` (`resource_type`,`resource_id`,`status`),
                  KEY `idx_pcc_parent` (`parent_id`),
                  KEY `idx_pcc_user` (`user_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void ensureColumn(String tableName, String columnName, String ddl) {
        if (!hasColumn(tableName, columnName)) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void ensureTable(String tableName, String ddl) {
        if (!hasTable(tableName)) {
            jdbcTemplate.execute(ddl);
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        Boolean result = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
                return columns.next();
            }
        });
        return Boolean.TRUE.equals(result);
    }

    private boolean hasTable(String tableName) {
        Boolean result = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
                return tables.next();
            }
        });
        return Boolean.TRUE.equals(result);
    }
}
