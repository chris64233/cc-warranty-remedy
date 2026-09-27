package com.chris64233.warrantyremedy.service;

import com.chris64233.warrantyremedy.api.DomainMapper;
import com.chris64233.warrantyremedy.api.Views.ClaimView;
import com.chris64233.warrantyremedy.api.Views.CorrectionView;
import com.chris64233.warrantyremedy.api.Views.DispositionView;
import com.chris64233.warrantyremedy.domain.ClaimEvidence;
import com.chris64233.warrantyremedy.domain.ClaimStatus;
import com.chris64233.warrantyremedy.domain.CorrectionType;
import com.chris64233.warrantyremedy.domain.Disposition;
import com.chris64233.warrantyremedy.domain.DispositionCorrection;
import com.chris64233.warrantyremedy.domain.DispositionStatus;
import com.chris64233.warrantyremedy.domain.DispositionType;
import com.chris64233.warrantyremedy.domain.EligibilityDecision;
import com.chris64233.warrantyremedy.domain.EvidenceType;
import com.chris64233.warrantyremedy.domain.Product;
import com.chris64233.warrantyremedy.domain.WarrantyClaim;
import com.chris64233.warrantyremedy.repo.DispositionRepository;
import com.chris64233.warrantyremedy.repo.ProductRepository;
import com.chris64233.warrantyremedy.repo.WarrantyClaimRepository;
import com.chris64233.warrantyremedy.service.dto.ApproveDispositionCommand;
import com.chris64233.warrantyremedy.service.dto.CorrectionCommand;
import com.chris64233.warrantyremedy.service.dto.SubmitClaimCommand;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 保修申请与处置的核心应用服务。
 *
 * <p>关键事务保证：
 * <ul>
 *   <li>提交：外部申请号唯一约束实现幂等；活动去重键唯一约束保证同一产品同一故障只有一笔活动申请；
 *       资格判断在受理时固化，此后不可修改；</li>
 *   <li>批准换货：以条件更新 {@code IN_STOCK -> HELD} 原子占用替换品，并发下至多一个事务成功；
 *       占用与处置记录写入同一事务，任一步失败整体回滚，绝不留下"被占用但未关联"的替换品；</li>
 *   <li>执行换货：原产品 REPLACED、保修关系（销售日期/保修期限/换货链）转移到新序列号，同一事务；</li>
 *   <li>执行退款：原产品 REFUNDED，后续保修资格终止；</li>
 *   <li>撤销：仅未执行的处置可撤销，换货占用的替换品原子释放回库存；</li>
 *   <li>已执行处置不可修改/撤销，只能追加纠正记录。</li>
 * </ul>
 */
@Service
public class WarrantyClaimService {

    private final WarrantyClaimRepository claims;
    private final ProductRepository products;
    private final DispositionRepository dispositions;
    private final EligibilityEvaluator eligibilityEvaluator;
    private final DomainMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTemplate;

    public WarrantyClaimService(WarrantyClaimRepository claims, ProductRepository products,
                                DispositionRepository dispositions,
                                EligibilityEvaluator eligibilityEvaluator, DomainMapper mapper, Clock clock,
                                PlatformTransactionManager txManager) {
        this.claims = claims;
        this.products = products;
        this.dispositions = dispositions;
        this.eligibilityEvaluator = eligibilityEvaluator;
        this.mapper = mapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(txManager);
        this.readOnlyTemplate = new TransactionTemplate(txManager);
        this.readOnlyTemplate.setReadOnly(true);
    }

    private ClaimView loadViewById(String externalRef) {
        return readOnlyTemplate.execute(status -> mapper.claimView(
                claims.findByExternalRef(externalRef).orElseThrow()));
    }

    // ---------------------------------------------------------------- 提交

