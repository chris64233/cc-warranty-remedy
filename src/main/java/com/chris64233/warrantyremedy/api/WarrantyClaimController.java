package com.chris64233.warrantyremedy.api;

import com.chris64233.warrantyremedy.api.Views.ClaimView;
import com.chris64233.warrantyremedy.api.Views.CorrectionView;
import com.chris64233.warrantyremedy.api.Views.DispositionView;
import com.chris64233.warrantyremedy.service.WarrantyClaimService;
import com.chris64233.warrantyremedy.service.dto.ApproveDispositionCommand;
import com.chris64233.warrantyremedy.service.dto.CorrectionCommand;
import com.chris64233.warrantyremedy.service.dto.SubmitClaimCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 保修申请与处置：提交（幂等）、批准、执行、撤销、追加纠正，以及申请证据/资格/替换关系/
 * 处置记录查询。
 */
@RestController
@RequestMapping("/api/claims")
public class WarrantyClaimController {

    private final WarrantyClaimService claimService;

    public WarrantyClaimController(WarrantyClaimService claimService) {
        this.claimService = claimService;
    }

    public record SubmitRequest(
            @NotBlank String externalRef,
            @NotBlank String serialNumber,
            @NotBlank String symptom,
            // 非空与"至少一份购买凭证"的校验在服务层完成（给出明确的业务错误）
            @NotNull List<SubmitClaimCommand.EvidenceCommand> evidences) {
    }

    public record ApproveRequest(
            @NotBlank String type,
            String replacementSerial,
            Long refundAmountCents,
            String note) {
    }

    public record CancelRequest(String reason) {
    }

    public record CorrectionRequest(
            @NotBlank String type,
            @NotBlank String detail,
            Long amountCents) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimView submit(@Valid @RequestBody SubmitRequest req) {
        return claimService.submit(new SubmitClaimCommand(
                req.externalRef(), req.serialNumber(), req.symptom(), req.evidences()));
    }

    /** 批准处置：REPAIR / REPLACEMENT / REFUND；换货须携带在库替换品序列号。 */
    @PostMapping("/{id}/approvals")
    @ResponseStatus(HttpStatus.CREATED)
    public DispositionView approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest req) {
        return claimService.approve(id, new ApproveDispositionCommand(
                req.type(), req.replacementSerial(), req.refundAmountCents(), req.note()));
    }

    /** 执行已批准处置（维修完成 / 换货转移保修 / 退款终止保修）。 */
    @PostMapping("/{id}/execution")
    public DispositionView execute(@PathVariable Long id) {
        return claimService.execute(id);
    }

    /** 撤销尚未执行的处置并释放占用资源（换货时替换品回库存）。 */
    @PostMapping("/{id}/cancellation")
    public ClaimView cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest req) {
        return claimService.cancel(id, req == null ? null : req.reason());
    }

    /** 对已执行处置追加纠正记录。 */
    @PostMapping("/{id}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public CorrectionView addCorrection(@PathVariable Long id, @Valid @RequestBody CorrectionRequest req) {
        return claimService.addCorrection(id,
                new CorrectionCommand(req.type(), req.detail(), req.amountCents()));
    }

    @GetMapping("/by-ref/{externalRef}")
    public ClaimView getByRef(@PathVariable String externalRef) {
        return claimService.getClaimDetail(externalRef);
    }

    @GetMapping("/{id}")
    public ClaimView getById(@PathVariable Long id) {
        return claimService.getClaimDetail(id);
    }

    /** 产品（含换货链自身）的处置记录与替换关系。 */
    @GetMapping
    public List<DispositionView> dispositionsOfProduct(@RequestParam("serialNumber") String serialNumber) {
        return claimService.getDispositions(serialNumber);
    }
}
