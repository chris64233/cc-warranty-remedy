package com.chris64233.warrantyremedy;

import com.chris64233.warrantyremedy.service.ProductService;
import com.chris64233.warrantyremedy.service.WarrantyClaimService;
import com.chris64233.warrantyremedy.service.dto.ApproveDispositionCommand;
import com.chris64233.warrantyremedy.service.dto.SubmitClaimCommand;
import com.chris64233.warrantyremedy.service.dto.SubmitClaimCommand.EvidenceCommand;
import java.time.LocalDate;
import java.util.List;

/**
 * 测试夹具：构造常见业务数据与命令。
 */
public class TestFixtures {

    public static final List<EvidenceCommand> PROOF =
            List.of(new EvidenceCommand("PURCHASE_PROOF", "receipt-001.pdf", "电子发票"));

    private final ProductService products;
    private final WarrantyClaimService claims;

    public TestFixtures(ProductService products, WarrantyClaimService claims) {
        this.products = products;
        this.claims = claims;
    }

    public void registerProduct(String serial, LocalDate saleDate, int warrantyMonths) {
        products.register(serial, saleDate, warrantyMonths);
    }

    public void registerStock(String serial, int warrantyMonths) {
        products.registerStockUnit(serial, warrantyMonths);
    }

    public Long submitClaim(String ref, String serial, String symptom) {
        return claims.submit(new SubmitClaimCommand(ref, serial, symptom, PROOF)).id();
    }

    public Long submitClaim(String ref, String serial, String symptom, List<EvidenceCommand> evidences) {
        return claims.submit(new SubmitClaimCommand(ref, serial, symptom, evidences)).id();
    }

    public void approveRepair(Long claimId) {
        claims.approve(claimId, new ApproveDispositionCommand("REPAIR", null, null, "维修"));
    }

    public void approveReplacement(Long claimId, String replacementSerial) {
        claims.approve(claimId,
                new ApproveDispositionCommand("REPLACEMENT", replacementSerial, null, "换货"));
    }

    public void approveRefund(Long claimId, Long amountCents) {
        claims.approve(claimId,
                new ApproveDispositionCommand("REFUND", null, amountCents, "退款"));
    }
}
