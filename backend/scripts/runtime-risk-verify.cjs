#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');
const { promisify } = require('util');
const crypto = require('crypto');

const execFileAsync = promisify(execFile);

const CONFIG = {
  baseUrl: process.env.RUNTIME_VERIFY_BASE_URL || 'http://localhost:8080',
  mysqlPath: process.env.RUNTIME_VERIFY_MYSQL || 'D:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe',
  mysqlUser: process.env.RUNTIME_VERIFY_DB_USER || 'root',
  mysqlPassword: process.env.RUNTIME_VERIFY_DB_PASSWORD || 'chen20040209',
  mysqlDatabase: process.env.RUNTIME_VERIFY_DB_NAME || 'aihub',
  redisCliPath: process.env.RUNTIME_VERIFY_REDIS || 'E:\\Redis\\redis-cli.exe',
  adminUsername: process.env.RUNTIME_VERIFY_ADMIN_USER || 'admin',
  adminPassword: process.env.RUNTIME_VERIFY_ADMIN_PASSWORD || 'admin123',
  longSliceMinutes: 5,
  groupGapMs: 13_000,
  intraGroupMs: 180,
  publicDelayMs: 80,
  uploadAccessSecret: process.env.RUNTIME_VERIFY_UPLOAD_SECRET || 'omnispark-dev-upload-access-secret-2026',
  uploadTtlSeconds: Number(process.env.RUNTIME_VERIFY_UPLOAD_TTL_SECONDS || 7200),
  reportDir: path.resolve(__dirname, '..', 'logs')
};

const PREFIX = `runtime_verify_${Date.now()}`;
const RUN_NUMERIC_TAG = Number(PREFIX.replace(/\D/g, '').slice(-6));
const RUN_SUFFIX = String(RUN_NUMERIC_TAG);
const TEST_IPS = {
  public86: `198.51.100.${10 + (RUN_NUMERIC_TAG % 40)}`,
  public90: `198.51.100.${60 + (RUN_NUMERIC_TAG % 40)}`,
  singleUser: `203.0.113.${20 + (RUN_NUMERIC_TAG % 40)}`,
  sameIpUa: `198.51.100.${110 + (RUN_NUMERIC_TAG % 40)}`,
  uaA: `198.51.100.${160 + (RUN_NUMERIC_TAG % 20)}`,
  uaB: `198.51.100.${181 + (RUN_NUMERIC_TAG % 20)}`,
  uaC: `198.51.100.${202 + (RUN_NUMERIC_TAG % 20)}`
};
const TEST_UA = {
  admin: `RuntimeAdmin/${RUN_SUFFIX}`,
  public86: `Runtime18086/${RUN_SUFFIX}`,
  public90: `Runtime18090/${RUN_SUFFIX}`,
  singleUser: `Runtime18088/${RUN_SUFFIX}`,
  sameIpUa: `Runtime18089/${RUN_SUFFIX}`,
  uaDistributed: `Runtime18091/${RUN_SUFFIX}`
};
const report = {
  prefix: PREFIX,
  startedAt: new Date().toISOString(),
  baseUrl: CONFIG.baseUrl,
  temp: {},
  scenarios: {},
  cleanup: {}
};

function log(message) {
  const stamp = new Date().toISOString().replace('T', ' ').replace('Z', '');
  console.log(`[${stamp}] ${message}`);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function stableStringify(value) {
  if (value === null || value === undefined) {
    return 'null';
  }
  if (Array.isArray(value)) {
    return `[${value.map(item => stableStringify(item)).join(',')}]`;
  }
  if (Object.prototype.toString.call(value) === '[object Object]') {
    const keys = Object.keys(value).sort();
    return `{${keys.map(key => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

function sha256Hex(value) {
  return crypto.createHash('sha256').update(value || '', 'utf8').digest('hex');
}

function hmacSha256Base64(value, secret) {
  return crypto.createHmac('sha256', secret).update(value, 'utf8').digest('base64');
}

function sqlEscape(value) {
  if (value === null || value === undefined) {
    return 'NULL';
  }
  return `'${String(value)
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "\\'")
    .replace(/\r/g, '\\r')
    .replace(/\n/g, '\\n')}'`;
}

