package com.flashsale.userservice.unit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.userservice.entity.Address;
import com.flashsale.userservice.mapper.AddressMapper;
import com.flashsale.userservice.service.impl.AddressServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 单元测试 — AddressServiceImpl
 *
 * <p>Mock: AddressMapper</p>
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    @Mock
    private AddressMapper addressMapper;

    @InjectMocks
    private AddressServiceImpl addressService;

    @Test
    void addAddress_ShouldReturn_WithId() {
        Address addr = new Address();
        addr.setReceiverName("张三");
        addr.setReceiverPhone("13800138000");

        when(addressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(addressMapper.insert(any(Address.class))).thenAnswer(invocation -> {
            Address a = invocation.getArgument(0);
            a.setId(5L);
            return 1;
        });

        Address result = addressService.addAddress(1L, addr);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getIsDefault()).isEqualTo(1); // 第一个地址自动默认
        verify(addressMapper).insert(any(Address.class));
    }

    @Test
    void addAddress_ShouldSetDefault_WhenFirst() {
        Address addr = new Address();
        addr.setReceiverName("李四");

        when(addressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(addressMapper.insert(any(Address.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Address.class).setId(10L);
            return 1;
        });

        Address result = addressService.addAddress(1L, addr);
        assertThat(result.getIsDefault()).isEqualTo(1);
    }

    @Test
    void listAddresses_ShouldReturnList() {
        Address a = new Address();
        a.setId(1L);
        a.setReceiverName("王五");

        when(addressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(a));

        List<Address> list = addressService.listAddresses(1L);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getReceiverName()).isEqualTo("王五");
    }

    @Test
    void deleteAddress_ShouldThrow_WhenNotOwner() {
        Address existing = new Address();
        existing.setId(1L);
        existing.setUserId(2L); // 另一个用户

        when(addressMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> addressService.deleteAddress(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("地址不存在");
        verify(addressMapper, never()).deleteById((Serializable) any());
    }
}