    /**
     * 受理保修申请（外部申请号幂等）。
     *
     * <p>并发提交同一外部申请号时，抢到唯一约束的事务提交成功；其余事务在约束冲突回滚后，
     * 于新事务中重读胜者申请并原样返回——并发下所有同号调用得到同一笔申请（幂等成功），
     * 而不是报错。若冲突来自"同产品同故障的活动申请去重键"（外部申请号不同），则抛出
     * {@link BusinessRuleException}。
     *
     * @throws ValidationException   缺少必填项或购买凭证，或资格不符合
     * @throws BusinessRuleException 同产品同故障已有活动申请（不同外部申请号）
     */
    public ClaimView submit(SubmitClaimCommand cmd) {
        String externalRef = requireText(cmd.externalRef(), "外部申请号");

        if (claims.findByExternalRef(externalRef).isPresent()) {
            return loadViewById(externalRef);
        }

        // 输入校验在事务外完成，避免无谓占用事务/连接
        String symptom = requireText(cmd.symptom(), "故障现象");
        String serial = requireText(cmd.serialNumber(), "序列号");
        List<ClaimEvidence> evidences = parseEvidences(cmd);

        try {
            return transactionTemplate.execute(status -> persistNewClaim(externalRef, serial, symptom, evidences));
        } catch (DataIntegrityViolationException e) {
            // 并发窗口：胜者可能刚刚提交。外部申请号一致则幂等返回；否则是活动申请去重冲突。
            if (claims.findByExternalRef(externalRef).isPresent()) {
                return loadViewById(externalRef);
            }
            throw new BusinessRuleException("同一产品同一故障已有一笔活动申请");
        }
    }

    private ClaimView persistNewClaim(String externalRef, String serial, String symptom,
                                      List<ClaimEvidence> evidences) {
        Product product = products.findBySerialNumber(serial)
                .orElseThrow(() -> new ValidationException("产品不存在：" + serial));
        EligibilityDecision decision = eligibilityEvaluator.evaluate(product);
        if (!decision.isEligible()) {
            throw new ValidationException("产品不符合保修资格：" + decision.getReason()
                    + "（" + decision.getNote() + "）");
        }
        String dedupeKey = activeDedupeKey(product.getId(), normalizeSymptom(symptom));
        WarrantyClaim claim = WarrantyClaim.submit(externalRef, product, symptom, dedupeKey,
                LocalDateTime.now(clock));
        claim.attachEligibility(decision);
        evidences.forEach(claim::addEvidence);
        claims.saveAndFlush(claim);
        return mapper.claimView(claim);
    }

    private List<ClaimEvidence> parseEvidences(SubmitClaimCommand cmd) {
        if (cmd.evidences() == null || cmd.evidences().isEmpty()) {
            throw new ValidationException("申请必须至少包含一份购买凭证");
        }
        List<ClaimEvidence> parsed = cmd.evidences().stream().map(e -> {
            if (e == null || e.reference() == null || e.reference().isBlank()) {
                throw new ValidationException("证据引用（文件/链接）不能为空");
            }
            EvidenceType type = parseEvidenceType(e.type());
            return new ClaimEvidence(type, e.reference().trim(), e.note());
        }).toList();
        if (parsed.stream().noneMatch(e -> e.getType() == EvidenceType.PURCHASE_PROOF)) {
            throw new ValidationException("申请必须至少包含一份购买凭证（PURCHASE_PROOF）");
        }
        return parsed;
    }