async function execFileText(file, args) {
  const { stdout, stderr } = await execFileAsync(file, args, {
    cwd: path.resolve(__dirname, '..', '..'),
    maxBuffer: 16 * 1024 * 1024
  });
  if (stderr && stderr.trim()) {
    const filtered = stderr
      .split(/\r?\n/)
      .filter(line => line && !line.includes('Using a password on the command line interface can be insecure.'))
      .join('\n')
      .trim();
    if (filtered) {
      log(`stderr from ${path.basename(file)}: ${filtered}`);
    }
  }
  return stdout;
}

async function mysqlRaw(query) {
  return execFileText(CONFIG.mysqlPath, [
    `-u${CONFIG.mysqlUser}`,
    `-p${CONFIG.mysqlPassword}`,
    '-D',
    CONFIG.mysqlDatabase,
    '-N',
    '-B',
    '-e',
    query
  ]);
}

async function mysqlRows(query) {
  const raw = await mysqlRaw(query);
  return raw
    .trim()
    .split(/\r?\n/)
    .filter(Boolean)
    .map(line => line.split('\t'));
}

async function mysqlValue(query) {
  const rows = await mysqlRows(query);
  if (!rows.length || !rows[0].length) {
    return '';
  }
  return rows[0][0];
}

async function redisRaw(args) {
  return execFileText(CONFIG.redisCliPath, args);
}

async function redisKeys(pattern) {
  const raw = await redisRaw(['--raw', '--scan', '--pattern', pattern]);
  return raw.trim().split(/\r?\n/).filter(Boolean);
}

async function clearRiskState() {
  const patterns = ['risk:*', 'rate:uploads:*'];
  let deleted = 0;
  for (const pattern of patterns) {
    const keys = await redisKeys(pattern);
    if (!keys.length) {
      continue;
    }
    for (const key of keys) {
      await redisRaw(['DEL', key]);
      deleted += 1;
    }
  }
  return { patterns, deleted };
}

async function getRedisJson(key) {
  const raw = await redisRaw(['--raw', 'GET', key]);
  const text = raw.trim();
  return text ? JSON.parse(text) : null;
}

async function requestJson(method, targetPath, options = {}) {
  const base = new URL(CONFIG.baseUrl);
  const url = targetPath.startsWith('http://') || targetPath.startsWith('https://')
    ? targetPath
    : new URL(targetPath, `${base.origin}/`).toString();
  const headers = Object.assign({}, options.headers || {});
  let body;
  if (options.body !== undefined && options.body !== null) {
    if (typeof options.body === 'string') {
      body = options.body;
    } else {
      body = stableStringify(options.body);
    }
    if (!headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }
  }
  const response = await fetch(url, {
    method,
    headers,
    body,
    redirect: 'manual'
  });
  const text = await response.text();
  let json = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch (error) {
      json = null;
    }
  }
  return {
    status: response.status,
    ok: response.ok,
    text,
    json
  };
}

async function getSignHeaders(method, targetPath, bodyString, headers) {
  const challengeResp = await requestJson('GET', '/api/auth/sign/challenge', { headers });
  if (challengeResp.status !== 200 || !challengeResp.json || challengeResp.json.code !== 200) {
    throw new Error(`sign challenge failed: ${challengeResp.text}`);
  }
  const challenge = challengeResp.json.data;
  const timestamp = Date.now().toString();
  const nonce = crypto.randomUUID().replace(/-/g, '');
  const url = new URL(targetPath, CONFIG.baseUrl);
  const pathWithQuery = `${url.pathname}${url.search}`;
  const payload = [
    method.toUpperCase(),
    pathWithQuery,
    sha256Hex(bodyString || ''),
    timestamp,
    nonce,
    challenge.challengeId
  ].join('\n');
  return {
    ...headers,
    'X-Timestamp': timestamp,
    'X-Nonce': nonce,
    'X-Challenge-Id': challenge.challengeId,
    'X-Sign': hmacSha256Base64(payload, challenge.challengeSecret)
  };
}

async function signedJson(method, targetPath, body, headers) {
  const bodyString = body === undefined || body === null
    ? ''
    : typeof body === 'string'
      ? body
      : stableStringify(body);
  const signedHeaders = await getSignHeaders(method, targetPath, bodyString, headers || {});
  return requestJson(method, targetPath, {
    headers: signedHeaders,
    body: bodyString || undefined
  });
}

