package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.application.service.BudgetPlanAggregate;
import banghak.home.halley.application.service.BudgetPlanService;
import banghak.home.halley.domain.budget.BudgetPlan;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 신혼 생활 시작 예산 계획 API. 모든 계획은 로그인한 사용자의 그룹 경계 안에서만 조회한다. */
@RestController
@RequestMapping("/api/budget/plans")
public class BudgetController {
    private final BudgetPlanService service;

    public BudgetController(BudgetPlanService service) {
        this.service = service;
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
}
