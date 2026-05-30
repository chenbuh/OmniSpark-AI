package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.entity.GenerationTask;
import com.example.aihub.infrastructure.entity.Project;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.*;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/stats")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminStatsController {
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final GenerationTaskMapper taskMapper;
    private final AssetMapper assetMapper;

    /** trends 结果按天缓存,跨天自动失效。 */
    private volatile Map<String, Object> cachedTrends;
    private volatile LocalDate cachedTrendsDate;

    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userMapper.selectCount(null));
        stats.put("totalProjects", projectMapper.selectCount(null));
        stats.put("totalTasks", taskMapper.selectCount(null));
        stats.put("totalAssets", assetMapper.selectCount(null));
        stats.put("successTasks", taskMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenerationTask>()
                        .eq(GenerationTask::getStatus, "success")));
        stats.put("failedTasks", taskMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenerationTask>()
                        .eq(GenerationTask::getStatus, "failed")));
        return ApiResult.ok(stats);
    }

    @GetMapping("/users")
    public ApiResult<List<User>> users() {
        return ApiResult.ok(userMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .orderByDesc(User::getId)));
    }

    @GetMapping("/trends")
    public ApiResult<Map<String, Object>> trends() {
        LocalDate today = LocalDate.now();
        // 结果按当天缓存,同一天内重复请求直接复用,避免重复聚合
        if (cachedTrends != null && today.equals(cachedTrendsDate)) {
            return ApiResult.ok(cachedTrends);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        LocalDate startDay = today.minusDays(6);
        String startTime = startDay.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 各表一次 GROUP BY DATE 聚合,替代每天一次的 14 次 COUNT
        Map<String, Long> taskCountByDay = countByDay(taskMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GenerationTask>()
                        .select("DATE(created_at) AS d", "COUNT(*) AS c")
                        .ge("created_at", startTime)
                        .groupBy("DATE(created_at)")));
        Map<String, Long> userCountByDay = countByDay(userMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .select("DATE(created_at) AS d", "COUNT(*) AS c")
                        .ge("created_at", startTime)
                        .groupBy("DATE(created_at)")));

        List<Map<String, Object>> dailyTasks = new ArrayList<>();
        List<Map<String, Object>> dailyUsers = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String key = day.toString();
            String label = day.format(fmt);
            dailyTasks.add(dayItem(label, taskCountByDay.getOrDefault(key, 0L)));
            dailyUsers.add(dayItem(label, userCountByDay.getOrDefault(key, 0L)));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dailyTasks", dailyTasks);
        result.put("dailyUsers", dailyUsers);

        cachedTrends = result;
        cachedTrendsDate = today;
        return ApiResult.ok(result);
    }

    /** 将 GROUP BY DATE 聚合结果(含 d/c 列)转为 {yyyy-MM-dd -> count}。 */
    private Map<String, Long> countByDay(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object d = row.get("d");
            Object c = row.get("c");
            if (d != null) {
                map.put(String.valueOf(d), c == null ? 0L : ((Number) c).longValue());
            }
        }
        return map;
    }

    private Map<String, Object> dayItem(String label, long count) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("date", label);
        item.put("count", count);
        return item;
    }

    @GetMapping("/export/csv")
    public void exportCsv(HttpServletResponse response) throws Exception {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=stats_" + LocalDate.now() + ".csv");

        StringBuilder csv = new StringBuilder();
        csv.append("指标,数值\n");

        Map<String, Object> stats = overview().getData();
        if (stats != null) {
            csv.append("总用户数,").append(stats.getOrDefault("totalUsers", 0)).append("\n");
            csv.append("总项目数,").append(stats.getOrDefault("totalProjects", 0)).append("\n");
            csv.append("总任务数,").append(stats.getOrDefault("totalTasks", 0)).append("\n");
            csv.append("总资产数,").append(stats.getOrDefault("totalAssets", 0)).append("\n");
            csv.append("成功任务数,").append(stats.getOrDefault("successTasks", 0)).append("\n");
            csv.append("失败任务数,").append(stats.getOrDefault("failedTasks", 0)).append("\n");
        }

        csv.append("\n日期,任务数,注册用户数\n");
        Map<String, Object> trends = trends().getData();
        if (trends != null) {
            List<Map<String, Object>> dailyTasks = (List<Map<String, Object>>) trends.get("dailyTasks");
            List<Map<String, Object>> dailyUsers = (List<Map<String, Object>>) trends.get("dailyUsers");
            if (dailyTasks != null && dailyUsers != null) {
                for (int i = 0; i < dailyTasks.size(); i++) {
                    String date = (String) dailyTasks.get(i).getOrDefault("date", "");
                    Long taskCount = (Long) dailyTasks.get(i).getOrDefault("count", 0L);
                    Long userCount = i < dailyUsers.size() ? (Long) dailyUsers.get(i).getOrDefault("count", 0L) : 0L;
                    csv.append(date).append(",").append(taskCount).append(",").append(userCount).append("\n");
                }
            }
        }

        csv.append("\n用户ID,用户名,角色,状态\n");
        List<User> users = users().getData();
        if (users != null) {
            for (User u : users) {
                csv.append(com.example.aihub.common.util.CsvUtil.escape(u.getId())).append(",")
                   .append(com.example.aihub.common.util.CsvUtil.escape(u.getUsername())).append(",")
                   .append(com.example.aihub.common.util.CsvUtil.escape(u.getRole())).append(",")
                   .append(com.example.aihub.common.util.CsvUtil.escape(u.getStatus())).append("\n");
            }
        }

        response.getWriter().write(csv.toString());
    }
}