function buildCaptchaTrail(type, answer, captcha) {
  if (type === 'rotate') {
    return [[160, 90, 420]];
  }
  if (type === 'sequence') {
    let time = 360;
    return (answer.targets || []).map(target => {
      const point = [Number(target[0]), Number(target[1]), time];
      time += 170;
      return point;
    });
  }
  if (type === 'track') {
    const trackY = captcha?.data?.trackY != null ? Number(captcha.data.trackY) : 90;
    const endX = Number(answer.endX);
    return [
      [26, trackY, 180],
      [58, trackY + 1, 340],
      [112, trackY - 1, 520],
      [174, trackY + 2, 730],
      [endX, trackY, 980]
    ];
  }
  throw new Error(`unsupported captcha type: ${type}`);
}

async function getCaptchaTicket(headers) {
  const generate = await requestJson('GET', '/api/auth/captcha/generate', { headers });
  if (generate.status !== 200 || !generate.json || generate.json.code !== 200) {
    throw new Error(`captcha generate failed: ${generate.text}`);
  }
  const captcha = generate.json;
  const captchaId = captcha.data.captchaId;
  const answer = await getRedisJson(`captcha:answer:${captchaId}`);
  if (!answer) {
    throw new Error(`captcha answer missing: ${captchaId}`);
  }
  const verifyBody = {
    captchaId,
    trail: buildCaptchaTrail(captcha.data.type, answer, captcha)
  };
  if (captcha.data.type === 'rotate') {
    verifyBody.angle = Number(answer.angle);
  } else if (captcha.data.type === 'sequence') {
    verifyBody.points = answer.targets;
  } else if (captcha.data.type === 'track') {
    verifyBody.x = Number(answer.endX);
  }
  const verify = await signedJson('POST', '/api/auth/captcha/verify', verifyBody, headers);
  if (verify.status !== 200 || !verify.json || verify.json.code !== 200) {
    throw new Error(`captcha verify failed: ${verify.text}`);
  }
  return verify.json.data.ticket;
}

async function encryptPassword(plainPassword, headers) {
  const publicKeyResp = await requestJson('GET', '/api/auth/public-key', { headers });
  if (publicKeyResp.status !== 200 || !publicKeyResp.json || publicKeyResp.json.code !== 200) {
    throw new Error(`public key fetch failed: ${publicKeyResp.text}`);
  }
  const publicKey = publicKeyResp.json.data && publicKeyResp.json.data.publicKey;
  if (!publicKey) {
    throw new Error('public key payload missing');
  }
  return crypto.publicEncrypt(
    {
      key: publicKey,
      padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
      oaepHash: 'sha256'
    },
    Buffer.from(plainPassword, 'utf8')
  ).toString('base64');
}

async function login(username, password, userAgent, ip) {
  const headers = {
    'User-Agent': userAgent,
    'X-Forwarded-For': ip
  };
  const captchaTicket = await getCaptchaTicket(headers);
  const encryptedPassword = await encryptPassword(password, headers);
  const loginResp = await signedJson('POST', '/api/auth/login', {
    username,
    encryptedPassword,
    captchaTicket
  }, headers);
  if (loginResp.status !== 200 || !loginResp.json || loginResp.json.code !== 200) {
    throw new Error(`login failed for ${username}: ${loginResp.text}`);
  }
  return loginResp.json.data.token;
}

async function listAssets(token, projectId, userAgent, ip) {
  const response = await requestJson('GET', `/api/assets?projectId=${projectId}&limit=100`, {
    headers: {
      'User-Agent': userAgent,
      'X-Forwarded-For': ip,
      satoken: token
    }
  });
  if (response.status !== 200 || !response.json || response.json.code !== 200) {
    throw new Error(`asset list failed for project ${projectId}: ${response.text}`);
  }
  return response.json.data || [];
}

async function downloadSignedUrl(url, { token, userAgent, ip }) {
  const headers = {
    'User-Agent': userAgent,
    'X-Forwarded-For': ip
  };
  if (token) {
    headers.satoken = token;
  }
  return requestJson('GET', url, {
    headers
  });
}

async function maxAccessLogId() {
  const value = await mysqlValue('select ifnull(max(id), 0) from access_log');
  return Number(value || 0);
}

async function latestRiskEvidence(afterId, clientIp, reasonLike) {
  const rows = await mysqlRows(
    `select id, ifnull(user_id,''), path, status_code, ifnull(risk_reason,''), ifnull(query_string,''), created_at ` +
    `from access_log where id > ${Number(afterId)} and client_ip = ${sqlEscape(clientIp)} ` +
    `and status_code = 429 and risk_reason like ${sqlEscape(`%${reasonLike}%`)} ` +
    `order by id desc limit 1`
  );
  if (!rows.length) {
    return null;
  }
  const [id, userId, pathValue, statusCode, riskReason, queryString, createdAt] = rows[0];
  return {
    id: Number(id),
    userId: userId ? Number(userId) : null,
    path: pathValue,
    statusCode: Number(statusCode),
    riskReason,
    queryString,
    createdAt
  };
}

