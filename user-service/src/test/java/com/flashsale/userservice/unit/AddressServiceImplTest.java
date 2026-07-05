package com.flashsale.userservice.unit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.userservice.entity.Address;
import com.flashsale.userservice.mapper.AddressMapper;
import com.flashsale.userservice.service.impl.AddressServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    @Mock
    private AddressMapper addressMapper;

    @InjectMocks
    private AddressServiceImpl addressService;

    @Captor
    private ArgumentCaptor<Address> addressCaptor;

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

    /** ⭐ P2-⑯ 默认地址自动覆盖旧默认 */
    @Test
    void addAddress_ShouldClearOldDefault_WhenNewAddressIsDefault() {
        Address oldDefault = new Address();
        oldDefault.setId(1L);
        oldDefault.setIsDefault(1);
        oldDefault.setUserId(1L);

        when(addressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(addressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(oldDefault));
        when(addressMapper.insert(any(Address.class))).thenAnswer(inv -> {
            inv.getArgument(0, Address.class).setId(10L);
            return 1;
        });

        Address newAddr = new Address();
        newAddr.setIsDefault(1);
        addressService.addAddress(1L, newAddr);

        verify(addressMapper).updateById(addressCaptor.capture());
        assertThat(addressCaptor.getValue().getId()).isEqualTo(1L);
        assertThat(addressCaptor.getValue().getIsDefault()).isZero();
    }

    /** ⭐ P2-⑯ setDefault 清除旧默认 */
    @Test
    void setDefault_ShouldClearOldDefault() {
        Address target = new Address();
        target.setId(2L);
        target.setUserId(1L);
        target.setIsDefault(0);

        Address oldDefault = new Address();
        oldDefault.setId(1L);
        oldDefault.setIsDefault(1);
        oldDefault.setUserId(1L);

        when(addressMapper.selectById(2L)).thenReturn(target);
        when(addressMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(oldDefault));

        addressService.setDefault(1L, 2L);

        verify(addressMapper, times(2)).updateById(addressCaptor.capture());
        List<Address> captured = addressCaptor.getAllValues();
        // 第一个 updateById: 清除旧默认 (isDefault=0)
        assertThat(captured.get(0).getId()).isEqualTo(1L);
        assertThat(captured.get(0).getIsDefault()).isZero();
        // 第二个 updateById: 设置新默认 (isDefault=1)
        assertThat(captured.get(1).getId()).isEqualTo(2L);
        assertThat(captured.get(1).getIsDefault()).isEqualTo(1);
    }
}
