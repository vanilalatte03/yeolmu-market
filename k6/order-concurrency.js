import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { check } from 'k6';
import {
  baseUrl,
  booleanEnv,
  createSummary,
  isSuccessfulApiResponse,
  nonNegativeIntegerEnv,
  positiveIntegerEnv,
} from './lib/common.js';
import {
  authHeaders,
  credentialPoolFromEnv,
  loginUser,
  loginUsers,
  requireCredentialCount,
  signupUsers,
} from './lib/auth.js';
import { createProduct, fetchCategoryIds } from './lib/data.js';

const BASE_URL = baseUrl();
const CONCURRENCY = positiveIntegerEnv('CONCURRENCY', 10);
const MAX_DURATION = __ENV.MAX_DURATION || '30s';
const ORDER_PRODUCT_ID = nonNegativeIntegerEnv('ORDER_PRODUCT_ID', 0);
const CATEGORY_ID = nonNegativeIntegerEnv('CATEGORY_ID', 0);
const AUTO_SIGNUP_ORDER_USERS = booleanEnv('AUTO_SIGNUP_ORDER_USERS', true);
const DEFAULT_PASSWORD = __ENV.PERF_RUN_KEY ? 'password' : 'Password123!';
const SUMMARY_BASENAME = __ENV.SUMMARY_BASENAME || 'order-concurrency-summary';
const SUMMARY_DIRECTORY = __ENV.SUMMARY_DIRECTORY || 'k6/results';

const orderCreateSuccess = new Counter('order_create_success');
const orderCreateConflict = new Counter('order_create_conflict');
const orderCreateUnexpected = new Counter('order_create_unexpected');

export const options = {
  scenarios: {
    concurrent_order: {
      executor: 'per-vu-iterations',
      vus: CONCURRENCY,
      iterations: 1,
      maxDuration: MAX_DURATION,
      gracefulStop: '10s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:orderCreate}': ['p(95)<1500', 'p(99)<3000'],
    order_create_success: ['count==1'],
    order_create_conflict: [`count==${CONCURRENCY - 1}`],
    order_create_unexpected: ['count==0'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const buyerCredentials = resolveBuyerCredentials();
  requireCredentialCount(buyerCredentials, CONCURRENCY, 'order-concurrency buyers');
  const buyers = loginUsers(BASE_URL, buyerCredentials.slice(0, CONCURRENCY), 'setupBuyerLogin');

  const productId = ORDER_PRODUCT_ID > 0 ? ORDER_PRODUCT_ID : createOrderTargetProduct();

  return {
    buyers: buyers,
    productId: productId,
  };
}

export default function (data) {
  const buyer = data.buyers[(exec.vu.idInTest - 1) % data.buyers.length];
  const response = http.post(`${BASE_URL}/api/products/${data.productId}/orders`, null, {
    headers: authHeaders(buyer.accessToken),
    tags: { endpoint: 'orderCreate' },
    responseCallback: http.expectedStatuses(201, 409),
  });

  if (response.status === 201) {
    orderCreateSuccess.add(1);
  } else if (response.status === 409) {
    orderCreateConflict.add(1);
  } else {
    orderCreateUnexpected.add(1);
  }

  check(response, {
    'order create status is 201 or 409': (res) => res.status === 201 || res.status === 409,
    'successful order create has success wrapper': (res) =>
      res.status !== 201 || isSuccessfulApiResponse(res),
    'conflicting order create is business conflict': (res) =>
      res.status !== 409 || conflictCode(res) === 'ORDER_ALREADY_EXISTS' || conflictCode(res) === 'PRODUCT_NOT_ON_SALE',
  });
}

export function handleSummary(data) {
  return createSummary(data, {
    title: 'k6 Order Concurrency Summary',
    consoleTitle: 'k6 order concurrency',
    summaryBasename: SUMMARY_BASENAME,
    summaryDirectory: SUMMARY_DIRECTORY,
    runLines: [
      `- Base URL: ${BASE_URL}`,
      `- Concurrency: ${CONCURRENCY}`,
      `- Max duration: ${MAX_DURATION}`,
      `- Product source: ${ORDER_PRODUCT_ID > 0 ? `provided ${ORDER_PRODUCT_ID}` : 'setup-created'}`,
      `- Auto signup order users: ${AUTO_SIGNUP_ORDER_USERS}`,
    ],
  });
}

function resolveBuyerCredentials() {
  const credentials = credentialPoolFromEnv({
    emailsEnv: 'BUYER_EMAILS',
    emailEnv: 'BUYER_EMAIL',
    passwordsEnv: 'BUYER_PASSWORDS',
    passwordEnv: 'BUYER_PASSWORD',
    sequenceStartEnv: 'BUYER_SEQUENCE_START',
    defaultSequenceStart: 2,
    count: CONCURRENCY,
    defaultPassword: DEFAULT_PASSWORD,
  });

  if (credentials.length > 0 || !AUTO_SIGNUP_ORDER_USERS) {
    return credentials;
  }

  const generatedCredentials = generatedOrderCredentials(
      'k6-order-buyer',
      CONCURRENCY,
      __ENV.BUYER_PASSWORD || DEFAULT_PASSWORD,
      'k6-order-buyer');
  signupUsers(BASE_URL, generatedCredentials, 'setupBuyerSignup');
  return generatedCredentials;
}

function createOrderTargetProduct() {
  const sellerCredentials = credentialPoolFromEnv({
    emailsEnv: 'SELLER_EMAILS',
    emailEnv: 'SELLER_EMAIL',
    passwordsEnv: 'SELLER_PASSWORDS',
    passwordEnv: 'SELLER_PASSWORD',
    sequenceStartEnv: 'SELLER_SEQUENCE_START',
    defaultSequenceStart: 1,
    count: 1,
    defaultPassword: DEFAULT_PASSWORD,
  });
  if (sellerCredentials.length === 0 && AUTO_SIGNUP_ORDER_USERS) {
    const generatedSellerCredentials = generatedOrderCredentials(
        'k6-order-seller',
        1,
        __ENV.SELLER_PASSWORD || DEFAULT_PASSWORD,
        'k6-order-seller');
    signupUsers(BASE_URL, generatedSellerCredentials, 'setupSellerSignup');
    sellerCredentials.push(generatedSellerCredentials[0]);
  }
  requireCredentialCount(sellerCredentials, 1, 'order-concurrency seller');
  const seller = loginUser(BASE_URL, sellerCredentials[0], 'setupSellerLogin');
  const categoryId = CATEGORY_ID > 0 ? CATEGORY_ID : firstCategoryId();
  return createProduct(BASE_URL, seller.accessToken, categoryId, 'k6 order concurrency');
}

function firstCategoryId() {
  const categoryIds = fetchCategoryIds(BASE_URL);
  if (categoryIds.length === 0) {
    throw new Error('No category id is available. Set CATEGORY_ID or seed categories first.');
  }
  return categoryIds[0];
}

function conflictCode(response) {
  try {
    return response.json('code');
  } catch (error) {
    return '';
  }
}

function generatedOrderCredentials(emailPrefix, count, password, nicknamePrefix) {
  const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  return Array.from({ length: count }, (_, index) => ({
    email: `${emailPrefix}-${runId}-${index + 1}@example.com`,
    password: password,
    nickname: `${nicknamePrefix}-${index + 1}`,
  }));
}
