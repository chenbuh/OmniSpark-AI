package com.example.aihub.infrastructure.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.util.PasswordUtil;
import com.example.aihub.infrastructure.entity.Role;
import com.example.aihub.infrastructure.entity.ScheduledTask;
import com.example.aihub.infrastructure.entity.SystemConfig;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.entity.DataDict;
import com.example.aihub.infrastructure.entity.DataDictItem;
import com.example.aihub.infrastructure.mapper.DataDictItemMapper;
import com.example.aihub.infrastructure.mapper.DataDictMapper;
import com.example.aihub.infrastructure.mapper.RoleMapper;
import com.example.aihub.infrastructure.mapper.ScheduledTaskMapper;
import com.example.aihub.infrastructure.mapper.SystemConfigMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
// 仅补齐系统运行所需的启动基础记录，不写入演示业务数据。
public class BootstrapDataInitializer implements CommandLineRunner {
    private static final String GENERATED_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final DataDictMapper dataDictMapper;
    private final DataDictItemMapper dataDictItemMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.bootstrap.admin.username:admin}")
    private String bootstrapAdminUsername;

    @Value("${app.bootstrap.admin.password:}")
    private String bootstrapAdminPassword;

    @Value("${app.bootstrap.admin.nickname:管理员}")
    private String bootstrapAdminNickname;

    @Value("${app.bootstrap.admin.avatar:}")
    private String bootstrapAdminAvatar;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        upsertRole("admin", "超级管理员");
        upsertRole("user", "普通用户");
        upsertAdminUser();
        ensureSystemConfig("platform.name", "OmniSpark AI", "system", "平台名称");
        ensureAssetCategoryDict();
        seedScheduledTasks();
    }

    private void upsertRole(String code, String name) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, code));
        if (role == null) {
            role = new Role();
            role.setRoleCode(code);
            role.setRoleName(name);
            role.setStatus(1);
            roleMapper.insert(role);
            return;
        }
        role.setRoleName(name);
        role.setStatus(1);
        roleMapper.updateById(role);
    }

    private void upsertAdminUser() {
        String username = normalizeText(bootstrapAdminUsername);
        if (username == null || username.isBlank()) {
            username = "admin";
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            String initialPassword = resolveBootstrapAdminPassword();
            user = new User();
            user.setUsername(username);
            user.setPassword(PasswordUtil.encode(initialPassword));
            user.setNickname(defaultIfBlank(normalizeText(bootstrapAdminNickname), "管理员"));
            user.setAvatar(normalizeText(bootstrapAdminAvatar));
            user.setRole("admin");
            user.setStatus(1);
            userMapper.insert(user);
            log.warn("Initialized bootstrap admin user '{}'. Initial password: {}", username, initialPassword);
            return;
        }

        boolean changed = false;
        if (!"admin".equals(user.getRole())) {
            user.setRole("admin");
            changed = true;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            user.setStatus(1);
            changed = true;
        }
        String nickname = defaultIfBlank(normalizeText(bootstrapAdminNickname), "管理员");
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            user.setNickname(nickname);
            changed = true;
        }
        String avatar = normalizeText(bootstrapAdminAvatar);
        if ((user.getAvatar() == null || user.getAvatar().isBlank()) && avatar != null && !avatar.isBlank()) {
            user.setAvatar(avatar);
            changed = true;
        }
        if (changed) {
            userMapper.updateById(user);
        }
    }

    private void ensureSystemConfig(String key, String value, String group, String remark) {
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        if (config != null) {
            return;
        }
        config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setConfigGroup(group);
        config.setRemark(remark);
        systemConfigMapper.insert(config);
    }

    private void ensureAssetCategoryDict() {
        DataDict dict = dataDictMapper.selectOne(new LambdaQueryWrapper<DataDict>()
                .eq(DataDict::getDictCode, "asset_category"));
        if (dict == null) {
            dict = new DataDict();
            dict.setDictCode("asset_category");
            dict.setDictName("资产分类");
            dict.setDescription("资产库与后台资产筛选使用的分类配置");
            dict.setStatus(1);
            dataDictMapper.insert(dict);
        }

        ensureAssetCategoryItem(dict.getId(), "image", "图像", 10);
        ensureAssetCategoryItem(dict.getId(), "video", "视频", 20);
        ensureAssetCategoryItem(dict.getId(), "reference", "参考素材", 30);
    }

    private void ensureAssetCategoryItem(Long dictId, String code, String name, int sortOrder) {
        DataDictItem item = dataDictItemMapper.selectOne(new LambdaQueryWrapper<DataDictItem>()
                .eq(DataDictItem::getDictId, dictId)
                .eq(DataDictItem::getItemCode, code));
        if (item != null) {
            return;
        }
        item = new DataDictItem();
        item.setDictId(dictId);
        item.setItemCode(code);
        item.setItemName(name);
        item.setSortOrder(sortOrder);
        item.setStatus(1);
        dataDictItemMapper.insert(item);
    }

    private void seedScheduledTasks() {
        seedTask("每日数据清理", "自动清理 30 天前的任务和日志", "0 0 3 * * ?", "cleanup", "{\"daysOld\":30}");
    }

    private void seedTask(String name, String desc, String cron, String type, String configJson) {
        Long count = scheduledTaskMapper.selectCount(new LambdaQueryWrapper<ScheduledTask>().eq(ScheduledTask::getName, name));
        if (count == null || count == 0) {
            ScheduledTask task = new ScheduledTask();
            task.setName(name);
            task.setDescription(desc);
            task.setCron(cron);
            task.setEnabled(1);
            task.setTaskType(type);
            task.setConfigJson(configJson);
            scheduledTaskMapper.insert(task);
        }
    }

    private String resolveBootstrapAdminPassword() {
        String configured = normalizeText(bootstrapAdminPassword);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return generatePassword(16);
    }

    private String generatePassword(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(GENERATED_PASSWORD_CHARS.length());
            builder.append(GENERATED_PASSWORD_CHARS.charAt(index));
        }
        return builder.toString();
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
