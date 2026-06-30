const ACCESS_TOKEN_KEY = "yeolmu.accessToken";
const USER_KEY = "yeolmu.user";

export const session = {
  get token() {
    return sessionStorage.getItem(ACCESS_TOKEN_KEY);
  },
  set token(value) {
    if (value) {
      sessionStorage.setItem(ACCESS_TOKEN_KEY, value);
    } else {
      sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    }
  },
  get user() {
    const stored = sessionStorage.getItem(USER_KEY);
    return stored ? JSON.parse(stored) : null;
  },
  set user(value) {
    if (value) {
      sessionStorage.setItem(USER_KEY, JSON.stringify(value));
    } else {
      sessionStorage.removeItem(USER_KEY);
    }
  },
  clear() {
    this.token = null;
    this.user = null;
  },
};

export class ApiError extends Error {
  constructor(response, body) {
    super(resolveErrorMessage(body, response.status));
    this.name = "ApiError";
    this.status = response.status;
    this.body = body;
    this.code = body?.code || `HTTP_${response.status}`;
    this.errors = body?.errors || [];
  }
}

function resolveErrorMessage(body, status) {
  if (body?.message) return body.message;
  if (body?.code) return body.code;
  return `요청에 실패했어요. (${status})`;
}

function normalizePath(path) {
  if (path.startsWith("http")) return path;
  return path.startsWith("/") ? path : `/${path}`;
}

function buildQuery(params = {}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    search.set(key, value);
  });
  const query = search.toString();
  return query ? `?${query}` : "";
}

function decodeTokenUser(accessToken) {
  try {
    const [, payload] = accessToken.split(".");
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const json = decodeURIComponent(
      atob(padded)
        .split("")
        .map((char) => `%${char.charCodeAt(0).toString(16).padStart(2, "0")}`)
        .join(""),
    );
    const claims = JSON.parse(json);
    const email = claims.email || "";
    return {
      userId: Number(claims.sub),
      email,
      nickname: session.user?.nickname || email.split("@")[0] || "열무",
      role: claims.role || "USER",
    };
  } catch {
    return null;
  }
}

function storeAccessToken(accessToken, user) {
  session.token = accessToken;
  session.user = user || session.user || decodeTokenUser(accessToken);
}

function shouldAttemptRefresh(path) {
  const normalized = normalizePath(path);
  return !["/api/auth/login", "/api/auth/signup", "/api/auth/refresh"].includes(normalized);
}

