import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const VUS = positiveIntegerEnv('VUS', 10);
const DURATION = __ENV.DURATION || '1m';
const RAMP_UP_DURATION = __ENV.RAMP_UP_DURATION || '';
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN_DURATION || '10s';
const PAGE_SIZE = positiveIntegerEnv('PAGE_SIZE', 20);
const MIN_SLEEP_SECONDS = positiveNumberEnv('MIN_SLEEP_SECONDS', 0.5);
const MAX_SLEEP_SECONDS = positiveNumberEnv('MAX_SLEEP_SECONDS', 1.5);
const SUMMARY_BASENAME = __ENV.SUMMARY_BASENAME || 'search-readonly-summary';
const SUMMARY_DIRECTORY = __ENV.SUMMARY_DIRECTORY || 'k6/results';

const KEYWORDS = [
  'iphone',
  'macbook',
  'chair',
  'desk',
  'bicycle',
  'camera',
  'bag',
  'keyboard',
  'monitor',
  'earphone',
];
const SORTS = ['latest', 'priceAsc', 'priceDesc'];

export const options = {
  scenarios: {
    readonly_search: scenarioOptions(),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:productList}': ['p(95)<1000', 'p(99)<2000'],
    'http_req_duration{endpoint:searchV2}': ['p(95)<1000', 'p(99)<2000'],
    'http_req_duration{endpoint:popularKeywords}': ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  group('public readonly catalog path', () => {
    requestProductList();
    requestSearchV2();
    requestPopularKeywords();
  });

  sleep(randomSleepSeconds());
}

export function handleSummary(data) {
  const markdown = createMarkdownSummary(data);

  return {
    stdout: createConsoleSummary(data),
    [`${SUMMARY_DIRECTORY}/${SUMMARY_BASENAME}.md`]: markdown,
    [`${SUMMARY_DIRECTORY}/${SUMMARY_BASENAME}.json`]: JSON.stringify(data, null, 2),
  };
}

function requestProductList() {
  const sort = randomItem(SORTS);
  const response = http.get(
      `${BASE_URL}/api/products?page=0&size=${PAGE_SIZE}&status=ON_SALE&sort=${sort}`,
      { tags: { endpoint: 'productList' } },
  );

  check(response, {
    'product list status is 200': (res) => res.status === 200,
    'product list response is successful': isSuccessfulApiResponse,
  });
}

function requestSearchV2() {
  const keyword = encodeURIComponent(randomItem(KEYWORDS));
  const sort = randomItem(SORTS);
  const response = http.get(
      `${BASE_URL}/api/search/v2/products?keyword=${keyword}&page=0&size=${PAGE_SIZE}&sort=${sort}`,
      { tags: { endpoint: 'searchV2' } },
  );

  check(response, {
    'search v2 status is 200': (res) => res.status === 200,
    'search v2 response is successful': isSuccessfulApiResponse,
  });
}

function requestPopularKeywords() {
  const response = http.get(`${BASE_URL}/api/search/popular-keywords?limit=10`, {
    tags: { endpoint: 'popularKeywords' },
  });

  check(response, {
    'popular keywords status is 200': (res) => res.status === 200,
    'popular keywords response is successful': isSuccessfulApiResponse,
  });
}

function isSuccessfulApiResponse(response) {
  try {
    return response.json('success') === true;
  } catch (error) {
    return false;
  }
}

function scenarioOptions() {
  if (rampUpEnabled()) {
    return {
      executor: 'ramping-vus',
      stages: [
        { duration: RAMP_UP_DURATION, target: VUS },
        { duration: DURATION, target: VUS },
        { duration: RAMP_DOWN_DURATION, target: 0 },
      ],
      gracefulRampDown: '10s',
      gracefulStop: '10s',
    };
  }

  return {
    executor: 'constant-vus',
    vus: VUS,
    duration: DURATION,
    gracefulStop: '10s',
  };
}

function rampUpEnabled() {
  const duration = RAMP_UP_DURATION.trim();
  return duration !== '' && duration !== '0' && duration !== '0s' && duration !== '0m';
}

function randomItem(items) {
  return items[Math.floor(Math.random() * items.length)];
}

function randomSleepSeconds() {
  if (MAX_SLEEP_SECONDS <= MIN_SLEEP_SECONDS) {
    return MIN_SLEEP_SECONDS;
  }
  return MIN_SLEEP_SECONDS + Math.random() * (MAX_SLEEP_SECONDS - MIN_SLEEP_SECONDS);
}

function positiveIntegerEnv(name, defaultValue) {
  const value = Number(__ENV[name]);
  if (!Number.isFinite(value) || value < 1) {
    return defaultValue;
  }
  return Math.floor(value);
}

function positiveNumberEnv(name, defaultValue) {
  const value = Number(__ENV[name]);
  if (!Number.isFinite(value) || value <= 0) {
    return defaultValue;
  }
  return value;
}

function createConsoleSummary(data) {
  const checks = metricValues(data, 'checks');
  const failed = metricValues(data, 'http_req_failed');
  const duration = metricValues(data, 'http_req_duration');
  const thresholdStatus = allThresholdsPassed(data) ? 'PASS' : 'FAIL';

  return [
    '',
    'k6 readonly search summary',
    `thresholds: ${thresholdStatus}`,
    `checks: ${formatRate(checks.rate)} (${numberValue(checks.passes)} passed, ${numberValue(checks.fails)} failed)`,
    `http_req_failed: ${formatRate(failed.rate)}`,
    `http_req_duration p95: ${formatDuration(duration['p(95)'])}`,
    `summary files: ${SUMMARY_DIRECTORY}/${SUMMARY_BASENAME}.md, ${SUMMARY_DIRECTORY}/${SUMMARY_BASENAME}.json`,
    '',
  ].join('\n');
}

