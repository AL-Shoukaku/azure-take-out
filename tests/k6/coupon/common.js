import http from 'k6/http';
import execution from 'k6/execution';
import { SharedArray } from 'k6/data';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 登录和发布在 prepare.py 中完成，不占用压测请求与计时。
let fixture;
try {
  const file = __ENV.DATA_FILE || './data/local.json';
  // 大规模测试共享用户Token数据，避免每个VU复制整批用户占用大量内存。
  const users = new SharedArray('coupon_users', () => {
    const rows = JSON.parse(open(file)).users;
    if (!Array.isArray(rows) || rows.length === 0
        || new Set(rows.map((user) => String(user.id))).size !== rows.length) {
      throw new Error('测试用户不能为空，且必须是不同的真实用户');
    }
    return rows;
  });
  const metadata = new SharedArray('coupon_metadata', () => {
    const { users: ignoredUsers, ...rest } = JSON.parse(open(file));
    return [rest];
  });
  fixture = { ...metadata[0], users };
} catch (_) {
  throw new Error('请先运行 python3 tests/k6/coupon/prepare.py 准备测试数据。');
}
const baseUrl = (__ENV.BASE_URL || fixture.baseUrl).replace(/\/$/, '');
const attempts = new Counter('coupon_attempts');
const successes = new Counter('coupon_successes');
const rejections = new Counter('coupon_rejections');
const errors = new Counter('coupon_errors');
const correct = new Rate('coupon_expected_response');
const latency = new Trend('coupon_duration', true);
const successLatency = new Trend('coupon_success_duration', true);
const endpointLatency = {
  list: new Trend('coupon_list_duration', true),
  claim: new Trend('coupon_claim_duration', true),
  buy: new Trend('coupon_buy_duration', true),
};
const flowAttempts = new Counter('coupon_flow_attempts');
const flowSuccesses = new Counter('coupon_flow_successes');
const flowLatency = new Trend('coupon_flow_duration', true);
// 用首轮开始到末轮结束计算批次耗时，排除setup预检查和k6启动时间。
const startedAt = new Trend('coupon_started_at');
const finishedAt = new Trend('coupon_finished_at');
let loggedError = false;

function positiveInteger(name, fallback) {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isSafeInteger(value) || value < 1) throw new Error(`${name} 必须是正整数`);
  return value;
}

export function configure(test) {
  const action = __ENV.ACTION || (test === 'performance' ? 'flow' : 'buy');
  if (!['flow', 'list', 'claim', 'buy'].includes(action) || (test !== 'performance' && ['flow', 'list'].includes(action))) {
    throw new Error('ACTION 可选 flow、list、claim、buy；正确性测试只能选择 claim 或 buy');
  }
  const iterations = positiveInteger('REQUESTS', test === 'single-user' ? 100 : fixture.users.length);
  const vus = positiveInteger('VUS', Math.min(100, iterations));
  if (vus > iterations) throw new Error('VUS 不能大于 REQUESTS');
  if (test !== 'performance' && vus < 2) throw new Error('正确性测试至少需要 VUS=2，以并发请求');
  if (action !== 'list' && test !== 'single-user' && iterations > fixture.users.length) {
    throw new Error('REQUESTS 超过独立用户数，请用 prepare.py --users 更大的数量重新准备数据');
  }
  const userIndex = Number(__ENV.USER_INDEX || 0);
  if (!Number.isSafeInteger(userIndex) || userIndex < 0 || userIndex >= fixture.users.length) {
    throw new Error('USER_INDEX 超出测试用户范围');
  }
  const coupons = (action === 'flow' ? ['claim', 'buy'] : action === 'list' ? [] : [action])
    .map((targetAction) => ({ ...fixture.coupons[test][targetAction], action: targetAction }));
  const coupon = coupons.length === 1 ? coupons[0] : null;
  if (test === 'oversell' && coupon.stock >= iterations) throw new Error('超卖测试的用户数必须大于库存');
  if ((test === 'performance' || test === 'single-user') && coupons.some((target) => target.stock < iterations)) {
    throw new Error('本组测试要求库存充足，请重新准备更多库存的专用券');
  }
  const expectedSuccesses = test === 'oversell' ? coupon.stock : test === 'single-user' ? 1 : iterations;
  return { test, action, iterations, vus, userIndex, coupon, coupons, expectedSuccesses };
}

export function optionsFor(config) {
  return {
    scenarios: {
      coupon: {
        executor: 'shared-iterations',
        vus: config.vus,
        iterations: config.iterations,
        maxDuration: __ENV.MAX_DURATION || '5m',
      },
    },
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
    thresholds: {
      coupon_attempts: [`count==${config.iterations * (config.action === 'flow' ? 3 : 1)}`],
      coupon_successes: [`count==${config.expectedSuccesses * (config.action === 'flow' ? 3 : 1)}`],
      coupon_errors: ['count==0'],
      coupon_expected_response: ['rate==1'],
      ...(config.action === 'flow' ? {
        coupon_flow_attempts: [`count==${config.iterations}`],
        coupon_flow_successes: [`count==${config.iterations}`],
      } : {}),
    },
  };
}