async function request(path, options = {}, retry = true) {
  const headers = new Headers(options.headers || {});
  const isFormData = options.body instanceof FormData;
  if (options.body && !isFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (session.token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${session.token}`);
  }

  const response = await fetch(normalizePath(path), {
    credentials: "include",
    ...options,
    headers,
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;

  if (response.status === 401 && retry && shouldAttemptRefresh(path)) {
    const refreshed = await refresh().catch(() => false);
    if (refreshed) {
      return request(path, options, false);
    }
  }
  if (!response.ok || body?.success === false) {
    throw new ApiError(response, body);
  }
  return body?.data ?? null;
}

async function refresh() {
  const response = await fetch("/api/auth/refresh", {
    method: "POST",
    credentials: "include",
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok || body?.success === false || !body?.data?.accessToken) {
    session.clear();
    return false;
  }
  storeAccessToken(body.data.accessToken);
  return true;
}

export const api = {
  get(path, params) {
    return request(`${path}${buildQuery(params)}`);
  },
  post(path, body, options = {}) {
    return request(path, {
      method: "POST",
      body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body),
      ...options,
    });
  },
  put(path, body) {
    return request(path, { method: "PUT", body: JSON.stringify(body) });
  },
  patch(path, body) {
    return request(path, {
      method: "PATCH",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  },
  delete(path) {
    return request(path, { method: "DELETE" });
  },
  auth: {
    async signup(payload) {
      return api.post("/api/auth/signup", payload);
    },
    async login(payload) {
      const data = await api.post("/api/auth/login", payload);
      storeAccessToken(data.accessToken, data.user);
      return data;
    },
    async restore() {
      if (session.token && session.user) return true;
      return refresh();
    },
    async logout() {
      try {
        return await api.post("/api/auth/logout");
      } finally {
        session.clear();
      }
    },
  },
  products: {
    list(params) {
      return api.get("/api/products", params);
    },
    detail(id) {
      return api.get(`/api/products/${id}`);
    },
    create(payload) {
      return api.post("/api/products", payload);
    },
    update(id, payload) {
      return api.put(`/api/products/${id}`, payload);
    },
    remove(id) {
      return api.delete(`/api/products/${id}`);
    },
    uploadImages(id, files) {
      const form = new FormData();
      Array.from(files).forEach((file) => form.append("images", file));
      return api.post(`/api/products/${id}/images`, form);
    },
    deleteImage(productId, imageId) {
      return api.delete(`/api/products/${productId}/images/${imageId}`);
    },
  },
  search: {
    products(params, cached = false) {
      return api.get(cached ? "/api/search/v2/products" : "/api/search/products", params);
    },
    popularKeywords(limit = 6) {
      return api.get("/api/search/popular-keywords", { limit });
    },
  },
  categories: {
    list() {
      return api.get("/api/categories");
    },
    products(categoryId, params) {
      return api.get(`/api/categories/${categoryId}/products`, params);
    },
    create(payload) {
      return api.post("/api/admin/categories", payload);
    },
    update(categoryId, payload) {
      return api.put(`/api/admin/categories/${categoryId}`, payload);
    },
    remove(categoryId) {
      return api.delete(`/api/admin/categories/${categoryId}`);
    },
  },
  users: {
    detail(userId) {
      return api.get(`/api/users/${userId}`);
    },
    updateMe(payload) {
      return api.put("/api/users/me", payload);
    },
    products(userId, params) {
      return api.get(`/api/users/${userId}/products`, params);
    },
    myProducts(params) {
      return api.get("/api/users/me/products", params);
    },
    myOrders(params) {
      return api.get("/api/users/me/orders", params);
    },
    mySales(params) {
      return api.get("/api/users/me/sales", params);
    },
    wishes(params) {
      return api.get("/api/users/me/wishes", params);
    },
    myReviews(status, params = {}) {
      return api.get("/api/users/me/reviews", { status, ...params });
    },
    publicReviews(userId, params) {
      return api.get(`/api/users/${userId}/reviews`, params);
    },
  },
  wishes: {
    add(productId) {
      return api.post(`/api/products/${productId}/wishes`);
    },
    remove(productId) {
      return api.delete(`/api/products/${productId}/wishes`);
    },
  },
  orders: {
    create(productId) {
      return api.post(`/api/products/${productId}/orders`);
    },
    detail(orderId) {
      return api.get(`/api/orders/${orderId}`);
    },
    cancel(orderId) {
      return api.post(`/api/orders/${orderId}/cancel`);
    },
    shipping(orderId, trackingNumber) {
      return api.patch(`/api/orders/${orderId}/shipping`, { trackingNumber });
    },
    confirm(orderId) {
      return api.post(`/api/orders/${orderId}/confirm`);
    },
  },
  payments: {
    create(orderId, payload, idempotencyKey) {
      const headers = idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {};
      return api.post(`/api/orders/${orderId}/payment`, payload, { headers });
    },
    status(paymentId) {
      return api.get(`/api/payments/${paymentId}/status`);
    },
    detail(paymentId) {
      return api.get(`/api/payments/${paymentId}`);
    },
    cancel(paymentId, reason) {
      return api.post(`/api/payments/${paymentId}/cancel`, reason ? { reason } : undefined);
    },
  },
  chat: {
    createRoom(productId) {
      return api.post(`/api/products/${productId}/chat-rooms`);
    },
    rooms(params) {
      return api.get("/api/chat-rooms", params);
    },
    messages(roomId, params) {
      return api.get(`/api/chat-rooms/${roomId}/messages`, params);
    },
  },
  reviews: {
    create(orderId, payload) {
      return api.post(`/api/orders/${orderId}/reviews`, payload);
    },
    update(orderId, reviewId, payload) {
      return api.patch(`/api/orders/${orderId}/reviews/${reviewId}`, payload);
    },
    remove(orderId, reviewId) {
      return api.delete(`/api/orders/${orderId}/reviews/${reviewId}`);
    },
  },
  refunds: {
    create(orderId, reason) {
      return api.post(`/api/orders/${orderId}/refund`, { reason });
    },
    approve(refundId) {
      return api.post(`/api/refund/${refundId}/approve`);
    },
    reject(refundId, reason) {
      return api.post(`/api/refund/${refundId}/reject`, reason ? { reason } : undefined);
    },
    resolve(refundId, payload) {
      return api.post(`/api/refund/${refundId}/resolve`, payload);
    },
  },
  admin: {
    hiddenProducts(params) {
      return api.get("/api/admin/products/hidden", params);
    },
    updateHidden(productId, hidden) {
      return api.patch(`/api/admin/products/${productId}/hidden`, { hidden });
    },
  },
};
