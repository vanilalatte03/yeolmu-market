package com.guingujig.yeolmumarket.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

  private final MockMvc mockMvc;

  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

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
  }

  record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {}
}
