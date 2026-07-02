import { api, ApiError, session } from "./api.js";
import { StompClient } from "./stomp-client.js";

const app = document.querySelector("#app");
const toastRoot = document.querySelector("#toast-root");
const RADISH_ASSET = "/assets/radish.svg?v=20260630-layout-radish";

const DEMO_CATEGORIES = [
  { categoryId: 1, name: "디지털기기" },
  { categoryId: 2, name: "생활/가구" },
  { categoryId: 3, name: "의류" },
  { categoryId: 4, name: "도서" },
  { categoryId: 5, name: "스포츠/레저" },
];

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
  myOrders: [],
  mySales: [],
  myReviewStatus: "written",
  myReviews: [],
  publicReviews: [],
  chatRooms: [],
  activeRoomId: null,
  chatMessages: [],
  chatMessageCache: new Map(),
  stomp: null,
  stompConnected: false,
  chatSubscriptions: { userErrors: null, roomId: null, messages: null, errors: null },
  handledChatSaveFailures: new Set(),
  adminHiddenProducts: [],
  selectedOrder: null,
  selectedPayment: null,
  lastResult: null,
};

bootstrap();

async function bootstrap() {
  window.addEventListener("hashchange", routeChanged);
  document.addEventListener("click", handleClick);
  document.addEventListener("change", handleChange);
  document.addEventListener("submit", handleSubmit);
  routeChanged();
}

async function routeChanged() {
  state.route = parseRoute();
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
                    <button class="btn btn-ghost" data-action="nav" data-target="#/reviews">⭐ 리뷰</button>
                    <button class="btn btn-ghost" data-action="nav" data-target="#/refunds">↩ 환불</button>
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
        <span class="label-sticker">🥬 우리 동네 중고거래</span>
        <h1>필요한 건 가까이에,<br />안 쓰는 건 신선하게.</h1>
        <p>실시간 채팅으로 믿고 거래하는 동네 마켓 🌱</p>
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
        <button class="btn btn-ghost" type="button" disabled>내가 등록한 상품입니다</button>
      </div>
      <p class="hand-note left">✏️ 내 상품은 채팅이나 주문을 시작할 수 없어요</p>
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
  return `
    <section class="me-page">
      <div class="profile-hero">
        <span class="avatar profile-avatar">${initial(session.user?.nickname || "열")}</span>
        <div>
          <h1>${escapeHtml(session.user?.nickname || "내 정보")}</h1>
          <p class="muted">${escapeHtml(session.user?.email || "")} · ${escapeHtml(session.user?.role || "USER")} · ⭐ 4.9 · 매너온도 52.3°C 🔥</p>
        </div>
        <button class="btn btn-ghost" data-action="nav" data-target="#/me">프로필 수정</button>
      </div>
      <div class="tab-list page-tabs">
        <button class="btn btn-dark" data-action="nav" data-target="#/me">판매내역</button>
        <button class="btn btn-ghost" data-action="nav" data-target="#/orders">주문내역</button>
        <button class="btn btn-ghost" data-action="nav" data-target="#/reviews">리뷰</button>
        <button class="btn btn-ghost" data-action="nav" data-target="#/refunds">환불</button>
      </div>
      <section class="split">
        <div class="panel">
          <form class="form-grid" data-form="update-me">
            <div class="field"><label>닉네임</label><input name="nickname" maxlength="30" placeholder="새 닉네임" /></div>
            <div class="field"><label>비밀번호</label><input name="password" type="password" placeholder="새 비밀번호" /></div>
            <button class="btn btn-primary field-full" type="submit">프로필 수정</button>
          </form>
        </div>
        <div class="panel">
          <h2>내 판매 상품</h2>
          ${simpleProductRows(state.myProducts)}
        </div>
      </section>
      <section class="split">
        <div class="panel">
          <h2>찜한 상품</h2>
          ${simpleProductRows(state.wishes)}
        </div>
        <div class="panel">
          <h2>바로가기</h2>
          <div class="action-row">
            <button class="btn btn-soft" data-action="nav" data-target="#/orders">거래내역</button>
            <button class="btn btn-soft" data-action="nav" data-target="#/reviews">리뷰</button>
            <button class="btn btn-soft" data-action="nav" data-target="#/refunds">환불</button>
          </div>
        </div>
      </section>
    </section>
  `;
}

