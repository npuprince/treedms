package com.example.treedms.mapper;

import com.example.treedms.dto.FavoriteDepartmentResponse;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DepartmentFavoriteMapper {

    @Insert("""
            INSERT IGNORE INTO user_department_favorite (username, dept_id)
            VALUES (#{username}, #{deptId})
            """)
    int insertFavorite(@Param("username") String username, @Param("deptId") Long deptId);

    @Delete("""
            DELETE FROM user_department_favorite
            WHERE username = #{username} AND dept_id = #{deptId}
            """)
    int deleteFavorite(@Param("username") String username, @Param("deptId") Long deptId);

    @Select("""
            SELECT COUNT(*)
            FROM user_department_favorite
            WHERE username = #{username} AND dept_id = #{deptId}
            """)
    Long countFavorite(@Param("username") String username, @Param("deptId") Long deptId);

    @Select("""
            SELECT d.id AS id,
                   d.pid AS pid,
                   d.department AS department,
                   f.created_at AS createdAt
            FROM user_department_favorite f
            JOIN sys_department d ON d.id = f.dept_id
            WHERE f.username = #{username}
            ORDER BY f.created_at DESC, d.id DESC
            """)
    List<FavoriteDepartmentResponse> selectFavorites(@Param("username") String username);
}