function currentSlice(minutes = CONFIG.longSliceMinutes) {
  return Math.floor(Date.now() / (minutes * 60_000));
}

async function waitForNextSlice(minutes = CONFIG.longSliceMinutes, offsetMs = 1_500) {
  const current = currentSlice(minutes);
  const nextStart = (current + 1) * minutes * 60_000;
  const waitMs = Math.max(nextStart - Date.now() + offsetMs, 0);
  log(`waiting ${(waitMs / 1000).toFixed(1)}s for next ${minutes}m slice`);
  await sleep(waitMs);
  return currentSlice(minutes);
}

async function requestMany(items, options) {
  const results = [];
  for (let index = 0; index < items.length; index += 1) {
    const item = items[index];
    const response = await options.run(item, index);
    results.push({
      index,
      status: response.status,
      text: response.text,
      json: response.json,
      item
    });
    if (options.onEach) {
      await options.onEach(results[results.length - 1]);
    }
    if (index < items.length - 1) {
      await sleep(options.delayMs || 0);
    }
  }
  return results;
}

async function requestGrouped(groups, options) {
  const results = [];
  for (let groupIndex = 0; groupIndex < groups.length; groupIndex += 1) {
    const group = groups[groupIndex];
    log(`${options.name}: group ${groupIndex + 1}/${groups.length}, ${group.length} requests`);
    const groupResults = await requestMany(group, {
      delayMs: options.intraGroupMs,
      run: options.run
    });
    results.push(...groupResults);
    if (groupIndex < groups.length - 1) {
      await sleep(options.groupGapMs);
    }
  }
  return results;
}

function chunk(items, size) {
  const groups = [];
  for (let index = 0; index < items.length; index += size) {
    groups.push(items.slice(index, index + size));
  }
  return groups;
}

function signUploadUrl(fileUrl, projectId, userId) {
  const normalizedPath = new URL(fileUrl, CONFIG.baseUrl).pathname;
  const expiresAt = Math.floor(Date.now() / 1000) + Math.max(CONFIG.uploadTtlSeconds, 60);
  const mode = 'project';
  const payload = [
    normalizedPath,
    expiresAt,
    mode,
    projectId,
    userId
  ].join('\n');
  const signature = crypto
    .createHmac('sha256', CONFIG.uploadAccessSecret)
    .update(payload, 'utf8')
    .digest('base64url');
  return `${CONFIG.baseUrl}${normalizedPath}?exp=${expiresAt}&mode=${mode}&pid=${projectId}&uid=${userId}&sig=${signature}`;
}

