package com.flashsale.userservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.userservice.entity.User;
import com.flashsale.userservice.mapper.UserMapper;
import com.flashsale.userservice.service.UserService;
import com.flashsale.userservice.vo.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public UserServiceImpl(UserMapper userMapper, StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public Long register(RegisterReq req) {
        // 1. 校验用户名唯一
        LambdaQueryWrapper<User> usernameQuery = new LambdaQueryWrapper<>();
        usernameQuery.eq(User::getUsername, req.getUsername());
        if (userMapper.selectCount(usernameQuery) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }

        // 2. 校验邮箱唯一
        LambdaQueryWrapper<User> emailQuery = new LambdaQueryWrapper<>();
        emailQuery.eq(User::getEmail, req.getEmail());
        if (userMapper.selectCount(emailQuery) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被注册");
        }

        // 3. BCrypt 加密密码
        String encodedPassword = passwordEncoder.encode(req.getPassword());

        // 4. 构建 User 实体
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(encodedPassword);
        user.setEmail(req.getEmail());
        user.setRole("CUSTOMER");

        // 5. INSERT
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    public LoginResp login(LoginReq req) {
        // 1. 根据用户名查询用户
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
        query.eq(User::getUsername, req.getUsername());
        User user = userMapper.selectOne(query);

        if (user == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名或密码错误");
        }

        // 2. BCrypt 校验密码
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名或密码错误");
        }

        // 3. 生成 JWT Token
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtExpiration);

        String token = Jwts.builder()
                .claim("userId", user.getId())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();

        return new LoginResp(token, user.getId(), user.getRole());
    }

    @Override
    public UserVO getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        UserVO vo = new UserVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    @Override
    public void updateProfile(Long userId, ProfileUpdateReq req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 如果修改邮箱，校验唯一性
        if (StringUtils.hasText(req.getEmail()) && !req.getEmail().equals(user.getEmail())) {
            LambdaQueryWrapper<User> emailQuery = new LambdaQueryWrapper<>();
            emailQuery.eq(User::getEmail, req.getEmail());
            if (userMapper.selectCount(emailQuery) > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被其他账号使用");
            }
        }

        User update = new User();
        update.setId(userId);
        if (StringUtils.hasText(req.getNickname())) {
            update.setNickname(req.getNickname());
        }
        if (StringUtils.hasText(req.getEmail())) {
            update.setEmail(req.getEmail());
        }
        userMapper.updateById(update);
    }

    @Override
    public void updatePassword(Long userId, PasswordUpdateReq req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 校验旧密码
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "旧密码错误");
        }

        // 更新新密码
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(update);
    }

    @Override
    public String forgotPassword(String username, String email) {
        // 校验用户名和邮箱是否匹配
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
        query.eq(User::getUsername, username);
        query.eq(User::getEmail, email);
        User user = userMapper.selectOne(query);

        if (user == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名或邮箱不匹配");
        }

        // 生成临时token，存入Redis，有效期5分钟
        String token = UUID.randomUUID().toString();
        String redisKey = "reset:token:" + token;
        redisTemplate.opsForValue().set(redisKey, String.valueOf(user.getId()), 5, TimeUnit.MINUTES);

        return token;
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        String redisKey = "reset:token:" + token;
        String userIdStr = redisTemplate.opsForValue().get(redisKey);

        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "重置链接已过期或无效");
        }

        Long userId = Long.parseLong(userIdStr);

        // 更新密码
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);

        // 删除已使用的token
        redisTemplate.delete(redisKey);
    }

    // ==================== 管理后台 ====================

    @Override
    public IPage<AdminUserVO> adminListUsers(int page, int size) {
        Page<User> pageReq = new Page<>(page, size);
        IPage<User> userPage = userMapper.selectPage(pageReq, null);

        IPage<AdminUserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(userPage.getRecords().stream()
                .map(this::toAdminUserVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public AdminUserVO adminGetUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return toAdminUserVO(user);
    }

    @Override
    public void adminUpdateUser(Long id, AdminUpdateUserReq req) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 如果修改用户名，校验唯一性
        if (req.getUsername() != null && !req.getUsername().isBlank()
                && !req.getUsername().equals(user.getUsername())) {
            LambdaQueryWrapper<User> usernameQuery = new LambdaQueryWrapper<>();
            usernameQuery.eq(User::getUsername, req.getUsername());
            if (userMapper.selectCount(usernameQuery) > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
            }
        }

        User update = new User();
        update.setId(id);
        if (req.getUsername() != null && !req.getUsername().isBlank()) {
            update.setUsername(req.getUsername());
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            update.setStatus(req.getStatus());
        }
        userMapper.updateById(update);
    }

    @Override
    public void adminDisableUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        User update = new User();
        update.setId(id);
        update.setStatus("DISABLED");
        userMapper.updateById(update);
    }

    private AdminUserVO toAdminUserVO(User user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus() == null ? "ACTIVE" : user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }
}
