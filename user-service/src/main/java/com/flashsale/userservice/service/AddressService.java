package com.flashsale.userservice.service;

import com.flashsale.userservice.entity.Address;
import java.util.List;

public interface AddressService {

    List<Address> listAddresses(Long userId);

    Address addAddress(Long userId, Address address);

    Address updateAddress(Long userId, Long addressId, Address address);

    void deleteAddress(Long userId, Long addressId);

    void setDefault(Long userId, Long addressId);
}
