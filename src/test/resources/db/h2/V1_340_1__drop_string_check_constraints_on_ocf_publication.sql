ALTER TABLE investment_ocf_publication
    DROP CONSTRAINT IF EXISTS investment_ocf_publication_basis;
ALTER TABLE investment_ocf_publication
    DROP CONSTRAINT IF EXISTS investment_ocf_publication_ex_post_carries_its_period;
ALTER TABLE investment_ocf_publication
    DROP CONSTRAINT IF EXISTS investment_ocf_publication_ex_post_is_not_a_calculation;
