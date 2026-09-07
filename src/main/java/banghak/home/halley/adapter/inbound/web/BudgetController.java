package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.application.service.BudgetPlanAggregate;
import banghak.home.halley.application.service.BudgetPlanService;
import banghak.home.halley.application.service.ProductPreviewService;
import banghak.home.halley.adapter.inbound.web.dto.ProductPreviewRequest;
import banghak.home.halley.domain.budget.BudgetPlan;
import banghak.home.halley.domain.budget.BudgetAsset;
import banghak.home.halley.domain.budget.BudgetFinancing;
import banghak.home.halley.domain.budget.BudgetItem;
import banghak.home.halley.domain.budget.ProductPreview;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 신혼 생활 시작 예산 계획 API. 모든 계획은 로그인한 사용자의 그룹 경계 안에서만 조회한다. */
@RestController
@RequestMapping("/api/budget/plans")
public class BudgetController {
    private final BudgetPlanService service;
    private final ProductPreviewService productPreviewService;

    public BudgetController(BudgetPlanService service, ProductPreviewService productPreviewService) {
        this.service = service;
        this.productPreviewService = productPreviewService;
    }

    @GetMapping
    public List<BudgetPlan> list() {
        return service.findMine();
    }

    @GetMapping("/{id}")
    public BudgetPlanAggregate get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public BudgetPlan create(@RequestBody BudgetPlan plan) {
        return service.create(plan);
    }

    @PostMapping("/default")
    public BudgetPlan createDefault() {
        return service.createDefault();
    }

    @PutMapping("/{id}")
    public BudgetPlan update(@PathVariable Long id, @RequestBody BudgetPlan plan) {
        if (!id.equals(plan.id())) {
            throw new IllegalArgumentException("plan id does not match path");
        }
        return service.update(plan);
    }

    @PostMapping("/{id}/assets")
    public BudgetAsset addAsset(@PathVariable Long id, @RequestBody BudgetAsset asset) {
        if (!id.equals(asset.planId())) {
            throw new IllegalArgumentException("plan id does not match path");
        }
        return service.addAsset(asset);
    }

    @PutMapping("/{id}/financing")
    public BudgetFinancing saveFinancing(@PathVariable Long id, @RequestBody BudgetFinancing financing) {
        if (!id.equals(financing.planId())) {
            throw new IllegalArgumentException("plan id does not match path");
        }
        return service.saveFinancing(financing);
    }

    @PutMapping("/{planId}/items/{itemId}")
    public BudgetItem updateItem(@PathVariable Long planId, @PathVariable Long itemId, @RequestBody BudgetItem item) {
        if (!planId.equals(item.planId()) || !itemId.equals(item.id())) {
            throw new IllegalArgumentException("item does not match path");
        }
        return service.updateItem(item);
    }

    @PostMapping("/product-preview")
    public ProductPreview previewProduct(@RequestBody ProductPreviewRequest request) {
        return productPreviewService.preview(request.url());
    }
}
