# 统一生图 + 视频平台：初始化骨架与 DTO/VO

## 1. 后端初始化骨架

### 1.1 Maven 依赖建议

- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-boot-starter-data-redis`
- `mybatis-plus-boot-starter`
- `mysql-connector-j`
- `sa-token-spring-boot3-starter`
- `lombok`
- `hutool-all`
- `spring-boot-starter-actuator`

### 1.2 启动类

```java
@SpringBootApplication
public class AiHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiHubApplication.class, args);
    }
}
```

### 1.3 推荐基础包

- `common.result`
- `common.exception`
- `common.config`
- `common.util`
- `module.auth`
- `module.user`
- `module.project`
- `module.modelprovider`
- `module.generation`
- `module.asset`
- `module.prompttemplate`
- `module.quota`
- `module.system`

### 1.4 统一返回体

```java
public class ApiResult<T> {
    private int code;
    private String message;
    private T data;
}
```

### 1.5 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handle(Exception e) {
        return ApiResult.fail(e.getMessage());
    }
}
```

### 1.6 Sa-Token 配置

- 登录态统一交给 `Sa-Token`
- 接口层通过注解或拦截器控制权限
- 管理端和后续 API 可共用一套鉴权逻辑

### 1.7 MyBatis-Plus 配置

- 开启分页插件
- 逻辑删除字段建议统一为 `deleted`
- 创建时间、更新时间统一自动填充

## 2. 前端初始化骨架

### 2.1 入口文件

```text
src
├── main.ts
├── App.vue
├── api
├── router
├── store
├── layouts
├── views
└── components
```

### 2.2 main.ts

```ts
import { createApp } from 'vue'
import App from './App.vue'

createApp(App).mount('#app')
```

### 2.3 路由入口

- `login`
- `dashboard`
- `generate/image`
- `generate/video`
- `tasks`
- `assets`
- `model-providers`
- `prompt-templates`
- `stats`
- `settings`

### 2.4 状态管理

- `userStore`
- `projectStore`
- `taskStore`
- `assetStore`
- `modelProviderStore`

### 2.5 页面布局建议

- 左侧菜单
- 顶部项目切换
- 中间工作区
- 右侧详情抽屉

## 3. DTO / VO 设计

### 3.1 模型配置 DTO

```java
public class ModelProviderSaveDTO {
    @NotBlank
    private String name;
    @NotBlank
    private String type;
    @NotBlank
    private String baseUrl;
    @NotBlank
    private String apiKey;
    @NotBlank
    private String modelName;
    private Boolean enabled;
    private Boolean isDefault;
    private String configJson;
}
```

### 3.2 生图请求 DTO

```java
public class ImageGenerateDTO {
    @NotNull
    private Long projectId;
    @NotNull
    private Long providerId;
    @NotBlank
    private String prompt;
    private String negativePrompt;
    private List<Long> referenceAssetIds;
    private String size;
    private Integer count;
    private Map<String, Object> options;
}
```

### 3.3 生视频请求 DTO

```java
public class VideoGenerateDTO {
    @NotNull
    private Long projectId;
    @NotNull
    private Long providerId;
    @NotBlank
    private String prompt;
    private Long sourceAssetId;
    private String duration;
    private Map<String, Object> options;
}
```

### 3.4 任务 VO

```java
public class GenerationTaskVO {
    private Long id;
    private String taskType;
    private String status;
    private Integer progress;
    private String prompt;
    private String errorMessage;
    private Long resultAssetId;
    private LocalDateTime createdAt;
}
```

### 3.5 资产 VO

```java
public class AssetVO {
    private Long id;
    private String assetType;
    private String fileName;
    private String fileUrl;
    private String thumbUrl;
    private Boolean favorite;
}
```

### 3.6 模型配置 VO

```java
public class ModelProviderVO {
    private Long id;
    private String name;
    private String type;
    private String baseUrl;
    private String modelName;
    private Boolean enabled;
    private Boolean isDefault;
}
```

## 4. 接口命名建议

- 输入用 `DTO`
- 输出用 `VO`
- 查询条件用 `Query`
- 分页结果统一用 `PageResult`

## 5. 关键约束

- 生图和视频都走同一个 `provider` 抽象
- 所有生成任务都走统一任务表
- 图片和视频结果都进统一资产库
- `baseUrl + apiKey` 可在项目级配置
- 前端只需要切换生成类型，不要拆成两个产品

