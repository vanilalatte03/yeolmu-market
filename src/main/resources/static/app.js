import { api, ApiError, session } from "./api.js";
import { StompClient } from "./stomp-client.js";

const app = document.querySelector("#app");
const toastRoot = document.querySelector("#toast-root");
const STATIC_ASSET_VERSION = "20260702-front-edit";
const RADISH_ASSET = `/assets/radish.svg?v=${STATIC_ASSET_VERSION}`;

const DEMO_CATEGORIES = [
  { categoryId: 1, name: "디지털기기" },
  { categoryId: 2, name: "생활/가구" },
  { categoryId: 3, name: "의류" },
  { categoryId: 4, name: "도서" },
  { categoryId: 5, name: "스포츠/레저" },
];

const MAX_PRODUCT_IMAGE_COUNT = 10;
const MAX_PRODUCT_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
const MAX_PRODUCT_IMAGE_REQUEST_BYTES = 25 * 1024 * 1024;
const SUPPORTED_PRODUCT_IMAGE_TYPES = new Set(["image/jpeg", "image/png", "image/gif", "image/webp"]);
const CHAT_SUBSCRIPTION_NOT_READY_MESSAGE = "채팅 구독 준비에 실패했어요. 잠시 후 다시 시도해 주세요.";
const ORDER_PAYMENT_IDS_KEY = "yeolmu.orderPaymentIds";
const ORDER_REFUND_REQUEST_IDS_KEY = "yeolmu.orderRefundRequestIds";

const DEMO_PRODUCTS = [
  {
    productId: 1,
    title: "아이폰 13 미니 128G 미드나이트",
    price: 420000,
    status: "ON_SALE",
    sellerNickname: "초록당근",
    categoryName: "디지털기기",
    createdAt: "2026-06-30T21:50:00",
    wishCount: 12,
    chatRoomCount: 3,
    viewCount: 132,
    description:
      "아이폰 13 미니 128GB 미드나이트 색상입니다.\n2년 정도 사용했고 액정/외관 모두 깨끗해요. 기스 거의 없습니다.\n배터리 성능 89%, 정품 케이스와 보호필름 부착 상태로 드려요.\n강남역 직거래 가능, 택배도 보내드립니다 🙂",
  },
  {
    productId: 2,
    title: "원목 4인 식탁 (의자 미포함)",
    price: 55000,
    status: "RESERVED",
    sellerNickname: "목공방",
    categoryName: "생활/가구",
    createdAt: "2026-06-30T19:40:00",
    wishCount: 8,
    chatRoomCount: 5,
    viewCount: 88,
    description: "이사 가면서 내놓아요. 상태 좋습니다.",
  },
  {
    productId: 3,
    title: "로드 자전거 입문용 (펑크X)",
    price: 180000,
    status: "SOLD_OUT",
    sellerNickname: "바이크샵",
    categoryName: "스포츠/레저",
    createdAt: "2026-06-29T12:20:00",
    wishCount: 24,
    chatRoomCount: 9,
    viewCount: 240,
    description: "입문용으로 좋아요. 거래 완료되었습니다.",
  },
  {
    productId: 4,
    title: "에어팟 프로 2세대 (C타입)",
    price: 160000,
    status: "ON_SALE",
    sellerNickname: "음향러버",
    categoryName: "디지털기기",
    createdAt: "2026-06-30T21:35:00",
    wishCount: 31,
    chatRoomCount: 7,
    viewCount: 301,
    description: "미개봉에 가까운 상태입니다.",
  },
  {
    productId: 5,
    title: "접이식 캠핑 의자 2개",
    price: 30000,
    status: "ON_SALE",
    sellerNickname: "캠핑왕",
    categoryName: "스포츠/레저",
    createdAt: "2026-06-30T21:20:00",
    wishCount: 5,
    chatRoomCount: 1,
    viewCount: 54,
    description: "두 개 일괄로 드려요.",
  },
  {
    productId: 6,
    title: "무선 기계식 키보드 적축",
    price: 48000,
    status: "ON_SALE",
    sellerNickname: "키보드덕후",
    categoryName: "디지털기기",
    createdAt: "2026-06-30T20:55:00",
    wishCount: 14,
    chatRoomCount: 2,
    viewCount: 97,
    description: "적축 무선 키보드입니다.",
  },
];

const DEMO_KEYWORDS = ["아이폰", "책상", "자전거", "에어팟", "캠핑 의자", "패딩"].map((keyword, index) => ({
  rank: index + 1,
  keyword,
}));

const state = {
  route: parseRoute(),
  loading: false,
  products: [],
  productPage: null,
  productDetail: null,
  sellerProfile: null,
  sellerReviews: [],
  sellerProducts: [],
  categories: [],
  keywords: [],
  selectedCategoryId: null,
  search: { keyword: "", minPrice: "", maxPrice: "", status: "ON_SALE", sort: "latest", cached: true },
  myProducts: [],
  wishes: [],
  myProfile: null,
  myOrders: [],
  mySales: [],
  myPageTab: "sales",
  profilePanel: null,
  orderTab: "orders",
  myReviewStatus: "received",
  myReviews: [],
  writtenReviews: [],
  publicReviews: [],
  chatRooms: [],
  activeRoomId: null,
  chatMessages: [],
  chatMessageCache: new Map(),
  stomp: null,
  stompConnected: false,
  chatSubscriptions: emptyChatSubscriptions(),
  chatReconnectTimer: null,
  chatReconnectAttempts: 0,
  handledChatSaveFailures: new Set(),
  adminHiddenProducts: [],
  orderPanel: null,
  pendingPaymentKeys: {},
  orderPaymentIds: loadStoredIds(ORDER_PAYMENT_IDS_KEY),
  orderRefundRequestIds: loadStoredIds(ORDER_REFUND_REQUEST_IDS_KEY),
  selectedOrder: null,
  selectedPayment: null,
  ownerProductId: null,
  ownerProductTool: null,
  lastResult: null,
};

bootstrap();

function emptyChatSubscriptions() {
  return { userErrors: null, roomId: null, messages: null, errors: null, ready: null };
}

function loadStoredIds(key) {
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || "{}");
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return {};
    return Object.fromEntries(
      Object.entries(parsed)
        .filter(([, value]) => value !== null && value !== undefined && value !== "")
        .map(([orderId, value]) => [String(orderId), String(value)])
    );
  } catch {
    return {};
  }
}

function persistStoredIds(key, ids) {
  try {
    localStorage.setItem(key, JSON.stringify(ids));
  } catch {
    // 저장소 접근이 막힌 환경에서는 현재 화면 상태만 유지한다.
  }
}

function rememberPaymentId(orderId, paymentId) {
  if (!orderId || !paymentId) return;
  state.orderPaymentIds[String(orderId)] = String(paymentId);
  persistStoredIds(ORDER_PAYMENT_IDS_KEY, state.orderPaymentIds);
}

function forgetPaymentId(orderId) {
  if (!orderId) return;
  delete state.orderPaymentIds[String(orderId)];
  persistStoredIds(ORDER_PAYMENT_IDS_KEY, state.orderPaymentIds);
}

function rememberRefundRequestId(orderId, refundRequestId) {
  if (!orderId || !refundRequestId) return;
  state.orderRefundRequestIds[String(orderId)] = String(refundRequestId);
  persistStoredIds(ORDER_REFUND_REQUEST_IDS_KEY, state.orderRefundRequestIds);
}

function paymentIdForOrder(row) {
  if (!row?.orderId) return null;
  return row.paymentId || row.payment?.paymentId || state.orderPaymentIds[String(row.orderId)] || null;
}

function refundRequestIdForOrder(row) {
  if (!row?.orderId) return null;
  return row.refundRequestId || state.orderRefundRequestIds[String(row.orderId)] || null;
}

function refundRequestIdBadge(row) {
  const refundId = refundRequestIdForOrder(row);
  return refundId ? `<span class="badge refund-id-badge">환불 #${escapeHtml(String(refundId))}</span>` : "";
}

async function bootstrap() {
  window.addEventListener("hashchange", routeChanged);
  document.addEventListener("click", handleClick);
  document.addEventListener("change", handleChange);
  document.addEventListener("submit", handleSubmit);
  routeChanged();
}

async function routeChanged() {
  state.route = parseRoute();
  if (state.route.name !== "chat") {
    clearChatReconnect();
  }
  state.loading = true;
  render();
  try {
    await restoreSessionForRoute();
    await loadForRoute();
  } catch (error) {
    handleError(error);
  } finally {
    state.loading = false;
    render();
  }
}

async function loadForRoute() {
  await ensureCategories();
  const { name, id } = state.route;
  if (name === "home") await loadHome();
  if (name === "product") await loadProductDetail(id);
  if (name === "sell") await loadMyProducts();
  if (name === "chat") await loadChatRooms();
  if (name === "me") await loadMyPage();
  if (name === "orders") await loadOrders();
  if (name === "refunds") await loadOrders();
  if (name === "reviews") await loadReviews();
  if (name === "admin") await loadAdmin();
}