async function prepareTempData() {
  log(`preparing temp data with prefix ${PREFIX}`);
  const adminHash = await mysqlValue(`select password from user where username = ${sqlEscape(CONFIG.adminUsername)} limit 1`);
  if (!adminHash) {
    throw new Error('admin password hash not found');
  }

  const postValues = [];
  for (let i = 1; i <= 30; i += 1) {
    postValues.push(
      `(${[
        7,
        sqlEscape('admin'),
        sqlEscape('admin'),
        sqlEscape(''),
        sqlEscape(`${PREFIX}_post_${String(i).padStart(2, '0')}`),
        sqlEscape(`${PREFIX} prompt ${i}`),
        sqlEscape(''),
        sqlEscape('runtime-verify'),
        sqlEscape(''),
        sqlEscape('runtime'),
        sqlEscape('verify'),
        0,
        0,
        1
      ].join(',')})`
    );
  }
  await mysqlRaw(
    'insert into community_post (user_id, username, nickname, avatar, title, prompt, negative_prompt, model_name, image_url, category, tags, likes_count, comments_count, status) values ' +
    postValues.join(',')
  );

  const userNames = [1, 2, 3, 4, 5, 6, 7].map(index => `${PREFIX}_user_${index}`);
  await mysqlRaw(
    'insert into user (username, password, nickname, role, status) values ' +
    userNames.map((username, index) =>
      `(${sqlEscape(username)}, ${sqlEscape(adminHash)}, ${sqlEscape(`${PREFIX}_nick_${index + 1}`)}, 'user', 1)`
    ).join(',')
  );
  const userRows = await mysqlRows(
    `select id, username from user where username in (${userNames.map(sqlEscape).join(',')}) order by id asc`
  );
  const users = userRows.map(([id, username]) => ({ id: Number(id), username }));

  const projectSpecs = [
    { key: 'single_a', userId: users[0].id },
    { key: 'single_b', userId: users[0].id },
    { key: 'single_c', userId: users[0].id },
    { key: 'sameip_a', userId: users[1].id },
    { key: 'sameip_b', userId: users[2].id },
    { key: 'sameip_c', userId: users[3].id },
    { key: 'uadist_a', userId: users[4].id },
    { key: 'uadist_b', userId: users[5].id },
    { key: 'uadist_c', userId: users[6].id }
  ];
  await mysqlRaw(
    'insert into project (user_id, name, description, status) values ' +
    projectSpecs.map(spec =>
      `(${spec.userId}, ${sqlEscape(`${PREFIX}_project_${spec.key}`)}, ${sqlEscape(`${PREFIX} project ${spec.key}`)}, 1)`
    ).join(',')
  );
  const projectRows = await mysqlRows(
    `select id, user_id, name from project where name like ${sqlEscape(`${PREFIX}_project_%`)} order by id asc`
  );
  const projects = projectRows.map(([id, userId, name]) => {
    const key = name.replace(`${PREFIX}_project_`, '');
    return {
      id: Number(id),
      userId: Number(userId),
      name,
      key
    };
  });
  const projectMap = Object.fromEntries(projects.map(project => [project.key, project]));

  const sourceRows = await mysqlRows(
    "select file_name, file_url, ifnull(mime_type,''), ifnull(file_size,0) from asset where project_id in (46,47,48) and file_url like '/uploads/%' and file_url not like '/uploads/\\\\_\\\\_%' escape '\\\\' order by project_id asc, id asc limit 500"
  );
  const sourceAssets = [];
  const seenUrls = new Set();
  for (const [fileName, fileUrl, mimeType, fileSize] of sourceRows) {
    if (seenUrls.has(fileUrl)) {
      continue;
    }
    seenUrls.add(fileUrl);
    sourceAssets.push({
      fileName,
      fileUrl,
      mimeType: mimeType || 'image/jpeg',
      fileSize: Number(fileSize || 0)
    });
    if (sourceAssets.length >= 24) {
      break;
    }
  }
  if (sourceAssets.length < 24) {
    throw new Error(`not enough source assets, found ${sourceAssets.length}`);
  }

  const assetPlans = [
    { projectKey: 'single_a', slice: sourceAssets.slice(0, 8) },
    { projectKey: 'single_b', slice: sourceAssets.slice(8, 16) },
    { projectKey: 'single_c', slice: sourceAssets.slice(16, 24) },
    { projectKey: 'sameip_a', slice: sourceAssets.slice(0, 8) },
    { projectKey: 'sameip_b', slice: sourceAssets.slice(8, 16) },
    { projectKey: 'sameip_c', slice: sourceAssets.slice(16, 24) },
    { projectKey: 'uadist_a', slice: sourceAssets.slice(0, 8) },
    { projectKey: 'uadist_b', slice: sourceAssets.slice(8, 16) },
    { projectKey: 'uadist_c', slice: sourceAssets.slice(16, 24) }
  ];
  const assetValues = [];
  const assetSeeds = [];
  assetPlans.forEach((plan) => {
    const project = projectMap[plan.projectKey];
    plan.slice.forEach((source, assetIndex) => {
      const ext = path.extname(source.fileName || '') || '.jpg';
      const fileName = `${PREFIX}_asset_${plan.projectKey}_${assetIndex + 1}${ext}`;
      assetValues.push(
        `(${[
          project.id,
          'NULL',
          sqlEscape('image'),
          sqlEscape(fileName),
          sqlEscape(source.fileUrl),
          sqlEscape(source.fileUrl),
          sqlEscape(source.mimeType || 'image/jpeg'),
          Number(source.fileSize || 0),
          sqlEscape(`${PREFIX} asset seed ${plan.projectKey}`),
          sqlEscape('runtime-verify'),
          0
        ].join(',')})`
      );
      assetSeeds.push({
        projectKey: plan.projectKey,
        projectId: project.id,
        userId: project.userId,
        fileUrl: source.fileUrl,
        fileName
      });
    });
  });
  await mysqlRaw(
    'insert into asset (project_id, task_id, asset_type, file_name, file_url, thumb_url, mime_type, file_size, prompt, model_name, favorite) values ' +
    assetValues.join(',')
  );

  const postIdRows = await mysqlRows(
    `select id from community_post where title like ${sqlEscape(`${PREFIX}_post_%`)} order by id asc`
  );
  report.temp = {
    users,
    projects,
    communityPostIds: postIdRows.map(([id]) => Number(id)),
    assetSeeds
  };
}

