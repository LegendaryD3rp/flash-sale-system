package com.flashsale.common;

import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.common.result.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 单元测试 — Result 工具类 + BusinessException
 *
 * <p>不 Mock 任何东西，纯 POJO 逻辑验证。</p>
 *
 * <pre>
 * 判准:
 *   Result.success()  → code=0, data非空
 *   Result.error()    → code=-1, message非空
 *   BusinessException → 异常携带code+message
 * </pre>
 */
class ResultBusinessExceptionTest {

    // ==================== Result.success ====================

    @Test
    void success_ShouldReturnCodeZeroAndNullData() {
        Result<Void> r = Result.success();
        assertThat(r.getCode()).isEqualTo(0);
        assertThat(r.getData()).isNull();
        assertThat(r.getTimestamp()).isPositive();
    }

    @Test
    void success_ShouldWrapData() {
        Result<String> r = Result.success("hello");
        assertThat(r.getCode()).isEqualTo(0);
        assertThat(r.getData()).isEqualTo("hello");
    }

    @Test
    void success_ShouldAcceptCustomMessage() {
        Result<Integer> r = Result.success("自定义消息", 42);
        assertThat(r.getCode()).isEqualTo(0);
        assertThat(r.getMessage()).isEqualTo("自定义消息");
        assertThat(r.getData()).isEqualTo(42);
    }

    // ==================== Result.error ====================

    @Test
    void error_ShouldReturnCodeMinusOne() {
        Result<Void> r = Result.error(-1, "服务器繁忙");
        assertThat(r.getCode()).isEqualTo(-1);
        assertThat(r.getMessage()).isEqualTo("服务器繁忙");
        assertThat(r.getData()).isNull();
    }

    @Test
    void error_ShouldAcceptErrorCodeEnum() {
        Result<Void> r = Result.error(ErrorCode.NOT_FOUND);
        assertThat(r.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        assertThat(r.getMessage()).isEqualTo(ErrorCode.NOT_FOUND.getMessage());
    }

    @Test
    void error_ShouldAcceptErrorCodeWithDetail() {
        Result<Void> r = Result.error(ErrorCode.FORBIDDEN, "无权访问该订单");
        assertThat(r.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
        assertThat(r.getMessage()).isEqualTo("无权访问该订单");
    }

    // ==================== BusinessException ====================

    @Test
    void businessException_ShouldCarryIntCode() {
        BusinessException ex = new BusinessException(4001, "用户名已存在");
        assertThat(ex.getCode()).isEqualTo(4001);
        assertThat(ex.getMessage()).isEqualTo("用户名已存在");
    }

    @Test
    void businessException_ShouldCarryErrorCode() {
        BusinessException ex = new BusinessException(ErrorCode.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode());
        assertThat(ex.getMessage()).isEqualTo(ErrorCode.CONFLICT.getMessage());
    }

    @Test
    void businessException_ShouldOverrideMessage() {
        BusinessException ex = new BusinessException(ErrorCode.CONFLICT, "自定义冲突描述");
        assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode());
        assertThat(ex.getMessage()).isEqualTo("自定义冲突描述");
    }

    // ==================== 综合场景 ====================

    @Test
    void fullCycle_ResultAndException() {
        // 模拟 Service 抛异常 → Controller 转 Result
        BusinessException be = new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        Result<Object> r = Result.error(be.getCode(), be.getMessage());

        assertThat(r.getCode()).isEqualTo(404);
        assertThat(r.getMessage()).isEqualTo("用户不存在");
        assertThat(r.getData()).isNull();
    }

    @Test
    void timestamp_ShouldBeMilliseconds() {
        long before = System.currentTimeMillis();
        Result<String> r = Result.success("ts");
        long after = System.currentTimeMillis();
        assertThat(r.getTimestamp()).isBetween(before, after);
    }
}
