package com.chris64233.warrantyremedy.web;

import com.chris64233.warrantyremedy.api.ClaimView;
import com.chris64233.warrantyremedy.api.RemedyView;
import com.chris64233.warrantyremedy.api.SubmitClaimRequest;
import com.chris64233.warrantyremedy.service.ClaimService;
import com.chris64233.warrantyremedy.service.RemedyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claimService;
    private final RemedyService remedyService;

    public ClaimController(ClaimService claimService, RemedyService remedyService) {
        this.claimService = claimService;
        this.remedyService = remedyService;
    }

    /** 提交保修申请（外部申请号幂等）。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimView submit(@Valid @RequestBody SubmitClaimRequest request) {
        return claimService.submit(request);
    }

    /** 申请详情：故障现象、购买凭证等证据与资格判断结果。 */
    @GetMapping("/{id}")
    public ClaimView get(@PathVariable long id) {
        return claimService.get(id);
    }

    /** 批准申请并生成处置决定（幂等）。 */
    @PostMapping("/{id}/approve")
    public RemedyView approve(@PathVariable long id) {
        return remedyService.approve(id);
    }
}