function bodyOf(response) {
  try { return response.json(); } catch (_) { return null; }
}

export function preflight(config) {
  const response = http.get(`${baseUrl}/user/coupon/available`, {
    headers: { authentication: fixture.users[config.userIndex].token },
    tags: { name: 'preflight_user' }, timeout: '10s',
  });
  const body = bodyOf(response);
  if (response.status !== 200 || !body || body.code !== 1 || !Array.isArray(body.data)) {
    throw new Error('用户鉴权或服务检查失败，请确认后端已启动、BASE_URL 正确、Token 未过期；可重新运行 prepare.py');
  }
  for (const coupon of config.coupons) {
    const detail = http.get(`${baseUrl}/admin/coupon/${coupon.id}`, {
      headers: { token: fixture.adminToken }, tags: { name: 'preflight_coupon' }, timeout: '10s',
    });
    const info = bodyOf(detail);
    if (detail.status !== 200 || !info || info.code !== 1 || !info.data) {
      throw new Error('无法检查专用券，请确认管理员 Token 未过期，或重新运行 prepare.py');
    }
    const row = info.data;
    const now = Date.now();
    const start = Date.parse(String(row.startTime).replace(' ', 'T') + '+08:00');
    const end = Date.parse(String(row.endTime).replace(' ', 'T') + '+08:00');
    if (row.stock !== coupon.stock || row.status !== 1 || row.type !== (coupon.action === 'buy' ? 1 : 2)
        || !Number.isFinite(start) || !Number.isFinite(end) || now < start || now >= end) {
      throw new Error('专用券已被使用或状态、时间、类型不匹配。每次写入测试都应重新 prepare，不要清理真实业务数据');
    }
  }
  console.log(`开始 ${config.test}/${config.action}：${config.vus} 个并发用户，${config.iterations} 个用户流程/轮次，`
    + `${config.iterations * (config.action === 'flow' ? 3 : 1)} 次目标 HTTP 请求`
    + config.coupons.map((coupon) => `；${coupon.action} 券 ID=${coupon.id}，初始库存=${coupon.stock}`).join(''));
}

export function runRequest(config) {
  startedAt.add(Date.now());
  try {
    executeIteration(config);
  } finally {
    finishedAt.add(Date.now());
  }
}

function executeIteration(config) {
  const index = execution.scenario.iterationInTest;
  const user = fixture.users[config.test === 'single-user' ? config.userIndex : index % fixture.users.length];
  if (config.action === 'flow') {
    const start = Date.now();
    // 同一用户依次查列表、领取满减券、创建代金券订单。前一步失败则结束本轮，不继续写入。
    const success = issueRequest(config, user, 'list', null)
      && issueRequest(config, user, 'claim', config.coupons[0])
      && issueRequest(config, user, 'buy', config.coupons[1]);
    flowAttempts.add(1);
    flowSuccesses.add(success ? 1 : 0);
    flowLatency.add(Date.now() - start);
    return;
  }
  issueRequest(config, user, config.action, config.coupon);
}

function issueRequest(config, user, action, coupon) {
  const params = {
    headers: { authentication: user.token },
    tags: { name: `coupon_${config.test}_${action}` },
    timeout: __ENV.REQUEST_TIMEOUT || '10s',
  };
  const response = action === 'list'
    ? http.get(`${baseUrl}/user/coupon/available`, params)
    : http.post(`${baseUrl}/user/coupon/${action === 'claim' ? 'get' : 'buy'}?couponId=${coupon.id}`, null, params);
  const body = bodyOf(response);
  const success = response.status === 200 && body && body.code === 1
    && (action === 'list' ? Array.isArray(body.data)
      && (config.action !== 'flow' || config.coupons.every((target) => body.data.some((row) => String(row.id) === String(target.id))))
      : body.data && typeof body.data.number === 'string' && body.data.number.length > 0);
  const message = body && typeof body.msg === 'string' ? body.msg : '';
  // 只接受本场景预期的业务拒绝。鉴权失败、500、错券、重复键异常都算异常。
  const rejection = response.status === 200 && body && body.code === 0
    && (config.test === 'oversell' ? message === '优惠券库存不足'
      : config.test === 'single-user' && message === (action === 'claim' ? '用户已领取过该满减券' : '用户已购买过该代金券'));
  const expected = Boolean(success || rejection);
  attempts.add(1);
  successes.add(success ? 1 : 0);
  rejections.add(rejection ? 1 : 0);
  errors.add(expected ? 0 : 1);
  correct.add(expected);
  latency.add(response.timings.duration);
  endpointLatency[action].add(response.timings.duration);
  if (success) successLatency.add(response.timings.duration);
  check(response, { '符合本场景预期的业务结果': () => expected });
  if (!expected && !loggedError) {
    console.error(`异常样本 ${action}：HTTP ${response.status}，业务消息=${message || '响应数据不符合预期（完整流程要求列表包含两张专用券）'}`);
    loggedError = true;
  }
  return Boolean(success);
}

