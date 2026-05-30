package com.example.aihub.infrastructure.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.util.PasswordUtil;
import com.example.aihub.infrastructure.entity.*;
import com.example.aihub.infrastructure.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(1)
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final ProjectMapper projectMapper;
    private final ModelProviderMapper providerMapper;
    private final GenerationTaskMapper taskMapper;
    private final AssetMapper assetMapper;
    private final PromptTemplateMapper templateMapper;
    private final QuotaRecordMapper quotaRecordMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        cleanupDemoData();
        upsertRole("admin", "超级管理员");
        upsertRole("user", "普通用户");
        upsertAdminUser();
        upsertSystemConfig("platform.name", "OmniSpark AI", "system", "平台名称");
        seedScheduledTasks();
    }

    private void cleanupDemoData() {
        userMapper.delete(new LambdaQueryWrapper<User>().in(User::getUsername, List.of("creator", "user")));
        projectMapper.delete(new LambdaQueryWrapper<Project>().in(Project::getName, List.of("默认创意项目", "电商营销视频")));
        providerMapper.delete(new LambdaQueryWrapper<ModelProvider>().in(ModelProvider::getName, List.of(
                "OpenAI 生图 API",
                "Sora 视频生成 API",
                "Midjourney 桥接 API",
                "Luma 视频接口",
                "内置 OpenAI 生图 API",
                "内置 Sora 视频生成 API"
        )));
        taskMapper.delete(new LambdaQueryWrapper<GenerationTask>().in(GenerationTask::getPrompt, List.of(
                "cyberpunk neon city street, futuristic high-tech skyscrapers, rain reflection on asphalt, cybernetic people walking, hyper realistic, Unreal Engine 5 render, 8k resolution",
                "gigantic space station orbiting a blue nebular galaxy, space warships departing, hyper-detailed hulls, solar panels, cinematic lighting, sci-fi concept art, masterpiece",
                "flying through a dense starfield at hyperspace speed, cosmic dust glowing in purple and gold, warp speed, ultra high definition, sci-fi movie opening scene",
                "first-person view cockpit of a high-speed flying sports car driving through futuristic neon mega city canyons at night, rain streaked glass, extreme speed, motion blur",
                "close up portrait of a cyberpunk female pilot warrior, white hair, neon glowing visor, cybernetic facial implants, soft cinematic neon rim light, highly detailed skin texture",
                "enchanted mystical forest with bioluminescent glowing plants and crystals, a serene sacred lake in the center reflecting giant glowing full moon, ethereal mood, fantasy landscape"
        )));
        assetMapper.delete(new LambdaQueryWrapper<Asset>().like(Asset::getFileName, "赛博朋克深渊霓虹.png")
                .or().like(Asset::getFileName, "银河太空堡垒星港.jpg")
                .or().like(Asset::getFileName, "宇宙星海穿梭之旅.mp4")
                .or().like(Asset::getFileName, "魔幻森林的水晶圣湖.png")
                .or().like(Asset::getFileName, "赛博朋克极速狂飙.mp4")
                .or().like(Asset::getFileName, "赛博女武神特写.jpg"));
        templateMapper.delete(new LambdaQueryWrapper<PromptTemplate>().in(PromptTemplate::getName, List.of(
                "赛博霓虹雨夜街道",
                "银河无垠太空星港",
                "梦幻森林荧光圣湖"
        )));
        quotaRecordMapper.delete(new LambdaQueryWrapper<QuotaRecord>().eq(QuotaRecord::getRemark, "初始化额度使用记录"));
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
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        if (user == null) {
            user = new User();
            user.setUsername("admin");
            user.setPassword(PasswordUtil.encode("admin123"));
            user.setNickname("管理员");
            user.setAvatar("https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=200");
            user.setRole("admin");
            user.setStatus(1);
            userMapper.insert(user);
            return;
        }
        if (!PasswordUtil.matches("admin123", user.getPassword())) {
            user.setPassword(PasswordUtil.encode("admin123"));
        }
        user.setNickname("管理员");
        user.setAvatar("https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=200");
        user.setRole("admin");
        user.setStatus(1);
        userMapper.updateById(user);
    }

    private void upsertSystemConfig(String key, String value, String group, String remark) {
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setConfigGroup(group);
            config.setRemark(remark);
            systemConfigMapper.insert(config);
            return;
        }
        config.setConfigValue(value);
        config.setConfigGroup(group);
        config.setRemark(remark);
        systemConfigMapper.updateById(config);
    }

    private void seedScheduledTasks() {
        seedTask("每日数据清理", "自动清理 30 天前的任务和日志", "0 0 3 * * ?", "cleanup", "{\"daysOld\":30}");
        seedTask("每小时统计快照", "每小时记录系统统计快照", "0 0 * * * ?", "stats", "{}");
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
}
