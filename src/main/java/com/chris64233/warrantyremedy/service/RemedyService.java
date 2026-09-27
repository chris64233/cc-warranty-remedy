package com.chris64233.warrantyremedy.service;

import com.chris64233.warrantyremedy.api.CorrectionView;
import com.chris64233.warrantyremedy.api.RemedyView;
import com.chris64233.warrantyremedy.domain.ClaimStatus;
import com.chris64233.warrantyremedy.domain.ProductStatus;
import com.chris64233.warrantyremedy.domain.ProductUnit;
import com.chris64233.warrantyremedy.domain.Remedy;
import com.chris64233.warrantyremedy.domain.RemedyCorrection;
import com.chris64233.warrantyremedy.domain.RemedyStatus;
import com.chris64233.warrantyremedy.domain.RemedyType;
import com.chris64233.warrantyremedy.domain.ReplacementUnit;
import com.chris64233.warrantyremedy.domain.ReplacementUnitStatus;
import com.chris64233.warrantyremedy.domain.WarrantyClaim;
import com.chris64233.warrantyremedy.repo.ProductUnitRepository;
import com.chris64233.warrantyremedy.repo.RemedyCorrectionRepository;
import com.chris64233.warrantyremedy.repo.RemedyRepository;
import com.chris64233.warrantyremedy.repo.ReplacementUnitRepository;
import com.chris64233.warrantyremedy.repo.WarrantyClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

/**
 * 处置决策与执行。
 *
 * <p>决策规则：同一故障首次发生批准维修；同一故障在维修执行后复发，
 * 有可用替换品则换货，否则退款。换货在批准时原子锁定替换品，
 * 执行时在同一事务内完成保修转移，任一步失败整体回滚，
 * 不会留下被占用但未关联的替换品。
 */
@Service
public class RemedyService {

    private final RemedyRepository remedyRepository;
    private final RemedyCorrectionRepository correctionRepository;
    private final WarrantyClaimRepository claimRepository;
    private final ProductUnitRepository productRepository;
    private final ReplacementUnitRepository replacementUnitRepository;

    public RemedyService(RemedyRepository remedyRepository,
                         RemedyCorrectionRepository correctionRepository,
                         WarrantyClaimRepository claimRepository,
                         ProductUnitRepository productRepository,
                         ReplacementUnitRepository replacementUnitRepository) {
        this.remedyRepository = remedyRepository;
        this.correctionRepository = correctionRepository;
        this.claimRepository = claimRepository;
        this.productRepository = productRepository;
        this.replacementUnitRepository = replacementUnitRepository;
    }

    /**
     * 批准申请并生成处置决定。幂等：同一申请已有未撤销的处置时直接返回该决定；
     * 并发批准通过对申请行加悲观写锁串行化，只有一个事务能创建处置。
     */
    @Transactional
    public RemedyView approve(long claimId) {
        WarrantyClaim claim = claimRepository.findWithLockById(claimId)
                .orElseThrow(() -> new NotFoundException("申请不存在: " + claimId));
        var existing = remedyRepository.findByClaim_IdAndStatusIn(
                claimId, EnumSet.of(RemedyStatus.PENDING, RemedyStatus.EXECUTED));
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        if (claim.getStatus() != ClaimStatus.OPEN) {
            throw new ConflictException("当前申请状态不允许批准: " + claim.getStatus());
        }
        ProductUnit product = productRepository.findWithLockById(claim.getProduct().getId())
                .orElseThrow(() -> new NotFoundException("产品不存在: " + claim.getProduct().getId()));
        if (!product.warrantyActiveOn(LocalDate.now())) {
            throw new IneligibleException("产品不具备保修资格，不能批准处置");
        }

        Remedy remedy = decideRemedy(claim, product);
        claim.setStatus(ClaimStatus.APPROVED);
        return toView(remedy);
    }

    private Remedy decideRemedy(WarrantyClaim claim, ProductUnit product) {
        boolean repairedBefore = remedyRepository.existsByProductIdAndTypeAndStatusAndFaultCode(
                product.getId(), RemedyType.REPAIR, RemedyStatus.EXECUTED, claim.getFaultCode());
        RemedyType type = repairedBefore ? RemedyType.REPLACEMENT : RemedyType.REPAIR;
        Remedy remedy = remedyRepository.save(new Remedy(claim, product.getId(), type));
        if (type == RemedyType.REPLACEMENT) {
            Long unitId = lockAvailableUnit(remedy.getId());
            if (unitId == null) {
                // 无可用替换品，降级为退款
                remedy.setType(RemedyType.REFUND);
            } else {
                remedy.setReplacementUnitId(unitId);
            }
        }
        return remedy;
    }

    /** 原子锁定一个可用替换品；并发占用同一替换品时只有一个事务成功。 */
    private Long lockAvailableUnit(long remedyId) {
        for (ReplacementUnit unit : replacementUnitRepository.findByStatusOrderByIdAsc(ReplacementUnitStatus.AVAILABLE)) {
            if (replacementUnitRepository.lockIfAvailable(unit.getId(), remedyId) == 1) {
                return unit.getId();
            }
        }
        return null;
    }

