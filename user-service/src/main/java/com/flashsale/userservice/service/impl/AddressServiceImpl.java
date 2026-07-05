package com.flashsale.userservice.service.impl;

import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.userservice.entity.Address;
import com.flashsale.userservice.mapper.AddressMapper;
import com.flashsale.userservice.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    public AddressServiceImpl(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    @Override
    public List<Address> listAddresses(Long userId) {
        return addressMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .orderByDesc(Address::getIsDefault)
                        .orderByDesc(Address::getCreatedAt));
    }

    @Override
    public Address addAddress(Long userId, Address address) {
        address.setId(null);
        address.setUserId(userId);

        // 如果是第一个地址，自动设为默认
        long count = addressMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId));
        if (count == 0) {
            address.setIsDefault(1);
        } else {
            if (address.getIsDefault() == null) {
                address.setIsDefault(0);
            }
        }

        // 如果设为默认，清除其他默认
        if (address.getIsDefault() == 1) {
            clearOtherDefaults(userId, null);
        }

        addressMapper.insert(address);
        return address;
    }

    @Override
    @Transactional
    public Address updateAddress(Long userId, Long addressId, Address address) {
        Address existing = addressMapper.selectById(addressId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "地址不存在");
        }

        if (address.getReceiverName() != null) existing.setReceiverName(address.getReceiverName());
        if (address.getReceiverPhone() != null) existing.setReceiverPhone(address.getReceiverPhone());
        if (address.getProvince() != null) existing.setProvince(address.getProvince());
        if (address.getCity() != null) existing.setCity(address.getCity());
        if (address.getDistrict() != null) existing.setDistrict(address.getDistrict());
        if (address.getDetailAddress() != null) existing.setDetailAddress(address.getDetailAddress());
        if (address.getIsDefault() != null) {
            if (address.getIsDefault() == 1) {
                clearOtherDefaults(userId, addressId);
            }
            existing.setIsDefault(address.getIsDefault());
        }

        addressMapper.updateById(existing);
        return addressMapper.selectById(addressId);
    }

    @Override
    public void deleteAddress(Long userId, Long addressId) {
        Address existing = addressMapper.selectById(addressId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "地址不存在");
        }
        addressMapper.deleteById(addressId);
    }

    @Override
    @Transactional
    public void setDefault(Long userId, Long addressId) {
        Address existing = addressMapper.selectById(addressId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "地址不存在");
        }
        clearOtherDefaults(userId, addressId);
        existing.setIsDefault(1);
        addressMapper.updateById(existing);
    }

    private void clearOtherDefaults(Long userId, Long excludeId) {
        List<Address> defaults = addressMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .eq(Address::getIsDefault, 1));
        for (Address a : defaults) {
            if (excludeId == null || !a.getId().equals(excludeId)) {
                a.setIsDefault(0);
                addressMapper.updateById(a);
            }
        }
    }
}
