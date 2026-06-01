package com.example.aihub.infrastructure.service;

import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.infrastructure.dto.CaptchaVerifyDTO;
import com.example.aihub.infrastructure.vo.CaptchaVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 自研多形态验证码服务（纯代码生成图料，零素材依赖）。
 *
 * <p>三种形态随机出现，攻击者无法只针对一种写解法：
 * <ul>
 *   <li>rotate —— 图被随机旋转，用户转正</li>
 *   <li>sequence —— 按指定顺序点击图上目标</li>
 *   <li>track —— 沿随机轨道把滑块拖到终点</li>
 * </ul>
 *
 * <p>安全要点：所有「答案」（角度 / 目标坐标顺序 / 终点 x）只写入 Redis，绝不下发给前端；
 * 校验叠加行为风控（耗时下限、轨迹像人）；按 IP 记失败次数，超限临时拒发。
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {
    private static final String ANSWER_KEY = "captcha:answer:";
    private static final String TICKET_KEY = "captcha:ticket:";
    private static final String FAIL_KEY = "captcha:fail:";
    private static final String LAST_TYPE_KEY = "captcha:last-type:";

    private static final Duration ANSWER_TTL = Duration.ofMinutes(2);
    private static final Duration TICKET_TTL = Duration.ofMinutes(2);
    private static final Duration FAIL_TTL = Duration.ofMinutes(10);
    private static final Duration LAST_TYPE_TTL = Duration.ofMinutes(5);

    /** 同一 IP 在 FAIL_TTL 内累计失败上限，超过临时拒发新验证码。 */
    private static final int MAX_FAIL_PER_IP = 20;
    /** 人类完成作答的耗时下限（毫秒），低于此值判定为脚本。 */
    private static final long MIN_HUMAN_MILLIS = 300;

    private static final int CANVAS_W = 320;
    private static final int CANVAS_H = 180;
    private static final int SEQUENCE_SHAPE_HALF = 26;
    private static final int SEQUENCE_CLICK_TOLERANCE = 40;

    private static final String[] TYPES = {"rotate", "sequence", "track"};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    /** 生成一个随机形态的验证码；答案写入 Redis，仅返回展示数据。 */
    public CaptchaVO generate(String clientIp) {
        if (isIpBlocked(clientIp)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }
        String type = pickRandomType(clientIp);
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        return switch (type) {
            case "rotate" -> generateRotate(captchaId);
            case "sequence" -> generateSequence(captchaId);
            default -> generateTrack(captchaId);
        };
    }

    private String pickRandomType(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || TYPES.length < 2) {
            return TYPES[random.nextInt(TYPES.length)];
        }

        String key = LAST_TYPE_KEY + clientIp;
        String lastType = redisTemplate.opsForValue().get(key);

        int totalWeight = 0;
        for (String type : TYPES) {
            totalWeight += type.equals(lastType) ? 1 : 4;
        }

        int roll = random.nextInt(totalWeight);
        String nextType = TYPES[0];
        for (String type : TYPES) {
            roll -= type.equals(lastType) ? 1 : 4;
            if (roll < 0) {
                nextType = type;
                break;
            }
        }
        redisTemplate.opsForValue().set(key, nextType, LAST_TYPE_TTL);
        return nextType;
    }

    private void saveAnswer(String captchaId, Answer answer) {
        try {
            redisTemplate.opsForValue().set(ANSWER_KEY + captchaId, objectMapper.writeValueAsString(answer), ANSWER_TTL);
        } catch (Exception e) {
            throw new BusinessException("验证码生成失败，请重试");
        }
    }

    // ============ 形态一：旋转校正 ============
    private CaptchaVO generateRotate(String captchaId) {
        // 圆形图案，随机旋转一个明显角度（避开接近 0/360 的歧义区）
        int rotation = 30 + random.nextInt(300); // 30~329 度
        BufferedImage base = drawRotatePattern();
        BufferedImage rotated = rotateImage(base, rotation);

        // 答案：用户需要把图再转 (360 - rotation) 才回正，即作答角度应接近该值
        int correctAnswer = (360 - rotation) % 360;
        saveAnswer(captchaId, Answer.rotate(correctAnswer));

        CaptchaVO vo = baseVO(captchaId, "rotate");
        vo.setImage(toDataUri(rotated));
        vo.setPrompt("转动圆盘，把图案摆正");
        return vo;
    }

    // ============ 形态二：多点顺序点击 ============
    private CaptchaVO generateSequence(String captchaId) {
        int targetCount = 3;
        // 候选形状池，随机抽 targetCount 个不重复，作为「按此顺序点击」
        String[] shapePool = {"方块", "三角", "圆形", "星形", "菱形"};
        List<Integer> shapeIdx = pickDistinct(shapePool.length, targetCount);

        BufferedImage img = newCanvas();
        Graphics2D g = img.createGraphics();
        applyQuality(g);
        paintNoisyBackground(g);

        List<int[]> targetCenters = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<int[]> placed = new ArrayList<>();
        for (int i = 0; i < targetCount; i++) {
            int[] c = placeNonOverlapping(placed, SEQUENCE_SHAPE_HALF);
            placed.add(c);
            targetCenters.add(c);
            labels.add(shapePool[shapeIdx.get(i)]);
            drawShape(g, shapeIdx.get(i), c[0], c[1], SEQUENCE_SHAPE_HALF);
        }
        g.dispose();

        // 答案：目标中心的有序坐标 + 命中容差
        saveAnswer(captchaId, Answer.sequence(targetCenters));

        CaptchaVO vo = baseVO(captchaId, "sequence");
        vo.setImage(toDataUri(img));
        vo.setSequenceLabels(labels);
        vo.setPrompt("依次点击：" + String.join(" → ", labels));
        return vo;
    }

    // ============ 形态三：轨迹仿形拖动 ============
    private CaptchaVO generateTrack(String captchaId) {
        BufferedImage img = newCanvas();
        Graphics2D g = img.createGraphics();
        applyQuality(g);
        paintNoisyBackground(g);

        // 随机生成一条折线/曲线轨道：从左侧某 y 到右侧终点
        int y = 60 + random.nextInt(CANVAS_H - 120);
        int endX = CANVAS_W - 40 - random.nextInt(30);
        List<int[]> path = buildTrackPath(40, y, endX);
        // 画轨道
        g.setColor(new Color(255, 255, 255, 160));
        g.setStroke(new BasicStroke(3f));
        for (int i = 1; i < path.size(); i++) {
            g.drawLine(path.get(i - 1)[0], path.get(i - 1)[1], path.get(i)[0], path.get(i)[1]);
        }
        // 终点圈
        int[] end = path.get(path.size() - 1);
        g.setColor(new Color(80, 200, 120));
        g.fillOval(end[0] - 8, end[1] - 8, 16, 16);
        g.dispose();

        saveAnswer(captchaId, Answer.track(end[0]));

        CaptchaVO vo = baseVO(captchaId, "track");
        vo.setImage(toDataUri(img));
        vo.setTrackPath(path);
        vo.setTrackY(path.get(0)[1]);
        vo.setPrompt("沿轨道把滑块拖到终点");
        return vo;
    }

    /**
     * 校验作答。通过则返回一次性票据（登录/注册时核销）；失败抛 BusinessException。
     */
    public String verify(CaptchaVerifyDTO dto, String clientIp) {
        if (isIpBlocked(clientIp)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }
        String raw = redisTemplate.opsForValue().get(ANSWER_KEY + dto.getCaptchaId());
        if (raw == null) {
            throw new BusinessException("验证码已过期，请刷新重试");
        }
        // 答案一次性：无论成败先删除，杜绝同一图重复试错爆破
        redisTemplate.delete(ANSWER_KEY + dto.getCaptchaId());

        Answer answer = parseAnswer(raw);
        boolean humanLike = looksHuman(answer, dto.getTrail());
        boolean correct = humanLike && switch (answer.type) {
            case "rotate" -> verifyRotate(answer, dto);
            case "sequence" -> verifySequence(answer, dto);
            case "track" -> verifyTrack(answer, dto);
            default -> false;
        };

        if (!correct) {
            recordFail(clientIp);
            throw new BusinessException("验证未通过，请重试");
        }

        String ticket = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(TICKET_KEY + ticket, "1", TICKET_TTL);
        return ticket;
    }

    /** 登录/注册核销票据：存在且未用过则消费并返回 true。 */
    public boolean consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return false;
        }
        Boolean deleted = redisTemplate.delete(TICKET_KEY + ticket);
        return Boolean.TRUE.equals(deleted);
    }

    private boolean verifyRotate(Answer answer, CaptchaVerifyDTO dto) {
        if (dto.getAngle() == null) {
            return false;
        }
        int diff = Math.abs(((dto.getAngle() - answer.angle) % 360 + 360) % 360);
        diff = Math.min(diff, 360 - diff);
        return diff <= 12; // 容差 ±12 度
    }

    private boolean verifySequence(Answer answer, CaptchaVerifyDTO dto) {
        List<int[]> points = dto.getPoints();
        if (points == null || answer.targets == null || points.size() != answer.targets.size()) {
            return false;
        }
        for (int i = 0; i < answer.targets.size(); i++) {
            int[] target = answer.targets.get(i);
            int[] click = points.get(i);
            if (click == null || click.length < 2) {
                return false;
            }
            double dist = Math.hypot(click[0] - target[0], click[1] - target[1]);
            if (dist > SEQUENCE_CLICK_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private boolean verifyTrack(Answer answer, CaptchaVerifyDTO dto) {
        if (dto.getX() == null) {
            return false;
        }
        return Math.abs(dto.getX() - answer.endX) <= 8; // 终点容差 ±8px
    }

    /**
     * 行为风控：判断作答轨迹是否「像人」。
     * 规则（宽松起步，可后续收紧）：① 至少几个采样点；② 总耗时不低于下限（防瞬时脚本）；
     * ③ 不是完全匀速直线（人手有抖动与加减速）。无轨迹则直接判否。
     */
    private boolean looksHuman(Answer answer, List<int[]> trail) {
        if (answer == null || trail == null || trail.size() < minTrailPoints(answer)) {
            return false;
        }

        long previousTime = -1;
        for (int[] point : trail) {
            if (point == null || point.length < 3) {
                return false;
            }
            long currentTime = point[2];
            if (currentTime < 0 || currentTime < previousTime) {
                return false;
            }
            previousTime = currentTime;
        }

        // 前端上报的是「从验证码展示开始」的相对时间，不能只计算首尾轨迹点差值。
        // 否则点击/旋转类验证码在用户观察一会儿后快速作答也会被误判为脚本。
        long elapsedSinceChallengeShown = trail.get(trail.size() - 1)[2];
        if (elapsedSinceChallengeShown < MIN_HUMAN_MILLIS) {
            return false;
        }

        if (!"track".equals(answer.type)) {
            return true;
        }

        return trackTrailLooksHuman(trail);
    }

    private int minTrailPoints(Answer answer) {
        return switch (answer.type) {
            case "rotate" -> 1;
            case "sequence" -> answer.targets == null ? 3 : answer.targets.size();
            case "track" -> 4;
            default -> 4;
        };
    }

    private boolean trackTrailLooksHuman(List<int[]> trail) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (int[] point : trail) {
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
        }
        if (maxX - minX < 80) {
            return false;
        }

        // 检测时间间隔与位移是否过于均匀（脚本常等步长）
        int n = trail.size();
        long prevT = trail.get(0)[2];
        int prevX = trail.get(0)[0];
        List<Double> intervals = new ArrayList<>();
        List<Double> xSteps = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            long it = trail.get(i)[2] - prevT;
            prevT = trail.get(i)[2];

            int dx = Math.abs(trail.get(i)[0] - prevX);
            prevX = trail.get(i)[0];
            if (dx > 0) {
                intervals.add((double) it);
                xSteps.add((double) dx);
            }
        }
        if (xSteps.size() < 3) {
            return false;
        }

        double intervalVariance = variance(intervals);
        double xStepVariance = variance(xSteps);
        // 完全匀速、等步长拖动视为脚本；真人拖动在事件间隔或位移上通常会有自然波动。
        return intervalVariance > 1.0 || xStepVariance > 1.0;
    }

    private double variance(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        double mean = sum / values.size();
        double variance = 0;
        for (double value : values) {
            variance += Math.pow(value - mean, 2);
        }
        return variance / values.size();
    }

    // ============ 画图工具 ============
    private BufferedImage newCanvas() {
        return new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_RGB);
    }

    private void applyQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /** 渐变底 + 随机干扰线/噪点，抬高纯图像识别成本。 */
    private void paintNoisyBackground(Graphics2D g) {
        Color c1 = randomColor(40, 120);
        Color c2 = randomColor(40, 120);
        g.setPaint(new GradientPaint(0, 0, c1, CANVAS_W, CANVAS_H, c2));
        g.fillRect(0, 0, CANVAS_W, CANVAS_H);
        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(randomColor(120, 220));
            g.setStroke(new BasicStroke(1f + random.nextFloat() * 2));
            g.drawLine(random.nextInt(CANVAS_W), random.nextInt(CANVAS_H),
                    random.nextInt(CANVAS_W), random.nextInt(CANVAS_H));
        }
        // 噪点
        for (int i = 0; i < 120; i++) {
            g.setColor(randomColor(150, 255));
            int x = random.nextInt(CANVAS_W);
            int y = random.nextInt(CANVAS_H);
            g.fillOval(x, y, 2, 2);
        }
    }

    /** 旋转形态的圆盘图案：一个带方向指示的圆，转正才容易辨认。 */
    private BufferedImage drawRotatePattern() {
        int size = 150;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        applyQuality(g);
        int cx = size / 2, cy = size / 2, r = size / 2 - 6;

        Color primary = randomColor(70, 170);
        Color secondary = randomColor(90, 210);
        Color accent = randomColor(190, 255);
        // 圆盘
        g.setPaint(new GradientPaint(0, 0, primary, size, size, secondary));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);

        g.setColor(new Color(255, 255, 255, 55));
        g.setStroke(new BasicStroke(2f));
        int arcCount = 3 + random.nextInt(3);
        for (int i = 0; i < arcCount; i++) {
            int inset = 18 + i * 12;
            int start = random.nextInt(360);
            int extent = 45 + random.nextInt(90);
            g.drawArc(inset, inset, size - inset * 2, size - inset * 2, start, extent);
        }

        int dotCount = 4 + random.nextInt(4);
        for (int i = 0; i < dotCount; i++) {
            double rad = Math.toRadians(random.nextInt(360));
            int dotR = 24 + random.nextInt(36);
            int x = (int) (cx + Math.cos(rad) * dotR);
            int y = (int) (cy + Math.sin(rad) * dotR);
            int d = 5 + random.nextInt(6);
            g.setColor(i % 2 == 0 ? accent : new Color(255, 255, 255, 190));
            g.fillOval(x - d / 2, y - d / 2, d, d);
        }

        String marks = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        String mark = String.valueOf(marks.charAt(random.nextInt(marks.length())));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        FontMetrics metrics = g.getFontMetrics();
        int markX = cx - metrics.stringWidth(mark) / 2;
        int markY = cy + (metrics.getAscent() - metrics.getDescent()) / 2 + 6;
        g.setColor(new Color(0, 0, 0, 85));
        g.drawString(mark, markX + 2, markY + 2);
        g.setColor(new Color(255, 255, 255, 230));
        g.drawString(mark, markX, markY);

        // 方向指针（朝上为正）
        g.setColor(new Color(255, 255, 255, 230));
        Polygon arrow = new Polygon(
                new int[]{cx, cx - 14, cx + 14},
                new int[]{cy - r + 8, cy - r + 34, cy - r + 34}, 3);
        g.fillPolygon(arrow);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx, cy - r + 31, cx, cy - r + 52);
        // 几个扇形刻度，增强方向辨识
        g.setStroke(new BasicStroke(3f));
        for (int a = 0; a < 360; a += 45) {
            double rad = Math.toRadians(a);
            int x1 = (int) (cx + Math.cos(rad) * (r - 4));
            int y1 = (int) (cy + Math.sin(rad) * (r - 4));
            int x2 = (int) (cx + Math.cos(rad) * (r - 14));
            int y2 = (int) (cy + Math.sin(rad) * (r - 14));
            g.setColor(new Color(255, 255, 255, 150));
            g.drawLine(x1, y1, x2, y2);
        }
        g.dispose();
        return img;
    }

    private BufferedImage rotateImage(BufferedImage src, int degrees) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        applyQuality(g);
        AffineTransform at = new AffineTransform();
        at.rotate(Math.toRadians(degrees), w / 2.0, h / 2.0);
        g.drawImage(src, at, null);
        g.dispose();
        return dst;
    }

    /** 在画布上画一个指定形状（0方块 1三角 2圆 3星 4菱形）。 */
    private void drawShape(Graphics2D g, int shape, int cx, int cy, int half) {
        g.setColor(randomColor(180, 255));
        switch (shape) {
            case 0 -> g.fillRect(cx - half, cy - half, half * 2, half * 2);
            case 1 -> g.fillPolygon(
                    new int[]{cx, cx - half, cx + half},
                    new int[]{cy - half, cy + half, cy + half}, 3);
            case 2 -> g.fillOval(cx - half, cy - half, half * 2, half * 2);
            case 3 -> g.fillPolygon(starX(cx, half), starY(cy, half), 10);
            default -> g.fillPolygon(
                    new int[]{cx, cx + half, cx, cx - half},
                    new int[]{cy - half, cy, cy + half, cy}, 4);
        }
        // 描边增强可见性
        g.setColor(new Color(0, 0, 0, 120));
        g.setStroke(new BasicStroke(2f));
    }

    private int[] starX(int cx, int r) {
        int[] xs = new int[10];
        for (int i = 0; i < 10; i++) {
            double rad = Math.toRadians(i * 36 - 90);
            int rr = (i % 2 == 0) ? r : r / 2;
            xs[i] = (int) (cx + Math.cos(rad) * rr);
        }
        return xs;
    }

    private int[] starY(int cy, int r) {
        int[] ys = new int[10];
        for (int i = 0; i < 10; i++) {
            double rad = Math.toRadians(i * 36 - 90);
            int rr = (i % 2 == 0) ? r : r / 2;
            ys[i] = (int) (cy + Math.sin(rad) * rr);
        }
        return ys;
    }

    // ============ 通用辅助 ============
    private CaptchaVO baseVO(String captchaId, String type) {
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaId(captchaId);
        vo.setType(type);
        vo.setWidth(CANVAS_W);
        vo.setHeight(CANVAS_H);
        return vo;
    }

    private List<Integer> pickDistinct(int poolSize, int count) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            all.add(i);
        }
        List<Integer> picked = new ArrayList<>();
        for (int i = 0; i < count && !all.isEmpty(); i++) {
            picked.add(all.remove(random.nextInt(all.size())));
        }
        return picked;
    }

    /** 在画布内放置一个不与已有目标重叠的中心点。 */
    private int[] placeNonOverlapping(List<int[]> placed, int half) {
        int margin = half + 6;
        for (int attempt = 0; attempt < 80; attempt++) {
            int x = margin + random.nextInt(CANVAS_W - margin * 2);
            int y = margin + random.nextInt(CANVAS_H - margin * 2);
            boolean ok = true;
            for (int[] p : placed) {
                if (Math.hypot(p[0] - x, p[1] - y) < SEQUENCE_CLICK_TOLERANCE * 2 + 10) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return new int[]{x, y};
            }
        }
        // 兜底：放中间偏移
        return new int[]{margin + random.nextInt(CANVAS_W - margin * 2), margin + random.nextInt(CANVAS_H - margin * 2)};
    }

    /** 生成一条从 (startX,y) 到终点的随机折线轨道。 */
    private List<int[]> buildTrackPath(int startX, int y, int endX) {
        List<int[]> path = new ArrayList<>();
        int segments = 3 + random.nextInt(2);
        int prevX = startX, prevY = y;
        path.add(new int[]{prevX, prevY});
        for (int i = 1; i <= segments; i++) {
            int nx = startX + (endX - startX) * i / segments;
            int ny = 40 + random.nextInt(CANVAS_H - 80);
            path.add(new int[]{nx, ny});
            prevX = nx;
            prevY = ny;
        }
        // 确保终点 x 为 endX
        path.get(path.size() - 1)[0] = endX;
        return path;
    }

    private Color randomColor(int min, int max) {
        int range = max - min;
        return new Color(min + random.nextInt(range), min + random.nextInt(range), min + random.nextInt(range));
    }

    private String toDataUri(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new BusinessException("验证码图片生成失败");
        }
    }

    private boolean isIpBlocked(String ip) {
        if (ip == null) {
            return false;
        }
        String v = redisTemplate.opsForValue().get(FAIL_KEY + ip);
        if (v == null) {
            return false;
        }
        try {
            return Integer.parseInt(v) >= MAX_FAIL_PER_IP;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void recordFail(String ip) {
        if (ip == null) {
            return;
        }
        try {
            Long c = redisTemplate.opsForValue().increment(FAIL_KEY + ip);
            if (c != null && c == 1L) {
                redisTemplate.expire(FAIL_KEY + ip, FAIL_TTL);
            }
        } catch (Exception ignored) {
        }
    }

    private Answer parseAnswer(String raw) {
        try {
            return objectMapper.readValue(raw, Answer.class);
        } catch (Exception e) {
            throw new BusinessException("验证码已失效，请刷新重试");
        }
    }

    /** 答案模型，序列化进 Redis。前端永远拿不到此对象。 */
    public static class Answer {
        public String type;
        public int angle;          // rotate
        public List<int[]> targets; // sequence
        public int endX;           // track

        public Answer() {
        }

        static Answer rotate(int angle) {
            Answer a = new Answer();
            a.type = "rotate";
            a.angle = angle;
            return a;
        }

        static Answer sequence(List<int[]> targets) {
            Answer a = new Answer();
            a.type = "sequence";
            a.targets = targets;
            return a;
        }

        static Answer track(int endX) {
            Answer a = new Answer();
            a.type = "track";
            a.endX = endX;
            return a;
        }
    }
}
