package com.xx.aitranslation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xx.aitranslation.dto.TranslationHistoryVO;
import com.xx.aitranslation.entity.Customer;
import com.xx.aitranslation.entity.TranslationHistory;
import com.xx.aitranslation.mapper.CustomerMapper;
import com.xx.aitranslation.mapper.TranslationHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 翻译历史服务：写入翻译历史，以及查询（可看所有客户、按客户名搜索）。
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final TranslationHistoryMapper translationHistoryMapper;
    private final CustomerMapper customerMapper;

    public void save(Long customerId, String originalText, String translatedText, String role, String style) {
        TranslationHistory history = new TranslationHistory();
        history.setCustomerId(customerId);
        history.setOriginalText(originalText);
        history.setTranslatedText(translatedText);
        history.setRole(role);
        history.setStyle(style);
        translationHistoryMapper.insert(history);
    }

    /**
     * 查询翻译历史（可看所有客户）：{@code customerName} 非空时先按客户名模糊匹配客户 id 集合再过滤，
     * 查不到客户则返回空列表。返回结果附带所属客户名称。
     */
    public List<TranslationHistoryVO> list(String customerName) {
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

        LambdaQueryWrapper<TranslationHistory> wrapper = new LambdaQueryWrapper<>();
        if (!ObjectUtils.isEmpty(customerIds)) {
            wrapper.in(TranslationHistory::getCustomerId, customerIds);
        }
        wrapper.orderByDesc(TranslationHistory::getCreateTime);
        List<TranslationHistory> list = translationHistoryMapper.selectList(wrapper);
        if (ObjectUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream().map(h -> toVO(h, customerNameMap)).collect(Collectors.toList());
    }

    private Map<Long, String> loadAllCustomerNames() {
        List<Customer> customers = customerMapper.selectList(null);
        if (ObjectUtils.isEmpty(customers)) {
            return Collections.emptyMap();
        }
        return customers.stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
    }

    private TranslationHistoryVO toVO(TranslationHistory history, Map<Long, String> customerNameMap) {
        TranslationHistoryVO vo = new TranslationHistoryVO();
        BeanUtils.copyProperties(history, vo);
        if (!ObjectUtils.isEmpty(history.getCustomerId())) {
            vo.setCustomerName(customerNameMap.get(history.getCustomerId()));
        }
        return vo;
    }
}
