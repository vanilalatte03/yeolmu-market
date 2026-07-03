import http from 'k6/http';
import exec from 'k6/execution';
import { check, group, sleep } from 'k6';
import {
  baseUrl,
  booleanEnv,
  createSummary,
  isSuccessfulApiResponse,
  positiveIntegerEnv,
  positiveNumberEnv,
  randomSleepSeconds,
  scenarioOptions,
} from './lib/common.js';
import { credentialPoolFromEnv, jsonHeaders, refreshCookie, requireCredentialCount } from './lib/auth.js';

const BASE_URL = baseUrl();
const VUS = positiveIntegerEnv('VUS', 1);
const DURATION = __ENV.DURATION || '1m';
const RAMP_UP_DURATION = __ENV.RAMP_UP_DURATION || '';
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN_DURATION || '10s';
const MIN_SLEEP_SECONDS = positiveNumberEnv('MIN_SLEEP_SECONDS', 0.5);
const MAX_SLEEP_SECONDS = positiveNumberEnv('MAX_SLEEP_SECONDS', 1.5);
const AUTO_SIGNUP_AUTH_USERS = booleanEnv('AUTO_SIGNUP_AUTH_USERS', true);
const VALIDATE_AUTH_SETUP = booleanEnv('VALIDATE_AUTH_SETUP', true);
const LOG_FAILURES = booleanEnv('LOG_FAILURES', false);
const DEFAULT_PASSWORD = __ENV.PERF_RUN_KEY ? 'password' : 'Password123!';
const SUMMARY_BASENAME = __ENV.SUMMARY_BASENAME || 'auth-session-summary';
const SUMMARY_DIRECTORY = __ENV.SUMMARY_DIRECTORY || 'k6/results';

export const options = {
  scenarios: {
    auth_session: scenarioOptions(VUS, DURATION, RAMP_UP_DURATION, RAMP_DOWN_DURATION),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:authLogin}': ['p(95)<1500', 'p(99)<3000'],
    'http_req_duration{endpoint:authRefresh}': ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const credentials = resolveCredentials();

  requireCredentialCount(credentials, VUS, 'auth-session');

  if (VALIDATE_AUTH_SETUP) {
    validateCredentials(credentials);
  }

  return {
    credentials: credentials,
  };
}

export default function (data) {
  const credential = data.credentials[(exec.vu.idInTest - 1) % data.credentials.length];

  group('login and refresh', () => {
    const loginResponse = requestLogin(credential);
    const cookie = refreshCookie(loginResponse);
    if (loginResponse.status === 200 && cookie !== '') {
      requestRefresh(cookie);
    }
  });

  sleep(randomSleepSeconds(MIN_SLEEP_SECONDS, MAX_SLEEP_SECONDS));
}

export function handleSummary(data) {
  return createSummary(data, {
    title: 'k6 Auth Session Summary',
    consoleTitle: 'k6 auth session',
    summaryBasename: SUMMARY_BASENAME,
    summaryDirectory: SUMMARY_DIRECTORY,
    runLines: [
      `- Base URL: ${BASE_URL}`,
      `- Target VUs: ${VUS}`,
      `- Duration: ${DURATION}`,
      `- Auto signup auth users: ${AUTO_SIGNUP_AUTH_USERS}`,
      `- Setup credential validation: ${VALIDATE_AUTH_SETUP}`,
    ],
  });
}

function resolveCredentials() {
  if (AUTO_SIGNUP_AUTH_USERS && !credentialEnvProvided()) {
    const credentials = generatedCredentials();
    signupCredentials(credentials);
    return credentials;
  }

  return credentialPoolFromEnv({
    count: VUS,
    defaultPassword: DEFAULT_PASSWORD,
    defaultEmail: __ENV.LOGIN_EMAIL,
  });
}

function credentialEnvProvided() {
  return Boolean(__ENV.AUTH_EMAILS || __ENV.AUTH_EMAIL || __ENV.LOGIN_EMAIL || __ENV.PERF_RUN_KEY);
}

function generatedCredentials() {
  const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const password = __ENV.AUTH_PASSWORD || 'Password123!';
  return Array.from({ length: VUS }, (_, index) => ({
    email: `k6-auth-${runId}-${index + 1}@example.com`,
    password: password,
    nickname: `k6-auth-${index + 1}`,
  }));
}

function signupCredentials(credentials) {
  credentials.forEach((credential) => {
    const response = http.post(
        `${BASE_URL}/api/auth/signup`,
        JSON.stringify({
          email: credential.email,
          password: credential.password,
          nickname: credential.nickname,
        }),
        {
          headers: jsonHeaders(),
          tags: { endpoint: 'setupAuthSignup' },
        },
    );

    if (response.status !== 201 || !isSuccessfulApiResponse(response)) {
      throw new Error(
          `auth setup signup failed for ${credential.email}. status=${response.status}, body=${bodyPreview(response)}`);
    }
  });
}

function validateCredentials(credentials) {
  credentials.forEach((credential) => {
    const response = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: credential.email, password: credential.password }),
        {
          headers: jsonHeaders(),
          tags: { endpoint: 'setupAuthValidate' },
          responseCallback: http.expectedStatuses(200, 400, 401, 403, 503),
        },
    );

    if (response.status !== 200 || !isSuccessfulApiResponse(response)) {
      throw new Error(
          `auth setup validation failed for ${credential.email}. status=${response.status}, body=${bodyPreview(response)}`);
    }
  });
}

function requestLogin(credential) {
  const response = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: credential.email, password: credential.password }),
      {
        headers: jsonHeaders(),
        tags: { endpoint: 'authLogin' },
      },
  );

  check(response, {
    'login status is 200': (res) => res.status === 200,
    'login response is successful': isSuccessfulApiResponse,
    'login sets refresh token cookie': (res) => refreshCookie(res) !== '',
  });

  logFailure('login', response);

  return response;
}

function requestRefresh(cookie) {
  const response = http.post(`${BASE_URL}/api/auth/refresh`, null, {
    headers: {
      Cookie: cookie,
    },
    tags: { endpoint: 'authRefresh' },
  });

  check(response, {
    'refresh status is 200': (res) => res.status === 200,
    'refresh response is successful': isSuccessfulApiResponse,
    'refresh rotates refresh token cookie': (res) => refreshCookie(res) !== '',
  });

  logFailure('refresh', response);
}

function logFailure(label, response) {
  if (!LOG_FAILURES || response.status === 200) {
    return;
  }
  console.error(`${label} failed. status=${response.status}, body=${bodyPreview(response)}`);
}

function bodyPreview(response) {
  const body = response.body || '';
  if (body.length <= 500) {
    return body;
  }
  return `${body.substring(0, 500)}...`;
}