    private EvidenceType parseEvidenceType(String raw) {
        try {
            return EvidenceType.valueOf(requireText(raw, "证据类型").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("未知证据类型：" + raw);
        }
    }

    // ---------------------------------------------------------------- 批准

    /**
     * 批准处置。申请必须处于 SUBMITTED；REPLACEMENT 需给出在库替换品序列号。
     * 换货占用与处置记录原子提交。
     */
    @Transactional
    public DispositionView approve(Long claimId, ApproveDispositionCommand cmd) {
        Objects.requireNonNull(claimId, "claimId");
        WarrantyClaim claim = claims.findByIdForUpdate(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在：id=" + claimId));

        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BusinessRuleException("申请当前状态为 " + claim.getStatus() + "，不能批准（并发下只有一笔批准成功）");
        }
        if (!claim.getEligibility().isEligible()) {
            throw new ValidationException("申请不符合保修资格，不能批准处置");
        }

        DispositionType type = parseDispositionType(cmd.type());
        LocalDateTime now = LocalDateTime.now(clock);

        // 先完成所有输入校验，再占用替换品，杜绝"占用后因校验失败回滚"之外的半占用窗口
        Long refundAmount = type == DispositionType.REFUND ? cmd.refundAmountCents() : null;
        if (refundAmount != null && refundAmount < 0) {
            throw new ValidationException("退款金额不能为负");
        }
        if (type == DispositionType.REPLACEMENT) {
            requireText(cmd.replacementSerial(), "替换品序列号");
        }

        Product replacement = null;
        if (type == DispositionType.REPLACEMENT) {
            replacement = holdReplacement(cmd.replacementSerial(), claim);
        }

        // 占用成功之后、提交之前若本事务内再抛任何异常，事务回滚会把替换品恢复为 IN_STOCK，
        // 因此不会出现"被占用但未关联处置"的替换品。
        Disposition disposition = Disposition.approved(claim, type, replacement, refundAmount, cmd.note(), now);
        claim.attachDisposition(disposition);
        dispositions.save(disposition);
        return mapper.dispositionView(disposition);
    }

    /**
     * 原子占用替换品：条件更新仅命中 IN_STOCK 行；返回更新后重新加载的托管实例。
     */
    private Product holdReplacement(String serial, WarrantyClaim claim) {
        String replacementSerial = requireText(serial, "替换品序列号");
        Long replacementId = products.findIdBySerialNumber(replacementSerial)
                .orElseThrow(() -> new ResourceNotFoundException("替换品不存在：" + replacementSerial));
        if (replacementId.equals(claim.getProduct().getId())) {
            throw new ValidationException("替换品不能是故障产品自身");
        }
        int updated = products.holdIfInStock(replacementId);
        products.flush();
        if (updated == 0) {
            throw new ConcurrencyConflictException(
                    "替换品 " + replacementSerial + " 已被其他申请占用或不在库存，并发占用只有一笔成功");
        }
        return products.findByIdForUpdate(replacementId)
                .orElseThrow(() -> new ResourceNotFoundException("替换品不存在：id=" + replacementId));
    }

    // ---------------------------------------------------------------- 执行

    /**
     * 执行已批准处置。
     *
     * <p>维修：仅推进状态；换货：转移保修关系到新序列号；退款：终止原产品保修资格。
     */
    @Transactional
    public DispositionView execute(Long claimId) {
        WarrantyClaim claim = claims.findByIdForUpdate(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在：id=" + claimId));

        if (claim.getStatus() == ClaimStatus.EXECUTED) {
            return mapper.dispositionView(claim.getDisposition());
        }
        if (claim.getStatus() != ClaimStatus.APPROVED || claim.getDisposition() == null) {
            throw new BusinessRuleException("申请当前状态为 " + claim.getStatus() + "，没有已批准待执行的处置");
        }

        Disposition disposition = claim.getDisposition();
        Product product = products.findByIdForUpdate(claim.getProduct().getId())
                .orElseThrow(() -> new ResourceNotFoundException("产品不存在：id=" + claim.getProduct().getId()));
        LocalDateTime now = LocalDateTime.now(clock);

        switch (disposition.getType()) {
            case REPAIR -> {
                // 维修不改变产品序列号与保修关系
            }
            case REPLACEMENT -> {
                Product replacement = products.findByIdForUpdate(disposition.getReplacement().getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "替换品不存在：id=" + disposition.getReplacement().getId()));
                replacement.inheritWarrantyFrom(product);
            }
            case REFUND -> product.markRefunded();
        }

        disposition.markExecuted(now);
        claim.markExecuted(now);
        claims.saveAndFlush(claim);
        return mapper.dispositionView(disposition);
    }

    // ---------------------------------------------------------------- 撤销

    /**
     * 撤销尚未执行的申请/处置。换货占用的替换品原子释放回库存；已执行处置不可撤销。
     */
    @Transactional
    public ClaimView cancel(Long claimId, String reason) {
        WarrantyClaim claim = claims.findByIdForUpdate(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在：id=" + claimId));
        if (claim.getStatus() == ClaimStatus.EXECUTED) {
            throw new BusinessRuleException("处置已执行，不能撤销；只能追加纠正记录");
        }
        if (claim.getStatus() == ClaimStatus.CANCELLED) {
            return mapper.claimView(claim);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Disposition disposition = claim.getDisposition();
        if (disposition != null) {
            if (disposition.getStatus() != DispositionStatus.APPROVED) {
                throw new BusinessRuleException("处置当前状态为 " + disposition.getStatus() + "，不能撤销");
            }
            Product replacement = disposition.getReplacement();
            if (replacement != null) {
                int released = products.releaseIfHeld(replacement.getId());
                products.flush();
                if (released == 0) {
                    throw new ConcurrencyConflictException("替换品状态异常，无法释放：id=" + replacement.getId());
                }
            }
            disposition.markCancelled(now);
        }
        claim.markCancelled(now, reason);
        claims.saveAndFlush(claim);
        return mapper.claimView(claim);
    }

    // ---------------------------------------------------------------- 纠正

    /** 已执行处置只能追加纠正记录，纠正本身也不可修改/删除。 */
    @Transactional
    public CorrectionView addCorrection(Long claimId, CorrectionCommand cmd) {
        WarrantyClaim claim = claims.findByIdForUpdate(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在：id=" + claimId));
        Disposition disposition = claim.getDisposition();
        if (claim.getStatus() != ClaimStatus.EXECUTED || disposition == null) {
            throw new BusinessRuleException("只有已执行的处置才能追加纠正记录（申请状态：" + claim.getStatus() + "）");
        }
        String detail = requireText(cmd == null ? null : cmd.detail(), "纠正说明");
        CorrectionType type;
        try {
            type = CorrectionType.valueOf(requireText(cmd.type(), "纠正类型").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("未知纠正类型：" + cmd.type());
        }
        if (cmd.amountCents() != null && cmd.amountCents() < 0) {
            throw new ValidationException("纠正金额不能为负");
        }
        DispositionCorrection correction =
                new DispositionCorrection(type, detail, cmd.amountCents(), LocalDateTime.now(clock));
        disposition.addCorrection(correction);
        claims.saveAndFlush(claim);
        return mapper.correctionView(correction);
    }

    // ---------------------------------------------------------------- 查询

    /** 申请详情（证据、资格判断、处置、替换品、纠正记录）。 */
    @Transactional(readOnly = true)
    public ClaimView getClaimDetail(String externalRef) {
        return mapper.claimView(claims.findDetailByExternalRef(requireText(externalRef, "外部申请号"))
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在：" + externalRef)));
    }

    @Transactional(readOnly = true)
    public ClaimView getClaimDetail(Long id) {
        return mapper.claimView(claims.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在：id=" + id)));
    }

    /** 一台产品（含换货链上的自身）的全部处置记录，按决定时间升序。 */
    @Transactional(readOnly = true)
    public List<DispositionView> getDispositions(String serialNumber) {
        Product p = products.findBySerialNumber(requireText(serialNumber, "序列号"))
                .orElseThrow(() -> new ResourceNotFoundException("产品不存在：" + serialNumber));
        return dispositions.findByProductId(p.getId()).stream()
                .map(mapper::dispositionView).toList();
    }

    // ---------------------------------------------------------------- 辅助

    private DispositionType parseDispositionType(String raw) {
        try {
            return DispositionType.valueOf(requireText(raw, "处置类型").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("未知处置类型：" + raw + "（应为 REPAIR/REPLACEMENT/REFUND）");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + "不能为空");
        }
        return value.trim();
    }

    /** 故障现象规范化作为同一故障的判定依据：去空白、转小写、压缩连续空白。 */
    static String normalizeSymptom(String symptom) {
        return symptom.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * 活动申请去重键：对 {@code 产品id|规范化故障} 取 SHA-256 定长哈希。
     * 定长可规避长故障描述在不同数据库上的唯一索引长度上限，碰撞概率可忽略。
     */
    static String activeDedupeKey(Long productId, String normalizedSymptom) {
        String raw = productId + "|" + normalizedSymptom;
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(
                            raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
