package com.flashsale.productservice.unit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.mapper.ProductMapper;
import com.flashsale.productservice.service.ProductImageService;
import com.flashsale.productservice.service.ProductSkuService;
import com.flashsale.productservice.service.impl.ProductServiceImpl;
import com.flashsale.productservice.vo.ProductCreateReq;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceImplTest {

    private ProductMapper productMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ProductImageService productImageService;
    private ProductSkuService productSkuService;
    private ObjectMapper objectMapper;

    private ProductServiceImpl productService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        productMapper = mock(ProductMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        productImageService = mock(ProductImageService.class);
        productSkuService = mock(ProductSkuService.class);
        objectMapper = new ObjectMapper();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        productService = new ProductServiceImpl(productMapper, redisTemplate,
                objectMapper, productImageService, productSkuService);
    }

    @Test
    void createProduct_ShouldReturnId() {
        ProductCreateReq req = new ProductCreateReq();
        req.setName("测试手机");
        req.setPrice(599900L);

        when(productMapper.insert(any(Product.class))).thenAnswer(inv -> {
            inv.getArgument(0, Product.class).setId(42L);
            return 1;
        });

        Long id = productService.createProduct(req);
        assertThat(id).isEqualTo(42L);
        verify(productMapper).insert(any(Product.class));
    }

    @Test
    void getProductById_ShouldReturnFromCache_WhenHit() throws Exception {
        String json = "{\"id\":1,\"name\":\"缓存手机\",\"price\":299900}";
        when(valueOps.get("product:1")).thenReturn(json);

        var vo = productService.getProductById(1L);
        assertThat(vo.getName()).isEqualTo("缓存手机");
        verify(productMapper, never()).selectById(any());
    }

    @Test
    void getProductById_ShouldQueryDB_WhenCacheMiss() {
        when(valueOps.get("product:1")).thenReturn(null);
        Product p = new Product();
        p.setId(1L);
        p.setName("数据库手机");
        p.setPrice(199900L);
        when(productMapper.selectById(1L)).thenReturn(p);

        var vo = productService.getProductById(1L);
        assertThat(vo.getName()).isEqualTo("数据库手机");
        verify(productMapper).selectById(1L);
    }

    @Test
    void getProductById_ShouldThrow_WhenNotExist() {
        when(valueOps.get("product:999")).thenReturn(null);
        when(productMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> productService.getProductById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    void listProducts_ShouldReturnPage() {
        Product p = new Product();
        p.setName("商品1");
        p.setPrice(10000L);
        when(productMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> {
                    Page<Product> pg = new Page<>(1, 10, 1);
                    pg.setRecords(List.of(p));
                    return pg;
                });

        IPage<?> result = productService.listProducts(1, 10, null, null, null, null, "CUSTOMER");
        assertThat(result.getRecords()).isNotEmpty();
    }

    @Test
    void search_ShouldReturn_ByKeyword() {
        Product p = new Product();
        p.setName("iphone 15");
        when(productMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> {
                    Page<Product> pg = new Page<>(1, 10, 1);
                    pg.setRecords(List.of(p));
                    return pg;
                });

        IPage<?> result = productService.listProducts(1, 10, "iphone", null, null, null, "CUSTOMER");
        assertThat(result.getRecords()).isNotEmpty();
    }

    @Test
    void search_ShouldReturnEmpty_WhenNoMatch() {
        when(productMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> {
                    Page<Product> pg = new Page<>(1, 10, 0);
                    pg.setRecords(List.of());
                    return pg;
                });

        IPage<?> result = productService.listProducts(1, 10, "zzzznotexist", null, null, null, "CUSTOMER");
        assertThat(result.getRecords()).isEmpty();
    }
}
