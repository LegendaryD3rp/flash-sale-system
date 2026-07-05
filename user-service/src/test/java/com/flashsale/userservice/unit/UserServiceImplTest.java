package com.flashsale.userservice.unit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.userservice.entity.User;
import com.flashsale.userservice.mapper.UserMapper;
import com.flashsale.userservice.service.impl.UserServiceImpl;
import com.flashsale.userservice.vo.LoginReq;
import com.flashsale.userservice.vo.LoginResp;
import com.flashsale.userservice.vo.PasswordUpdateReq;
import com.flashsale.userservice.vo.RegisterReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 单元测试 — UserServiceImpl
 * <p>Mock: UserMapper, StringRedisTemplate</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    private UserServiceImpl userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, redisTemplate);
        ReflectionTestUtils.setField(userService, "jwtSecret",
                "test-secret-key-for-unit-test-at-least-256-bit-long!!");
        ReflectionTestUtils.setField(userService, "jwtExpiration", 3600000L);
    }

    // ==================== register ====================

    @Test
    void register_ShouldReturnUserId_WhenValid() {
        RegisterReq req = new RegisterReq();
        req.setUsername("newuser");
        req.setEmail("new@test.com");
        req.setPassword("pass123");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, User.class).setId(100L);
            return 1;
        });

        Long userId = userService.register(req);
        assertThat(userId).isEqualTo(100L);

        verify(userMapper, times(2)).selectCount(any(LambdaQueryWrapper.class));
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void register_ShouldThrow_WhenUsernameExists() {
        RegisterReq req = new RegisterReq();
        req.setUsername("existing");
        req.setEmail("new@test.com");
        req.setPassword("pass123");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void register_ShouldThrow_WhenEmailExists() {
        RegisterReq req = new RegisterReq();
        req.setUsername("newuser");
        req.setEmail("dup@test.com");
        req.setPassword("pass123");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L)   // username check
                .thenReturn(1L);  // email check

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮箱已被注册");
        verify(userMapper, never()).insert(any(User.class));
    }

    // ==================== login ====================

    @Test
    void login_ShouldReturnToken_WhenValid() {
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setPassword(encoder.encode("pass123"));
        mockUser.setRole("CUSTOMER");

        LoginReq req = new LoginReq();
        req.setUsername("testuser");
        req.setPassword("pass123");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        LoginResp resp = userService.login(req);
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getUserId()).isEqualTo(1L);
        assertThat(resp.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void login_ShouldThrow_WhenPasswordWrong() {
        User mockUser = new User();
        mockUser.setPassword(encoder.encode("correct"));

        LoginReq req = new LoginReq();
        req.setUsername("testuser");
        req.setPassword("wrong");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码错误");
    }

    @Test
    void login_ShouldThrow_WhenUserNotFound() {
        LoginReq req = new LoginReq();
        req.setUsername("nobody");
        req.setPassword("pass");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    // ==================== getUserById ====================

    @Test
    void getUserById_ShouldReturn_WhenExists() {
        User u = new User();
        u.setId(1L);
        u.setUsername("testuser");
        u.setEmail("test@test.com");

        when(userMapper.selectById(1L)).thenReturn(u);

        var vo = userService.getUserById(1L);
        assertThat(vo.getUsername()).isEqualTo("testuser");
        assertThat(vo.getEmail()).isEqualTo("test@test.com");
    }

    @Test
    void getUserById_ShouldThrow_WhenNotExist() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    // ==================== updatePassword ====================

    @Test
    void updatePassword_ShouldSucceed_WhenOldPasswordCorrect() {
        User u = new User();
        u.setId(1L);
        u.setPassword(encoder.encode("old123"));

        PasswordUpdateReq req = new PasswordUpdateReq();
        req.setOldPassword("old123");
        req.setNewPassword("new456");

        when(userMapper.selectById(1L)).thenReturn(u);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        userService.updatePassword(1L, req);

        verify(userMapper).updateById(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isNotEqualTo("new456");
    }

    @Test
    void updatePassword_ShouldThrow_WhenOldPasswordWrong() {
        User u = new User();
        u.setId(1L);
        u.setPassword(encoder.encode("correctOld"));

        PasswordUpdateReq req = new PasswordUpdateReq();
        req.setOldPassword("wrongOld");
        req.setNewPassword("new456");

        when(userMapper.selectById(1L)).thenReturn(u);

        assertThatThrownBy(() -> userService.updatePassword(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("旧密码错误");
    }
}