export function report(config, data) {
  const value = (metric, field = 'count') => data.metrics[metric] ? data.metrics[metric].values[field] : null;
  const failed = Object.entries(data.metrics).flatMap(([name, metric]) =>
    Object.entries(metric.thresholds || {}).filter(([, result]) => !result.ok).map(([rule]) => `${name}: ${rule}`));
  const duration = data.metrics.coupon_started_at && data.metrics.coupon_finished_at
    ? (value('coupon_finished_at', 'max') - value('coupon_started_at', 'min')) / 1000 : 0;
  const summary = {
    runLabel: __ENV.LABEL || fixture.label,
    test: config.test, action: config.action, baseUrl,
    couponId: config.coupon ? config.coupon.id : null,
    initialStock: config.coupon ? config.coupon.stock : null,
    coupons: config.coupons,
    userId: config.test === 'single-user' ? fixture.users[config.userIndex].id : null,
    configuredVus: config.vus, plannedIterations: config.iterations,
    plannedRequests: config.iterations * (config.action === 'flow' ? 3 : 1),
    attempts: value('coupon_attempts'), successes: value('coupon_successes'),
    expectedRejections: value('coupon_rejections'), errors: value('coupon_errors'),
    durationSeconds: duration,
    totalRunDurationSeconds: data.state.testRunDurationMs / 1000,
    successfulResponsesPerSecond: duration > 0 ? value('coupon_successes') / duration : null,
    durationMs: data.metrics.coupon_duration ? data.metrics.coupon_duration.values : {},
    successDurationMs: data.metrics.coupon_success_duration ? data.metrics.coupon_success_duration.values : {},
    endpointDurationMs: Object.fromEntries(['list', 'claim', 'buy'].map((action) =>
      [action, data.metrics[`coupon_${action}_duration`] ? data.metrics[`coupon_${action}_duration`].values : {}])),
    ...(config.action === 'flow' ? {
      completedFlows: value('coupon_flow_successes'),
      attemptedFlows: value('coupon_flow_attempts'),
      flowDurationMs: data.metrics.coupon_flow_duration ? data.metrics.coupon_flow_duration.values : {},
      successfulFlowsPerSecond: duration > 0 ? value('coupon_flow_successes') / duration : null,
    } : {}),
    failedThresholds: failed,
    note: '结果对应当前同步接口。数据库行数、重复订单及库存账实一致性还需执行 verify.sql 核对；未测量 SQL 次数。',
    k6: data,
  };
  const label = String(summary.runLabel || 'run').replace(/[^a-zA-Z0-9_-]/g, '_');
  const file = `tests/k6/coupon/results/${label}-${config.test}-${config.action}-${Date.now()}.json`;
  return {
    [file]: JSON.stringify(summary, null, 2),
    stdout: `\n${config.test}/${config.action} 测试结束\n`
      + `总运行时间 ${duration.toFixed(3)} 秒；目标 HTTP 请求 ${summary.plannedRequests}，实际 ${summary.attempts}；成功 ${summary.successes}；预期拒绝 ${summary.expectedRejections}；异常 ${summary.errors}\n`
      + (config.action === 'flow' ? `完整流程成功 ${summary.completedFlows}/${config.iterations}；单个流程耗时(ms)：平均=${value('coupon_flow_duration', 'avg')}，P95=${value('coupon_flow_duration', 'p(95)')}，P99=${value('coupon_flow_duration', 'p(99)')}\n` : '')
      + ['list', 'claim', 'buy'].filter((action) => data.metrics[`coupon_${action}_duration`]).map((action) =>
        `${action} 接口耗时(ms)：平均=${value(`coupon_${action}_duration`, 'avg')}，P95=${value(`coupon_${action}_duration`, 'p(95)')}，P99=${value(`coupon_${action}_duration`, 'p(99)')}\n`).join('')
      + `HTTP 断言：${failed.length ? '未通过：' + failed.join('; ') : '通过'}\n`
      + `报告：${file}\n`
      + config.coupons.map((coupon) => `请使用 ${coupon.action} 券 ID=${coupon.id}、初始库存=${coupon.stock} 执行 verify.sql 核对数据库。\n`).join(''),
  };
}
