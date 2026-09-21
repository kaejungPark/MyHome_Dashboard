package com.myhome;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DbCheckMapper {
    @Select("Select DB_NAME()")
    String getDatabaseName();
}
