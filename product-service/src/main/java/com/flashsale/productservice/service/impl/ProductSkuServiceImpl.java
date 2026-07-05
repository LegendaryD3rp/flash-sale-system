package com.flashsale.productservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashsale.productservice.entity.ProductSku;
import com.flashsale.productservice.mapper.ProductSkuMapper;
import com.flashsale.productservice.service.ProductSkuService;
import com.flashsale.productservice.vo.ProductSkuVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductSkuServiceImpl implements ProductSkuService {

    private final ProductSkuMapper productSkuMapper;

    public ProductSkuServiceImpl(ProductSkuMapper productSkuMapper) {
        this.productSkuMapper = productSkuMapper;
    }

    @Override
    public List<ProductSkuVO> findByProductId(Long productId) {
        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductSku::getProductId, productId);
        return productSkuMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveSkus(Long productId, List<ProductSkuVO> skus) {
        // 先删旧SKU
        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductSku::getProductId, productId);
        productSkuMapper.delete(wrapper);

        // 再插新SKU
        if (skus != null) {
            for (ProductSkuVO vo : skus) {
                ProductSku sku = new ProductSku();
                sku.setProductId(productId);
                sku.setName(vo.getName());
                sku.setPrice(vo.getPrice());
                sku.setStock(vo.getStock());
                sku.setImageUrl(vo.getImageUrl());
                productSkuMapper.insert(sku);
            }
        }
    }

    private ProductSkuVO toVO(ProductSku sku) {
        ProductSkuVO vo = new ProductSkuVO();
        vo.setId(sku.getId());
        vo.setName(sku.getName());
        vo.setPrice(sku.getPrice());
        vo.setStock(sku.getStock());
        vo.setImageUrl(sku.getImageUrl());
        return vo;
    }
}
