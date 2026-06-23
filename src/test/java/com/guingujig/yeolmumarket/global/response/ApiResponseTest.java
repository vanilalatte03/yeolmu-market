package com.guingujig.yeolmumarket.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ApiResponseTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void 성공_응답은_공통_wrapper로_직렬화된다() throws Exception {
    ApiResponse<TestData> response = ApiResponse.success(new TestData(1L, "열무"));

    String json = objectMapper.writeValueAsString(response);

    assertThat(json).contains("\"success\":true");
    assertThat(json).contains("\"code\":\"SUCCESS\"");
    assertThat(json).contains("\"message\":\"요청이 성공했습니다.\"");
    assertThat(json).contains("\"data\":{\"id\":1,\"name\":\"열무\"}");
    assertThat(json).doesNotContain("errors");
  }

  @Test
  void 실패_응답은_null_data를_생략하고_errors를_포함한다() throws Exception {
    ApiResponse<Void> response =
        ApiResponse.failure("VALIDATION_FAILED", "검증에 실패했습니다.", List.of("email: 필수입니다."));

    String json = objectMapper.writeValueAsString(response);

    assertThat(json).contains("\"success\":false");
    assertThat(json).contains("\"code\":\"VALIDATION_FAILED\"");
    assertThat(json).doesNotContain("data");
    assertThat(json).contains("\"errors\":[\"email: 필수입니다.\"]");
  }

  private record TestData(Long id, String name) {}
}