async function cleanupTempData() {
  const stats = {};
  stats.assets = Number(await mysqlValue(`select count(*) from asset where file_name like ${sqlEscape(`${PREFIX}_asset_%`)}`));
  stats.projects = Number(await mysqlValue(`select count(*) from project where name like ${sqlEscape(`${PREFIX}_project_%`)}`));
  stats.users = Number(await mysqlValue(`select count(*) from user where username like ${sqlEscape(`${PREFIX}_user_%`)}`));
  stats.posts = Number(await mysqlValue(`select count(*) from community_post where title like ${sqlEscape(`${PREFIX}_post_%`)}`));

  await mysqlRaw(`delete from asset where file_name like ${sqlEscape(`${PREFIX}_asset_%`)}`);
  await mysqlRaw(`delete from project where name like ${sqlEscape(`${PREFIX}_project_%`)}`);
  await mysqlRaw(`delete from user where username like ${sqlEscape(`${PREFIX}_user_%`)}`);
  await mysqlRaw(`delete from community_post where title like ${sqlEscape(`${PREFIX}_post_%`)}`);
  report.cleanup = stats;
}

async function verify18086(postIds) {
  log('verifying 18086 on runtime 8080');
  const clear = await clearRiskState();
  const ip = TEST_IPS.public86;
  const userAgent = TEST_UA.public86;
  const beforeId = await maxAccessLogId();

  const detailResults = await requestMany(postIds.slice(0, 12), {
    delayMs: CONFIG.publicDelayMs,
    run: (postId) => requestJson('GET', `/api/community/posts/${postId}`, {
      headers: {
        'User-Agent': userAgent,
        'X-Forwarded-For': ip,
        'Cache-Control': 'no-store'
      }
    })
  });
  const pageResults = [];
  let hit = null;
  for (let page = 1; page <= 19; page += 1) {
    const response = await requestJson('GET', `/api/community/posts?page=${page}&pageSize=1&sort=newest`, {
      headers: {
        'User-Agent': userAgent,
        'X-Forwarded-For': ip,
        'Cache-Control': 'no-store'
      }
    });
    pageResults.push({ page, status: response.status, text: response.text });
    if (response.status === 429) {
      hit = { page, response };
      break;
    }
    await sleep(CONFIG.publicDelayMs);
  }
  const evidence = await latestRiskEvidence(beforeId, ip, '公共内容分页扫库并伴随详情/评论遍历');
  if (!hit || !evidence) {
    throw new Error('18086 runtime verification did not hit expected 429');
  }
  report.scenarios['18086'] = {
    clear,
    detailStatuses: detailResults.map(item => item.status),
    pageStatuses: pageResults,
    hitPage: hit.page,
    hitMessage: hit.response.json?.message || hit.response.text,
    evidence
  };
}

