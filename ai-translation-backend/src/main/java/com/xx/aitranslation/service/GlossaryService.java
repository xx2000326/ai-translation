package com.xx.aitranslation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xx.aitranslation.dto.GlossaryVO;
import com.xx.aitranslation.entity.Customer;
import com.xx.aitranslation.entity.Glossary;
import com.xx.aitranslation.mapper.CustomerMapper;
import com.xx.aitranslation.mapper.GlossaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 术语库服务：术语的增查（可看全部客户、按客户名搜索），以及构建注入 Prompt 的术语规则文本。
 */
@Service
@RequiredArgsConstructor
public class GlossaryService {

    private final GlossaryMapper glossaryMapper;
    private final CustomerMapper customerMapper;

    /**
     * 查询术语（可看所有客户）：
     * <ul>
     *     <li>{@code customerName} 非空时，先按客户名模糊匹配得到客户 id 集合，再按 customerId in (...) 过滤；查不到客户则返回空列表。</li>
     *     <li>{@code term} 非空时按术语模糊匹配。</li>
     * </ul>
     * 返回结果附带所属客户名称。
     */
    public List<GlossaryVO> query(String customerName, String term) {
        Map<Long, String> customerNameMap;
        List<Long> customerIds = null;
        if (!ObjectUtils.isEmpty(customerName)) {
            List<Customer> customers = customerMapper.selectList(
                    new LambdaQueryWrapper<Customer>().like(Customer::getName, customerName));
            if (ObjectUtils.isEmpty(customers)) {
                return Collections.emptyList();
            }
            customerNameMap = customers.stream()
                    .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
            customerIds = customers.stream().map(Customer::getId).collect(Collectors.toList());
        } else {
            customerNameMap = loadAllCustomerNames();
        }

        LambdaQueryWrapper<Glossary> wrapper = new LambdaQueryWrapper<>();
        if (!ObjectUtils.isEmpty(customerIds)) {
            wrapper.in(Glossary::getCustomerId, customerIds);
        }
        if (!ObjectUtils.isEmpty(term)) {
            wrapper.like(Glossary::getTerm, term);
        }
        wrapper.orderByDesc(Glossary::getCreateTime);
        List<Glossary> list = glossaryMapper.selectList(wrapper);
        if (ObjectUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream().map(g -> toVO(g, customerNameMap)).collect(Collectors.toList());
    }

    /**
     * 新增术语（归属指定客户）。
     */
    public Glossary add(Long customerId, String term, String translation, String category) {
        Glossary glossary = new Glossary();
        glossary.setCustomerId(customerId);
        glossary.setTerm(term);
        glossary.setTranslation(translation);
        glossary.setCategory(category);
        glossaryMapper.insert(glossary);
        return glossary;
    }

    /**
     * 根据客户与待翻译文本匹配命中的术语，构建注入 Prompt 的术语规则文本。
     * 仅返回该客户术语库中、且在原文中出现的术语，避免无关术语干扰。
     *
     * @return 形如 "term -> translation" 的多行文本，无命中时返回空串
     */
    public String buildGlossaryRules(Long customerId, String text) {
        if (ObjectUtils.isEmpty(text)) {
            return "";
        }
        LambdaQueryWrapper<Glossary> wrapper = new LambdaQueryWrapper<>();
        if (!ObjectUtils.isEmpty(customerId)) {
            wrapper.eq(Glossary::getCustomerId, customerId);
        }
        List<Glossary> customerGlossary = glossaryMapper.selectList(wrapper);
        if (ObjectUtils.isEmpty(customerGlossary)) {
            return "";
        }
        List<Glossary> matched = customerGlossary.stream()
                .filter(g -> !ObjectUtils.isEmpty(g.getTerm()) && text.contains(g.getTerm()))
                .collect(Collectors.toList());
        if (ObjectUtils.isEmpty(matched)) {
            return "";
        }
        return matched.stream()
                .map(g -> g.getTerm() + " -> " + g.getTranslation())
                .collect(Collectors.joining("\n"));
    }

    public List<String> categories(Long customerId) {
        LambdaQueryWrapper<Glossary> wrapper = new LambdaQueryWrapper<>();
        if (!ObjectUtils.isEmpty(customerId)) {
            wrapper.eq(Glossary::getCustomerId, customerId);
        }
        List<Glossary> all = glossaryMapper.selectList(wrapper);
        if (ObjectUtils.isEmpty(all)) {
            return Collections.emptyList();
        }
        return all.stream()
                .map(Glossary::getCategory)
                .filter(c -> !ObjectUtils.isEmpty(c))
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<Long, String> loadAllCustomerNames() {
        List<Customer> customers = customerMapper.selectList(null);
        if (ObjectUtils.isEmpty(customers)) {
            return Collections.emptyMap();
        }
        return customers.stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
    }

    private GlossaryVO toVO(Glossary glossary, Map<Long, String> customerNameMap) {
        GlossaryVO vo = new GlossaryVO();
        BeanUtils.copyProperties(glossary, vo);
        if (!ObjectUtils.isEmpty(glossary.getCustomerId())) {
            vo.setCustomerName(customerNameMap.get(glossary.getCustomerId()));
        }
        return vo;
    }
}
