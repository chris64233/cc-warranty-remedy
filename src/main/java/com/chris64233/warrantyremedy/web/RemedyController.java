package com.chris64233.warrantyremedy.web;

import com.chris64233.warrantyremedy.api.CorrectionRequest;
import com.chris64233.warrantyremedy.api.CorrectionView;
import com.chris64233.warrantyremedy.api.RemedyView;
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
@RequestMapping("/api/remedies")
public class RemedyController {

    private final RemedyService remedyService;

    public RemedyController(RemedyService remedyService) {
        this.remedyService = remedyService;
    }

    /** 处置记录：类型、状态、替换关系与纠正记录。 */
    @GetMapping("/{id}")
    public RemedyView get(@PathVariable long id) {
        return remedyService.get(id);
    }

    @PostMapping("/{id}/execute")
    public RemedyView execute(@PathVariable long id) {
        return remedyService.execute(id);
    }

    @PostMapping("/{id}/cancel")
    public RemedyView cancel(@PathVariable long id) {
        return remedyService.cancel(id);
    }

    /** 已执行处置追加纠正记录。 */
    @PostMapping("/{id}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public CorrectionView addCorrection(@PathVariable long id, @Valid @RequestBody CorrectionRequest request) {
        return remedyService.addCorrection(id, request.note());
    }
}