function parseRoute() {
  const hash = window.location.hash || "#/home";
  const parts = hash.replace(/^#\/?/, "").split("/");
  return { name: parts[0] || "home", id: parts[1] || null };
}

async function restoreSessionForRoute() {
  if (session.user) return;
  if (!session.token && !requiresAuthRoute(state.route.name)) return;
  await api.auth.restore().catch(() => session.clear());
}

function requiresAuthRoute(name) {
  return ["sell", "chat", "me", "orders", "reviews", "refunds", "admin"].includes(name);
}

function navigate(hash) {
  window.location.hash = hash;
}

function render() {
  const view = state.loading ? loadingView() : renderView();
  const chromeHidden = state.route.name === "auth";
  app.innerHTML = `
    <div class="app-shell ${chromeHidden ? "app-shell-plain" : ""}">
      ${chromeHidden ? "" : topbar()}
      <main class="page">${view}</main>
      ${chromeHidden ? "" : bottomNav()}
    </div>
  `;
}

function topbar() {
  const user = session.user;
  return `
    <header class="topbar">
      <div class="topbar-inner">
        <button class="brand" data-action="nav" data-target="#/home">
          <img class="brand-mark" src="${RADISH_ASSET}" alt="" />
          <span>열무마켓</span>
        </button>
        <form class="searchbar" data-form="search">
          <span class="search-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <circle cx="10.5" cy="10.5" r="7"></circle>
              <line x1="15.6" y1="15.6" x2="21" y2="21"></line>
            </svg>
          </span>
          <input name="keyword" value="${escapeAttr(state.search.keyword)}" placeholder="어떤 물건을 찾으세요?" />
          <button class="btn btn-ghost btn-small search-submit" type="submit">검색</button>
        </form>
        <div class="top-actions">
          <button class="btn btn-primary" data-action="nav" data-target="#/sell">
            <span>✏️</span><span class="btn-label">판매하기</span>
          </button>
          <button class="btn btn-ghost icon-btn chat-btn" data-action="nav" data-target="#/chat" title="채팅">
            💬<span class="chat-dot" aria-hidden="true"></span>
          </button>
          ${
            user
              ? `
                <div class="user-chip">
                  <button class="btn btn-ghost" data-action="toggle-menu">
                    <span class="avatar">${initial(user.nickname)}</span>
                    <span class="btn-label">${escapeHtml(user.nickname)}</span>
                    <span>▾</span>
                  </button>
                  <div class="menu-popover ${state.menuOpen ? "" : "hidden"}">
                    <button class="btn btn-ghost" data-action="nav" data-target="#/me">🧺 마이페이지</button>
                    <button class="btn btn-ghost" data-action="nav" data-target="#/orders">📦 거래내역</button>
                    <button class="btn btn-ghost" data-action="nav" data-target="#/reviews">⭐ 받은후기</button>
                    ${user.role === "ADMIN" ? `<button class="btn btn-ghost" data-action="nav" data-target="#/admin">🛠 관리자</button>` : ""}
                    <button class="btn btn-ghost btn-danger" data-action="logout">로그아웃</button>
                  </div>
                </div>`
              : `<button class="btn btn-ghost" data-action="nav" data-target="#/auth">로그인</button>`
          }
        </div>
      </div>
    </header>
  `;
}

function bottomNav() {
  return `
    <nav class="bottom-nav" aria-label="주요 메뉴">
      <button class="btn btn-ghost" data-action="nav" data-target="#/home">홈</button>
      <button class="btn btn-ghost" data-action="nav" data-target="#/sell">판매</button>
      <button class="btn btn-ghost" data-action="nav" data-target="#/chat">채팅</button>
      <button class="btn btn-ghost" data-action="nav" data-target="#/orders">거래</button>
      <button class="btn btn-ghost" data-action="nav" data-target="#/me">내정보</button>
    </nav>
  `;
}

function renderView() {
  const { name } = state.route;
  if (name === "auth") return authView();
  if (name === "product") return productDetailView();
  if (name === "sell") return requireLogin(sellView);
  if (name === "chat") return requireLogin(chatView);
  if (name === "me") return requireLogin(meView);
  if (name === "orders") return requireLogin(ordersView);
  if (name === "reviews") return requireLogin(reviewsView);
  if (name === "refunds") return requireLogin(refundsView);
  if (name === "admin") return requireLogin(adminView);
  return homeView();
}

function requireLogin(viewFactory) {
  if (!session.user) {
    return `
      <section class="panel empty-state">
        <h1>로그인이 필요해요</h1>
        <p>거래, 채팅, 찜, 리뷰 기능은 로그인 후 사용할 수 있어요.</p>
        <button class="btn btn-primary" data-action="nav" data-target="#/auth">로그인하러 가기</button>
      </section>
    `;
  }
  return viewFactory();
}

function loadingView() {
  return `
    <section class="paper-shell empty-state">
      <img class="brand-mark" src="${RADISH_ASSET}" alt="" />
      <h1>열무를 싱싱하게 불러오는 중...</h1>
    </section>
  `;
}

function authView() {
  return `
    <section class="auth-page">
      <div class="auth-wrap">
        <div class="auth-brand">
          <img class="auth-mark" src="${RADISH_ASSET}" alt="" />
          <strong>열무마켓</strong>
          <p>동네 중고거래, 신선하게 🥬</p>
        </div>
        <div class="panel auth-panel">
          <div class="tab-list auth-tabs">
            <button class="btn ${state.authMode !== "signup" ? "btn-soft" : "btn-ghost"}" data-action="auth-mode" data-mode="login">로그인</button>
            <button class="btn ${state.authMode === "signup" ? "btn-soft" : "btn-ghost"}" data-action="auth-mode" data-mode="signup">회원가입</button>
          </div>
          <form class="form-grid single" data-form="${state.authMode === "signup" ? "signup" : "login"}">
            ${
              state.authMode === "signup"
                ? `<div class="field"><label>닉네임</label><input name="nickname" maxlength="30" placeholder="동네에서 쓸 별명" required /></div>`
                : ""
            }
            <div class="field">
              <label>이메일</label>
              <input name="email" type="email" placeholder="you@example.com" required />
            </div>
            <div class="field">
              <label>비밀번호</label>
              <input name="password" type="password" placeholder="••••••••" required />
            </div>
            <button class="btn btn-primary field-full auth-submit" type="submit">${state.authMode === "signup" ? "가입하고 시작하기" : "로그인"}</button>
          </form>
          <p class="auth-help">비회원도 상품 목록·상세는 둘러볼 수 있어요</p>
        </div>
        <p class="hand-note">✏️ 메모: 이메일+비밀번호 / 모든 보호 기능의 진입점</p>
      </div>
    </section>
  `;
}

function homeView() {
  if (state.search.keyword) return searchResultsView();
  const title = state.search.keyword ? `"${escapeHtml(state.search.keyword)}" 검색 결과` : "방금 올라온 물건 🧺";
  return `
    <section class="hero">
      <div>
        <span class="label-sticker">🥬 우리 안전 중고거래</span>
        <h1>필요한 건 가까이에,<br />안 쓰는 건 신선하게.</h1>
        <p>실시간 채팅으로 믿고 거래하는 열무 마켓 🌱</p>
      </div>
      <img class="hero-radish" src="${RADISH_ASSET}" alt="" />
    </section>

    <section>
      <div class="section-head">
        <h2>🔥 실시간 인기 검색어</h2>
      </div>
      <div class="keyword-list">
        ${state.keywords.map((item) => keywordButton(item)).join("") || keywordFallback()}
      </div>
    </section>

    <section class="home-layout">
      <aside class="category-rail">
        <div class="category-title">카테고리</div>
        <div class="category-list">
          <button class="btn ${state.selectedCategoryId ? "btn-ghost" : "btn-soft"}" data-action="category" data-id="">🧺 전체</button>
          ${state.categories.map((category) => `
            <button class="btn ${String(category.categoryId) === String(state.selectedCategoryId) ? "btn-soft" : "btn-ghost"}" data-action="category" data-id="${category.categoryId}">
              ${categoryIcon(category.name)} ${escapeHtml(category.name)}
            </button>
          `).join("")}
        </div>
      </aside>
      <div>
        <div class="section-head">
          <h2>${title}</h2>
          <span class="muted">전체 ${state.productPage?.totalElements ?? state.products.length}개</span>
        </div>
        ${productGrid(state.products)}
      </div>
    </section>
  `;
}

function searchResultsView() {
  const count = state.productPage?.totalElements ?? state.products.length;
  return `
    <section class="search-page">
      <div class="breadcrumb">
        <button class="btn-link" data-action="nav" data-target="#/home">홈</button>
        <span>›</span>
        <strong>검색결과</strong>
      </div>
      <div class="search-layout">
        ${searchFilters()}
        <section class="search-results">
          <div class="section-head search-head">
            <h2>'${escapeHtml(state.search.keyword)}' 검색결과 <span>${count}</span></h2>
            <div class="sort-pills">
              <button class="btn ${state.search.sort === "latest" ? "btn-dark" : "btn-ghost"}" data-action="sort-search" data-sort="latest" type="button">최신순</button>
              <button class="btn ${state.search.sort === "priceAsc" ? "btn-dark" : "btn-ghost"}" data-action="sort-search" data-sort="priceAsc" type="button">낮은가격</button>
              <button class="btn ${state.search.sort === "priceDesc" ? "btn-dark" : "btn-ghost"}" data-action="sort-search" data-sort="priceDesc" type="button">높은가격</button>
            </div>
          </div>
          ${productSearchRows(state.products)}
        </section>
      </div>
    </section>
  `;
}

function searchFilters() {
  return `
    <aside class="filter-rail">
      <form class="filter-card" data-form="filter-products">
        <div class="filter-title">
          <span>필터</span>
          <button class="btn-link danger" data-action="reset-filter" type="button">초기화</button>
        </div>
        <div class="filter-group">
          <strong>카테고리</strong>
          <div class="filter-checks">
            <button class="check-row ${state.selectedCategoryId ? "" : "active"}" data-action="filter-category" data-id="" type="button"><span>✓</span>전체</button>
            ${state.categories.map((category) => `
              <button class="check-row ${String(category.categoryId) === String(state.selectedCategoryId) ? "active" : ""}" data-action="filter-category" data-id="${category.categoryId}" type="button">
                <span>${String(category.categoryId) === String(state.selectedCategoryId) ? "✓" : ""}</span>${escapeHtml(category.name)}
              </button>
            `).join("")}
          </div>
        </div>
        <div class="filter-group">
          <strong>가격대</strong>
          <div class="price-range">
            <input name="minPrice" inputmode="numeric" value="${escapeAttr(state.search.minPrice)}" placeholder="최소" />
            <span>~</span>
            <input name="maxPrice" inputmode="numeric" value="${escapeAttr(state.search.maxPrice)}" placeholder="최대" />
          </div>
          <div class="range-doodle"><i></i><b></b><b></b></div>
        </div>
        <div class="filter-group">
          <strong>거래 상태</strong>
          <select name="status">
            ${option("ON_SALE", "판매중만 보기", state.search.status)}
            ${option("RESERVED", "예약중", state.search.status)}
            ${option("SOLD_OUT", "판매완료", state.search.status)}
          </select>
        </div>
        <input type="hidden" name="sort" value="${escapeAttr(state.search.sort)}" />
        <select class="hidden" name="cached">
          ${option("true", "사용", String(state.search.cached))}
          ${option("false", "기본 검색", String(state.search.cached))}
        </select>
        <button class="btn btn-primary field-full" type="submit">조건 적용</button>
      </form>
      <p class="hand-note left">✏️ 필터는 결과에 바로 반영돼요</p>
    </aside>
  `;
}

function productGrid(products) {
  if (!products.length) {
    return `
      <div class="empty-state">
        <h3>아직 보여줄 물건이 없어요</h3>
        <p>검색 조건을 바꾸거나 첫 상품을 등록해 보세요.</p>
        <button class="btn btn-primary" data-action="nav" data-target="#/sell">판매하기</button>
      </div>
    `;
  }
  return `<div class="product-grid">${products.map(productCard).join("")}</div>`;
}

function productCard(product) {
  const title = productTitle(product);
  const seller = displayNickname(product.sellerNickname);
  return `
    <article class="card product-card">
      <button class="image-frame" data-action="open-product" data-id="${product.productId}">
        ${imageOrPlaceholder(product.thumbnailUrl, title)}
        ${statusBadge(product.status, "badge-float")}
        <span class="wish-dot">${product.wished ? "♥" : "♡"}</span>
      </button>
      <div class="card-body">
        <button class="product-title btn-ghost" data-action="open-product" data-id="${product.productId}">${escapeHtml(title)}</button>
        <p class="price">${price(product.price)}</p>
        <div class="card-meta">
          <span>${escapeHtml(seller)} · ${dateShort(product.createdAt)}</span>
          <span>♡ ${product.wishCount ?? 0} · 💬 ${product.chatRoomCount ?? product.chatCount ?? 0}</span>
        </div>
      </div>
    </article>
  `;
}

function productSearchRows(products) {
  if (!products.length) {
    return `
      <div class="empty-state">
        <h3>검색 결과가 없어요</h3>
        <p>다른 키워드나 가격 조건으로 다시 찾아보세요.</p>
      </div>
    `;
  }
  return `<div class="search-list">${products.map(productSearchRow).join("")}</div>`;
}

function productSearchRow(product) {
  const title = productTitle(product);
  const seller = displayNickname(product.sellerNickname);
  return `
    <article class="card search-row">
      <button class="search-thumb" data-action="open-product" data-id="${product.productId}">
        ${imageOrPlaceholder(product.thumbnailUrl, title)}
      </button>
      <div class="search-row-body">
        <div class="search-title-line">
          ${statusBadge(product.status)}
          <button class="product-title btn-ghost" data-action="open-product" data-id="${product.productId}">${escapeHtml(title)}</button>
        </div>
        <p class="price">${price(product.price)}</p>
        <p class="muted">${escapeHtml(seller)} · ${dateShort(product.createdAt)} · ♡ ${product.wishCount ?? 0}</p>
      </div>
      <button class="wish-dot search-wish" data-action="open-product" data-id="${product.productId}">${product.wished ? "♥" : "♡"}</button>
    </article>
  `;
}

function productDetailView() {
  const product = state.productDetail;
  if (!product) {
    return empty("상품을 찾을 수 없어요", "목록에서 다시 선택해 주세요.", "#/home");
  }
  const image = product.images?.find((item) => item.thumbnail)?.url || product.images?.[0]?.url;
  const seller = product.seller || {};
  const title = productTitle(product);
  const category = cleanDisplayText(product.categoryName || product.category?.name, "상품");
  const description = productDescription(product);
  const sellerName = displayNickname(seller.nickname || product.sellerNickname, "판매자");
  return `
    <section class="detail-page">
      <div class="breadcrumb">
        <button class="btn-link" data-action="nav" data-target="#/home">홈</button>
        <span>›</span>
        <span>${escapeHtml(category)}</span>
        <span>›</span>
        <strong>${escapeHtml(title)}</strong>
      </div>
      <div class="detail-layout">
        <div class="detail-media">
          <div class="product-photo">
            ${imageOrPlaceholder(image, title)}
            ${statusBadge(product.status, "badge-float")}
          </div>
          <div class="thumb-list">
            ${(product.images?.length ? product.images : [{ url: image }, { url: null }, { url: null }, { url: null }]).slice(0, 4).map((item, index) => `
              <button class="thumb ${index === 0 ? "active" : ""}" data-action="open-product" data-id="${product.productId}">
                ${imageOrPlaceholder(item.url, title)}
              </button>
            `).join("")}
          </div>
        </div>
        <div class="detail-info">
          <div class="seller-strip">
            <span class="avatar">${initial(sellerName)}</span>
            <div>
              <strong>${escapeHtml(sellerName)}</strong>
              <div class="muted">⭐ ${seller.averageRating ?? state.sellerProfile?.averageRating ?? 0} · 거래후기 ${state.sellerProfile?.reviewCount ?? 0}개</div>
            </div>
            <button class="btn btn-small btn-ghost" data-action="load-public-reviews" data-id="${seller.userId}">후기</button>
          </div>
          <h1>${escapeHtml(title)}</h1>
          <div class="muted">${escapeHtml(category)} · ${dateShort(product.createdAt)}</div>
          <p class="detail-price">${price(product.price)}</p>
          <div class="description-paper">${nl2br(description)}</div>
          <div class="detail-stats">
            <span>♡ 찜 ${product.wishCount ?? 0}</span>
            <span>💬 채팅 ${product.chatRoomCount ?? product.chatCount ?? 0}</span>
            <span>👁 조회 ${product.viewCount ?? 0}</span>
          </div>
          ${productDetailActions(product)}
          ${ownerProductPanel(product)}
        </div>
      </div>
      <section class="review-section">
        <div class="section-head">
          <h2>판매자의 다른 상품</h2>
          <span class="muted">${state.sellerProducts.length}개</span>
        </div>
        ${sellerProductRows(state.sellerProducts)}
      </section>
      <section class="review-section">
        <h2>판매자 거래후기 ⭐ ${seller.averageRating ?? state.sellerProfile?.averageRating ?? 0}</h2>
        ${reviewList(state.sellerReviews, "public")}
      </section>
    </section>
  `;
}

function sellView() {
  return `
    <section class="sell-page">
      <div class="page-title">
        <h1>상품 등록 ✏️</h1>
        <p>동네 이웃에게 내 물건을 소개해보세요</p>
      </div>
      <div class="panel sell-panel">
        ${state.categories.length ? productForm("create-product") : noCategoryNotice()}
      </div>
    </section>
  `;
}

function productForm(formName, isUpdate = false) {
  return `
    <form class="form-grid" data-form="${formName}">
      ${!isUpdate ? `
        <div class="field field-full">
          <label>상품 이미지 <span class="muted">(최대 10장)</span></label>
          <div class="upload-doodle">
            <label class="upload-add" title="상품 이미지 선택">
              <input class="visually-hidden" name="images" type="file" accept="image/*" multiple data-image-upload />
              <span>＋</span>
              <small data-upload-count>0/10</small>
            </label>
            <span class="upload-slot" data-upload-slot>${imageOrPlaceholder(null, "상품 이미지")}</span>
            <span class="upload-slot" data-upload-slot>${imageOrPlaceholder(null, "상품 이미지")}</span>
            <span class="upload-slot" data-upload-slot>${imageOrPlaceholder(null, "상품 이미지")}</span>
          </div>
        </div>
      ` : ""}
      ${isUpdate ? `<div class="field"><label>상품 ID</label><input name="productId" inputmode="numeric" required /></div>` : ""}
      <div class="field ${isUpdate ? "" : "field-full"}">
        <label>상품명${isUpdate ? "" : " *"}</label>
        <input name="title" maxlength="100" placeholder="예) 아이폰 13 미니 128G" ${isUpdate ? "" : "required"} />
      </div>
      <div class="field">
        <label>가격${isUpdate ? "" : " *"}</label>
        <div class="input-suffix">
          <input name="price" inputmode="numeric" placeholder="0" ${isUpdate ? "" : "required"} />
          <span>원</span>
        </div>
      </div>
      <div class="field">
        <label>카테고리${isUpdate ? "" : " *"}</label>
        <select name="categoryId" ${isUpdate ? "" : "required"}>
          <option value="">카테고리</option>
          ${state.categories.map((category) => option(category.categoryId, category.name)).join("")}
        </select>
      </div>
      <div class="field field-full">
        <label>상품 설명${isUpdate ? "" : " *"}</label>
        <textarea name="description" placeholder="상품 상태, 구매 시기, 거래 방법 등을 적어주세요" ${isUpdate ? "" : "required"}></textarea>
      </div>
      ${!isUpdate ? `<p class="field-full form-note">✏️ 상품명·설명·가격·카테고리는 필수 · 이미지는 최대 10장까지 올릴 수 있어요</p>` : ""}
      ${
        isUpdate
          ? `<button class="btn btn-primary field-full" type="submit">수정 완료</button>`
          : `<div class="form-actions field-full">
              <button class="btn btn-ghost" type="button" data-action="nav" data-target="#/home">취소</button>
              <button class="btn btn-primary" type="submit">등록 완료</button>
            </div>`
      }
    </form>
  `;
}

function noCategoryNotice() {
  return `
    <div class="empty-state">
      <h3>등록할 카테고리가 아직 없어요</h3>
      <p>상품 등록에는 카테고리가 필요합니다. 관리자에게 카테고리 생성을 요청해 주세요.</p>
      ${session.user?.role === "ADMIN" ? `<button class="btn btn-soft" data-action="nav" data-target="#/admin">관리자 화면으로</button>` : ""}
    </div>
  `;
}

function chatView() {
  return `
    <section class="chat-shell">
      <aside class="chat-sidebar">
        <div class="chat-title">채팅 💬</div>
        <div class="chat-guide">상품 상세에서 <strong>채팅하기</strong>를 누르면 대화가 시작돼요.</div>
        <div class="chat-list">
          ${state.chatRooms.map(chatRoomButton).join("") || `<div class="empty-state"><p>아직 채팅방이 없어요.</p></div>`}
        </div>
      </aside>
      <section class="chat-room-panel">
        ${activeChatPanel()}
      </section>
    </section>
  `;
}

function productDetailActions(product) {
  if (isCurrentUserSeller(product)) {
    return `
      <div class="detail-actions owner-actions">
        <button class="btn ${state.ownerProductTool === "edit" ? "btn-soft" : "btn-ghost"}" data-action="owner-product-tool" data-tool="edit">수정</button>
        <button class="btn ${state.ownerProductTool === "images" ? "btn-soft" : "btn-ghost"}" data-action="owner-product-tool" data-tool="images">이미지 관리</button>
        <button class="btn ${state.ownerProductTool === "delete" ? "btn-soft" : "btn-ghost"}" data-action="owner-product-tool" data-tool="delete">삭제</button>
      </div>
      <p class="hand-note left">✏️ 내 상품은 이곳에서 수정·이미지 관리·삭제할 수 있어요</p>
    `;
  }
  const canOrder = product.status === "ON_SALE";
  return `
    <div class="detail-actions">
      <button class="btn wish-action ${product.wished ? "btn-soft" : "btn-ghost"}" data-action="toggle-wish" data-id="${product.productId}">
        ${product.wished ? "♥" : "♡"}
      </button>
      <button class="btn btn-soft" data-action="create-chat" data-id="${product.productId}">💬 채팅하기</button>
      <button class="btn btn-primary" ${canOrder ? `data-action="create-order" data-id="${product.productId}"` : "disabled"}>${canOrder ? "주문하기" : "주문 불가"}</button>
    </div>
    <p class="hand-note left">✏️ 채팅하기는 판매자와 대화 시작 · 주문하기는 거래 의사 확정</p>
  `;
}

function ownerProductPanel(product) {
  if (!isCurrentUserSeller(product) || !state.ownerProductTool) return "";
  const panels = {
    edit: ownerProductEditPanel,
    images: ownerProductImagePanel,
    delete: ownerProductDeletePanel,
  };
  return panels[state.ownerProductTool]?.(product) || "";
}

function ownerProductEditPanel(product) {
  const selectedCategoryId = product.categoryId || product.category?.categoryId;
  return `
    <section class="owner-product-panel">
      <h2>상품 수정</h2>
      <form class="form-grid" data-form="update-product">
        <input type="hidden" name="productId" value="${escapeAttr(product.productId)}" />
        <div class="field field-full">
          <label>상품명</label>
          <input name="title" maxlength="100" value="${escapeAttr(productTitle(product))}" />
        </div>
        <div class="field">
          <label>가격</label>
          <div class="input-suffix">
            <input name="price" inputmode="numeric" value="${escapeAttr(product.price ?? "")}" />
            <span>원</span>
          </div>
        </div>
        <div class="field">
          <label>카테고리</label>
          <select name="categoryId">
            <option value="">카테고리 유지</option>
            ${state.categories.map((category) => option(category.categoryId, category.name, selectedCategoryId)).join("")}
          </select>
        </div>
        <div class="field field-full">
          <label>상품 설명</label>
          <textarea name="description">${escapeHtml(productDescription(product))}</textarea>
        </div>
        <button class="btn btn-primary field-full" type="submit">수정 완료</button>
      </form>
    </section>
  `;
}

function ownerProductImagePanel(product) {
  const images = product.images || [];
  return `
    <section class="owner-product-panel">
      <h2>이미지 관리</h2>
      <form class="form-grid single" data-form="upload-images">
        <input type="hidden" name="productId" value="${escapeAttr(product.productId)}" />
        <div class="field">
          <label>상품 이미지 <span class="muted">(최대 10장, 파일당 5MB)</span></label>
          <div class="upload-doodle compact">
            <label class="upload-add" title="상품 이미지 선택">
              <input class="visually-hidden" name="images" type="file" accept="image/*" multiple required data-image-upload />
              <span>＋</span>
              <small data-upload-count>0/10</small>
            </label>
            <span class="upload-slot" data-upload-slot>${imageOrPlaceholder(null, "상품 이미지")}</span>
            <span class="upload-slot" data-upload-slot>${imageOrPlaceholder(null, "상품 이미지")}</span>
            <span class="upload-slot" data-upload-slot>${imageOrPlaceholder(null, "상품 이미지")}</span>
          </div>
        </div>
        <button class="btn btn-primary" type="submit">이미지 올리기</button>
      </form>
      <div class="owner-image-list">
        ${images.length ? images.map((image) => ownerProductImageRow(product, image)).join("") : `<div class="empty-state compact-empty"><p>등록된 이미지가 없어요.</p></div>`}
      </div>
    </section>
  `;
}

function ownerProductImageRow(product, image) {
  return `
    <div class="owner-image-row">
      <div class="thumb">${imageOrPlaceholder(image.url, productTitle(product))}</div>
      <div>
        <strong>${image.thumbnail ? "대표 이미지" : "상품 이미지"}</strong>
        <div class="muted">이미지 ${image.imageId}</div>
      </div>
      ${
        image.imageId
          ? `<button class="btn btn-danger btn-small" data-action="delete-product-image" data-product-id="${product.productId}" data-image-id="${image.imageId}">삭제</button>`
          : ""
      }
    </div>
  `;
}

function ownerProductDeletePanel(product) {
  return `
    <section class="owner-product-panel">
      <h2>상품 삭제</h2>
      <form class="form-grid single" data-form="delete-product">
        <input type="hidden" name="productId" value="${escapeAttr(product.productId)}" />
        <p class="form-note">✏️ 삭제한 상품은 일반 목록과 검색 결과에 노출되지 않아요. 진행 중인 거래가 있으면 서버에서 거부됩니다.</p>
        <button class="btn btn-danger" type="submit">상품 삭제</button>
      </form>
    </section>
  `;
}

function chatRoomButton(room) {
  const title = cleanDisplayText(room.productTitle, "상품");
  return `
    <button class="chat-room-btn ${String(room.roomId) === String(state.activeRoomId) ? "active" : ""}" data-action="select-chat-room" data-id="${room.roomId}">
      <strong>${escapeHtml(room.opponentNickname || "이웃")}</strong>
      <div>${escapeHtml(title)}</div>
      <small class="muted">${escapeHtml(room.lastMessage || "아직 메시지가 없어요.")}</small>
    </button>
  `;
}

function activeChatPanel() {
  const room = state.chatRooms.find((item) => String(item.roomId) === String(state.activeRoomId));
  if (!room) {
    return empty("채팅방을 선택해 주세요", "상품 상세에서 채팅을 시작하거나 왼쪽 목록을 선택해 주세요.");
  }
  const title = cleanDisplayText(room.productTitle, "상품");
  return `
    <div class="chat-room-head">
      <div class="chat-product-thumb">${imageOrPlaceholder(room.productThumbnailUrl, title)}</div>
      <div>
        <h2>${escapeHtml(title)}</h2>
        <span class="muted">${escapeHtml(room.opponentNickname || "이웃")}</span>
      </div>
      <button class="btn ${state.stompConnected ? "btn-soft" : "btn-primary"}" data-action="connect-chat">
        ${state.stompConnected ? "연결됨" : "실시간 연결"}
      </button>
    </div>
    <button class="btn btn-ghost btn-small load-more" data-action="load-more-messages">↑ 이전 메시지 더 보기</button>
    <div class="messages">
      ${state.chatMessages.map(messageBubble).join("") || `<div class="empty-state"><p>아직 메시지가 없어요.</p></div>`}
    </div>
    <form class="message-form" data-form="send-message">
      <input name="content" autocomplete="off" placeholder="메시지를 입력하세요" required />
      <button class="btn btn-primary" type="submit">전송</button>
    </form>
  `;
}

function messageBubble(message) {
  const mine = session.user && message.senderId === session.user.userId;
  const failed = message.deliveryStatus === "failed";
  return `
    <div class="message ${mine ? "mine" : "other"} ${failed ? "failed" : ""}">
      <strong>${escapeHtml(message.senderNickname || (mine ? "나" : "이웃"))}</strong>
      <div>${escapeHtml(message.content || "")}</div>
      <small class="${failed ? "message-error" : "muted"}">${failed ? "저장 실패" : dateShort(message.createdAt)}</small>
    </div>
  `;
}

function meView() {
  return myPageShell(state.myPageTab === "wishes" ? "wishes" : "sales");
}

function ordersView() {
  return myPageShell("orders");
}

function reviewsView() {
  return myPageShell("reviews");
}

function refundsView() {
  return myPageShell("refunds");
}

function myPageShell(activeTab) {
  return `
    <section class="me-page mypage-shell">
      ${profileHero()}
      ${profileEditPanel()}
      ${myPageTabs(activeTab)}
      <div class="mypage-content">
        ${myPageContent(activeTab)}
      </div>
    </section>
  `;
}

function profileHero() {
  const profile = state.myProfile || session.user || {};
  const ratingValue = Number(profile.averageRating ?? 0);
  const rating = Number.isFinite(ratingValue) ? ratingValue.toFixed(1) : "0.0";
  const reviewCount = Number(profile.reviewCount ?? 0);
  return `
    <div class="profile-hero mypage-hero">
      <span class="avatar profile-avatar">${initial(profile.nickname || session.user?.nickname || "열")}</span>
      <div>
        <h1>${escapeHtml(profile.nickname || session.user?.nickname || "내 정보")}</h1>
        <p class="muted">${escapeHtml(session.user?.email || "")} · ${escapeHtml(profile.role || session.user?.role || "USER")}</p>
        <div class="profile-rating">
          <span>⭐ ${rating}</span>
          <span>받은후기 ${Number.isFinite(reviewCount) ? reviewCount : 0}개</span>
        </div>
      </div>
      <div class="profile-hero-actions">
        <button class="btn btn-primary" data-action="nav" data-target="#/sell">상품 등록</button>
        <button class="btn ${state.profilePanel === "edit" ? "btn-soft" : "btn-ghost"}" data-action="toggle-profile-panel" data-panel="edit">정보 수정</button>
      </div>
    </div>
  `;
}

function profileEditPanel() {
  if (state.profilePanel !== "edit") return "";
  return `
    <section class="profile-edit-panel">
      <div>
        <h2>내정보 수정</h2>
        <p class="muted">닉네임이나 비밀번호 중 필요한 항목만 바꿀 수 있어요.</p>
      </div>
      <form class="form-grid profile-edit-form" data-form="update-me">
        <div class="field">
          <label>닉네임</label>
          <input name="nickname" maxlength="30" value="${escapeAttr(session.user?.nickname || "")}" />
        </div>
        <div class="field">
          <label>새 비밀번호</label>
          <input name="password" type="password" minlength="8" maxlength="100" autocomplete="new-password" placeholder="8자 이상" />
        </div>
        <div class="action-row field-full">
          <button class="btn btn-primary" type="submit">저장</button>
          <button class="btn btn-ghost" type="button" data-action="toggle-profile-panel" data-panel="edit">닫기</button>
        </div>
      </form>
    </section>
  `;
}

function myPageTabs(activeTab) {
  const tabs = [
    { tab: "sales", label: "판매내역", target: "#/me" },
    { tab: "orders", label: "주문내역", target: "#/orders" },
    { tab: "wishes", label: "찜한상품", target: "#/me" },
    { tab: "reviews", label: "받은후기", target: "#/reviews" },
    { tab: "refunds", label: "환불처리", target: "#/refunds" },
  ];
  return `
    <div class="tab-list page-tabs mypage-tabs">
      ${tabs.map((tab) => `
        <button class="btn ${activeTab === tab.tab ? "btn-dark" : "btn-ghost"}" data-action="mypage-tab" data-tab="${tab.tab}" data-target="${tab.target}">
          ${escapeHtml(tab.label)}
        </button>
      `).join("")}
    </div>
  `;
}

function myPageContent(activeTab) {
  if (activeTab === "orders") return ordersTabView();
  if (activeTab === "wishes") return wishesTabView();
  if (activeTab === "reviews") return reviewsTabView();
  if (activeTab === "refunds") return refundsTabView();
  return salesTabView();
}

function salesTabView() {
  return `
    <section class="view-stack">
      <div class="section-head">
        <h2>내가 올린 상품</h2>
        <button class="btn btn-soft" data-action="nav" data-target="#/sell">새 상품 등록</button>
      </div>
      ${myProductGrid(state.myProducts)}
    </section>
  `;
}

function wishesTabView() {
  return `
    <section class="view-stack">
      <div class="section-head">
        <h2>찜한상품</h2>
        <span class="muted">${state.wishes.length}개</span>
      </div>
      ${wishProductGrid(state.wishes)}
    </section>
  `;
}

function ordersTabView() {
  const mode = state.orderTab === "sales" ? "sales" : "orders";
  const rows = mode === "sales" ? state.mySales : state.myOrders;
  return `
    <section class="view-stack">
      <div class="order-tab-head">
        <div>
          <h2>주문내역</h2>
          <p class="muted">결제, 배송, 구매확정, 후기와 환불 요청은 각 주문 카드에서 처리해요.</p>
        </div>
        <div class="tab-list segment-tabs">
          <button class="btn ${mode === "orders" ? "btn-soft" : "btn-ghost"}" data-action="order-tab" data-tab="orders">구매</button>
          <button class="btn ${mode === "sales" ? "btn-soft" : "btn-ghost"}" data-action="order-tab" data-tab="sales">판매</button>
        </div>
      </div>
      ${orderCards(rows, mode)}
      ${resultPanel()}
    </section>
  `;
}

function reviewsTabView() {
  return `
    <section class="review-dashboard">
      <div class="view-stack">
        <div class="section-head">
          <h2>받은후기</h2>
          <span class="muted">${state.myReviews.length}개</span>
        </div>
        ${reviewCards(state.myReviews, "received")}
      </div>
      <div class="view-stack">
        <div class="section-head">
          <h2>내가 쓴 후기</h2>
          <span class="muted">${state.writtenReviews.length}개</span>
        </div>
        ${reviewCards(state.writtenReviews, "written")}
      </div>
    </section>
  `;
}

function refundsTabView() {
  const pendingSales = state.mySales.filter((row) => ["REFUND_REQUESTED", "DISPUTED"].includes(row.status));
  return `
    <section class="refund-dashboard">
      <div class="view-stack">
        <div class="section-head">
          <div>
            <h2>판매 환불 처리</h2>
            <p class="muted">환불 요청과 분쟁 주문은 판매 흐름과 분리해서 처리해요.</p>
          </div>
          <span class="muted">${pendingSales.length}건 대기</span>
        </div>
        ${refundWorkQueue(pendingSales)}
        ${resultPanel()}
      </div>
    </section>
  `;
}

function refundWorkQueue(rows) {
  if (!rows.length) {
    return `
      <div class="empty-state panel">
        <h3>처리할 환불 주문이 없어요</h3>
        <p>판매 주문이 환불 요청 또는 분쟁 상태가 되면 이곳에 모아 보여요.</p>
      </div>
    `;
  }
  return `
    <div class="refund-work-list">
      ${rows.map((row) => `
        <article class="refund-work-row" data-status="${escapeAttr(row.status || "")}">
          <div>
            ${statusBadge(row.status)}
            ${refundRequestIdBadge(row)}
            <h3>${escapeHtml(cleanDisplayText(row.productTitle, "상품"))}</h3>
            <p class="muted">주문 ${escapeHtml(String(row.orderId))} · 구매자 ${escapeHtml(orderCounterpart(row, "sales"))}</p>
          </div>
          ${refundWorkActions(row)}
        </article>
      `).join("")}
    </div>
  `;
}

function refundWorkActions(row) {
  const refundId = refundRequestIdForOrder(row);
  const orderIdInput = `<input type="hidden" name="orderId" value="${escapeAttr(String(row.orderId))}" />`;
  const refundIdInput = refundId
    ? `<input type="hidden" name="refundId" value="${escapeAttr(String(refundId))}" />`
    : `<div class="field"><label>환불 요청 ID</label><input name="refundId" inputmode="numeric" required placeholder="예: 300" /></div>`;
  if (row.status === "REFUND_REQUESTED") {
    return `
      <form class="refund-row-form" data-form="refund-row-decision" data-refund-id="${escapeAttr(String(refundId || ""))}">
        ${orderIdInput}
        ${refundIdInput}
        <div class="field"><label>거절 사유</label><input name="reason" maxlength="255" placeholder="거절할 때 입력" /></div>
        <div class="action-row">
          <button class="btn btn-primary" name="intent" value="approve" type="submit">승인</button>
          <button class="btn btn-danger" name="intent" value="reject" type="submit">거절</button>
        </div>
      </form>
    `;
  }
  return `
    <form class="refund-row-form" data-form="refund-row-resolve" data-refund-id="${escapeAttr(String(refundId || ""))}">
      ${orderIdInput}
      ${refundIdInput}
      <div class="field"><label>종료 방향</label><select name="resolution" required>${option("REFUND", "구매자 환불")}${option("COMPLETE", "거래 완료")}</select></div>
      <div class="field"><label>종료 사유</label><input name="reason" maxlength="255" /></div>
      <button class="btn btn-primary" type="submit">분쟁 종료</button>
    </form>
  `;
}

function myProductGrid(rows) {
  if (!rows.length) {
    return `
      <div class="empty-state panel">
        <h3>판매 중인 상품이 없어요</h3>
        <p>상품을 등록하면 판매내역에서 한눈에 관리할 수 있어요.</p>
        <button class="btn btn-primary" data-action="nav" data-target="#/sell">상품 등록하기</button>
      </div>
    `;
  }
  return `<div class="product-grid mypage-product-grid">${rows.map(myProductCard).join("")}</div>`;
}

function myProductCard(product) {
  const title = productTitle(product);
  const image = productImageUrl(product);
  return `
    <article class="card product-card my-product-card">
      <button class="image-frame" data-action="open-product" data-id="${product.productId}">
        ${imageOrPlaceholder(image, title)}
        ${statusBadge(product.status, "badge-float")}
      </button>
      <div class="card-body">
        <button class="product-title btn-ghost" data-action="open-product" data-id="${product.productId}">${escapeHtml(title)}</button>
        <p class="price">${price(product.price)}</p>
        <p class="muted">${dateShort(product.createdAt || product.updatedAt)}</p>
        <div class="card-actions">
          <button class="btn btn-small btn-soft" data-action="edit-my-product" data-id="${product.productId}">수정</button>
          <button class="btn btn-small btn-danger" data-action="delete-my-product" data-id="${product.productId}">삭제</button>
        </div>
      </div>
    </article>
  `;
}

function wishProductGrid(rows) {
  if (!rows.length) {
    return `
      <div class="empty-state panel">
        <h3>찜한 상품이 없어요</h3>
        <p>마음에 드는 상품의 하트를 눌러 모아 보세요.</p>
        <button class="btn btn-primary" data-action="nav" data-target="#/home">상품 둘러보기</button>
      </div>
    `;
  }
  return `<div class="product-grid mypage-product-grid">${rows.map(wishProductCard).join("")}</div>`;
}

function wishProductCard(product) {
  const title = productTitle(product);
  const image = productImageUrl(product);
  return `
    <article class="card product-card wish-card">
      <button class="image-frame" data-action="open-product" data-id="${product.productId}">
        ${imageOrPlaceholder(image, title)}
        ${statusBadge(product.status, "badge-float")}
        <span class="wish-dot">♥</span>
      </button>
      <div class="card-body">
        <button class="product-title btn-ghost" data-action="open-product" data-id="${product.productId}">${escapeHtml(title)}</button>
        <p class="price">${price(product.price)}</p>
        <p class="muted">${dateShort(product.wishedAt || product.createdAt || product.updatedAt)}</p>
        <div class="card-actions">
          <button class="btn btn-small btn-ghost" data-action="open-product" data-id="${product.productId}">상세보기</button>
          <button class="btn btn-small btn-danger" data-action="remove-wish" data-id="${product.productId}">찜 취소</button>
        </div>
      </div>
    </article>
  `;
}

function orderCards(rows, mode) {
  if (!rows.length) {
    return `
      <div class="empty-state panel">
        <h3>${mode === "sales" ? "판매 주문이 아직 없어요" : "구매 주문이 아직 없어요"}</h3>
        <p>${mode === "sales" ? "상품이 주문되면 이곳에서 배송과 거래 상태를 관리할 수 있어요." : "상품 상세에서 주문하기를 누르면 거래가 시작돼요."}</p>
      </div>
    `;
  }
  return `<div class="order-card-list">${rows.map((row) => orderCard(row, mode)).join("")}</div>`;
}

function orderCard(row, mode) {
  const product = row.product || {};
  const title = cleanDisplayText(row.productTitle || product.title, "상품");
  const productId = row.productId || product.productId;
  const counterpart = orderCounterpart(row, mode);
  return `
    <article class="order-card" data-status="${escapeAttr(row.status || "")}">
      <button class="order-thumb" ${productId ? `data-action="open-product" data-id="${productId}"` : "disabled"}>
        ${imageOrPlaceholder(productImageUrl(row) || productImageUrl(product), title)}
      </button>
      <div class="order-card-main">
        <div class="order-card-top">
          <div>
            ${statusBadge(row.status)}
            <h3>${escapeHtml(title)}</h3>
          </div>
          <strong class="order-price">${price(row.price ?? row.amount ?? product.price)}</strong>
        </div>
        <div class="order-meta">
          <span>주문일 ${dateShort(row.createdAt)}</span>
          <span>${mode === "sales" ? "구매자" : "판매자"} ${escapeHtml(counterpart)}</span>
        </div>
        ${orderProgress(row.status)}
        ${orderStatusMessage(row.status, mode)}
        ${orderActionRow(row, mode)}
        ${orderInlinePanel(row)}
      </div>
    </article>
  `;
}

function orderActionRow(row, mode) {
  const status = row.status;
  const productId = row.productId || row.product?.productId;
  const orderId = row.orderId;
  const paymentId = paymentIdForOrder(row);
  const buttons = [];

  if (mode === "orders" && status === "CREATED") {
    buttons.push(`<button class="btn btn-primary" data-action="pay-order" data-order-id="${orderId}">결제하기</button>`);
    buttons.push(`<button class="btn btn-danger" data-action="cancel-order" data-order-id="${orderId}">주문취소</button>`);
  }
  if (status === "PAID") {
    if (mode === "orders" && productId) {
      buttons.push(`<button class="btn btn-soft" data-action="create-chat" data-id="${productId}">채팅하기</button>`);
    }
    if (mode === "orders") {
      buttons.push(`<button class="btn btn-danger" data-action="order-panel" data-panel="payment-cancel" data-order-id="${escapeAttr(String(orderId))}" data-payment-id="${escapeAttr(String(paymentId || ""))}">결제취소</button>`);
    }
    if (mode === "sales") {
      buttons.push(`<button class="btn btn-primary" data-action="order-panel" data-panel="shipping" data-order-id="${orderId}">배송등록</button>`);
    }
  }
  if (status === "SHIPPING") {
    buttons.push(`<button class="btn btn-ghost" data-action="order-detail" data-order-id="${orderId}">배송조회</button>`);
    if (mode === "orders") {
      buttons.push(`<button class="btn btn-primary" data-action="confirm-order" data-order-id="${orderId}">구매확정</button>`);
      buttons.push(`<button class="btn btn-danger" data-action="order-panel" data-panel="refund" data-order-id="${orderId}">환불요청</button>`);
    }
  }
  if (status === "COMPLETED") {
    buttons.push(`<button class="btn btn-primary" data-action="order-panel" data-panel="review" data-order-id="${orderId}">후기쓰기</button>`);
  }
  if (["REFUND_REQUESTED", "DISPUTED"].includes(status) && mode === "sales") {
    buttons.push(`<button class="btn btn-soft" data-action="nav" data-target="#/refunds">환불 처리</button>`);
    buttons.push(refundRequestIdBadge(row));
  }

  return buttons.length ? `<div class="order-actions">${buttons.join("")}</div>` : "";
}

function orderInlinePanel(row) {
  if (!state.orderPanel || String(state.orderPanel.orderId) !== String(row.orderId)) return "";
  if (state.orderPanel.type === "shipping") {
    return `
      <form class="inline-panel form-grid single" data-form="shipping-order" data-order-id="${row.orderId}">
        <div class="field"><label>배송 운송장</label><input name="trackingNumber" placeholder="운송장 번호" required /></div>
        <div class="action-row">
          <button class="btn btn-primary" type="submit">배송등록</button>
          <button class="btn btn-ghost" type="button" data-action="close-order-panel">닫기</button>
        </div>
      </form>
    `;
  }
  if (state.orderPanel.type === "refund") {
    return `
      <form class="inline-panel form-grid single" data-form="refund-create" data-order-id="${row.orderId}">
        <div class="field"><label>환불 사유</label><textarea name="reason" maxlength="255" required></textarea></div>
        <div class="action-row">
          <button class="btn btn-primary" type="submit">환불 요청</button>
          <button class="btn btn-ghost" type="button" data-action="close-order-panel">닫기</button>
        </div>
      </form>
    `;
  }
  if (state.orderPanel.type === "payment-cancel") {
    const paymentId = state.orderPanel.paymentId || paymentIdForOrder(row);
    const paymentIdInput = paymentId
      ? `<input type="hidden" name="paymentId" value="${escapeAttr(String(paymentId))}" />`
      : `<div class="field"><label>결제 ID</label><input name="paymentId" inputmode="numeric" required placeholder="예: 200" /></div>`;
    return `
      <form class="inline-panel form-grid single" data-form="payment-cancel" data-order-id="${escapeAttr(String(row.orderId))}" data-payment-id="${escapeAttr(String(paymentId || ""))}">
        ${paymentIdInput}
        <div class="field"><label>취소 사유</label><textarea name="reason" maxlength="255" placeholder="선택 입력"></textarea></div>
        <div class="action-row">
          <button class="btn btn-danger" type="submit">결제취소</button>
          <button class="btn btn-ghost" type="button" data-action="close-order-panel">닫기</button>
        </div>
      </form>
    `;
  }
  if (state.orderPanel.type === "review") {
    return `
      <form class="inline-panel form-grid single" data-form="create-review" data-order-id="${row.orderId}">
        <div class="field"><label>점수</label><select name="score">${[5, 4, 3, 2, 1].map((n) => option(n, `${n}점`)).join("")}</select></div>
        <div class="field"><label>내용</label><textarea name="content" maxlength="255" required></textarea></div>
        <div class="action-row">
          <button class="btn btn-primary" type="submit">후기 등록</button>
          <button class="btn btn-ghost" type="button" data-action="close-order-panel">닫기</button>
        </div>
      </form>
    `;
  }
  return "";
}

function orderProgress(status) {
  const steps = ["결제완료", "배송중", "거래완료", "후기"];
  const indexByStatus = { PAID: 0, SHIPPING: 1, COMPLETED: 2 };
  const currentIndex = indexByStatus[status] ?? -1;
  return `
    <ol class="order-progress" aria-label="거래 진행 단계">
      ${steps.map((step, index) => `<li class="${index <= currentIndex ? "done" : ""}">${escapeHtml(step)}</li>`).join("")}
    </ol>
  `;
}

function orderStatusMessage(status, mode) {
  const messages = {
    CREATED: "결제 전 상태예요. 구매자는 결제를 진행하거나 주문을 취소할 수 있어요.",
    PAID: mode === "sales" ? "결제가 완료됐어요. 배송 증빙을 등록해 주세요." : "결제가 완료됐어요. 배송 전에는 결제취소를 할 수 있어요.",
    SHIPPING: mode === "orders" ? "배송 중이에요. 수령 후 구매확정하거나 문제가 있으면 환불을 요청할 수 있어요." : "배송 중이에요. 구매자의 구매확정을 기다리고 있어요.",
    COMPLETED: "거래가 완료됐어요. 상대방에게 후기를 남길 수 있어요.",
    CANCELED: "취소된 주문이에요.",
    REFUNDED: "환불이 완료된 주문이에요.",
    REFUND_REQUESTED: "환불 요청이 접수됐어요. 처리 결과를 기다려 주세요.",
    DISPUTED: "분쟁 처리 중이에요. 운영 정책에 따라 종료됩니다.",
  };
  return `<p class="order-status-message">${escapeHtml(messages[status] || "주문 상태를 확인해 주세요.")}</p>`;
}

function orderCounterpart(row, mode) {
  const user = mode === "sales" ? row.buyer : row.seller;
  return displayNickname(
    user?.nickname || (mode === "sales" ? row.buyerNickname : row.sellerNickname),
    mode === "sales" ? "구매자" : "판매자"
  );
}

function reviewCards(rows, mode) {
  if (!rows.length) return `<div class="empty-state panel"><p>보여줄 후기가 없어요.</p></div>`;
  return `<div class="review-card-list">${rows.map((row) => reviewCard(row, mode)).join("")}</div>`;
}

function reviewList(rows, mode) {
  if (!rows.length) return `<div class="empty-state"><p>보여줄 후기가 없어요.</p></div>`;
  return `<div class="list">${rows.map((row) => `
    <div class="list-row">
      <span class="avatar">${initial(row.reviewerNickname || row.revieweeNickname || "리")}</span>
      <div>
        <strong>${escapeHtml(row.reviewerNickname || row.revieweeNickname || (mode === "public" ? "이웃" : "거래상대"))}</strong>
        <div>${"★".repeat(row.score || 0)}${"☆".repeat(Math.max(0, 5 - (row.score || 0)))}</div>
        <p>${escapeHtml(row.content || "")}</p>
        <small class="muted">${dateShort(row.createdAt || row.updatedAt)}</small>
      </div>
      <span></span>
    </div>
  `).join("")}</div>`;
}

function reviewCard(row, mode) {
  const name = displayNickname(row.reviewerNickname || row.revieweeNickname, mode === "received" ? "작성자" : "거래상대");
  return `
    <article class="review-card">
      <span class="avatar">${initial(name)}</span>
      <div>
        <div class="review-card-head">
          <strong>${escapeHtml(name)}</strong>
          <span class="review-stars">${"★".repeat(row.score || 0)}${"☆".repeat(Math.max(0, 5 - (row.score || 0)))}</span>
        </div>
        <p>${escapeHtml(row.content || "")}</p>
        <small class="muted">${dateShort(row.createdAt || row.updatedAt)}</small>
        ${mode === "written" ? writtenReviewActions(row) : ""}
      </div>
    </article>
  `;
}

function writtenReviewActions(row) {
  return `
    <div class="card-actions review-actions">
      <button class="btn btn-small btn-soft" data-action="order-panel" data-panel="edit-review" data-order-id="${row.orderId}" data-review-id="${row.reviewId}">수정</button>
      <button class="btn btn-small btn-danger" data-action="delete-review" data-order-id="${row.orderId}" data-review-id="${row.reviewId}">삭제</button>
    </div>
    ${writtenReviewEditPanel(row)}
  `;
}

function writtenReviewEditPanel(row) {
  if (
    !state.orderPanel ||
    state.orderPanel.type !== "edit-review" ||
    String(state.orderPanel.reviewId) !== String(row.reviewId)
  ) {
    return "";
  }
  return `
    <form class="inline-panel form-grid single" data-form="update-review" data-order-id="${row.orderId}" data-review-id="${row.reviewId}">
      <div class="field"><label>점수</label><select name="score"><option value="">유지</option>${[5, 4, 3, 2, 1].map((n) => option(n, `${n}점`)).join("")}</select></div>
      <div class="field"><label>내용</label><textarea name="content" maxlength="255">${escapeHtml(row.content || "")}</textarea></div>
      <div class="action-row">
        <button class="btn btn-primary" type="submit">수정 완료</button>
        <button class="btn btn-ghost" type="button" data-action="close-order-panel">닫기</button>
      </div>
    </form>
  `;
}

function adminView() {
  if (session.user?.role !== "ADMIN") {
    return empty("관리자 계정이 필요해요", "카테고리와 숨김 상품 관리는 관리자만 사용할 수 있어요.", "#/home");
  }
  return `
    <section class="split">
      <div class="view-stack">
        <div class="panel">
          <h1>관리자 🛠</h1>
          <h2>카테고리</h2>
          <div class="list">
            ${state.categories.map((category) => `
              <div class="list-row">
                <span class="avatar">${categoryIcon(category.name)}</span>
                <strong>${escapeHtml(category.name)}</strong>
                <span class="muted">ID ${category.categoryId}</span>
              </div>
            `).join("") || `<div class="empty-state"><p>카테고리가 없어요.</p></div>`}
          </div>
        </div>
        <div class="panel">
          <h2>숨긴 상품</h2>
          ${state.adminHiddenProducts.length ? simpleProductRows(state.adminHiddenProducts) : `<div class="empty-state"><p>숨긴 상품이 없어요.</p></div>`}
        </div>
      </div>
      <aside class="view-stack">
        <div class="panel">
          <h2>카테고리 생성/수정/삭제</h2>
          <form class="form-grid single" data-form="category-admin">
            <div class="field"><label>카테고리 ID</label><input name="categoryId" inputmode="numeric" placeholder="수정/삭제 때 입력" /></div>
            <div class="field"><label>카테고리명</label><input name="name" maxlength="20" /></div>
            <div class="action-row">
              <button class="btn btn-primary" name="intent" value="create" type="submit">생성</button>
              <button class="btn btn-soft" name="intent" value="update" type="submit">수정</button>
              <button class="btn btn-danger" name="intent" value="delete" type="submit">삭제</button>
            </div>
          </form>
        </div>
        <div class="panel">
          <h2>상품 숨김 변경</h2>
          <form class="form-grid single" data-form="hidden-admin">
            <div class="field"><label>상품 ID</label><input name="productId" inputmode="numeric" required /></div>
            <div class="field"><label>숨김 여부</label><select name="hidden">${option("true", "숨김")}${option("false", "공개")}</select></div>
            <button class="btn btn-primary" type="submit">변경</button>
          </form>
        </div>
      </aside>
    </section>
  `;
}

function resultPanel() {
  if (!state.lastResult) return "";
  return `
    <div class="panel">
      <h2>최근 처리 결과</h2>
      <p>${escapeHtml(state.lastResult.title)}</p>
      <div class="action-row">${Object.entries(state.lastResult.items || {}).map(([key, value]) => `
        <span class="badge">${escapeHtml(key)} ${escapeHtml(String(value ?? "-"))}</span>
      `).join("")}</div>
    </div>
  `;
}

async function loadHome() {
  const [keywords] = await Promise.all([
    api.search.popularKeywords(6).catch(() => ({ keywords: DEMO_KEYWORDS })),
  ]);
  state.keywords = keywords.keywords || [];
  if (state.selectedCategoryId) {
    const page = await api.categories
      .products(state.selectedCategoryId, { page: 0, size: 12, sort: state.search.sort })
      .catch(() => demoProductPage());
    state.productPage = page;
    state.products = page.content || [];
    return;
  }
  if (state.search.keyword) {
    const page = await api.search.products(state.search, state.search.cached).catch(() => demoProductPage(DEMO_PRODUCTS.slice(0, 5)));
    state.productPage = page;
    state.products = page.content || [];
    return;
  }
  const page = await api.products
    .list({ page: 0, size: 12, status: state.search.status, sort: state.search.sort })
    .catch(() => demoProductPage());
  state.productPage = page;
  state.products = page.content || [];
}

async function ensureCategories() {
  const response = await api.categories.list().catch(() => ({ categories: DEMO_CATEGORIES }));
  state.categories = response.categories || [];
}

async function loadProductDetail(id) {
  if (!id) return;
  if (String(state.ownerProductId || "") !== String(id)) {
    state.ownerProductId = id;
    state.ownerProductTool = null;
  }
  const product = await api.products.detail(id).catch(() => demoProductDetail(id));
  state.productDetail = product;
  const sellerId = product.seller?.userId;
  if (!sellerId) {
    state.sellerProfile = null;
    state.sellerReviews = [];
    state.sellerProducts = [];
    return;
  }
  const [profile, reviews, products] = await Promise.all([
    api.users.detail(sellerId).catch(() => null),
    api.users.publicReviews(sellerId, { page: 0, size: 5 }).catch(() => ({ content: [] })),
    api.users
      .products(sellerId, { page: 0, size: 6 })
      .catch(() => demoProductPage(DEMO_PRODUCTS.filter((item) => String(item.productId) !== String(product.productId)).slice(0, 4))),
  ]);
  state.sellerProfile = profile;
  state.sellerReviews = reviews.content || [];
  state.sellerProducts = (products.content || [])
    .filter((item) => String(item.productId) !== String(product.productId))
    .slice(0, 4);
}

function demoProductPage(products = DEMO_PRODUCTS) {
  return {
    content: products,
    totalElements: products.length,
    totalPages: 1,
    page: 0,
    size: products.length,
  };
}

function demoProductDetail(id) {
  const product = DEMO_PRODUCTS.find((item) => String(item.productId) === String(id)) || DEMO_PRODUCTS[0];
  return {
    ...product,
    category: { name: product.categoryName },
    seller: {
      userId: 1,
      nickname: product.sellerNickname,
      averageRating: 4.8,
      reviewCount: 32,
    },
    images: [],
  };
}

async function loadMyProducts() {
  if (!session.user) return;
  const response = await api.users.myProducts({ page: 0, size: 20 }).catch(() => ({ content: [] }));
  state.myProducts = response.content || [];
}

async function loadMyProfile() {
  const userId = session.user?.userId;
  if (!userId) return null;
  const profile = await api.users.detail(userId).catch(() => null);
  if (!profile) return null;
  state.myProfile = profile;
  session.user = {
    ...session.user,
    nickname: profile.nickname ?? session.user.nickname,
    role: profile.role ?? session.user.role,
    averageRating: profile.averageRating,
    reviewCount: profile.reviewCount,
  };
  return profile;
}

async function loadMyPage() {
  if (!session.user) return;
  const [, products, wishes] = await Promise.all([
    loadMyProfile(),
    api.users.myProducts({ page: 0, size: 12 }).catch(() => ({ content: [] })),
    api.users.wishes({ page: 0, size: 12 }).catch(() => ({ content: [] })),
  ]);
  state.myProducts = products.content || [];
  state.wishes = wishes.content || [];
}

async function loadOrders() {
  if (!session.user) return;
  state.orderTab = state.orderTab || "orders";
  const [, orders, sales] = await Promise.all([
    loadMyProfile(),
    api.users.myOrders({ page: 0, size: 20 }).catch(() => ({ content: [] })),
    api.users.mySales({ page: 0, size: 20 }).catch(() => ({ content: [] })),
  ]);
  state.myOrders = orders.content || [];
  state.mySales = sales.content || [];
  state.myOrders.forEach((row) => rememberPaymentId(row.orderId, row.paymentId || row.payment?.paymentId));
  state.mySales.forEach((row) => rememberRefundRequestId(row.orderId, row.refundRequestId));
}

async function loadReviews() {
  if (!session.user) return;
  const [, received, written] = await Promise.all([
    loadMyProfile(),
    api.users.myReviews("received", { page: 0, size: 20 }).catch(() => ({ content: [] })),
    api.users.myReviews("written", { page: 0, size: 20 }).catch(() => ({ content: [] })),
  ]);
  state.myReviews = received.content || [];
  state.writtenReviews = written.content || [];
}

async function loadChatRooms() {
  if (!session.user) return;
  const response = await api.chat.rooms({ page: 0, size: 30 }).catch(() => ({ content: [] }));
  state.chatRooms = response.content || [];
  if (!state.activeRoomId && state.chatRooms.length) {
    setActiveChatRoom(state.chatRooms[0].roomId);
  }
  await syncChatRoomSubscriptions();
  if (state.activeRoomId) {
    await loadMessages(undefined, state.activeRoomId);
  }
}

async function loadMessages(beforeMessageId, roomId = state.activeRoomId) {
  if (!roomId) return;
  const response = await api.chat.messages(roomId, { beforeMessageId, size: 30 });
  const messages = response.messages || [];
  const currentMessages = getCachedChatMessages(roomId);
  const mergedMessages = mergeChatMessages(messages, currentMessages);
  setCachedChatMessages(roomId, mergedMessages);
  if (isActiveChatRoom(roomId)) {
    state.chatMessages = mergedMessages;
  }
}

async function loadAdmin() {
  if (session.user?.role !== "ADMIN") return;
  const response = await api.admin.hiddenProducts({ page: 0, size: 20 }).catch(() => ({ content: [] }));
  state.adminHiddenProducts = response.content || [];
}

async function handleClick(event) {
  const target = event.target.closest("[data-action]");
  if (!target) return;
  const action = target.dataset.action;
  if (action === "nav") {
    state.menuOpen = false;
    navigate(target.dataset.target);
  }
  if (action === "mypage-tab") {
    state.menuOpen = false;
    state.myPageTab = target.dataset.tab || "sales";
    const nextTarget = target.dataset.target || "#/me";
    if (window.location.hash === nextTarget) {
      await routeChanged();
    } else {
      navigate(nextTarget);
    }
  }
  if (action === "toggle-menu") {
    state.menuOpen = !state.menuOpen;
    render();
  }
  if (action === "auth-mode") {
    state.authMode = target.dataset.mode;
    render();
  }
  if (action === "logout") await runAction("로그아웃했어요.", () => api.auth.logout(), () => navigate("#/home"));
  if (action === "open-product") navigate(`#/product/${target.dataset.id}`);
  if (action === "toggle-profile-panel") {
    state.profilePanel = state.profilePanel === target.dataset.panel ? null : target.dataset.panel;
    render();
  }
  if (action === "category") {
    state.selectedCategoryId = target.dataset.id || null;
    state.search.keyword = "";
    await routeChanged();
  }
  if (action === "filter-category") {
    state.selectedCategoryId = target.dataset.id || null;
    await routeChanged();
  }
  if (action === "sort-search") {
    state.search.sort = target.dataset.sort || "latest";
    await routeChanged();
  }
  if (action === "reset-filter") {
    state.selectedCategoryId = null;
    state.search = { ...state.search, minPrice: "", maxPrice: "", status: "ON_SALE", sort: "latest", cached: true };
    await routeChanged();
  }
  if (action === "keyword") {
    state.selectedCategoryId = null;
    state.search.keyword = target.dataset.keyword;
    await routeChanged();
  }
  if (action === "toggle-wish") await toggleWish(target.dataset.id);
  if (action === "remove-wish") await removeWish(target.dataset.id);
  if (action === "create-chat") await createChat(target.dataset.id);
  if (action === "create-order") await createOrder(target.dataset.id);
  if (action === "edit-my-product") {
    state.ownerProductId = target.dataset.id;
    state.ownerProductTool = "edit";
    navigate(`#/product/${target.dataset.id}`);
  }
  if (action === "delete-my-product") await deleteMyProduct(target.dataset.id);
  if (action === "pay-order") await payOrder(target.dataset.orderId);
  if (action === "cancel-order") await cancelOrder(target.dataset.orderId);
  if (action === "confirm-order") await confirmOrder(target.dataset.orderId);
  if (action === "order-detail") await showOrderDetail(target.dataset.orderId);
  if (action === "order-panel") {
    state.orderPanel = {
      type: target.dataset.panel,
      orderId: target.dataset.orderId,
      paymentId: target.dataset.paymentId || null,
      reviewId: target.dataset.reviewId || null,
    };
    render();
  }
  if (action === "close-order-panel") {
    state.orderPanel = null;
    render();
  }
  if (action === "delete-review") await deleteReview(target.dataset.orderId, target.dataset.reviewId);
  if (action === "select-chat-room") {
    setActiveChatRoom(target.dataset.id);
    await routeChanged();
  }
  if (action === "connect-chat") await connectChat().catch(handleError);
  if (action === "load-more-messages") await loadMoreMessages();
  if (action === "owner-product-tool") {
    state.ownerProductTool = target.dataset.tool;
    render();
  }
  if (action === "delete-product-image") {
    await submitDeleteImage({ productId: target.dataset.productId, imageId: target.dataset.imageId });
  }
  if (action === "order-tab") {
    state.orderTab = target.dataset.tab;
    state.orderPanel = null;
    render();
  }
  if (action === "review-status") {
    state.myReviewStatus = target.dataset.status;
    await routeChanged();
  }
  if (action === "load-public-reviews") await loadPublicReviews(target.dataset.id);
}

function handleChange(event) {
  const input = event.target.closest("[data-image-upload]");
  if (!input) return;
  const form = input.closest("form");
  const files = rawSelectedImageFiles(form);
  const errors = validateProductImageFiles(files);
  if (errors.length) toast(errors[0], "error");
  const previewFiles = selectedImageFiles(form);
  form.querySelector("[data-upload-count]").textContent = `${Math.min(files.length, MAX_PRODUCT_IMAGE_COUNT)}/${MAX_PRODUCT_IMAGE_COUNT}`;
  form.querySelectorAll("[data-upload-slot]").forEach((slot, index) => {
    const file = previewFiles[index];
    slot.classList.toggle("filled", Boolean(file));
    slot.title = file?.name || "상품 이미지";
  });
}

async function handleSubmit(event) {
  const form = event.target.closest("form[data-form]");
  if (!form) return;
  event.preventDefault();
  const data = formValues(form);
  const formName = form.dataset.form;
  if (formName === "search") {
    state.selectedCategoryId = null;
    state.search.keyword = data.keyword || "";
    navigate("#/home");
    await routeChanged();
  }
  if (formName === "filter-products") await filterProducts(data);
  if (formName === "signup") await submitSignup(data);
  if (formName === "login") await submitLogin(data);
  if (formName === "create-product") await submitCreateProduct(form);
  if (formName === "update-product") await submitUpdateProduct(data);
  if (formName === "delete-product") await submitDeleteProduct(data);
  if (formName === "upload-images") await submitUploadImages(form);
  if (formName === "delete-image") await submitDeleteImage(data);
  if (formName === "send-message") await sendChatMessage(data.content);
  if (formName === "update-me") await submitUpdateMe(data);
  if (formName === "create-order") await createOrder(data.productId);
  if (formName === "order-action") await submitOrderAction(data, submitterValue(event));
  if (formName === "shipping-order") await submitShippingOrder(form, data);
  if (formName === "payment-cancel") await submitPaymentCancel(form, data);
  if (formName === "payment-create") await submitPaymentCreate(data);
  if (formName === "payment-action") await submitPaymentAction(data, submitterValue(event));
  if (formName === "create-review") await submitCreateReview(form, data);
  if (formName === "update-review") await submitUpdateReview(form, data, submitterValue(event));
  if (formName === "public-reviews") await loadPublicReviews(data.userId);
  if (formName === "refund-create") await submitRefundCreate(form, data);
  if (formName === "refund-decision") await submitRefundDecision(data, submitterValue(event));
  if (formName === "refund-resolve") await submitRefundResolve(data);
  if (formName === "refund-row-decision") await submitRefundDecision(data, submitterValue(event));
  if (formName === "refund-row-resolve") await submitRefundResolve(data);
  if (formName === "category-admin") await submitCategoryAdmin(data, submitterValue(event));
  if (formName === "hidden-admin") await submitHiddenAdmin(data);
}

async function filterProducts(data) {
  state.search = {
    ...state.search,
    minPrice: data.minPrice,
    maxPrice: data.maxPrice,
    status: data.status || "ON_SALE",
    sort: data.sort || "latest",
    cached: data.cached === "true",
  };
  await routeChanged();
}

async function submitSignup(data) {
  await runAction("회원가입이 완료됐어요. 로그인해 주세요.", () => api.auth.signup(data), () => {
    state.authMode = "login";
    render();
  });
}

async function submitLogin(data) {
  await runAction("로그인했어요.", () => api.auth.login(data), () => navigate("#/home"));
}

async function submitCreateProduct(form) {
  const data = formValues(form);
  const imageFiles = rawSelectedImageFiles(form);
  const errors = validateProductImageFiles(imageFiles);
  if (errors.length) {
    toast(errors[0], "error");
    return;
  }

  try {
    const product = await api.products.create(productPayload(data));
    if (!imageFiles.length) {
      toast("상품을 등록했어요.", "success");
      navigate(`#/product/${product.productId}`);
      return;
    }

    try {
      await api.products.uploadImages(product.productId, imageFiles);
      toast("상품과 이미지를 등록했어요.", "success");
    } catch (error) {
      toast(productImageUploadFailureMessage(error), "error");
    }
    navigate(`#/product/${product.productId}`);
  } catch (error) {
    handleError(error);
  }
}

async function submitUpdateProduct(data) {
  const { productId, ...rest } = data;
  await runAction("상품을 수정했어요.", () => api.products.update(productId, compact(productPayload(rest, true))), () => routeChanged());
}

async function submitDeleteProduct(data) {
  await runAction("상품을 삭제했어요.", () => api.products.remove(data.productId), () => navigate("#/me"));
}

async function submitUploadImages(form) {
  const formData = new FormData(form);
  const imageFiles = rawSelectedImageFiles(form);
  const errors = validateProductImageFiles(imageFiles, { requireFiles: true });
  if (errors.length) {
    toast(errors[0], "error");
    return;
  }
  await runAction("이미지를 올렸어요.", () => api.products.uploadImages(formData.get("productId"), imageFiles), () => routeChanged());
}

async function submitDeleteImage(data) {
  await runAction("이미지를 삭제했어요.", () => api.products.deleteImage(data.productId, data.imageId), () => routeChanged());
}

async function toggleWish(productId) {
  if (!session.user) {
    navigate("#/auth");
    return;
  }
  const product = state.productDetail;
  const action = product?.wished ? api.wishes.remove(productId) : api.wishes.add(productId);
  await runAction(product?.wished ? "찜을 취소했어요." : "찜했어요.", () => action, () => routeChanged());
}

async function createChat(productId) {
  if (!session.user) {
    navigate("#/auth");
    return;
  }
  await runAction("채팅방을 열었어요.", () => api.chat.createRoom(productId), (room) => {
    setActiveChatRoom(room.roomId);
    navigate("#/chat");
  });
}

async function createOrder(productId) {
  if (!session.user) {
    navigate("#/auth");
    return;
  }
  await runAction("주문이 생성됐어요.", () => api.orders.create(productId), (order) => {
    state.lastResult = resultFrom("주문 생성", order);
    navigate("#/orders");
  });
}

async function connectChat({ renderAfter = true } = {}) {
  clearChatReconnectTimer();
  if (!state.stomp?.isOpen()) {
    state.chatSubscriptions = emptyChatSubscriptions();
    state.stomp = new StompClient({
      token: session.token,
      onStatus: toast,
      onError: (error) => {
        if (state.stompConnected) toast(error.message, "error");
      },
      onClose: handleChatConnectionClosed,
      onMessage: handleChatFrame,
    });
    await state.stomp.connect();
    state.stompConnected = true;
  }
  const ready = await syncChatRoomSubscriptions();
  if (state.activeRoomId && !ready) {
    throw new Error(CHAT_SUBSCRIPTION_NOT_READY_MESSAGE);
  }
  if (!state.activeRoomId || ready) {
    state.chatReconnectAttempts = 0;
  }
  if (renderAfter) render();
  return ready;
}

function handleChatConnectionClosed(client) {
  if (client && state.stomp && client !== state.stomp) return;
  state.stompConnected = false;
  state.chatSubscriptions = emptyChatSubscriptions();
  if (state.route.name !== "chat") return;
  render();
  scheduleChatReconnect();
}

function scheduleChatReconnect() {
  if (state.chatReconnectTimer || state.route.name !== "chat" || !session.token || !state.activeRoomId) return;
  const delayMs = Math.min(1000 * 2 ** state.chatReconnectAttempts, 10000);
  state.chatReconnectTimer = window.setTimeout(async () => {
    state.chatReconnectTimer = null;
    state.chatReconnectAttempts += 1;
    try {
      await refreshChatSessionForReconnect();
      await connectChat();
    } catch (error) {
      if (!session.token) {
        handleChatReconnectExpired(error);
        return;
      }
      scheduleChatReconnect();
    }
  }, delayMs);
}

async function refreshChatSessionForReconnect() {
  const refreshed = await api.auth.refresh();
  if (!refreshed) {
    throw new Error("로그인이 만료됐어요. 다시 로그인해 주세요.");
  }
}

function handleChatReconnectExpired(error) {
  clearChatReconnect();
  state.stomp = null;
  state.stompConnected = false;
  state.chatSubscriptions = emptyChatSubscriptions();
  if (state.route.name !== "chat") return;
  toast(error?.message || "로그인이 만료됐어요. 다시 로그인해 주세요.", "error");
  render();
}

function clearChatReconnect() {
  clearChatReconnectTimer();
  state.chatReconnectAttempts = 0;
}

function clearChatReconnectTimer() {
  if (!state.chatReconnectTimer) return;
  window.clearTimeout(state.chatReconnectTimer);
  state.chatReconnectTimer = null;
}

async function sendChatMessage(content) {
  if (!state.activeRoomId) {
    toast("채팅방을 선택해 주세요.", "error");
    return;
  }
  try {
    const ready = await connectChat({ renderAfter: false });
    if (!ready) {
      throw new Error(CHAT_SUBSCRIPTION_NOT_READY_MESSAGE);
    }
    state.stomp.send(`/pub/chat-rooms/${state.activeRoomId}/message`, { content });
  } catch (error) {
    handleError(error);
  }
}

async function syncChatRoomSubscriptions() {
  if (!state.stomp?.isOpen()) return false;
  if (!state.chatSubscriptions.userErrors) {
    state.chatSubscriptions.userErrors = state.stomp.subscribe("/user/queue/errors");
  }

  const roomId = state.activeRoomId ? String(state.activeRoomId) : null;
  const subscriptions = state.chatSubscriptions;
  if (!roomId || (subscriptions.roomId && subscriptions.roomId !== roomId)) {
    unsubscribeChatRoom();
  }
  if (!roomId || (subscriptions.roomId === roomId && subscriptions.messages && subscriptions.errors)) {
    const ready = subscriptions.ready ? await subscriptions.ready : true;
    if (!ready) {
      unsubscribeChatRoom();
      return false;
    }
    return Boolean(roomId);
  }

  unsubscribeChatRoom();
  state.chatSubscriptions.roomId = roomId;
  const messageSubscriptionId = state.stomp.subscribe(`/sub/chat-rooms/${roomId}`);
  const errorSubscriptionId = state.stomp.subscribe(`/sub/chat-rooms/${roomId}/errors`);
  if (!messageSubscriptionId || !errorSubscriptionId) {
    unsubscribeChatRoom();
    return false;
  }
  state.chatSubscriptions.messages = messageSubscriptionId;
  state.chatSubscriptions.errors = errorSubscriptionId;
  state.chatSubscriptions.ready = Promise.resolve(true);
  const ready = await state.chatSubscriptions.ready;
  if (state.chatSubscriptions.roomId !== roomId) return false;
  if (!ready) {
    unsubscribeChatRoom();
    return false;
  }
  return true;
}

function setActiveChatRoom(roomId) {
  if (state.activeRoomId && String(state.activeRoomId) === String(roomId)) return;
  cacheActiveChatMessages();
  state.activeRoomId = roomId;
  state.chatMessages = getCachedChatMessages(roomId);
  void syncChatRoomSubscriptions().catch(handleError);
}

function isActiveChatRoom(roomId) {
  return Boolean(state.activeRoomId) && String(state.activeRoomId) === String(roomId);
}

function unsubscribeChatRoom() {
  const { messages, errors } = state.chatSubscriptions;
  state.stomp?.unsubscribe(messages);
  state.stomp?.unsubscribe(errors);
  state.chatSubscriptions.roomId = null;
  state.chatSubscriptions.messages = null;
  state.chatSubscriptions.errors = null;
  state.chatSubscriptions.ready = null;
}

function handleChatFrame(message, headers) {
  const destination = headers.destination || "";
  if (message.code === "CHAT_MESSAGE_SAVE_FAILED" && message.acceptedMessageId) {
    handleChatSaveFailure(message);
    return;
  }
  if (destination.includes("/queue/errors")) {
    toast(message.message || message.code || "채팅 오류가 발생했어요.", "error");
    return;
  }
  if (!message.roomId) return;
  const mergedMessages = mergeCachedChatMessages(message.roomId, [message]);
  if (belongsToActiveRoom(message)) {
    state.chatMessages = mergedMessages;
    render();
  }
}

function handleChatSaveFailure(error) {
  const acceptedMessageId = String(error.acceptedMessageId);
  if (state.handledChatSaveFailures.has(acceptedMessageId)) return;
  state.handledChatSaveFailures.add(acceptedMessageId);

  let changed = false;
  getFailureCandidateRoomIds(error.roomId).forEach((roomId) => {
    const messages = getCachedChatMessages(roomId);
    const failedMessages = messages.map((message) => {
      if (String(message.acceptedMessageId) !== acceptedMessageId) return message;
      if (error.roomId && String(message.roomId) !== String(error.roomId)) return message;
      changed = true;
      return { ...message, deliveryStatus: "failed" };
    });
    setCachedChatMessages(roomId, failedMessages);
    if (isActiveChatRoom(roomId)) {
      state.chatMessages = failedMessages;
    }
  });
  toast(error.message || "채팅 메시지 저장에 실패했어요.", "error");
  if (changed) render();
}

function belongsToActiveRoom(message) {
  return state.activeRoomId && String(message.roomId) === String(state.activeRoomId);
}

function withChatDeliveryStatus(message) {
  if (message.acceptedMessageId && state.handledChatSaveFailures.has(String(message.acceptedMessageId))) {
    return { ...message, deliveryStatus: "failed" };
  }
  return message;
}

function cacheActiveChatMessages() {
  if (!state.activeRoomId) return;
  setCachedChatMessages(state.activeRoomId, state.chatMessages);
}

function getCachedChatMessages(roomId) {
  return state.chatMessageCache.get(String(roomId)) || [];
}

function setCachedChatMessages(roomId, messages) {
  state.chatMessageCache.set(String(roomId), sortChatMessages(messages.map(withChatDeliveryStatus)));
}

function mergeCachedChatMessages(roomId, messages) {
  const mergedMessages = mergeChatMessages(getCachedChatMessages(roomId), messages);
  setCachedChatMessages(roomId, mergedMessages);
  return getCachedChatMessages(roomId);
}

function getFailureCandidateRoomIds(roomId) {
  const roomIds = new Set(roomId ? [String(roomId)] : []);
  state.chatMessageCache.forEach((_, cachedRoomId) => roomIds.add(cachedRoomId));
  if (state.activeRoomId) roomIds.add(String(state.activeRoomId));
  return [...roomIds];
}

function mergeChatMessages(firstMessages, secondMessages) {
  return sortChatMessages(
    [...firstMessages, ...secondMessages].reduce((merged, message) => {
      const normalized = withChatDeliveryStatus(message);
      const existingIndex = merged.findIndex((current) => isSameChatMessage(current, normalized));
      if (existingIndex === -1) {
        merged.push(normalized);
        return merged;
      }
      merged[existingIndex] = mergeChatMessage(merged[existingIndex], normalized);
      return merged;
    }, [])
  );
}

function mergeChatMessage(existing, incoming) {
  const merged = { ...existing };
  Object.entries(incoming).forEach(([key, value]) => {
    if (value !== null && value !== undefined) {
      merged[key] = value;
      return;
    }
    if (!(key in merged)) {
      merged[key] = value;
    }
  });
  if (existing.deliveryStatus === "failed" || incoming.deliveryStatus === "failed") {
    merged.deliveryStatus = "failed";
  }
  return withChatDeliveryStatus(merged);
}

function isSameChatMessage(first, second) {
  if (first.acceptedMessageId && second.acceptedMessageId) {
    return String(first.acceptedMessageId) === String(second.acceptedMessageId);
  }
  return Boolean(first.messageId && second.messageId) && String(first.messageId) === String(second.messageId);
}

function sortChatMessages(messages) {
  return [...messages].sort(compareChatMessages);
}

function compareChatMessages(first, second) {
  const firstCreatedAt = Date.parse(first.createdAt || "") || 0;
  const secondCreatedAt = Date.parse(second.createdAt || "") || 0;
  if (firstCreatedAt !== secondCreatedAt) return secondCreatedAt - firstCreatedAt;

  const firstMessageId = Number(first.messageId) || 0;
  const secondMessageId = Number(second.messageId) || 0;
  if (firstMessageId !== secondMessageId) return secondMessageId - firstMessageId;

  return String(second.acceptedMessageId || "").localeCompare(String(first.acceptedMessageId || ""));
}

function findOldestLoadedMessageId(messages) {
  return [...messages].reverse().find((message) => message.messageId)?.messageId;
}

async function loadMoreMessages() {
  const beforeMessageId = findOldestLoadedMessageId(state.chatMessages);
  await runAction("이전 메시지를 불러왔어요.", () => loadMessages(beforeMessageId), () => render());
}

async function submitUpdateMe(data) {
  await runAction("프로필을 수정했어요.", () => api.users.updateMe(compact(data)), (updated) => {
    session.user = { ...session.user, ...updated };
    state.myProfile = {
      ...(state.myProfile || {}),
      userId: updated.userId ?? state.myProfile?.userId,
      nickname: updated.nickname ?? state.myProfile?.nickname,
      role: updated.role ?? state.myProfile?.role,
    };
    state.profilePanel = null;
    routeChanged();
  });
}

async function removeWish(productId) {
  await runAction("찜을 취소했어요.", () => api.wishes.remove(productId), () => routeChanged());
}

async function deleteMyProduct(productId) {
  if (!window.confirm("이 상품을 삭제할까요?")) return;
  await runAction("상품을 삭제했어요.", () => api.products.remove(productId), () => routeChanged());
}

async function payOrder(orderId) {
  const key = paymentKeyForOrder(orderId);
  try {
    const result = await api.payments.create(orderId, { method: "MOCK_CARD", result: "PAID" }, key);
    rememberPaymentFromResult(orderId, result);
    delete state.pendingPaymentKeys[String(orderId)];
    toast("결제가 완료됐어요.", "success");
    state.lastResult = resultFrom("결제 요청", result);
    state.orderPanel = null;
    await routeChanged();
  } catch (error) {
    if (!(error instanceof ApiError) && await recoverPaymentAfterCreateError(orderId, key)) {
      toast("주문 상태를 다시 확인했어요.", "success");
      return;
    }
    handleError(error);
  }
}

async function recoverPaymentAfterCreateError(orderId, key) {
  try {
    const result = await api.payments.create(orderId, { method: "MOCK_CARD", result: "PAID" }, key);
    rememberPaymentFromResult(orderId, result);
    delete state.pendingPaymentKeys[String(orderId)];
    state.lastResult = resultFrom("결제 요청", result);
    state.orderPanel = null;
    await routeChanged();
    return true;
  } catch {
    return refreshOrderAfterPaymentError(orderId);
  }
}

async function refreshOrderAfterPaymentError(orderId) {
  try {
    const order = await api.orders.detail(orderId);
    if (!order?.status || order.status === "CREATED") return false;
    delete state.pendingPaymentKeys[String(orderId)];
    state.lastResult = resultFrom("주문 상태 확인", order);
    await routeChanged();
    return true;
  } catch {
    return false;
  }
}

function rememberPaymentFromResult(orderId, result) {
  rememberPaymentId(result?.orderId || orderId, result?.paymentId);
}

function paymentKeyForOrder(orderId) {
  const key = String(orderId);
  if (!state.pendingPaymentKeys[key]) {
    state.pendingPaymentKeys[key] = randomIdempotencyKey();
  }
  return state.pendingPaymentKeys[key];
}

function randomIdempotencyKey() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
  return `payment-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function cancelOrder(orderId) {
  await runAction("주문을 취소했어요.", () => api.orders.cancel(orderId), (result) => {
    state.lastResult = resultFrom("주문 취소", result);
    routeChanged();
  });
}

async function confirmOrder(orderId) {
  await runAction("구매확정을 완료했어요.", () => api.orders.confirm(orderId), (result) => {
    state.lastResult = resultFrom("구매확정", result);
    routeChanged();
  });
}

async function showOrderDetail(orderId) {
  await runAction("주문 상세를 불러왔어요.", () => api.orders.detail(orderId), (result) => {
    state.lastResult = resultFrom("주문 상세", result);
    render();
  });
}

async function submitShippingOrder(form, data) {
  const orderId = form.dataset.orderId;
  await runAction("배송 정보를 등록했어요.", () => api.orders.shipping(orderId, data.trackingNumber), (result) => {
    state.lastResult = resultFrom("배송 등록", result);
    state.orderPanel = null;
    routeChanged();
  });
}

async function submitOrderAction(data, intent) {
  const actions = {
    detail: () => api.orders.detail(data.orderId),
    cancel: () => api.orders.cancel(data.orderId),
    shipping: () => api.orders.shipping(data.orderId, data.trackingNumber),
    confirm: () => api.orders.confirm(data.orderId),
  };
  await runAction("주문 처리가 완료됐어요.", actions[intent], (result) => {
    state.lastResult = resultFrom("주문 처리", result);
    routeChanged();
  });
}

async function submitPaymentCreate(data) {
  await runAction(
    "결제 요청이 처리됐어요.",
    () => api.payments.create(data.orderId, { method: "MOCK_CARD", result: data.result || "PAID" }, paymentKeyForOrder(data.orderId)),
    (result) => {
      rememberPaymentFromResult(data.orderId, result);
      delete state.pendingPaymentKeys[String(data.orderId)];
      state.lastResult = resultFrom("결제 요청", result);
      routeChanged();
    }
  );
}

async function submitPaymentCancel(form, data) {
  const paymentId = form.dataset.paymentId || data.paymentId;
  const orderId = form.dataset.orderId || data.orderId;
  await runAction("결제를 취소했어요.", () => api.payments.cancel(paymentId, data.reason), (result) => {
    if (orderId) {
      delete state.pendingPaymentKeys[String(orderId)];
      forgetPaymentId(orderId);
    }
    state.lastResult = resultFrom("결제 취소", result);
    state.orderPanel = null;
    routeChanged();
  });
}

async function submitPaymentAction(data, intent) {
  const actions = {
    status: () => api.payments.status(data.paymentId),
    detail: () => api.payments.detail(data.paymentId),
    cancel: () => api.payments.cancel(data.paymentId, data.reason),
  };
  await runAction("결제 처리가 완료됐어요.", actions[intent], (result) => {
    state.lastResult = resultFrom("결제 처리", result);
    routeChanged();
  });
}

async function submitCreateReview(form, data) {
  const orderId = form.dataset.orderId || data.orderId;
  await runAction("후기를 등록했어요.", () => api.reviews.create(orderId, { score: numberOrNull(data.score), content: data.content }), () => {
    state.orderPanel = null;
    routeChanged();
  });
}

async function submitUpdateReview(form, data, intent) {
  const orderId = form.dataset.orderId || data.orderId;
  const reviewId = form.dataset.reviewId || data.reviewId;
  if (intent === "delete") {
    await deleteReview(orderId, reviewId);
    return;
  }
  await runAction(
    "후기를 수정했어요.",
    () => api.reviews.update(orderId, reviewId, compact({ score: numberOrNull(data.score), content: data.content })),
    () => {
      state.orderPanel = null;
      routeChanged();
    }
  );
}

async function deleteReview(orderId, reviewId) {
  if (!window.confirm("이 후기를 삭제할까요?")) return;
  await runAction("후기를 삭제했어요.", () => api.reviews.remove(orderId, reviewId), () => {
    state.orderPanel = null;
    routeChanged();
  });
}

async function loadPublicReviews(userId) {
  await runAction("공개 리뷰를 불러왔어요.", () => api.users.publicReviews(userId, { page: 0, size: 20 }), (response) => {
    state.publicReviews = response.content || [];
    render();
  });
}

async function submitRefundCreate(form, data) {
  const orderId = form.dataset.orderId || data.orderId;
  await runAction("환불 요청을 만들었어요.", () => api.refunds.create(orderId, data.reason), (result) => {
    rememberRefundRequestFromResult(orderId, result);
    state.lastResult = resultFrom("환불 요청", result);
    state.orderPanel = null;
    routeChanged();
  });
}

async function submitRefundDecision(data, intent) {
  const action = intent === "approve" ? () => api.refunds.approve(data.refundId) : () => api.refunds.reject(data.refundId, data.reason);
  await runAction("환불 요청을 처리했어요.", action, (result) => {
    rememberRefundRequestFromResult(data.orderId, result);
    state.lastResult = resultFrom("환불 처리", result);
    routeChanged();
  });
}

async function submitRefundResolve(data) {
  await runAction("분쟁을 종료했어요.", () => api.refunds.resolve(data.refundId, { resolution: data.resolution, reason: data.reason }), (result) => {
    rememberRefundRequestFromResult(data.orderId, result);
    state.lastResult = resultFrom("분쟁 종료", result);
    routeChanged();
  });
}

function rememberRefundRequestFromResult(orderId, result) {
  rememberRefundRequestId(result?.orderId || orderId, result?.refundRequestId);
}

async function submitCategoryAdmin(data, intent) {
  const actions = {
    create: () => api.categories.create({ name: data.name }),
    update: () => api.categories.update(data.categoryId, { name: data.name }),
    delete: () => api.categories.remove(data.categoryId),
  };
  await runAction("카테고리를 처리했어요.", actions[intent], () => routeChanged());
}

async function submitHiddenAdmin(data) {
  await runAction("상품 숨김 상태를 변경했어요.", () => api.admin.updateHidden(data.productId, data.hidden === "true"), () => routeChanged());
}

async function runAction(successMessage, action, after) {
  try {
    const result = await action();
    toast(successMessage, "success");
    await after?.(result);
  } catch (error) {
    handleError(error);
  }
}

function handleError(error) {
  if (error instanceof ApiError) {
    const detail = error.errors?.length ? ` ${error.errors.join(", ")}` : "";
    toast(`${error.message}${detail}`, "error");
    return;
  }
  toast(error.message || "처리 중 오류가 발생했어요.", "error");
}

function toast(message, type = "success") {
  const element = document.createElement("div");
  element.className = `toast ${type}`;
  element.textContent = message;
  toastRoot.appendChild(element);
  window.setTimeout(() => element.remove(), 3600);
}

function formValues(form) {
  const data = {};
  const formData = new FormData(form);
  formData.forEach((value, key) => {
    data[key] = typeof value === "string" ? value.trim() : value;
  });
  return data;
}

function submitterValue(event) {
  return event.submitter?.value || event.submitter?.dataset?.intent;
}

function productPayload(data, partial = false) {
  return {
    title: data.title || (partial ? undefined : ""),
    description: data.description || (partial ? undefined : ""),
    price: data.price ? Number(data.price) : partial ? undefined : null,
    categoryId: data.categoryId ? Number(data.categoryId) : partial ? undefined : null,
  };
}

function rawSelectedImageFiles(form) {
  const formData = new FormData(form);
  return formData
    .getAll("images")
    .filter((file) => file && typeof file === "object" && "size" in file && file.size > 0);
}

function selectedImageFiles(form) {
  return rawSelectedImageFiles(form).slice(0, MAX_PRODUCT_IMAGE_COUNT);
}

function validateProductImageFiles(files, options = {}) {
  if (options.requireFiles && !files.length) {
    return ["업로드할 이미지를 선택해 주세요."];
  }
  if (!files.length) return [];
  if (files.length > MAX_PRODUCT_IMAGE_COUNT) {
    return [`상품 이미지는 최대 ${MAX_PRODUCT_IMAGE_COUNT}장까지 올릴 수 있어요.`];
  }
  const unsupportedFile = files.find((file) => !SUPPORTED_PRODUCT_IMAGE_TYPES.has(String(file.type || "").toLowerCase()));
  if (unsupportedFile) {
    return [`${unsupportedFile.name || "선택한 파일"}은 지원하지 않는 이미지 형식이에요.`];
  }
  const oversizedFile = files.find((file) => file.size > MAX_PRODUCT_IMAGE_SIZE_BYTES);
  if (oversizedFile) {
    return [`${oversizedFile.name || "선택한 파일"}은 ${formatFileSize(MAX_PRODUCT_IMAGE_SIZE_BYTES)}를 넘을 수 없어요.`];
  }
  const totalSize = files.reduce((sum, file) => sum + file.size, 0);
  if (totalSize > MAX_PRODUCT_IMAGE_REQUEST_BYTES) {
    return [`한 번에 올릴 이미지 용량은 총 ${formatFileSize(MAX_PRODUCT_IMAGE_REQUEST_BYTES)}를 넘을 수 없어요.`];
  }
  return [];
}

function formatFileSize(bytes) {
  return `${Math.floor(bytes / 1024 / 1024)}MB`;
}

function productImageUploadFailureMessage(error) {
  const reason = error instanceof ApiError ? error.message : error?.message;
  const suffix = reason ? ` ${reason}` : "";
  return `상품은 등록됐지만 이미지 업로드는 실패했어요.${suffix} 내 상품 상세에서 다시 올려주세요.`;
}

function isCurrentUserSeller(product) {
  const sellerId = product?.seller?.userId ?? product?.sellerId;
  return Boolean(session.user?.userId && sellerId && String(session.user.userId) === String(sellerId));
}

function compact(value) {
  return Object.fromEntries(
    Object.entries(value).filter(([, item]) => item !== undefined && item !== null && item !== "")
  );
}

function resultFrom(title, result) {
  return {
    title,
    items: Object.fromEntries(
      Object.entries(result || {})
        .filter(([key]) => !isInternalIdentifierKey(key))
        .filter(([, value]) => value === null || ["string", "number", "boolean"].includes(typeof value))
        .slice(0, 6)
    ),
  };
}

function isInternalIdentifierKey(key) {
  return /^id$/i.test(key);
}

function productTitle(product) {
  return cleanDisplayText(product?.title, "상품");
}

function productImageUrl(product) {
  if (!product) return null;
  return (
    product.thumbnailUrl ||
    product.imageUrl ||
    product.productThumbnailUrl ||
    product.images?.find?.((item) => item.thumbnail)?.url ||
    product.images?.[0]?.url ||
    null
  );
}

function productDescription(product) {
  const original = product?.description || "";
  if (!original || isNoisyText(original)) {
    return "상품 설명이 준비되지 않았어요.\n채팅으로 상태와 거래 방법을 확인해 주세요.";
  }
  return cleanDisplayText(original, "상품 설명이 준비되지 않았어요.");
}

function cleanDisplayText(value, fallback) {
  const original = String(value || "").trim();
  if (!original) return fallback;
  const syntheticPerfText = /\[PERF\]/i.test(original);

  let text = recoverUtf8Mojibake(original)
    .replace(/\[PERF\]/gi, "")
    .replace(/\b(keyword|price|sequence)\s*=\s*\S+/gi, "")
    .replace(/\s+/g, " ")
    .trim();

  if (isNoisyText(text)) {
    text = text
      .replace(/\?.*$/g, "")
      .replace(/[�]+/g, "")
      .replace(/[ìíîïêëãÃÂ]+[^\s]*/g, "")
      .replace(/\s+\d{4,}$/g, "")
      .trim();
  }

  if (syntheticPerfText) {
    text = text.replace(/\s+\d+$/g, "").trim();
  }

  text = text.replace(/\s+/g, " ").trim();
  return text || fallback;
}

function displayNickname(value, fallback = "이웃") {
  const nickname = cleanDisplayText(value, fallback);
  return /^perfUser\d+$/i.test(nickname) ? "열무 이웃" : nickname;
}

function recoverUtf8Mojibake(value) {
  if (!/[ÃÂìíîïêë]/.test(value)) return value;
  try {
    const bytes = Uint8Array.from(Array.from(value, (char) => char.charCodeAt(0) & 0xff));
    const decoded = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    return readabilityScore(decoded) > readabilityScore(value) ? decoded : value;
  } catch {
    return value;
  }
}

function isNoisyText(value) {
  return /(\[PERF\]|\?|�|Ã|Â|ì|í|î|ï|ê|ë|\bkeyword\s*=|\bprice\s*=|\bsequence\s*=)/i.test(String(value || ""));
}

function readabilityScore(value) {
  const text = String(value || "");
  const korean = (text.match(/[가-힣]/g) || []).length;
  const noisy = (text.match(/[ÃÂìíîïêë�]/g) || []).length;
  return korean * 2 - noisy * 3 + text.length * 0.01;
}

function simpleProductRows(rows) {
  if (!rows.length) return `<div class="empty-state"><p>보여줄 상품이 없어요.</p></div>`;
  return `<div class="list">${rows.map((row) => `
    <div class="list-row">
      <span class="avatar">${categoryIcon(cleanDisplayText(row.title, "상품"))}</span>
      <div>
        <strong>${escapeHtml(cleanDisplayText(row.title, "상품"))}</strong>
        <div class="muted">${price(row.price)} · ${dateShort(row.createdAt || row.wishedAt || row.updatedAt)}</div>
      </div>
      <div class="simple-row-actions">
        ${row.status ? statusBadge(row.status) : ""}
        ${row.productId ? `<button class="btn btn-small btn-ghost" data-action="open-product" data-id="${row.productId}">보기</button>` : ""}
      </div>
    </div>
  `).join("")}</div>`;
}

function sellerProductRows(rows) {
  if (!rows.length) return `<div class="empty-state"><p>판매자의 다른 상품이 없어요.</p></div>`;
  return `<div class="list">${rows.map((row) => `
    <div class="list-row seller-product-row">
      <span class="avatar">${categoryIcon(cleanDisplayText(row.title, "상품"))}</span>
      <div>
        <strong>${escapeHtml(cleanDisplayText(row.title, "상품"))}</strong>
        <div class="muted">${price(row.price)} · ${dateShort(row.createdAt)}</div>
      </div>
      <div class="seller-product-actions">
        ${row.status ? statusBadge(row.status) : ""}
        <button class="btn btn-small btn-ghost" data-action="open-product" data-id="${row.productId}">보기</button>
      </div>
    </div>
  `).join("")}</div>`;
}

function keywordButton(item) {
  return `
    <button class="btn btn-ghost" data-action="keyword" data-keyword="${escapeAttr(item.keyword)}">
      <span class="keyword-rank">${item.rank}</span> ${escapeHtml(item.keyword)}
    </button>
  `;
}

function keywordFallback() {
  return ["아이폰", "책상", "자전거", "에어팟", "캠핑 의자", "패딩"]
    .map((keyword, index) => `<button class="btn btn-ghost" data-action="keyword" data-keyword="${keyword}"><span class="keyword-rank">${index + 1}</span> ${keyword}</button>`)
    .join("");
}

function imageOrPlaceholder(url, alt) {
  if (url) return `<img src="${escapeAttr(url)}" alt="${escapeAttr(alt || "상품 이미지")}" />`;
  return `
    <svg class="image-placeholder" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" stroke-width="2"></rect>
      <circle cx="8.5" cy="10" r="1.8" fill="currentColor"></circle>
      <path d="M5 17l4.5-4.5L13 16l3-3 3 3.5" stroke="currentColor" stroke-width="2" stroke-linecap="round"></path>
    </svg>
  `;
}

function statusBadge(status, className = "") {
  const labels = {
    ON_SALE: "판매중",
    RESERVED: "예약중",
    SOLD_OUT: "판매완료",
    DELETED: "삭제됨",
    CREATED: "결제대기",
    PAID: "결제완료",
    SHIPPING: "배송중",
    COMPLETED: "거래완료",
    CANCELED: "취소됨",
    REFUND_REQUESTED: "환불요청",
    REFUNDED: "환불완료",
    DISPUTED: "분쟁중",
    REQUESTED: "요청됨",
    APPROVED: "승인됨",
    CLOSED: "종료됨",
    FAILED: "실패",
  };
  return `<span class="badge ${className}" data-status="${escapeAttr(status || "")}">${labels[status] || status || "-"}</span>`;
}

function categoryIcon(name) {
  if (!name) return "🧺";
  if (name.includes("디지털") || name.includes("폰") || name.includes("전자")) return "📱";
  if (name.includes("생활") || name.includes("가구")) return "🪑";
  if (name.includes("의류") || name.includes("옷")) return "👕";
  if (name.includes("도서") || name.includes("책")) return "📚";
  if (name.includes("스포츠") || name.includes("레저")) return "⚽";
  return "🧺";
}

function option(value, label, selected) {
  return `<option value="${escapeAttr(value)}" ${String(value) === String(selected) ? "selected" : ""}>${escapeHtml(label)}</option>`;
}

function empty(title, message, target) {
  return `
    <section class="empty-state">
      <h1>${escapeHtml(title)}</h1>
      <p>${escapeHtml(message || "")}</p>
      ${target ? `<button class="btn btn-primary" data-action="nav" data-target="${target}">돌아가기</button>` : ""}
    </section>
  `;
}

function price(value) {
  if (value === undefined || value === null || value === "") return "-";
  return `${Number(value).toLocaleString("ko-KR")}원`;
}

function dateShort(value) {
  if (!value) return "방금";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(date);
}

function initial(value) {
  return escapeHtml(String(value || "열").trim().slice(0, 1) || "열");
}

function nl2br(value) {
  return escapeHtml(value || "").replace(/\n/g, "<br />");
}

function numberOrNull(value) {
  return value === undefined || value === null || value === "" ? null : Number(value);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttr(value) {
  return escapeHtml(value);
}
