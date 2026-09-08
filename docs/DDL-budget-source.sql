BEGIN;

ALTER TABLE household_budget_plan ADD COLUMN IF NOT EXISTS acquisition_tax_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE household_budget_plan ADD COLUMN IF NOT EXISTS brokerage_fee_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE household_budget_plan ADD COLUMN IF NOT EXISTS registration_fee_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE household_budget_plan ADD COLUMN IF NOT EXISTS moving_cost_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE household_budget_plan ADD COLUMN IF NOT EXISTS cleaning_cost_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

COMMIT;