function createMarkdownSummary(data) {
  const checks = metricValues(data, 'checks');
  const failed = metricValues(data, 'http_req_failed');
  const requests = metricValues(data, 'http_reqs');
  const duration = metricValues(data, 'http_req_duration');
  const thresholdStatus = allThresholdsPassed(data) ? 'PASS' : 'FAIL';

  return [
    '# k6 Readonly Search Summary',
    '',
    '## Run',
    '',
    `- Generated at: ${new Date().toISOString()}`,
    `- Base URL: ${BASE_URL}`,
    `- Executor: ${scenarioOptions().executor}`,
    `- Target VUs: ${VUS}`,
    ...runDurationSummaryLines(),
    `- Page size: ${PAGE_SIZE}`,
    `- Test run duration: ${formatDuration(data.state && data.state.testRunDurationMs)}`,
    `- Thresholds: ${thresholdStatus}`,
    '',
    '## Highlights',
    '',
    '| Metric | Value |',
    '| --- | ---: |',
    `| HTTP requests | ${numberValue(requests.count)} |`,
    `| HTTP request rate | ${formatNumber(requests.rate)}/s |`,
    `| HTTP failed rate | ${formatRate(failed.rate)} |`,
    `| Checks passed rate | ${formatRate(checks.rate)} |`,
    `| Checks passed | ${numberValue(checks.passes)} |`,
    `| Checks failed | ${numberValue(checks.fails)} |`,
    `| HTTP duration avg | ${formatDuration(duration.avg)} |`,
    `| HTTP duration p95 | ${formatDuration(duration['p(95)'])} |`,
    `| HTTP duration p99 | ${formatDuration(duration['p(99)'])} |`,
    '',
    '## Thresholds',
    '',
    thresholdTable(data),
    '',
    '## HTTP Duration By Endpoint',
    '',
    endpointDurationTable(data),
    '',
  ].join('\n');
}

function runDurationSummaryLines() {
  if (!rampUpEnabled()) {
    return [`- Duration: ${DURATION}`];
  }

  return [
    `- Ramp up duration: ${RAMP_UP_DURATION}`,
    `- Steady duration: ${DURATION}`,
    `- Ramp down duration: ${RAMP_DOWN_DURATION}`,
  ];
}

function thresholdTable(data) {
  const rows = [];

  Object.keys(data.metrics || {})
      .sort()
      .forEach((metricName) => {
        const thresholds = data.metrics[metricName].thresholds || {};
        Object.keys(thresholds).forEach((condition) => {
          rows.push([
            markdownCell(metricName),
            markdownCell(condition),
            thresholds[condition].ok ? 'PASS' : 'FAIL',
          ]);
        });
      });

  if (rows.length === 0) {
    return 'No thresholds were reported.';
  }

  return [
    '| Metric | Condition | Result |',
    '| --- | --- | --- |',
    ...rows.map((row) => `| ${row[0]} | ${row[1]} | ${row[2]} |`),
  ].join('\n');
}

function endpointDurationTable(data) {
  const endpointMetrics = Object.keys(data.metrics || {})
      .filter((metricName) => metricName.startsWith('http_req_duration{endpoint:'))
      .sort();

  if (endpointMetrics.length === 0) {
    return 'No endpoint duration metrics were reported.';
  }

  const rows = endpointMetrics.map((metricName) => {
    const values = metricValues(data, metricName);
    return [
      markdownCell(endpointName(metricName)),
      formatDuration(values.avg),
      formatDuration(values.med),
      formatDuration(values['p(95)']),
      formatDuration(values['p(99)']),
      formatDuration(values.max),
    ];
  });

  return [
    '| Endpoint | Avg | Med | P95 | P99 | Max |',
    '| --- | ---: | ---: | ---: | ---: | ---: |',
    ...rows.map((row) => `| ${row[0]} | ${row[1]} | ${row[2]} | ${row[3]} | ${row[4]} | ${row[5]} |`),
  ].join('\n');
}

function endpointName(metricName) {
  return metricName.replace('http_req_duration{endpoint:', '').replace('}', '');
}

function allThresholdsPassed(data) {
  return Object.keys(data.metrics || {}).every((metricName) => {
    const thresholds = data.metrics[metricName].thresholds || {};
    return Object.keys(thresholds).every((condition) => thresholds[condition].ok);
  });
}

function metricValues(data, metricName) {
  const metric = data.metrics && data.metrics[metricName];
  return (metric && metric.values) || {};
}

function markdownCell(value) {
  return String(value).replace(/\|/g, '\\|');
}

function numberValue(value) {
  if (!Number.isFinite(value)) {
    return '0';
  }
  return String(Math.round(value));
}

function formatNumber(value) {
  if (!Number.isFinite(value)) {
    return '0';
  }
  return value.toFixed(2);
}

function formatRate(value) {
  if (!Number.isFinite(value)) {
    return '0.00%';
  }
  return `${(value * 100).toFixed(2)}%`;
}

function formatDuration(value) {
  if (!Number.isFinite(value)) {
    return '0ms';
  }
  return `${value.toFixed(2)}ms`;
}
