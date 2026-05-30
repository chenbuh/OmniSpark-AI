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
    }

    private void ensureColumn(String tableName, String columnName, String ddl) {
        if (!hasColumn(tableName, columnName)) {
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
}
