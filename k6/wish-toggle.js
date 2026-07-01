import http from 'k6/http';
import exec from 'k6/execution';
import { check, group, sleep } from 'k6';
import {
  baseUrl,
  booleanEnv,
  createSummary,
  isSuccessfulApiResponse,
  nonNegativeIntegerEnv,
  numberCsvEnv,
  positiveIntegerEnv,
  positiveNumberEnv,
  randomInteger,
  randomItem,
  randomSleepSeconds,
  scenarioOptions,
} from './lib/common.js';
import {
  authHeaders,
  credentialPoolFromEnv,
  loginUsers,
  requireCredentialCount,
  signupUsers,
} from './lib/auth.js';
import { fetchProductIdsFromList } from './lib/data.js';

const BASE_URL = baseUrl();
const VUS = positiveIntegerEnv('VUS', 10);
const DURATION = __ENV.DURATION || '1m';
const RAMP_UP_DURATION = __ENV.RAMP_UP_DURATION || '';
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN_DURATION || '10s';
const SETUP_SAMPLE_PAGES = positiveIntegerEnv('SETUP_SAMPLE_PAGES', 5);
const SETUP_SAMPLE_PAGE_SIZE = positiveIntegerEnv('SETUP_SAMPLE_PAGE_SIZE', 50);
const PRODUCT_ID_MIN = nonNegativeIntegerEnv('PRODUCT_ID_MIN', 0);
const PRODUCT_ID_MAX = nonNegativeIntegerEnv('PRODUCT_ID_MAX', 0);
const CLEANUP_BEFORE_CREATE = booleanEnv('CLEANUP_BEFORE_CREATE', true);
const MIN_SLEEP_SECONDS = positiveNumberEnv('MIN_SLEEP_SECONDS', 0.3);
const MAX_SLEEP_SECONDS = positiveNumberEnv('MAX_SLEEP_SECONDS', 1.0);
const AUTO_SIGNUP_WISH_USERS = booleanEnv('AUTO_SIGNUP_WISH_USERS', true);
const DEFAULT_PASSWORD = __ENV.PERF_RUN_KEY ? 'password' : 'Password123!';
const SUMMARY_BASENAME = __ENV.SUMMARY_BASENAME || 'wish-toggle-summary';
const SUMMARY_DIRECTORY = __ENV.SUMMARY_DIRECTORY || 'k6/results';

export const options = {
  scenarios: {
    wish_toggle: scenarioOptions(VUS, DURATION, RAMP_UP_DURATION, RAMP_DOWN_DURATION),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{endpoint:wishCreate}': ['p(95)<1000', 'p(99)<2000'],
    'http_req_duration{endpoint:wishDelete}': ['p(95)<1000', 'p(99)<2000'],
    checks: ['rate>0.98'],
  },
};

export function setup() {
  const credentials = resolveCredentials();
  requireCredentialCount(credentials, VUS, 'wish-toggle');

  const sessions = loginUsers(BASE_URL, credentials.slice(0, VUS), 'setupAuthLogin');
  const configuredProductIds = numberCsvEnv('PRODUCT_IDS');
  const productIds = configuredProductIds.length > 0
    ? configuredProductIds
    : fetchProductIdsFromList(BASE_URL, SETUP_SAMPLE_PAGES, SETUP_SAMPLE_PAGE_SIZE);

  if (productIds.length === 0 && !productIdRangeEnabled()) {
    throw new Error('No product ids are available. Set PRODUCT_IDS or PRODUCT_ID_MIN/PRODUCT_ID_MAX.');
  }

  return {
    sessions: sessions,
    productIds: productIds,
  };
}

export default function (data) {
  const session = data.sessions[(exec.vu.idInTest - 1) % data.sessions.length];
  const productId = randomProductId(data);

  group('wish toggle', () => {
    if (CLEANUP_BEFORE_CREATE) {
      cleanupWish(session, productId);
    }
    createWish(session, productId);
    deleteWish(session, productId);
  });

  sleep(randomSleepSeconds(MIN_SLEEP_SECONDS, MAX_SLEEP_SECONDS));
}

export function handleSummary(data) {
  return createSummary(data, {
    title: 'k6 Wish Toggle Summary',
    consoleTitle: 'k6 wish toggle',
    summaryBasename: SUMMARY_BASENAME,
    summaryDirectory: SUMMARY_DIRECTORY,
    runLines: [
      `- Base URL: ${BASE_URL}`,
      `- Target VUs: ${VUS}`,
      `- Duration: ${DURATION}`,
      `- Cleanup before create: ${CLEANUP_BEFORE_CREATE}`,
      `- Auto signup wish users: ${AUTO_SIGNUP_WISH_USERS}`,
      `- Product id range: ${productIdRangeEnabled() ? `${PRODUCT_ID_MIN}-${PRODUCT_ID_MAX}` : 'disabled'}`,
    ],
  });
}

function resolveCredentials() {
  if (AUTO_SIGNUP_WISH_USERS && !credentialEnvProvided()) {
    const generatedCredentials = generatedWishCredentials(
        VUS,
        __ENV.AUTH_PASSWORD || DEFAULT_PASSWORD);
    signupUsers(BASE_URL, generatedCredentials, 'setupWishUserSignup');
    return generatedCredentials;
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

function generatedWishCredentials(count, password) {
  const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  return Array.from({ length: count }, (_, index) => ({
    email: `k6-wish-${runId}-${index + 1}@example.com`,
    password: password,
    nickname: `k6-wish-${index + 1}`,
  }));
}

function cleanupWish(session, productId) {
  const response = http.del(`${BASE_URL}/api/products/${productId}/wishes`, null, {
    headers: authHeaders(session.accessToken),
    tags: { endpoint: 'wishCleanup' },
    responseCallback: http.expectedStatuses(200, 404),
  });

  check(response, {
    'wish cleanup status is 200 or 404': (res) => res.status === 200 || res.status === 404,
  });
}

function createWish(session, productId) {
  const response = http.post(`${BASE_URL}/api/products/${productId}/wishes`, null, {
    headers: authHeaders(session.accessToken),
    tags: { endpoint: 'wishCreate' },
  });

  check(response, {
    'wish create status is 201': (res) => res.status === 201,
    'wish create response is successful': isSuccessfulApiResponse,
  });
}

function deleteWish(session, productId) {
  const response = http.del(`${BASE_URL}/api/products/${productId}/wishes`, null, {
    headers: authHeaders(session.accessToken),
    tags: { endpoint: 'wishDelete' },
  });

  check(response, {
    'wish delete status is 200': (res) => res.status === 200,
    'wish delete response is successful': isSuccessfulApiResponse,
  });
}

function randomProductId(data) {
  if (productIdRangeEnabled()) {
    return randomInteger(PRODUCT_ID_MIN, PRODUCT_ID_MAX);
  }
  return randomItem(data.productIds);
}

function productIdRangeEnabled() {
  return PRODUCT_ID_MIN > 0 && PRODUCT_ID_MAX >= PRODUCT_ID_MIN;
}
