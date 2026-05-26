package com.example.treedms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.treedms.entity.BusinessFile;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface BusinessFileMapper extends BaseMapper<BusinessFile> {

    @Select("""
            SELECT id, dept_id, original_name, storage_name, relative_path, content_type, size,
                   uploader, pinned, sort_order, created_at, updated_at, deleted
            FROM biz_file
            WHERE deleted = 1
            ORDER BY updated_at DESC, id DESC
            """)
    List<BusinessFile> selectDeletedFiles();

    @Select("""
            SELECT id, dept_id, original_name, storage_name, relative_path, content_type, size,
                   uploader, pinned, sort_order, created_at, updated_at, deleted
            FROM biz_file
            WHERE id = #{id} AND deleted = 1
            """)
    BusinessFile selectDeletedById(Long id);

    @Update("""
            UPDATE biz_file
            SET deleted = 0, updated_at = NOW()
            WHERE id = #{id} AND deleted = 1
            """)
    int restoreDeletedById(Long id);
}
