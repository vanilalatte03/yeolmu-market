export const DEFAULT_BASE_URL = 'http://localhost:8080';
export const DEFAULT_SUMMARY_DIRECTORY = 'k6/results';

export function baseUrl() {
  return (__ENV.BASE_URL || DEFAULT_BASE_URL).replace(/\/+$/, '');
}

export function wsUrlFromBaseUrl(value) {
  if (value.startsWith('https://')) {
    return value.replace(/^https:\/\//, 'wss://');
  }
  if (value.startsWith('http://')) {
    return value.replace(/^http:\/\//, 'ws://');
  }
  return value;
}

export function positiveIntegerEnv(name, defaultValue) {
  const value = Number(__ENV[name]);
  if (!Number.isFinite(value) || value < 1) {
    return defaultValue;
  }
  return Math.floor(value);
}

export function nonNegativeIntegerEnv(name, defaultValue) {
  const value = Number(__ENV[name]);
  if (!Number.isFinite(value) || value < 0) {
    return defaultValue;
  }
  return Math.floor(value);
}

export function positiveNumberEnv(name, defaultValue) {
  const value = Number(__ENV[name]);
  if (!Number.isFinite(value) || value <= 0) {
    return defaultValue;
  }
  return value;
}

export function booleanEnv(name, defaultValue) {
  const value = __ENV[name];
  if (value === undefined || value === null || value === '') {
    return defaultValue;
  }
  return ['1', 'true', 'yes', 'y', 'on'].includes(String(value).toLowerCase());
}

export function csvEnv(name) {
  const value = __ENV[name];
  if (!value) {
    return [];
  }
  return value
      .split(',')
      .map((item) => item.trim())
      .filter((item) => item !== '');
}

export function numberCsvEnv(name) {
  return csvEnv(name)
      .map((item) => Number(item))
      .filter((item) => Number.isFinite(item) && item > 0)
      .map((item) => Math.floor(item));
}

export function randomItem(items) {
  return items[Math.floor(Math.random() * items.length)];
}

export function randomInteger(minInclusive, maxInclusive) {
  if (maxInclusive <= minInclusive) {
    return minInclusive;
  }
  return minInclusive + Math.floor(Math.random() * (maxInclusive - minInclusive + 1));
}

export function randomSleepSeconds(minSleepSeconds, maxSleepSeconds) {
  if (maxSleepSeconds <= minSleepSeconds) {
    return minSleepSeconds;
  }
  return minSleepSeconds + Math.random() * (maxSleepSeconds - minSleepSeconds);
}

export function scenarioOptions(vus, duration, rampUpDuration, rampDownDuration) {
  if (rampUpEnabled(rampUpDuration)) {
    return {
      executor: 'ramping-vus',
      stages: [
        { duration: rampUpDuration, target: vus },
        { duration: duration, target: vus },
        { duration: rampDownDuration, target: 0 },
      ],
      gracefulRampDown: '10s',
      gracefulStop: '10s',
    };
  }

  return {
    executor: 'constant-vus',
    vus: vus,
    duration: duration,
    gracefulStop: '10s',
  };
}

export function rampUpEnabled(rampUpDuration) {
  const duration = (rampUpDuration || '').trim();
  return duration !== '' && duration !== '0' && duration !== '0s' && duration !== '0m';
}

export function isSuccessfulApiResponse(response) {
  try {
    return response.json('success') === true;
  } catch (error) {
    return false;
  }
}

export function apiData(response) {
  if (!isSuccessfulApiResponse(response)) {
    return null;
  }
  try {
    return response.json('data');
  } catch (error) {
    return null;
  }
}

export function requireApiData(response, context) {
  const data = apiData(response);
  if (response.status < 200 || response.status >= 300 || data === null) {
    throw new Error(
        `${context} failed. status=${response.status}, body=${response.body || ''}`);
  }
  return data;
}

export function createSummary(data, config) {
  const summaryDirectory = config.summaryDirectory || DEFAULT_SUMMARY_DIRECTORY;
  const summaryBasename = config.summaryBasename;
  const markdown = createMarkdownSummary(data, config);

  return {
    stdout: createConsoleSummary(data, config),
    [`${summaryDirectory}/${summaryBasename}.md`]: markdown,
    [`${summaryDirectory}/${summaryBasename}.json`]: JSON.stringify(data, null, 2),
  };
}

function createConsoleSummary(data, config) {
  const checks = metricValues(data, 'checks');
  const failed = metricValues(data, 'http_req_failed');
  const duration = metricValues(data, 'http_req_duration');
  const thresholdStatus = allThresholdsPassed(data) ? 'PASS' : 'FAIL';

  return [
    '',
    `${config.consoleTitle || config.title} summary`,
    `thresholds: ${thresholdStatus}`,
    `checks: ${formatRate(checks.rate)} (${numberValue(checks.passes)} passed, ${numberValue(checks.fails)} failed)`,
    `http_req_failed: ${formatRate(failed.rate)}`,
    `http_req_duration p95: ${formatDuration(duration['p(95)'])}`,
    `summary files: ${config.summaryDirectory || DEFAULT_SUMMARY_DIRECTORY}/${config.summaryBasename}.md, ${config.summaryDirectory || DEFAULT_SUMMARY_DIRECTORY}/${config.summaryBasename}.json`,
    '',
  ].join('\n');
}

function createMarkdownSummary(data, config) {
  const checks = metricValues(data, 'checks');
  const failed = metricValues(data, 'http_req_failed');
  const requests = metricValues(data, 'http_reqs');
  const duration = metricValues(data, 'http_req_duration');
  const thresholdStatus = allThresholdsPassed(data) ? 'PASS' : 'FAIL';
  const runLines = config.runLines || [];

  return [
    `# ${config.title}`,
    '',
    '## Run',
    '',
    `- Generated at: ${new Date().toISOString()}`,
    ...runLines,
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
