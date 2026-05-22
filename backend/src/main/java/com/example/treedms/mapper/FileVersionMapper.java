package com.example.treedms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.treedms.entity.FileVersion;
import org.apache.ibatis.annotations.Select;

public interface FileVersionMapper extends BaseMapper<FileVersion> {

    @Select("""
            SELECT COUNT(*)
            FROM biz_file_version
            WHERE file_id = #{fileId}
            """)
    int countByFileId(Long fileId);

    @Select("""
            SELECT COALESCE(MAX(version_no), 0) + 1
            FROM biz_file_version
            WHERE file_id = #{fileId}
            """)
    int nextVersionNo(Long fileId);
}
