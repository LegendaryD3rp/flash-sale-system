package com.flashsale.productservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.productservice.entity.ProductImage;
import com.flashsale.productservice.mapper.ProductImageMapper;
import com.flashsale.productservice.service.ProductImageService;
import com.flashsale.productservice.vo.ProductImageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageMapper productImageMapper;

    public ProductImageServiceImpl(ProductImageMapper productImageMapper) {
        this.productImageMapper = productImageMapper;
    }

    @Override
    public List<ProductImageVO> findByProductId(Long productId) {
        LambdaQueryWrapper<ProductImage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductImage::getProductId, productId);
        wrapper.orderByAsc(ProductImage::getSortOrder);
        return productImageMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveImages(Long productId, List<ProductImageVO> images) {
        // 先删旧图
        LambdaQueryWrapper<ProductImage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductImage::getProductId, productId);
        productImageMapper.delete(wrapper);

        // 再插新图
        if (images != null) {
            for (int i = 0; i < images.size(); i++) {
                ProductImage image = new ProductImage();
                image.setProductId(productId);
                image.setImageUrl(images.get(i).getImageUrl());
                image.setSortOrder(images.get(i).getSortOrder() != null ? images.get(i).getSortOrder() : i);
                productImageMapper.insert(image);
            }
        }
    }

    private ProductImageVO toVO(ProductImage image) {
        ProductImageVO vo = new ProductImageVO();
        vo.setId(image.getId());
        vo.setImageUrl(image.getImageUrl());
        vo.setSortOrder(image.getSortOrder());
        return vo;
    }
}