async function verifyDistributedScenarios(context) {
  log('verifying distributed runtime scenarios 18090/18088/18089/18091');
  const clear = await clearRiskState();
  const beforeIds = {
    '18090': await maxAccessLogId(),
    '18088': await maxAccessLogId(),
    '18089': await maxAccessLogId(),
    '18091': await maxAccessLogId()
  };

  await waitForNextSlice(CONFIG.longSliceMinutes);
  log(`distributed group started in slice ${currentSlice(CONFIG.longSliceMinutes)}`);

  const publicIp = TEST_IPS.public90;
  const publicUa = TEST_UA.public90;
  const publicInitialDetails = context.publicPostIds.slice(0, 24);
  await requestMany(publicInitialDetails, {
    delayMs: CONFIG.publicDelayMs,
    run: (postId) => requestJson('GET', `/api/community/posts/${postId}`, {
      headers: {
        'User-Agent': publicUa,
        'X-Forwarded-For': publicIp,
        'Cache-Control': 'no-store'
      }
    })
  });
  const publicPages = [];
  for (let page = 1; page <= 8; page += 1) {
    const response = await requestJson('GET', `/api/community/posts?page=${page}&pageSize=1&sort=newest`, {
      headers: {
        'User-Agent': publicUa,
        'X-Forwarded-For': publicIp,
        'Cache-Control': 'no-store'
      }
    });
    publicPages.push({ page, status: response.status });
    await sleep(CONFIG.publicDelayMs);
  }

  const singleUserGroups = chunk(context.singleUserUrls.slice(0, 24), 5);
  const singleUserInitial = await requestGrouped(singleUserGroups, {
    name: '18088-initial',
    intraGroupMs: CONFIG.intraGroupMs,
    groupGapMs: CONFIG.groupGapMs,
    run: (item) => downloadSignedUrl(item.url, {
      userAgent: TEST_UA.singleUser,
      ip: TEST_IPS.singleUser
    })
  });

  const sameIpUaGroups = chunk(context.multiUserUrls.slice(0, 24), 4);
  const sameIpUaInitial = await requestGrouped(sameIpUaGroups, {
    name: '18089-initial',
    intraGroupMs: CONFIG.intraGroupMs,
    groupGapMs: CONFIG.groupGapMs,
    run: (item) => downloadSignedUrl(item.url, {
      userAgent: TEST_UA.sameIpUa,
      ip: TEST_IPS.sameIpUa
    })
  });

  const uaDistributedGroups = chunk(context.uaDistributedUrls.slice(0, 24), 4);
  const uaDistributedInitial = await requestGrouped(uaDistributedGroups, {
    name: '18091-initial',
    intraGroupMs: CONFIG.intraGroupMs,
    groupGapMs: CONFIG.groupGapMs,
    run: (item) => downloadSignedUrl(item.url, {
      userAgent: TEST_UA.uaDistributed,
      ip: item.ip
    })
  });

  const followups = {
    '18090': [],
    '18088': [],
    '18089': [],
    '18091': []
  };
  let hit18090 = null;
  let hit18088 = null;
  let hit18089 = null;
  let hit18091 = null;

  for (let hop = 1; hop <= 3; hop += 1) {
    await waitForNextSlice(CONFIG.longSliceMinutes);

    const publicResponse = await requestJson('GET', `/api/community/posts?page=${8 + hop}&pageSize=1&sort=newest`, {
      headers: {
        'User-Agent': publicUa,
        'X-Forwarded-For': publicIp,
        'Cache-Control': 'no-store'
      }
    });
    followups['18090'].push({ hop, status: publicResponse.status, text: publicResponse.text });
    if (publicResponse.status === 429 && !hit18090) {
      hit18090 = { hop, response: publicResponse };
    }

    const singleUserResponse = await downloadSignedUrl(context.singleUserUrls[0].url, {
      userAgent: TEST_UA.singleUser,
      ip: TEST_IPS.singleUser
    });
    followups['18088'].push({ hop, status: singleUserResponse.status, text: singleUserResponse.text });
    if (singleUserResponse.status === 429 && !hit18088) {
      hit18088 = { hop, response: singleUserResponse };
    }

    const sameIpUaResponse = await downloadSignedUrl(context.multiUserUrls[0].url, {
      userAgent: TEST_UA.sameIpUa,
      ip: TEST_IPS.sameIpUa
    });
    followups['18089'].push({ hop, status: sameIpUaResponse.status, text: sameIpUaResponse.text });
    if (sameIpUaResponse.status === 429 && !hit18089) {
      hit18089 = { hop, response: sameIpUaResponse };
    }

    const uaDistributedResponse = await downloadSignedUrl(context.uaDistributedUrls[0].url, {
      userAgent: TEST_UA.uaDistributed,
      ip: context.uaDistributedUrls[0].ip
    });
    followups['18091'].push({ hop, status: uaDistributedResponse.status, text: uaDistributedResponse.text });
    if (uaDistributedResponse.status === 429 && !hit18091) {
      hit18091 = { hop, response: uaDistributedResponse };
    }
  }

  const evidence18090 = await latestRiskEvidence(beforeIds['18090'], publicIp, '跨时段慢速公共内容采集');
  const evidence18088 = await latestRiskEvidence(beforeIds['18088'], TEST_IPS.singleUser, '跨时段、跨项目的低速分布式资源搬运');
  const evidence18089 = await latestRiskEvidence(beforeIds['18089'], TEST_IPS.sameIpUa, '同一 IP/UA 下多账号跨时段慢速协同搬运');
  const evidence18091 = await latestRiskEvidence(beforeIds['18091'], TEST_IPS.uaA, '跨 IP 池分散协同搬运')
    || await latestRiskEvidence(beforeIds['18091'], TEST_IPS.uaB, '跨 IP 池分散协同搬运')
    || await latestRiskEvidence(beforeIds['18091'], TEST_IPS.uaC, '跨 IP 池分散协同搬运');

  if (!hit18090 || !evidence18090) {
    throw new Error('18090 runtime verification did not hit expected 429');
  }
  if (!hit18088 || !evidence18088) {
    throw new Error('18088 runtime verification did not hit expected 429');
  }
  if (!hit18089 || !evidence18089) {
    throw new Error('18089 runtime verification did not hit expected 429');
  }
  if (!hit18091 || !evidence18091) {
    throw new Error('18091 runtime verification did not hit expected 429');
  }

  report.scenarios['18090'] = {
    clear,
    initialPageStatuses: publicPages,
    followups: followups['18090'],
    hitSliceHop: hit18090.hop,
    hitMessage: hit18090.response.json?.message || hit18090.response.text,
    evidence: evidence18090
  };
  report.scenarios['18088'] = {
    initialStatuses: singleUserInitial.map(item => item.status),
    followups: followups['18088'],
    hitSliceHop: hit18088.hop,
    hitMessage: hit18088.response.json?.message || hit18088.response.text,
    evidence: evidence18088
  };
  report.scenarios['18089'] = {
    initialStatuses: sameIpUaInitial.map(item => item.status),
    followups: followups['18089'],
    hitSliceHop: hit18089.hop,
    hitMessage: hit18089.response.json?.message || hit18089.response.text,
    evidence: evidence18089
  };
  report.scenarios['18091'] = {
    initialStatuses: uaDistributedInitial.map(item => item.status),
    followups: followups['18091'],
    hitSliceHop: hit18091.hop,
    hitMessage: hit18091.response.json?.message || hit18091.response.text,
    evidence: evidence18091
  };
}