function ordersView() {
  return `
    <section class="view-stack">
      <div class="panel">
        <h1>거래내역 📦</h1>
        <div class="action-row">
          <button class="btn ${state.orderTab !== "sales" ? "btn-soft" : "btn-ghost"}" data-action="order-tab" data-tab="orders">구매 주문</button>
          <button class="btn ${state.orderTab === "sales" ? "btn-soft" : "btn-ghost"}" data-action="order-tab" data-tab="sales">판매 주문</button>
        </div>
      </div>
      <section class="split">
        <div class="panel">
          <h2>${state.orderTab === "sales" ? "내 판매 주문" : "내 구매 주문"}</h2>
          ${orderRows(state.orderTab === "sales" ? state.mySales : state.myOrders)}
        </div>
        <aside class="view-stack">
          <div class="panel">
            <h2>주문 만들기</h2>
            <form class="form-grid single" data-form="create-order">
              <div class="field"><label>상품 ID</label><input name="productId" inputmode="numeric" required /></div>
              <button class="btn btn-primary" type="submit">주문하기</button>
            </form>
          </div>
          <div class="panel">
            <h2>주문 처리</h2>
            <form class="form-grid single" data-form="order-action">
              <div class="field"><label>주문 ID</label><input name="orderId" inputmode="numeric" required /></div>
              <div class="field"><label>배송 운송장</label><input name="trackingNumber" placeholder="배송 처리 때 사용" /></div>
              <div class="action-row">
                <button class="btn btn-ghost" name="intent" value="detail" type="submit">상세</button>
                <button class="btn btn-danger" name="intent" value="cancel" type="submit">취소</button>
                <button class="btn btn-soft" name="intent" value="shipping" type="submit">배송등록</button>
                <button class="btn btn-primary" name="intent" value="confirm" type="submit">구매확정</button>
              </div>
            </form>
          </div>
          <div class="panel">
            <h2>결제</h2>
            <form class="form-grid single" data-form="payment-create">
              <div class="field"><label>주문 ID</label><input name="orderId" inputmode="numeric" required /></div>
              <div class="field"><label>멱등성 키</label><input name="idempotencyKey" placeholder="payment-001" /></div>
              <div class="field"><label>결제 결과</label><select name="result">${option("PAID", "성공")}${option("FAILED", "실패")}</select></div>
              <button class="btn btn-primary" type="submit">결제 요청</button>
            </form>
            <hr />
            <form class="form-grid single" data-form="payment-action">
              <div class="field"><label>결제 ID</label><input name="paymentId" inputmode="numeric" required /></div>
              <div class="field"><label>취소 사유</label><input name="reason" /></div>
              <div class="action-row">
                <button class="btn btn-ghost" name="intent" value="status" type="submit">상태조회</button>
                <button class="btn btn-ghost" name="intent" value="detail" type="submit">상세조회</button>
                <button class="btn btn-danger" name="intent" value="cancel" type="submit">결제취소</button>
              </div>
            </form>
          </div>
          ${resultPanel()}
        </aside>
      </section>
    </section>
  `;
}

function orderRows(rows) {
  if (!rows.length) return `<div class="empty-state"><p>거래 내역이 아직 없어요.</p></div>`;
  return `<div class="list">${rows.map((row) => `
    <div class="list-row">
      <span class="avatar">📦</span>
      <div>
        <strong>${escapeHtml(cleanDisplayText(row.productTitle || row.product?.title, "상품"))}</strong>
        <div class="muted">주문 ${row.orderId} · ${price(row.price || row.product?.price)} · ${dateShort(row.createdAt)}</div>
      </div>
      ${statusBadge(row.status)}
    </div>
  `).join("")}</div>`;
}

