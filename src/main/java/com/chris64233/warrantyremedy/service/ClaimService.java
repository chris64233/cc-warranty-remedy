package com.chris64233.warrantyremedy.service;

import com.chris64233.warrantyremedy.api.ClaimView;
import com.chris64233.warrantyremedy.api.SubmitClaimRequest;
import com.chris64233.warrantyremedy.domain.ClaimStatus;
import com.chris64233.warrantyremedy.domain.EligibilityDecision;
import com.chris64233.warrantyremedy.domain.ProductUnit;
import com.chris64233.warrantyremedy.domain.WarrantyClaim;
import com.chris64233.warrantyremedy.repo.EligibilityDecisionRepository;
import com.chris64233.warrantyremedy.repo.ProductUnitRepository;
import com.chris64233.warrantyremedy.repo.WarrantyClaimRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * 保修申请提交与查询。
 *
 * <p>幂等：外部申请号全局唯一，重复提交返回已存在的申请；
 * 同一产品同一故障只允许一笔活动申请（OPEN/APPROVED），
 * 通过对产品行加悲观写锁串行化检查与创建。
 */
@Service
public class ClaimService {

    /** 活动申请状态：占用“同一产品同一故障”名额。 */
    static final Set<ClaimStatus> ACTIVE_STATUSES = EnumSet.of(ClaimStatus.OPEN, ClaimStatus.APPROVED);

    private final WarrantyClaimRepository claimRepository;
    private final EligibilityDecisionRepository decisionRepository;
    private final ProductUnitRepository productRepository;
    private final TransactionTemplate tx;

    public ClaimService(WarrantyClaimRepository claimRepository,
                        EligibilityDecisionRepository decisionRepository,
                        ProductUnitRepository productRepository,
                        PlatformTransactionManager transactionManager) {
        this.claimRepository = claimRepository;
        this.decisionRepository = decisionRepository;
        this.productRepository = productRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public ClaimView submit(SubmitClaimRequest request) {
        var existing = claimRepository.findByExternalClaimNo(request.externalClaimNo());
        if (existing.isPresent()) {
            return loadView(existing.get().getId());
        }
        try {
            return tx.execute(status -> doSubmit(request));
        } catch (DataIntegrityViolationException e) {
            // 并发提交同一外部申请号：唯一约束兜底，返回已存在的申请
            return claimRepository.findByExternalClaimNo(request.externalClaimNo())
                    .map(claim -> loadView(claim.getId()))
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public ClaimView get(long claimId) {
        return loadView(claimId);
    }

    private ClaimView loadView(long claimId) {
        WarrantyClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException("申请不存在: " + claimId));
        EligibilityDecision decision = decisionRepository.findByClaim_Id(claimId).orElse(null);
        return ClaimView.of(claim, decision);
    }

    private ClaimView doSubmit(SubmitClaimRequest request) {
        ProductUnit product = productRepository.findWithLockById(request.productId())
                .orElseThrow(() -> new NotFoundException("产品不存在: " + request.productId()));
        // 持有产品锁后重查外部申请号：并发提交同一申请号时，后到的线程返回已存在的申请
        var existing = claimRepository.findByExternalClaimNo(request.externalClaimNo());
        if (existing.isPresent()) {
            WarrantyClaim claim = existing.get();
            return ClaimView.of(claim, decisionRepository.findByClaim_Id(claim.getId()).orElse(null));
        }
        boolean duplicateActive = claimRepository.existsByProduct_IdAndFaultCodeAndStatusIn(
                product.getId(), request.faultCode(), ACTIVE_STATUSES);
        if (duplicateActive) {
            throw new ConflictException("同一产品同一故障已存在活动申请");
        }
        WarrantyClaim claim = claimRepository.save(new WarrantyClaim(
                product, request.faultCode(), request.faultDescription(),
                request.proofOfPurchase(), request.externalClaimNo()));

        LocalDate today = LocalDate.now();
        boolean eligible = product.warrantyActiveOn(today);
        EligibilityDecision decision = decisionRepository.save(
                new EligibilityDecision(claim, eligible, eligibilityReason(product, today)));
        if (!eligible) {
            claim.setStatus(ClaimStatus.REJECTED);
        }
        return ClaimView.of(claim, decision);
    }

    private String eligibilityReason(ProductUnit product, LocalDate today) {
        return switch (product.getStatus()) {
            case REFUNDED -> "产品已退款，保修资格已终止";
            case REPLACED -> "产品已换货，保修资格已转移至新序列号";
            case ACTIVE -> product.warrantyEndDate().isBefore(today) ? "已过保修期限" : "在保修期内";
        };
    }
}
