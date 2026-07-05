package com.flashsale.productservice.unit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.productservice.controller.FavoriteController;
import com.flashsale.productservice.entity.UserFavorite;
import com.flashsale.productservice.mapper.FavoriteMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteControllerTest {

    @Mock
    private FavoriteMapper favoriteMapper;

    @InjectMocks
    private FavoriteController favoriteController;

    @Test
    void addFavorite_ShouldInsert_WhenNotExist() {
        when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(favoriteMapper.insert(any(UserFavorite.class))).thenReturn(1);

        var result = favoriteController.addFavorite(Map.of("productId", 1L), 1L);

        assertThat(result.getCode()).isEqualTo(0);
        verify(favoriteMapper).insert(any(UserFavorite.class));
    }

    @Test
    void addFavorite_ShouldIdempotent_WhenAlreadyFavorited() {
        when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        var result = favoriteController.addFavorite(Map.of("productId", 1L), 1L);

        assertThat(result.getCode()).isEqualTo(0);
        verify(favoriteMapper, never()).insert(any(UserFavorite.class));
    }

    @Test
    void removeFavorite_ShouldDelete() {
        var result = favoriteController.removeFavorite(1L, 1L);
        assertThat(result.getCode()).isEqualTo(0);
        verify(favoriteMapper).delete(any(LambdaQueryWrapper.class));
    }
}