function reviewsView() {
  return `
    <section class="split">
      <div class="view-stack">
        <div class="panel">
          <h1>리뷰 ⭐</h1>
          <div class="action-row">
            <button class="btn ${state.myReviewStatus === "written" ? "btn-soft" : "btn-ghost"}" data-action="review-status" data-status="written">작성한 리뷰</button>
            <button class="btn ${state.myReviewStatus === "received" ? "btn-soft" : "btn-ghost"}" data-action="review-status" data-status="received">받은 리뷰</button>
          </div>
          ${reviewList(state.myReviews, state.myReviewStatus)}
        </div>
        <div class="panel">
          <h2>공개 리뷰 조회</h2>
          <form class="form-grid" data-form="public-reviews">
            <div class="field"><label>유저 ID</label><input name="userId" inputmode="numeric" required /></div>
            <button class="btn btn-primary" type="submit">조회</button>
          </form>
          ${reviewList(state.publicReviews, "public")}
        </div>
      </div>
      <aside class="view-stack">
        <div class="panel">
          <h2>리뷰 작성</h2>
          <form class="form-grid single" data-form="create-review">
            <div class="field"><label>주문 ID</label><input name="orderId" inputmode="numeric" required /></div>
            <div class="field"><label>점수</label><select name="score">${[5,4,3,2,1].map((n) => option(n, `${n}점`)).join("")}</select></div>
            <div class="field"><label>내용</label><textarea name="content" maxlength="255" required></textarea></div>
            <button class="btn btn-primary" type="submit">리뷰 등록</button>
          </form>
        </div>
        <div class="panel">
          <h2>리뷰 수정/삭제</h2>
          <form class="form-grid single" data-form="update-review">
            <div class="field"><label>주문 ID</label><input name="orderId" inputmode="numeric" required /></div>
            <div class="field"><label>리뷰 ID</label><input name="reviewId" inputmode="numeric" required /></div>
            <div class="field"><label>점수</label><select name="score"><option value="">유지</option>${[5,4,3,2,1].map((n) => option(n, `${n}점`)).join("")}</select></div>
            <div class="field"><label>내용</label><textarea name="content" maxlength="255"></textarea></div>
            <div class="action-row">
              <button class="btn btn-primary" name="intent" value="update" type="submit">수정</button>
              <button class="btn btn-danger" name="intent" value="delete" type="submit">삭제</button>
            </div>
          </form>
        </div>
      </aside>
    </section>
  `;
}

function reviewList(rows, mode) {
  if (!rows.length) return `<div class="empty-state"><p>보여줄 리뷰가 없어요.</p></div>`;
  return `<div class="list">${rows.map((row) => `
    <div class="list-row">
      <span class="avatar">${initial(row.reviewerNickname || row.revieweeNickname || "리")}</span>
      <div>
        <strong>${escapeHtml(row.reviewerNickname || row.revieweeNickname || (mode === "public" ? "이웃" : "거래상대"))}</strong>
        <div>${"★".repeat(row.score || 0)}${"☆".repeat(Math.max(0, 5 - (row.score || 0)))}</div>
        <p>${escapeHtml(row.content || "")}</p>
        <small class="muted">리뷰 ${row.reviewId} ${row.orderId ? `· 주문 ${row.orderId}` : ""} · ${dateShort(row.createdAt)}</small>
      </div>
      <span></span>
    </div>
  `).join("")}</div>`;
}

