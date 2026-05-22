package com.example.treedms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.treedms.dto.DepartmentNode;
import com.example.treedms.dto.FavoriteDepartmentResponse;
import com.example.treedms.dto.SessionUser;
import com.example.treedms.entity.Department;
import com.example.treedms.entity.BusinessFile;
import com.example.treedms.exception.AppException;
import com.example.treedms.mapper.BusinessFileMapper;
import com.example.treedms.mapper.DepartmentFavoriteMapper;
import com.example.treedms.mapper.DepartmentMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final BusinessFileMapper businessFileMapper;
    private final DepartmentFavoriteMapper departmentFavoriteMapper;

    public DepartmentService(
            DepartmentMapper departmentMapper,
            BusinessFileMapper businessFileMapper,
            DepartmentFavoriteMapper departmentFavoriteMapper) {
        this.departmentMapper = departmentMapper;
        this.businessFileMapper = businessFileMapper;
        this.departmentFavoriteMapper = departmentFavoriteMapper;
    }

    public List<DepartmentNode> tree(SessionUser user) {
        List<Department> departments = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .orderByAsc(Department::getPid)
                .orderByAsc(Department::getSortOrder)
                .orderByAsc(Department::getId));

        Map<Long, Department> departmentMap = toDepartmentMap(departments);
        Set<Long> hiddenIds = user.isAdmin() ? Collections.emptySet() : hiddenDepartmentIds(departments, departmentMap);
        Set<Long> favoriteIds = favoriteIds(user);
        Map<Long, DepartmentNode> nodes = new LinkedHashMap<>();
        for (Department department : departments) {
            if (hiddenIds.contains(department.getId())) {
                continue;
            }
            nodes.put(department.getId(), new DepartmentNode(
                    department.getId(),
                    department.getPid(),
                    department.getDepartment(),
                    department.getSortOrder(),
                    Integer.valueOf(1).equals(department.getEncrypted()),
                    favoriteIds.contains(department.getId())));
        }

        List<DepartmentNode> roots = new ArrayList<>();
        for (DepartmentNode node : nodes.values()) {
            if (node.getPid() == null || !nodes.containsKey(node.getPid())) {
                roots.add(node);
            } else {
                nodes.get(node.getPid()).getChildren().add(node);
            }
        }
        return roots;
    }

    public void requireExists(Long departmentId) {
        if (departmentId == null || departmentMapper.selectById(departmentId) == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "部门不存在");
        }
    }

    public void requireVisible(Long departmentId, SessionUser user) {
        Department department = findById(departmentId);
        if (!user.isAdmin() && hasEncryptedAncestor(department, loadDepartmentMap())) {
            throw new AppException(HttpStatus.NOT_FOUND, "部门不存在或不可见");
        }
    }

    public synchronized DepartmentNode createChild(Long parentId, String departmentName) {
        requireExists(parentId);

        Department department = new Department();
        department.setId(nextId());
        department.setPid(parentId);
        department.setDepartment(cleanName(departmentName));
        department.setSortOrder(nextSortOrder(parentId));
        department.setEncrypted(0);
        departmentMapper.insert(department);
        return new DepartmentNode(
                department.getId(),
                department.getPid(),
                department.getDepartment(),
                department.getSortOrder(),
                false,
                false);
    }

    public DepartmentNode rename(Long id, String departmentName) {
        Department department = findById(id);
        department.setDepartment(cleanName(departmentName));
        departmentMapper.updateById(department);
        return new DepartmentNode(
                department.getId(),
                department.getPid(),
                department.getDepartment(),
                department.getSortOrder(),
                Integer.valueOf(1).equals(department.getEncrypted()),
                false);
    }

    public DepartmentNode setEncrypted(Long id, boolean encrypted) {
        Department department = findById(id);
        if (department.getPid() == null || Long.valueOf(0L).equals(department.getId())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "根部门不能加密");
        }
        department.setEncrypted(encrypted ? 1 : 0);
        departmentMapper.updateById(department);
        return new DepartmentNode(
                department.getId(),
                department.getPid(),
                department.getDepartment(),
                department.getSortOrder(),
                encrypted,
                false);
    }

    public void addFavorite(Long id, SessionUser user) {
        requireVisible(id, user);
        departmentFavoriteMapper.insertFavorite(user.username(), id);
    }

    public void removeFavorite(Long id, SessionUser user) {
        departmentFavoriteMapper.deleteFavorite(user.username(), id);
    }

    public List<FavoriteDepartmentResponse> favorites(SessionUser user) {
        Map<Long, Department> departmentMap = loadDepartmentMap();
        return departmentFavoriteMapper.selectFavorites(user.username())
                .stream()
                .filter(item -> departmentMap.containsKey(item.getId()))
                .filter(item -> user.isAdmin() || !hasEncryptedAncestor(departmentMap.get(item.getId()), departmentMap))
                .peek(item -> item.setPath(departmentPath(item.getId(), departmentMap)))
                .toList();
    }

    public synchronized void move(Long id, Long parentId, List<Long> orderedIds) {
        Department department = findById(id);
        if (department.getPid() == null || Long.valueOf(0L).equals(department.getId())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "根部门不能移动");
        }

        Department parent = findById(parentId);
        if (parent.getPid() != null && Objects.equals(parent.getId(), id)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "不能移动到自身下面");
        }
        if (isDescendant(parentId, id)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "不能移动到自己的子部门下面");
        }
        if (orderedIds == null || orderedIds.isEmpty() || !orderedIds.contains(id)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "部门排序数据不完整");
        }

        department.setPid(parentId);
        departmentMapper.updateById(department);
        updateSiblingSort(parentId, orderedIds);
    }

    public void delete(Long id) {
        Department department = findById(id);
        if (department.getPid() == null || Long.valueOf(0L).equals(department.getId())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "根部门不能删除");
        }

        Long childCount = departmentMapper.selectCount(new LambdaQueryWrapper<Department>()
                .eq(Department::getPid, id));
        if (childCount > 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "该部门下还有子部门，不能删除");
        }

        Long fileCount = businessFileMapper.selectCount(new LambdaQueryWrapper<BusinessFile>()
                .eq(BusinessFile::getDeptId, id));
        if (fileCount > 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "该部门下还有文件，不能删除");
        }

        departmentMapper.deleteById(id);
    }

    private Department findById(Long id) {
        if (id == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "部门不存在");
        }
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "部门不存在");
        }
        return department;
    }

    private Map<Long, Department> loadDepartmentMap() {
        return toDepartmentMap(departmentMapper.selectList(new LambdaQueryWrapper<Department>()));
    }

    private Map<Long, Department> toDepartmentMap(List<Department> departments) {
        Map<Long, Department> departmentMap = new LinkedHashMap<>();
        for (Department department : departments) {
            departmentMap.put(department.getId(), department);
        }
        return departmentMap;
    }

    private Set<Long> favoriteIds(SessionUser user) {
        Set<Long> ids = new HashSet<>();
        for (FavoriteDepartmentResponse item : departmentFavoriteMapper.selectFavorites(user.username())) {
            ids.add(item.getId());
        }
        return ids;
    }

    private Set<Long> hiddenDepartmentIds(List<Department> departments, Map<Long, Department> departmentMap) {
        Set<Long> hiddenIds = new HashSet<>();
        for (Department department : departments) {
            if (hasEncryptedAncestor(department, departmentMap)) {
                hiddenIds.add(department.getId());
            }
        }
        return hiddenIds;
    }

    private boolean hasEncryptedAncestor(Department department, Map<Long, Department> departmentMap) {
        Department current = department;
        while (current != null) {
            if (Integer.valueOf(1).equals(current.getEncrypted())) {
                return true;
            }
            current = current.getPid() == null ? null : departmentMap.get(current.getPid());
        }
        return false;
    }

    private String departmentPath(Long id, Map<Long, Department> departmentMap) {
        List<String> names = new ArrayList<>();
        Department current = departmentMap.get(id);
        while (current != null) {
            names.add(current.getDepartment());
            current = current.getPid() == null ? null : departmentMap.get(current.getPid());
        }
        Collections.reverse(names);
        return String.join(" / ", names);
    }

    private String cleanName(String departmentName) {
        String name = StringUtils.trimWhitespace(departmentName);
        if (!StringUtils.hasText(name)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "部门名称不能为空");
        }
        if (name.length() > 100) {
            throw new AppException(HttpStatus.BAD_REQUEST, "部门名称长度不能超过100个字符");
        }
        return name;
    }

    private boolean isDescendant(Long possibleDescendantId, Long ancestorId) {
        Long currentId = possibleDescendantId;
        while (currentId != null) {
            if (Objects.equals(currentId, ancestorId)) {
                return true;
            }
            Department current = departmentMapper.selectById(currentId);
            if (current == null) {
                return false;
            }
            currentId = current.getPid();
        }
        return false;
    }

    private void updateSiblingSort(Long parentId, List<Long> orderedIds) {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            Long siblingId = orderedIds.get(i);
            if (!seen.add(siblingId)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "部门排序数据重复");
            }

            Department sibling = findById(siblingId);
            if (!Objects.equals(sibling.getPid(), parentId)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "部门排序数据与父部门不匹配");
            }
            sibling.setSortOrder((i + 1) * 1000);
            departmentMapper.updateById(sibling);
        }
    }

    private Long nextId() {
        List<Object> values = departmentMapper.selectObjs(new QueryWrapper<Department>()
                .select("COALESCE(MAX(id), 0) + 1"));
        if (values.isEmpty() || values.get(0) == null) {
            return 1L;
        }
        Object value = values.get(0);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Integer nextSortOrder(Long parentId) {
        QueryWrapper<Department> wrapper = new QueryWrapper<Department>()
                .select("COALESCE(MAX(sort_order), 0) + 1000");
        if (parentId == null) {
            wrapper.isNull("pid");
        } else {
            wrapper.eq("pid", parentId);
        }

        List<Object> values = departmentMapper.selectObjs(wrapper);
        if (values.isEmpty() || values.get(0) == null) {
            return 1000;
        }
        Object value = values.get(0);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
