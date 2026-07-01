import http from 'k6/http';
import { authHeaders } from './auth.js';
import { apiData, requireApiData } from './common.js';

export function fetchCategoryIds(baseUrl) {
  const response = http.get(`${baseUrl}/api/categories`, {
    tags: { endpoint: 'setupCategories' },
  });
  const data = requireApiData(response, 'fetch categories');
  const categories = data.categories || [];
  return categories
      .map((category) => category.categoryId)
      .filter((categoryId) => Number.isFinite(categoryId));
}

export function fetchProductIdsFromList(baseUrl, pages, pageSize) {
  const productIds = [];

  for (let page = 0; page < pages; page += 1) {
    const response = http.get(
        `${baseUrl}/api/products?page=${page}&size=${pageSize}&status=ON_SALE&sort=latest`,
        { tags: { endpoint: 'setupProductList' } },
    );
    const data = apiData(response);
    if (!data || !data.content) {
      continue;
    }
    data.content.forEach((product) => {
      if (Number.isFinite(product.productId)) {
        productIds.push(product.productId);
      }
    });
    if (!data.hasNext) {
      break;
    }
  }

  return Array.from(new Set(productIds));
}

export function createProduct(baseUrl, accessToken, categoryId, titlePrefix) {
  const uniqueSuffix = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  const response = http.post(
      `${baseUrl}/api/products`,
      JSON.stringify({
        title: `${titlePrefix} ${uniqueSuffix}`,
        description: `k6 setup product ${uniqueSuffix}`,
        price: 10000 + Math.floor(Math.random() * 90000),
        categoryId: categoryId,
      }),
      {
        headers: authHeaders(accessToken),
        tags: { endpoint: 'setupCreateProduct' },
      },
  );
  const data = requireApiData(response, 'create product');
  return data.productId;
}

export function createChatRoom(baseUrl, accessToken, productId) {
  const response = http.post(`${baseUrl}/api/products/${productId}/chat-rooms`, null, {
    headers: authHeaders(accessToken),
    tags: { endpoint: 'setupCreateChatRoom' },
  });
  const data = requireApiData(response, 'create chat room');
  return data.roomId;
}