function refundsView() {
  return `
    <section class="split">
      <div class="panel">
        <h1>환불/분쟁 ↩</h1>
        <p>배송 이후 문제가 생기면 환불 요청을 만들고, 판매자는 승인/거절할 수 있어요. 분쟁은 환불 또는 거래완료로 종료합니다.</p>
        ${resultPanel()}
      </div>
      <aside class="view-stack">
        <div class="panel">
          <h2>환불 요청</h2>
          <form class="form-grid single" data-form="refund-create">
            <div class="field"><label>주문 ID</label><input name="orderId" inputmode="numeric" required /></div>
            <div class="field"><label>사유</label><textarea name="reason" required></textarea></div>
            <button class="btn btn-primary" type="submit">환불 요청</button>
          </form>
        </div>
        <div class="panel">
          <h2>판매자 처리</h2>
          <form class="form-grid single" data-form="refund-decision">
            <div class="field"><label>환불 요청 ID</label><input name="refundId" inputmode="numeric" required /></div>
            <div class="field"><label>거절 사유</label><input name="reason" /></div>
            <div class="action-row">
              <button class="btn btn-primary" name="intent" value="approve" type="submit">승인</button>
              <button class="btn btn-danger" name="intent" value="reject" type="submit">거절</button>
            </div>
          </form>
        </div>
        <div class="panel">
          <h2>분쟁 종료</h2>
          <form class="form-grid single" data-form="refund-resolve">
            <div class="field"><label>환불 요청 ID</label><input name="refundId" inputmode="numeric" required /></div>
            <div class="field"><label>결론</label><select name="resolution">${option("REFUND", "환불")}${option("COMPLETE", "거래완료")}</select></div>
            <div class="field"><label>사유</label><input name="reason" /></div>
            <button class="btn btn-primary" type="submit">분쟁 종료</button>
          </form>
        </div>
      </aside>
    </section>
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

async function loadMyPage() {
  if (!session.user) return;
  const [products, wishes] = await Promise.all([
    api.users.myProducts({ page: 0, size: 12 }).catch(() => ({ content: [] })),
    api.users.wishes({ page: 0, size: 12 }).catch(() => ({ content: [] })),
  ]);
  state.myProducts = products.content || [];
  state.wishes = wishes.content || [];
}

async function loadOrders() {
  if (!session.user) return;
  state.orderTab = state.orderTab || "orders";
  const [orders, sales] = await Promise.all([
    api.users.myOrders({ page: 0, size: 20 }).catch(() => ({ content: [] })),
    api.users.mySales({ page: 0, size: 20 }).catch(() => ({ content: [] })),
  ]);
  state.myOrders = orders.content || [];
  state.mySales = sales.content || [];
}

async function loadReviews() {
  if (!session.user) return;
  const response = await api.users.myReviews(state.myReviewStatus, { page: 0, size: 20 }).catch(() => ({ content: [] }));
  state.myReviews = response.content || [];
}

async function loadChatRooms() {
  if (!session.user) return;
  const response = await api.chat.rooms({ page: 0, size: 30 }).catch(() => ({ content: [] }));
  state.chatRooms = response.content || [];
  if (!state.activeRoomId && state.chatRooms.length) {
    setActiveChatRoom(state.chatRooms[0].roomId);
  }
  syncChatRoomSubscriptions();
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
  if (action === "create-chat") await createChat(target.dataset.id);
  if (action === "create-order") await createOrder(target.dataset.id);
  if (action === "select-chat-room") {
    setActiveChatRoom(target.dataset.id);
    await routeChanged();
  }
  if (action === "connect-chat") await connectChat();
  if (action === "load-more-messages") await loadMoreMessages();
  if (action === "order-tab") {
    state.orderTab = target.dataset.tab;
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
  const files = selectedImageFiles(form);
  form.querySelector("[data-upload-count]").textContent = `${files.length}/10`;
  form.querySelectorAll("[data-upload-slot]").forEach((slot, index) => {
    const file = files[index];
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
  if (formName === "payment-create") await submitPaymentCreate(data);
  if (formName === "payment-action") await submitPaymentAction(data, submitterValue(event));
  if (formName === "create-review") await submitCreateReview(data);
  if (formName === "update-review") await submitUpdateReview(data, submitterValue(event));
  if (formName === "public-reviews") await loadPublicReviews(data.userId);
  if (formName === "refund-create") await submitRefundCreate(data);
  if (formName === "refund-decision") await submitRefundDecision(data, submitterValue(event));
  if (formName === "refund-resolve") await submitRefundResolve(data);
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
  const imageFiles = selectedImageFiles(form);
  await runAction(
    imageFiles.length ? "상품과 이미지를 등록했어요." : "상품을 등록했어요.",
    async () => {
      const product = await api.products.create(productPayload(data));
      if (imageFiles.length) {
        await api.products.uploadImages(product.productId, imageFiles);
      }
      return product;
    },
    (product) => navigate(`#/product/${product.productId}`)
  );
}

async function submitUpdateProduct(data) {
  const { productId, ...rest } = data;
  await runAction("상품을 수정했어요.", () => api.products.update(productId, compact(productPayload(rest, true))), () => routeChanged());
}

async function submitDeleteProduct(data) {
  await runAction("상품을 삭제했어요.", () => api.products.remove(data.productId), () => routeChanged());
}

