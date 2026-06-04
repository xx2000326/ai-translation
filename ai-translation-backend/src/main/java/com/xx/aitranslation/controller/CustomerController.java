package com.xx.aitranslation.controller;

import com.xx.aitranslation.common.MessageUtils;
import com.xx.aitranslation.common.Result;
import com.xx.aitranslation.dto.CustomerRequest;
import com.xx.aitranslation.entity.Customer;
import com.xx.aitranslation.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户管理接口。
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public Result<List<Customer>> list() {
        return Result.success(customerService.list());
    }

    @GetMapping("/{id}")
    public Result<Customer> get(@PathVariable Long id) {
        return Result.success(customerService.getById(id));
    }

    @PostMapping
    public Result<Customer> create(@Valid @RequestBody CustomerRequest request) {
        return Result.success(MessageUtils.get("customer.save.success"), customerService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Customer> update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return Result.success(MessageUtils.get("customer.save.success"), customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.success(MessageUtils.get("customer.delete.success"), null);
    }
}
