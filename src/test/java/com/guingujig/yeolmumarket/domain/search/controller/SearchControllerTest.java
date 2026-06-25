package com.guingujig.yeolmumarket.domain.search.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.domain.product.entity.Product;
import com.guingujig.yeolmumarket.domain.product.entity.ProductStatus;
import com.guingujig.yeolmumarket.domain.product.repository.ProductRepository;
import com.guingujig.yeolmumarket.domain.user.entity.User;
import com.guingujig.yeolmumarket.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchControllerTest {

  private final MockMvc mockMvc;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final PasswordEncoder passwordEncoder;

  @Autowired
  SearchControllerTest(
      MockMvc mockMvc,
      UserRepository userRepository,
      ProductRepository productRepository,
      PasswordEncoder passwordEncoder) {
    this.mockMvc = mockMvc;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Test
  void 상품_검색은_숨김_상품과_삭제_상품을_제외한다() throws Exception {
    User seller = saveUser("seller@example.com", "열무판매자");
    Product visibleProduct = saveProduct(seller, "아이패드 미니 6", 450000);
    saveHiddenProduct(seller, "아이패드 숨김 상품", 300000);
    saveProductWithStatus(seller, "아이패드 삭제 상품", 200000, ProductStatus.DELETED);
    saveProduct(seller, "맥북 에어", 900000);

    mockMvc
        .perform(get("/api/search/products").param("keyword", "아이패드"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content", hasSize(1)))
        .andExpect(jsonPath("$.data.content[0].productId").value(visibleProduct.getId()))
        .andExpect(jsonPath("$.data.content[0].title").value("아이패드 미니 6"))
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void 상품_검색_가격_범위가_잘못되면_400으로_응답한다() throws Exception {
    mockMvc
        .perform(get("/api/search/products").param("minPrice", "5000").param("maxPrice", "1000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  private User saveUser(String email, String nickname) {
    return userRepository.save(new User(email, passwordEncoder.encode("Password123!"), nickname));
  }

  private Product saveProduct(User seller, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    return productRepository.saveAndFlush(product);
  }

  private Product saveHiddenProduct(User seller, String title, Integer price) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    product.changeHidden(true);
    return productRepository.saveAndFlush(product);
  }

  private Product saveProductWithStatus(
      User seller, String title, Integer price, ProductStatus status) {
    Product product = Product.create(seller, title, "생활기스 조금 있습니다.", price);
    ReflectionTestUtils.setField(product, "status", status);
    return productRepository.saveAndFlush(product);
  }
}
