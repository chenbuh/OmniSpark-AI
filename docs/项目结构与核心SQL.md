# 统一生图 + 视频平台：项目结构与核心 SQL

## 1. 后端项目结构

```text
src/main/java/com/example/aihub
├── AiHubApplication.java
├── common
│   ├── config
│   ├── exception
│   ├── result
│   ├── util
│   └── enum
├── module
│   ├── auth
│   ├── user
│   ├── project
│   ├── modelprovider
│   ├── generation
│   ├── asset
│   ├── prompttemplate
│   ├── quota
│   └── system
└── infrastructure
    ├── mapper
    ├── entity
    ├── dto
    ├── vo
    └── service
```

### 模块职责

- `auth`：登录、登出、鉴权
- `user`：用户、角色、权限
- `project`：项目空间
- `modelprovider`：模型 URL、API Key、模型名配置
- `generation`：生图、生视频、任务流转
- `asset`：图片、视频、参考图等素材
- `prompttemplate`：提示词模板
- `quota`：次数、额度、统计
- `system`：系统配置

## 2. 前端项目结构

```text
src
├── api
├── assets
├── components
├── layouts
├── router
├── store
├── views
│   ├── dashboard
│   ├── generate
│   ├── tasks
│   ├── assets
│   ├── model-providers
│   ├── prompt-templates
│   ├── stats
│   └── settings
├── utils
└── styles
```

### 页面映射

- `dashboard`：总览页
- `generate/image`：生图页
- `generate/video`：生视频页
- `tasks`：任务中心
- `assets`：素材库
- `model-providers`：模型配置
- `prompt-templates`：提示词模板
- `stats`：统计分析
- `settings`：系统设置

## 3. 核心 SQL

```sql
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `password` varchar(255) NOT NULL,
  `nickname` varchar(64) DEFAULT NULL,
  `avatar` varchar(512) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_code` varchar(64) NOT NULL,
  `role_name` varchar(64) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `model_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `type` varchar(32) NOT NULL,
  `base_url` varchar(512) NOT NULL,
  `api_key` varchar(512) NOT NULL,
  `model_name` varchar(128) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `is_default` tinyint NOT NULL DEFAULT 0,
  `config_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_provider_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `generation_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `task_type` varchar(32) NOT NULL,
  `prompt` text NOT NULL,
  `negative_prompt` text DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `progress` int NOT NULL DEFAULT 0,
  `request_json` json DEFAULT NULL,
  `response_json` json DEFAULT NULL,
  `result_asset_id` bigint DEFAULT NULL,
  `error_message` varchar(1024) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_project_id` (`project_id`),
  KEY `idx_task_provider_id` (`provider_id`),
  KEY `idx_task_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `asset` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `task_id` bigint DEFAULT NULL,
  `asset_type` varchar(32) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_url` varchar(1024) NOT NULL,
  `thumb_url` varchar(1024) DEFAULT NULL,
  `mime_type` varchar(128) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `favorite` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_asset_project_id` (`project_id`),
  KEY `idx_asset_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `prompt_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `content` text NOT NULL,
  `tag` varchar(64) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_template_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quota_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `task_id` bigint DEFAULT NULL,
  `quota_type` varchar(32) NOT NULL,
  `amount` int NOT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_quota_user_id` (`user_id`),
  KEY `idx_quota_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(128) NOT NULL,
  `config_value` text NOT NULL,
  `config_group` varchar(64) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 4. 建表原则

- 统一使用 `utf8mb4`
- 关键配置表都保留 `created_at` / `updated_at`
- `model_provider` 必须支持项目级隔离
- `generation_task` 统一承接生图和视频
- `asset` 统一承接图片和视频结果

