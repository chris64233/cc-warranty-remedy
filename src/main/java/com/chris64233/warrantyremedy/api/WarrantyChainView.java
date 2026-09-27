package com.chris64233.warrantyremedy.api;

import java.util.List;

/** 产品保修链：从最初的产品实例到当前实例的换货序列。 */
public record WarrantyChainView(List<ProductView> chain) {
}
