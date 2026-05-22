package com.example.treedms.controller;

import com.example.treedms.dto.DepartmentEncryptRequest;
import com.example.treedms.dto.DepartmentSaveRequest;
import com.example.treedms.dto.DepartmentNode;
import com.example.treedms.dto.DepartmentMoveRequest;
import com.example.treedms.dto.FavoriteDepartmentResponse;
import com.example.treedms.service.AuthService;
import com.example.treedms.service.DepartmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final AuthService authService;

    public DepartmentController(DepartmentService departmentService, AuthService authService) {
        this.departmentService = departmentService;
        this.authService = authService;
    }

    @GetMapping("/tree")
    public List<DepartmentNode> tree() {
        return departmentService.tree(authService.currentUser());
    }

    @GetMapping("/favorites")
    public List<FavoriteDepartmentResponse> favorites() {
        return departmentService.favorites(authService.currentUser());
    }

    @PostMapping("/{parentId}/children")
    public DepartmentNode createChild(@PathVariable Long parentId, @Valid @RequestBody DepartmentSaveRequest request) {
        authService.requireAdmin();
        return departmentService.createChild(parentId, request.department());
    }

    @PutMapping("/{id}")
    public DepartmentNode rename(@PathVariable Long id, @Valid @RequestBody DepartmentSaveRequest request) {
        authService.requireAdmin();
        return departmentService.rename(id, request.department());
    }

    @PutMapping("/{id}/move")
    public void move(@PathVariable Long id, @Valid @RequestBody DepartmentMoveRequest request) {
        authService.requireAdmin();
        departmentService.move(id, request.parentId(), request.orderedIds());
    }

    @PutMapping("/{id}/encrypt")
    public DepartmentNode setEncrypted(@PathVariable Long id, @Valid @RequestBody DepartmentEncryptRequest request) {
        authService.requireAdmin();
        return departmentService.setEncrypted(id, request.encrypted());
    }

    @PostMapping("/{id}/favorite")
    public void addFavorite(@PathVariable Long id) {
        departmentService.addFavorite(id, authService.currentUser());
    }

    @DeleteMapping("/{id}/favorite")
    public void removeFavorite(@PathVariable Long id) {
        departmentService.removeFavorite(id, authService.currentUser());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        authService.requireAdmin();
        departmentService.delete(id);
    }
}
