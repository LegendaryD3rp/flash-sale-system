package com.flashsale.userservice.controller;

import com.flashsale.common.result.Result;
import com.flashsale.userservice.entity.Address;
import com.flashsale.userservice.service.AddressService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/address")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    /**
     * GET /api/address/list — 查询用户地址列表
     */
    @GetMapping("/list")
    public Result<List<Address>> listAddresses(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(addressService.listAddresses(userId));
    }

    /**
     * POST /api/address — 新增地址
     */
    @PostMapping
    public Result<Address> addAddress(@RequestHeader("X-User-Id") Long userId,
                                      @RequestBody Address address) {
        return Result.success(addressService.addAddress(userId, address));
    }

    /**
     * PUT /api/address/{id} — 修改地址
     */
    @PutMapping("/{id}")
    public Result<Address> updateAddress(@RequestHeader("X-User-Id") Long userId,
                                         @PathVariable Long id,
                                         @RequestBody Address address) {
        return Result.success(addressService.updateAddress(userId, id, address));
    }

    /**
     * DELETE /api/address/{id} — 删除地址
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAddress(@RequestHeader("X-User-Id") Long userId,
                                      @PathVariable Long id) {
        addressService.deleteAddress(userId, id);
        return Result.success();
    }

    /**
     * PUT /api/address/{id}/default — 设为默认地址
     */
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@RequestHeader("X-User-Id") Long userId,
                                   @PathVariable Long id) {
        addressService.setDefault(userId, id);
        return Result.success();
    }
}
