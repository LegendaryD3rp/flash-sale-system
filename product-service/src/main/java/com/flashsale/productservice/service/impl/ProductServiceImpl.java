package com.flashsale.productservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.mapper.ProductMapper;
import com.flashsale.productservice.service.ProductImageService;
import com.flashsale.productservice.service.ProductService;
import com.flashsale.productservice.service.ProductSkuService;
import com.flashsale.productservice.vo.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductImageService productImageService;
    private final ProductSkuService productSkuService;

    public ProductServiceImpl(ProductMapper productMapper, StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              ProductImageService productImageService,
                              ProductSkuService productSkuService) {
        this.productMapper = productMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.productImageService = productImageService;
        this.productSkuService = productSkuService;
    }

    @Override
    public IPage<ProductVO> listProducts(int page, int size, String keyword, String category,
                                         Long minPrice, Long maxPrice, String role) {
        // Build query conditions
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // CUSTOMER can only see ON-shelf products; ADMIN sees all
        if (!"ADMIN".equals(role)) {
            wrapper.eq(Product::getStatus, "ON");
        }

        // Keyword search on name
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Product::getName, keyword);
        }

        // Category filter
        if (StringUtils.hasText(category)) {
            wrapper.eq(Product::getCategory, category);
        }

        // Price range filter
        if (minPrice != null) {
            wrapper.ge(Product::getPrice, minPrice);
        }
        if (maxPrice != null) {
            wrapper.le(Product::getPrice, maxPrice);
        }

        // Sort by created_at desc
        wrapper.orderByDesc(Product::getCreatedAt);

        // Paginate
        Page<Product> productPage = productMapper.selectPage(new Page<>(page, size), wrapper);

        // Convert to VO
        return productPage.convert(this::toVO);
    }

    @Override
    public IPage<ProductVO> search(int page, int size, String keyword) {
        IPage<Product> productPage = productMapper.searchProducts(new Page<>(page, size), keyword);
        return productPage.convert(this::toVO);
    }

    @Override
    public ProductVO getProductById(Long id) {
        String cacheKey = "product:" + id;

        // 1. Try Redis cache first
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ProductVO.class);
            } catch (Exception e) {
                // Deserialization failure — fall through to DB
            }
        }

        // 2. Cache miss — query database
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        ProductVO vo = toVO(product);
        // Load images and SKUs
        vo.setImages(productImageService.findByProductId(id));
        vo.setSkus(productSkuService.findByProductId(id));

        // 3. Backfill cache
        try {
            String json = objectMapper.writeValueAsString(vo);
            redisTemplate.opsForValue().set(cacheKey, json, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            // Cache write failure is non-fatal
        }

        return vo;
    }

    @Override
    @Transactional
    public Long createProduct(ProductCreateReq req) {
        Product product = new Product();
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setPrice(req.getPrice());
        product.setImageUrl(req.getImageUrl());
        product.setCategory(req.getCategory());
        product.setStock(req.getStock());
        product.setStatus("ON");

        productMapper.insert(product);
        return product.getId();
    }

    @Override
    @Transactional
    public Long updateProduct(Long id, ProductUpdateReq req) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }

        Product update = new Product();
        update.setId(id);
        if (req.getName() != null) update.setName(req.getName());
        if (req.getDescription() != null) update.setDescription(req.getDescription());
        if (req.getPrice() != null) update.setPrice(req.getPrice());
        if (req.getImageUrl() != null) update.setImageUrl(req.getImageUrl());
        if (req.getCategory() != null) update.setCategory(req.getCategory());
        if (req.getStock() != null) update.setStock(req.getStock());
        productMapper.updateById(update);

        // Save images and SKUs if provided
        if (req.getImages() != null) {
            productImageService.saveImages(id, req.getImages());
        }
        if (req.getSkus() != null) {
            productSkuService.saveSkus(id, req.getSkus());
        }

        // Clear cache
        redisTemplate.delete("product:" + id);

        return id;
    }

    @Override
    public Long deleteProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        Product update = new Product();
        update.setId(id);
        update.setStatus("OFF");
        productMapper.updateById(update);

        redisTemplate.delete("product:" + id);
        return id;
    }

    private ProductVO toVO(Product product) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setName(product.getName());
        vo.setDescription(product.getDescription());
        vo.setPrice(product.getPrice());
        vo.setImageUrl(product.getImageUrl());
        vo.setCategory(product.getCategory());
        vo.setStock(product.getStock());
        vo.setStatus(product.getStatus());
        vo.setCreatedAt(product.getCreatedAt());
        vo.setUpdatedAt(product.getUpdatedAt());
        return vo;
    }
}