async function prepareRuntimeContext() {
  const seeds = report.temp.assetSeeds || [];
  const singleUserUrls = seeds
    .filter(item => ['single_a', 'single_b', 'single_c'].includes(item.projectKey))
    .map(item => ({
      projectId: item.projectId,
      userId: item.userId,
      url: signUploadUrl(item.fileUrl, item.projectId, item.userId)
    }));
  const multiUserUrls = seeds
    .filter(item => ['sameip_a', 'sameip_b', 'sameip_c'].includes(item.projectKey))
    .map(item => ({
      projectId: item.projectId,
      userId: item.userId,
      url: signUploadUrl(item.fileUrl, item.projectId, item.userId)
    }));
  const projectToIp = {
    uadist_a: TEST_IPS.uaA,
    uadist_b: TEST_IPS.uaB,
    uadist_c: TEST_IPS.uaC
  };
  const uaDistributedUrls = seeds
    .filter(item => ['uadist_a', 'uadist_b', 'uadist_c'].includes(item.projectKey))
    .map(item => ({
      projectId: item.projectId,
      userId: item.userId,
      url: signUploadUrl(item.fileUrl, item.projectId, item.userId),
      ip: projectToIp[item.projectKey]
    }));

  return {
    singleUserUrls,
    multiUserUrls,
    uaDistributedUrls,
    publicPostIds: report.temp.communityPostIds
  };
}

async function writeReport() {
  fs.mkdirSync(CONFIG.reportDir, { recursive: true });
  report.finishedAt = new Date().toISOString();
  const reportPath = path.join(CONFIG.reportDir, `${PREFIX}.runtime-risk-report.json`);
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), 'utf8');
  log(`report written to ${reportPath}`);
  return reportPath;
}

async function main() {
  let reportPath = null;
  try {
    await prepareTempData();
    const runtimeContext = await prepareRuntimeContext();
    await verify18086(report.temp.communityPostIds);
    await verifyDistributedScenarios(runtimeContext);
    report.summary = {
      success: true,
      verified: Object.keys(report.scenarios)
    };
  } catch (error) {
    report.summary = {
      success: false,
      error: error && error.message ? error.message : String(error)
    };
    throw error;
  } finally {
    try {
      await cleanupTempData();
    } catch (cleanupError) {
      report.cleanupError = cleanupError && cleanupError.message ? cleanupError.message : String(cleanupError);
      log(`cleanup failed: ${report.cleanupError}`);
    }
    reportPath = await writeReport();
  }
  log(`runtime risk verification completed successfully: ${reportPath}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
