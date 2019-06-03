create or replace view aml_check_success as
select aml.user_id,
       array_agg(aml.type) @> ARRAY ['POLITICALLY_EXPOSED_PERSON', 'SK_NAME', 'PENSION_REGISTRY_NAME', 'DOCUMENT']
         AND (array_agg(aml.type) @> ARRAY ['RESIDENCY_AUTO'] OR
              array_agg(aml.type) @> ARRAY ['RESIDENCY_MANUAL']) as all_checks_passed,
       (select max(a.created_time)
        from aml_check a
        where a.user_id = aml.user_id
          and a.success = true)                                  as completed_time
from aml_check aml
where aml.success = true
group by aml.user_id;
