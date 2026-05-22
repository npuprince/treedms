package com.example.treedms.dto;

import java.util.ArrayList;
import java.util.List;

public class DepartmentNode {

    private Long id;
    private Long pid;
    private String department;
    private Integer sortOrder;
    private Boolean encrypted;
    private Boolean favorite;
    private List<DepartmentNode> children = new ArrayList<>();

    public DepartmentNode() {
    }

    public DepartmentNode(Long id, Long pid, String department) {
        this(id, pid, department, 0, false, false);
    }

    public DepartmentNode(Long id, Long pid, String department, Integer sortOrder) {
        this(id, pid, department, sortOrder, false, false);
    }

    public DepartmentNode(Long id, Long pid, String department, Integer sortOrder, Boolean encrypted, Boolean favorite) {
        this.id = id;
        this.pid = pid;
        this.department = department;
        this.sortOrder = sortOrder;
        this.encrypted = encrypted;
        this.favorite = favorite;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getEncrypted() {
        return encrypted;
    }

    public void setEncrypted(Boolean encrypted) {
        this.encrypted = encrypted;
    }

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }

    public List<DepartmentNode> getChildren() {
        return children;
    }

    public void setChildren(List<DepartmentNode> children) {
        this.children = children;
    }
}