async function submitUploadImages(form) {
  const formData = new FormData(form);
  await runAction("이미지를 올렸어요.", () => api.products.uploadImages(formData.get("productId"), formData.getAll("images")), () => routeChanged());
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

async function connectChat() {
  if (!state.stomp?.isOpen()) {
    state.chatSubscriptions = { userErrors: null, roomId: null, messages: null, errors: null };
    state.stomp = new StompClient({
      token: session.token,
      onStatus: toast,
      onError: (error) => toast(error.message, "error"),
      onMessage: handleChatFrame,
    });
    await state.stomp.connect();
    state.stompConnected = true;
  }
  syncChatRoomSubscriptions();
  render();
}

async function sendChatMessage(content) {
  await connectChat();
  state.stomp.send(`/pub/chat-rooms/${state.activeRoomId}/message`, { content });
}

function syncChatRoomSubscriptions() {
  if (!state.stomp?.isOpen()) return;
  if (!state.chatSubscriptions.userErrors) {
    state.chatSubscriptions.userErrors = state.stomp.subscribe("/user/queue/errors");
  }

  const roomId = state.activeRoomId ? String(state.activeRoomId) : null;
  const subscriptions = state.chatSubscriptions;
  if (!roomId || (subscriptions.roomId && subscriptions.roomId !== roomId)) {
    unsubscribeChatRoom();
  }
  if (!roomId || (subscriptions.roomId === roomId && subscriptions.messages && subscriptions.errors)) {
    return;
  }

  unsubscribeChatRoom();
  state.chatSubscriptions.roomId = roomId;
  state.chatSubscriptions.messages = state.stomp.subscribe(`/sub/chat-rooms/${roomId}`);
  state.chatSubscriptions.errors = state.stomp.subscribe(`/sub/chat-rooms/${roomId}/errors`);
}

function setActiveChatRoom(roomId) {
  if (state.activeRoomId && String(state.activeRoomId) === String(roomId)) return;
  cacheActiveChatMessages();
  state.activeRoomId = roomId;
  state.chatMessages = getCachedChatMessages(roomId);
  syncChatRoomSubscriptions();
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
    () => api.payments.create(data.orderId, { method: "MOCK_CARD", result: data.result || "PAID" }, data.idempotencyKey),
    (result) => {
      state.lastResult = resultFrom("결제 요청", result);
      routeChanged();
    }
  );
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

async function submitCreateReview(data) {
  await runAction("리뷰를 등록했어요.", () => api.reviews.create(data.orderId, { score: numberOrNull(data.score), content: data.content }), () => routeChanged());
}

async function submitUpdateReview(data, intent) {
  if (intent === "delete") {
    await runAction("리뷰를 삭제했어요.", () => api.reviews.remove(data.orderId, data.reviewId), () => routeChanged());
    return;
  }
  await runAction(
    "리뷰를 수정했어요.",
    () => api.reviews.update(data.orderId, data.reviewId, compact({ score: numberOrNull(data.score), content: data.content })),
    () => routeChanged()
  );
}

async function loadPublicReviews(userId) {
  await runAction("공개 리뷰를 불러왔어요.", () => api.users.publicReviews(userId, { page: 0, size: 20 }), (response) => {
    state.publicReviews = response.content || [];
    render();
  });
}

async function submitRefundCreate(data) {
  await runAction("환불 요청을 만들었어요.", () => api.refunds.create(data.orderId, data.reason), (result) => {
    state.lastResult = resultFrom("환불 요청", result);
    render();
  });
}

async function submitRefundDecision(data, intent) {
  const action = intent === "approve" ? () => api.refunds.approve(data.refundId) : () => api.refunds.reject(data.refundId, data.reason);
  await runAction("환불 요청을 처리했어요.", action, (result) => {
    state.lastResult = resultFrom("환불 처리", result);
    render();
  });
}

async function submitRefundResolve(data) {
  await runAction("분쟁을 종료했어요.", () => api.refunds.resolve(data.refundId, { resolution: data.resolution, reason: data.reason }), (result) => {
    state.lastResult = resultFrom("분쟁 종료", result);
    render();
  });
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

function selectedImageFiles(form) {
  const formData = new FormData(form);
  return formData
    .getAll("images")
    .filter((file) => file && typeof file === "object" && "size" in file && file.size > 0)
    .slice(0, 10);
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
        .filter(([, value]) => value === null || ["string", "number", "boolean"].includes(typeof value))
        .slice(0, 6)
    ),
  };
}

function productTitle(product) {
  return cleanDisplayText(product?.title, "상품");
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
        <div class="muted">ID ${row.productId} · ${price(row.price)} · ${dateShort(row.createdAt || row.wishedAt || row.updatedAt)}</div>
      </div>
      ${row.status ? statusBadge(row.status) : ""}
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
    CREATED: "주문생성",
    PAID: "결제완료",
    SHIPPING: "배송중",
    COMPLETED: "거래완료",
    CANCELED: "취소됨",
    REFUND_REQUESTED: "환불요청",
    REFUNDED: "환불됨",
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
