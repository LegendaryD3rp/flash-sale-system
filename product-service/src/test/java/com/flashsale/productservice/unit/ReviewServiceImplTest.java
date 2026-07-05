package com.flashsale.productservice.unit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.productservice.entity.ProductReview;
import com.flashsale.productservice.mapper.ReviewMapper;
import com.flashsale.productservice.service.impl.ReviewServiceImpl;
import com.flashsale.productservice.vo.ReviewCreateReq;
import com.flashsale.productservice.vo.ReviewVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 单元测试 — ReviewServiceImpl
 * <p>Mock: ReviewMapper</p>
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewMapper reviewMapper;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Captor
    private ArgumentCaptor<ProductReview> reviewCaptor;

    @Test
    void createReview_ShouldReturnId() {
        ReviewCreateReq req = new ReviewCreateReq();
        req.setProductId(10L);
        req.setOrderId(100L);
        req.setRating(5);
        req.setContent("很好！");

        when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(reviewMapper.insert(any(ProductReview.class))).thenAnswer(inv -> {
            inv.getArgument(0, ProductReview.class).setId(55L);
            return 1;
        });

        Long id = reviewService.createReview(1L, req);
        assertThat(id).isEqualTo(55L);

        verify(reviewMapper).insert(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getRating()).isEqualTo(5);
    }

    @Test
    void createReview_ShouldThrow_WhenOrderAlreadyReviewed() {
        ReviewCreateReq req = new ReviewCreateReq();
        req.setOrderId(100L);

        when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> reviewService.createReview(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已评价");
        verify(reviewMapper, never()).insert(any(ProductReview.class));
    }

    @Test
    void getReviewsByProductId_ShouldReturnPage() {
        when(reviewMapper.selectReviewsByProductId(any(), eq(10L)))
                .thenAnswer(inv -> {
                    com.baomidou.mybatisplus.extension.plugins.pagination.Page<ReviewVO> pg =
                            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 2);
                    ReviewVO vo = new ReviewVO();
                    vo.setRating(4);
                    vo.setContent("不错");
                    pg.setRecords(List.of(vo, vo));
                    return pg;
                });

        var result = reviewService.getReviewsByProductId(10L, 1, 10);
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
    }
}