    /**
     * 执行处置。换货在同一事务内完成：锁定态校验、新序列号产品实例创建、
     * 保修关系转移、替换品出库；任一步失败整体回滚。
     */
    @Transactional
    public RemedyView execute(long remedyId) {
        Remedy remedy = remedyRepository.findWithLockById(remedyId)
                .orElseThrow(() -> new NotFoundException("处置不存在: " + remedyId));
        if (remedy.getStatus() == RemedyStatus.EXECUTED) {
            return toView(remedy);
        }
        if (remedy.getStatus() != RemedyStatus.PENDING) {
            throw new ConflictException("仅待执行的处置可以执行，当前状态: " + remedy.getStatus());
        }
        ProductUnit product = productRepository.findWithLockById(remedy.getProductId())
                .orElseThrow(() -> new NotFoundException("产品不存在: " + remedy.getProductId()));

        switch (remedy.getType()) {
            case REPAIR -> {
                // 维修不改变产品状态，仅记录处置历史
            }
            case REPLACEMENT -> executeReplacement(remedy, product);
            case REFUND -> product.setStatus(ProductStatus.REFUNDED);
        }
        remedy.setStatus(RemedyStatus.EXECUTED);
        remedy.setExecutedAt(Instant.now());
        remedy.getClaim().setStatus(ClaimStatus.CLOSED);
        return toView(remedy);
    }

    private void executeReplacement(Remedy remedy, ProductUnit product) {
        ReplacementUnit unit = replacementUnitRepository.findById(remedy.getReplacementUnitId())
                .orElseThrow(() -> new NotFoundException("替换品不存在: " + remedy.getReplacementUnitId()));
        if (unit.getStatus() != ReplacementUnitStatus.LOCKED || !remedy.getId().equals(unit.getLockedByRemedyId())) {
            throw new ConflictException("替换品未被本处置锁定，不能执行换货");
        }
        // 保修关系转移：新实例沿用原销售日期与保修期限，序列号取自替换品
        ProductUnit newUnit = new ProductUnit(unit.getSerialNumber(), product.getSaleDate(), product.getWarrantyMonths());
        newUnit.setOriginUnitId(product.getId());
        productRepository.save(newUnit);

        product.setStatus(ProductStatus.REPLACED);
        product.setReplacedByUnitId(newUnit.getId());
        unit.setStatus(ReplacementUnitStatus.CONSUMED);
        remedy.setNewProductUnitId(newUnit.getId());
    }

    /** 撤销尚未执行的处置并释放占用的替换品；申请回到 OPEN 可重新批准。 */
    @Transactional
    public RemedyView cancel(long remedyId) {
        Remedy remedy = remedyRepository.findWithLockById(remedyId)
                .orElseThrow(() -> new NotFoundException("处置不存在: " + remedyId));
        if (remedy.getStatus() == RemedyStatus.CANCELLED) {
            return toView(remedy);
        }
        if (remedy.getStatus() != RemedyStatus.PENDING) {
            throw new ConflictException("已执行的处置不可撤销，只能追加纠正记录");
        }
        if (remedy.getReplacementUnitId() != null) {
            replacementUnitRepository.releaseIfLockedBy(remedy.getReplacementUnitId(), remedy.getId());
        }
        remedy.setStatus(RemedyStatus.CANCELLED);
        remedy.setCancelledAt(Instant.now());
        remedy.getClaim().setStatus(ClaimStatus.OPEN);
        return toView(remedy);
    }

    /** 已执行的处置只能追加纠正记录。 */
    @Transactional
    public CorrectionView addCorrection(long remedyId, String note) {
        Remedy remedy = remedyRepository.findById(remedyId)
                .orElseThrow(() -> new NotFoundException("处置不存在: " + remedyId));
        if (remedy.getStatus() != RemedyStatus.EXECUTED) {
            throw new ConflictException("仅已执行的处置可以追加纠正记录");
        }
        return CorrectionView.of(correctionRepository.save(new RemedyCorrection(remedy, note)));
    }

    @Transactional(readOnly = true)
    public RemedyView get(long remedyId) {
        return toView(remedyRepository.findById(remedyId)
                .orElseThrow(() -> new NotFoundException("处置不存在: " + remedyId)));
    }

    /** 产品的历史处置记录。 */
    @Transactional(readOnly = true)
    public List<RemedyView> remediesOfProduct(long productId) {
        if (!productRepository.existsById(productId)) {
            throw new NotFoundException("产品不存在: " + productId);
        }
        return remedyRepository.findByProductIdOrderByDecidedAtAsc(productId).stream()
                .map(this::toView)
                .toList();
    }

    public RemedyView toView(Remedy remedy) {
        String replacementSerial = null;
        if (remedy.getReplacementUnitId() != null) {
            replacementSerial = replacementUnitRepository.findById(remedy.getReplacementUnitId())
                    .map(ReplacementUnit::getSerialNumber)
                    .orElse(null);
        }
        String newSerial = null;
        if (remedy.getNewProductUnitId() != null) {
            newSerial = productRepository.findById(remedy.getNewProductUnitId())
                    .map(ProductUnit::getSerialNumber)
                    .orElse(null);
        }
        List<CorrectionView> corrections = correctionRepository
                .findByRemedy_IdOrderByCreatedAtAsc(remedy.getId())
                .stream()
                .map(CorrectionView::of)
                .toList();
        return new RemedyView(
                remedy.getId(),
                remedy.getClaim().getId(),
                remedy.getProductId(),
                remedy.getType(),
                remedy.getStatus(),
                remedy.getReplacementUnitId(),
                replacementSerial,
                remedy.getNewProductUnitId(),
                newSerial,
                corrections,
                remedy.getDecidedAt(),
                remedy.getExecutedAt(),
                remedy.getCancelledAt());
    }
}
