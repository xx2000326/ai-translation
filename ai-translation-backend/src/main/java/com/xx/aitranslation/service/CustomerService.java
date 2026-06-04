package com.xx.aitranslation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.dto.CustomerRequest;
import com.xx.aitranslation.entity.Customer;
import com.xx.aitranslation.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * 客户服务：客户信息的增删改查，以及默认翻译偏好的读取。
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerMapper customerMapper;

    public List<Customer> list() {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Customer::getCreateTime);
        return customerMapper.selectList(wrapper);
    }

    public Customer getById(Long customerId) {
        if (ObjectUtils.isEmpty(customerId)) {
            return null;
        }
        return customerMapper.selectById(customerId);
    }

    public Customer create(CustomerRequest request) {
        if (existsByName(request.getName(), null)) {
            throw new BizException("customer.name.exists");
        }
        Customer customer = new Customer();
        customer.setName(request.getName());
        customer.setContact(request.getContact());
        customer.setDefaultRole(request.getDefaultRole());
        customer.setDefaultStyle(request.getDefaultStyle());
        customer.setRemark(request.getRemark());
        customerMapper.insert(customer);
        return customer;
    }

    public Customer update(Long id, CustomerRequest request) {
        Customer customer = customerMapper.selectById(id);
        if (ObjectUtils.isEmpty(customer)) {
            throw new BizException("customer.not.found");
        }
        if (existsByName(request.getName(), id)) {
            throw new BizException("customer.name.exists");
        }
        customer.setName(request.getName());
        customer.setContact(request.getContact());
        customer.setDefaultRole(request.getDefaultRole());
        customer.setDefaultStyle(request.getDefaultStyle());
        customer.setRemark(request.getRemark());
        customerMapper.updateById(customer);
        return customer;
    }

    public void delete(Long id) {
        if (ObjectUtils.isEmpty(customerMapper.selectById(id))) {
            throw new BizException("customer.not.found");
        }
        customerMapper.deleteById(id);
    }

    private boolean existsByName(String name, Long excludeId) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Customer::getName, name);
        if (!ObjectUtils.isEmpty(excludeId)) {
            wrapper.ne(Customer::getId, excludeId);
        }
        return customerMapper.selectCount(wrapper) > 0;
    }
}
