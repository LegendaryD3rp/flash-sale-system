package com.flashsale.orderservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 订单实体，对应 order 表
 * 状态流转:
 *   秒杀: PENDING → SUCCESS / FAILED
 *   常规: PENDING_PAY → PAID → SHIPPED → RECEIVED → COMPLETED
 *         PENDING_PAY → CANCELLED
 */
@TableName("`order`")
public class Order {

    private Long id;             // 雪花算法生成，非自增
    private Long userId;
    private Long productId;
    private Long seckillActivityId;
    private Long seckillPrice;
    private Integer quantity;
    private Long totalAmount;
    private String status;       // PENDING / SUCCESS / FAILED / PENDING_PAY / PAID / SHIPPED / RECEIVED / COMPLETED / CANCELLED
    private Long addressId;
    private String receiverName;
    private String receiverPhone;
    private String deliveryProvince;
    private String deliveryCity;
    private String deliveryDistrict;
    private String deliveryAddress;
    private Long couponId;
    private Long discount;
    private Long userCouponId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getSeckillActivityId() { return seckillActivityId; }
    public void setSeckillActivityId(Long seckillActivityId) { this.seckillActivityId = seckillActivityId; }

    public Long getSeckillPrice() { return seckillPrice; }
    public void setSeckillPrice(Long seckillPrice) { this.seckillPrice = seckillPrice; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Long totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getAddressId() { return addressId; }
    public void setAddressId(Long addressId) { this.addressId = addressId; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getDeliveryProvince() { return deliveryProvince; }
    public void setDeliveryProvince(String deliveryProvince) { this.deliveryProvince = deliveryProvince; }

    public String getDeliveryCity() { return deliveryCity; }
    public void setDeliveryCity(String deliveryCity) { this.deliveryCity = deliveryCity; }

    public String getDeliveryDistrict() { return deliveryDistrict; }
    public void setDeliveryDistrict(String deliveryDistrict) { this.deliveryDistrict = deliveryDistrict; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }

    public Long getDiscount() { return discount; }
    public void setDiscount(Long discount) { this.discount = discount; }

    public Long getUserCouponId() { return userCouponId; }
    public void setUserCouponId(Long userCouponId) { this.userCouponId = userCouponId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
