package com.guingujig.yeolmumarket.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guingujig.yeolmumarket.YeolmuMarketApplication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    classes = {YeolmuMarketApplication.class, GlobalExceptionHandlerTest.TestController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

  private final MockMvc mockMvc;

  @Autowired
  GlobalExceptionHandlerTest(MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  @Test
  void 비즈니스_예외는_지정된_HTTP_상태와_에러코드로_응답한다() throws Exception {
    mockMvc
        .perform(get("/test/business-exception"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("회원을 찾을 수 없습니다."))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void validation_실패는_VALIDATION_FAILED와_errors로_응답한다() throws Exception {
    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("요청 본문, 쿼리 파라미터, 경로 변수 검증에 실패했습니다."))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.errors[0]", containsString("name: 이름은 필수입니다.")));
  }

  @Test
  void 쿼리_파라미터_검증_실패는_VALIDATION_FAILED와_errors로_응답한다() throws Exception {
    mockMvc
        .perform(get("/test/param-validation").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("요청 본문, 쿼리 파라미터, 경로 변수 검증에 실패했습니다."))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.errors[0]", containsString("size: 1 이상이어야 합니다.")));
  }

  @ParameterizedTest
  @CsvSource({
    "uk_category_name, CATEGORY_NAME_ALREADY_EXISTS, 이미 존재하는 카테고리명입니다.",
    "uk_payment_order, PAYMENT_ALREADY_EXISTS, 해당 주문의 결제가 이미 존재합니다.",
    "uk_payment_idempotency_key, PAYMENT_ALREADY_EXISTS, 해당 주문의 결제가 이미 존재합니다.",
    "uk_refund_request_order, REFUND_REQUEST_ALREADY_EXISTS, 해당 주문의 환불 요청이 이미 존재합니다.",
    "uk_review_order_reviewer, REVIEW_ALREADY_EXISTS, 이미 작성한 주문 리뷰입니다.",
    "uk_wish_user_product, WISH_ALREADY_EXISTS, 이미 찜한 상품입니다.",
    "uk_users_email, EMAIL_ALREADY_EXISTS, 이미 가입된 이메일입니다."
  })
  void 알려진_DB_unique_제약은_도메인_에러로_응답한다(
      String constraintName, String expectedCode, String expectedMessage) throws Exception {
    mockMvc
        .perform(get("/test/data-integrity").param("constraint", constraintName))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(expectedCode))
        .andExpect(jsonPath("$.message").value(expectedMessage));
  }

  @Test
  void DB_제약명은_quote_schema_대소문자를_정규화한다() throws Exception {
    mockMvc
        .perform(get("/test/data-integrity").param("constraint", "`PUBLIC`.`UK_PAYMENT_ORDER`"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_EXISTS"));
  }

  @Test
  void 메시지에_있는_DB_제약명도_도메인_에러로_응답한다() throws Exception {
    mockMvc
        .perform(get("/test/data-integrity-message"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WISH_ALREADY_EXISTS"));
  }

  @Test
  void 트랜잭션_커밋_중_DB_제약_예외도_도메인_에러로_응답한다() throws Exception {
    mockMvc
        .perform(get("/test/transaction-system"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ALREADY_EXISTS"));
  }

  @Test
  void 알수없는_DB_무결성_예외는_INTERNAL_SERVER_ERROR로_응답한다() throws Exception {
    mockMvc
        .perform(get("/test/unknown-data-integrity"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
  }

  @RestController
  @Validated
  static class TestController {

    @GetMapping("/test/business-exception")
    void businessException() {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }

    @PostMapping("/test/validation")
    void validation(@Valid @RequestBody TestRequest request) {}

    @GetMapping("/test/param-validation")
    void paramValidation(@RequestParam @Min(value = 1, message = "1 이상이어야 합니다.") int size) {}

    @GetMapping("/test/data-integrity")
    void dataIntegrity(@RequestParam String constraint) {
      throw new DataIntegrityViolationException(
          "duplicate key", hibernateConstraintViolation(constraint));
    }

    @GetMapping("/test/data-integrity-message")
    void dataIntegrityMessage() {
      throw new DataIntegrityViolationException(
          "Duplicate entry for key 'PUBLIC.UK_WISH_USER_PRODUCT'");
    }

    @GetMapping("/test/transaction-system")
    void transactionSystemException() {
      throw new TransactionSystemException(
          "commit failed",
          new DataIntegrityViolationException(
              "duplicate key", hibernateConstraintViolation("uk_refund_request_order")));
    }

    @GetMapping("/test/unknown-data-integrity")
    void unknownDataIntegrity() {
      throw new DataIntegrityViolationException("not-null violation");
    }

    private static org.hibernate.exception.ConstraintViolationException
        hibernateConstraintViolation(String constraint) {
      return new org.hibernate.exception.ConstraintViolationException(
          "unique constraint violation", new SQLException(), constraint);
    }
  }

  record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {}
}
