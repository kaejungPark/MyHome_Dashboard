package com.myhome;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class DbCheckController {
    private final DbCheckMapper dbCheckMapper;
    public DbCheckController(DbCheckMapper dbCheckMapper) {
        this.dbCheckMapper = dbCheckMapper;
    }

    @GetMapping("/api/check/db")
    public Map<String, String> checkDatabase() {
        return Map.of("database", dbCheckMapper.getDatabaseName());
    }
}
