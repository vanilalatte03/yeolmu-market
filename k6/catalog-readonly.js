import http from 'k6/http';
import { check, group, sleep } from 'k6';
import {
  baseUrl,
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
import { fetchCategoryIds, fetchProductIdsFromList } from './lib/data.js';

const BASE_URL = baseUrl();
const VUS = positiveIntegerEnv('VUS', 10);
const DURATION = __ENV.DURATION || '1m';
const RAMP_UP_DURATION = __ENV.RAMP_UP_DURATION || '';
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN_DURATION || '10s';
const PAGE_SIZE = positiveIntegerEnv('PAGE_SIZE', 20);
const MAX_PAGE = nonNegativeIntegerEnv('MAX_PAGE', 50);
const SETUP_SAMPLE_PAGES = positiveIntegerEnv('SETUP_SAMPLE_PAGES', 5);
const SETUP_SAMPLE_PAGE_SIZE = positiveIntegerEnv('SETUP_SAMPLE_PAGE_SIZE', 50);
const PRODUCT_ID_MIN = nonNegativeIntegerEnv('PRODUCT_ID_MIN', 0);
const PRODUCT_ID_MAX = nonNegativeIntegerEnv('PRODUCT_ID_MAX', 0);
const MIN_SLEEP_SECONDS = positiveNumberEnv('MIN_SLEEP_SECONDS', 0.5);
const MAX_SLEEP_SECONDS = positiveNumberEnv('MAX_SLEEP_SECONDS', 1.5);
const SUMMARY_BASENAME = __ENV.SUMMARY_BASENAME || 'catalog-readonly-summary';
const SUMMARY_DIRECTORY = __ENV.SUMMARY_DIRECTORY || 'k6/results';
const SORTS = ['latest', 'priceAsc', 'priceDesc'];

export const options = {
  scenarios: {
    readonly_catalog: scenarioOptions(VUS, DURATION, RAMP_UP_DURATION, RAMP_DOWN_DURATION),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:productList}': ['p(95)<1000', 'p(99)<2000'],
    'http_req_duration{endpoint:productDetailFromList}': ['p(95)<800', 'p(99)<1500'],
    'http_req_duration{endpoint:categoryProducts}': ['p(95)<1000', 'p(99)<2000'],
    'http_req_duration{endpoint:randomProductDetail}': ['p(95)<800', 'p(99)<1500'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const configuredProductIds = numberCsvEnv('PRODUCT_IDS');
  const configuredCategoryIds = numberCsvEnv('CATEGORY_IDS');
  const productIds = configuredProductIds.length > 0
    ? configuredProductIds
    : fetchProductIdsFromList(BASE_URL, SETUP_SAMPLE_PAGES, SETUP_SAMPLE_PAGE_SIZE);
  const categoryIds = configuredCategoryIds.length > 0 ? configuredCategoryIds : fetchCategoryIds(BASE_URL);

  if (productIds.length === 0 && !productIdRangeEnabled()) {
    throw new Error('No product ids are available. Set PRODUCT_IDS or PRODUCT_ID_MIN/PRODUCT_ID_MAX.');
  }
  if (categoryIds.length === 0) {
    throw new Error('No category ids are available. Set CATEGORY_IDS or seed categories first.');
  }

  return {
    productIds: productIds,
    categoryIds: categoryIds,
  };
}

export default function (data) {
  group('public readonly catalog path', () => {
    const productIdFromList = requestProductList();
    requestProductDetail(productIdFromList || randomProductId(data), 'productDetailFromList');
    requestCategoryProducts(data);
    requestProductDetail(randomProductId(data), 'randomProductDetail');
  });

  sleep(randomSleepSeconds(MIN_SLEEP_SECONDS, MAX_SLEEP_SECONDS));
}

export function handleSummary(data) {
  return createSummary(data, {
    title: 'k6 Catalog Readonly Summary',
    consoleTitle: 'k6 catalog readonly',
    summaryBasename: SUMMARY_BASENAME,
    summaryDirectory: SUMMARY_DIRECTORY,
    runLines: [
      `- Base URL: ${BASE_URL}`,
      `- Target VUs: ${VUS}`,
      `- Duration: ${DURATION}`,
      `- Page size: ${PAGE_SIZE}`,
      `- Max random page: ${MAX_PAGE}`,
      `- Product id range: ${productIdRangeEnabled() ? `${PRODUCT_ID_MIN}-${PRODUCT_ID_MAX}` : 'disabled'}`,
    ],
  });
}

function requestProductList() {
  const page = randomInteger(0, MAX_PAGE);
  const sort = randomItem(SORTS);
  const response = http.get(
      `${BASE_URL}/api/products?page=${page}&size=${PAGE_SIZE}&status=ON_SALE&sort=${sort}`,
      { tags: { endpoint: 'productList' } },
  );

  check(response, {
    'product list status is 200': (res) => res.status === 200,
    'product list response is successful': isSuccessfulApiResponse,
  });

  try {
    const content = response.json('data.content') || [];
    if (content.length === 0) {
      return null;
    }
    return randomItem(content).productId;
  } catch (error) {
    return null;
  }
}

function requestProductDetail(productId, endpoint) {
  const response = http.get(`${BASE_URL}/api/products/${productId}`, {
    tags: { endpoint: endpoint },
  });

  check(response, {
    [`${endpoint} status is 200`]: (res) => res.status === 200,
    [`${endpoint} response is successful`]: isSuccessfulApiResponse,
  });
}

function requestCategoryProducts(data) {
  const categoryId = randomItem(data.categoryIds);
  const page = randomInteger(0, MAX_PAGE);
  const sort = randomItem(SORTS);
  const response = http.get(
      `${BASE_URL}/api/categories/${categoryId}/products?page=${page}&size=${PAGE_SIZE}&sort=${sort}`,
      { tags: { endpoint: 'categoryProducts' } },
  );

  check(response, {
    'category products status is 200': (res) => res.status === 200,
    'category products response is successful': isSuccessfulApiResponse,
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
