package com.myhome;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DbCheckMapper {
    /**
     * 현재 연결된 SQL Server 데이터베이스 이름을 조회한다.
     * 백엔드에서 DB에 실제로 접근할 수 있는지 확인하는 데 사용한다.
     */
    @Select("Select DB_NAME()")
    String getDatabaseName();
}
