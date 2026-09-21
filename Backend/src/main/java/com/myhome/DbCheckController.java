package com.myhome;

import com.myhome.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class DbCheckController {
    private final DbCheckMapper dbCheckMapper;
    public DbCheckController(DbCheckMapper dbCheckMapper) {
        this.dbCheckMapper = dbCheckMapper;
    }

    // GET /api/check/db 요청을 처리해 현재 연결된 데이터베이스 이름을 반환한다.
    @GetMapping("/api/check/db")
    public ApiResponse<Map<String, String>> checkDatabase() {
        return ApiResponse.success(
                Map.of("database", dbCheckMapper.getDatabaseName())
        );
    }
}
