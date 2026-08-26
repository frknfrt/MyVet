\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE tmp_tenants AS SELECT id FROM tenants WHERE name IN ('Smoke Test Klinik', 'Invite Test Klinik', 'Fix Test Klinik', 'Debug Klinik2');
CREATE TEMP TABLE tmp_branches AS SELECT id FROM branches WHERE tenant_id IN (SELECT id FROM tmp_tenants);
CREATE TEMP TABLE tmp_owners AS SELECT id FROM owners WHERE tenant_id IN (SELECT id FROM tmp_tenants);
CREATE TEMP TABLE tmp_patients AS SELECT id FROM patients WHERE owner_id IN (SELECT id FROM tmp_owners);
CREATE TEMP TABLE tmp_staff AS SELECT id FROM staff_users WHERE branch_id IN (SELECT id FROM tmp_branches);
CREATE TEMP TABLE tmp_encounters AS SELECT id FROM encounters WHERE patient_id IN (SELECT id FROM tmp_patients);
CREATE TEMP TABLE tmp_invoices AS SELECT id FROM invoices WHERE tenant_id IN (SELECT id FROM tmp_tenants);
CREATE TEMP TABLE tmp_prescriptions AS SELECT id FROM prescriptions WHERE patient_id IN (SELECT id FROM tmp_patients);
CREATE TEMP TABLE tmp_lab_results AS SELECT id FROM lab_results WHERE patient_id IN (SELECT id FROM tmp_patients);
CREATE TEMP TABLE tmp_imaging AS SELECT id FROM imaging_records WHERE patient_id IN (SELECT id FROM tmp_patients);
CREATE TEMP TABLE tmp_boarding_stays AS SELECT id FROM boarding_stays WHERE tenant_id IN (SELECT id FROM tmp_tenants);
CREATE TEMP TABLE tmp_boarding_rooms AS SELECT id FROM boarding_rooms WHERE tenant_id IN (SELECT id FROM tmp_tenants);
CREATE TEMP TABLE tmp_inventory_items AS SELECT id FROM inventory_items WHERE branch_id IN (SELECT id FROM tmp_branches);
CREATE TEMP TABLE tmp_breeds AS SELECT id FROM breeds WHERE name ILIKE '%Smoke%' OR name ILIKE '%Kangal-Smoke%';
CREATE TEMP TABLE tmp_species AS SELECT id FROM species WHERE name ILIKE '%Smoke%';
CREATE TEMP TABLE tmp_drugs AS SELECT id FROM drug_catalog WHERE name ILIKE '%-Smoke%';

-- leaf tables
DELETE FROM encounter_inventory_usage WHERE encounter_id IN (SELECT id FROM tmp_encounters);
DELETE FROM imaging_record_files WHERE imaging_record_id IN (SELECT id FROM tmp_imaging);
DELETE FROM lab_result_files WHERE lab_result_id IN (SELECT id FROM tmp_lab_results);
DELETE FROM lab_result_items WHERE lab_result_id IN (SELECT id FROM tmp_lab_results);
DELETE FROM invoice_lines WHERE invoice_id IN (SELECT id FROM tmp_invoices);
DELETE FROM payments WHERE invoice_id IN (SELECT id FROM tmp_invoices);
DELETE FROM efatura_submission WHERE tenant_id IN (SELECT id FROM tmp_tenants);
DELETE FROM consent_records WHERE owner_id IN (SELECT id FROM tmp_owners);
DELETE FROM notification_log WHERE tenant_id IN (SELECT id FROM tmp_tenants);
DELETE FROM prescription_items WHERE prescription_id IN (SELECT id FROM tmp_prescriptions);
DELETE FROM tarbil_sync_log WHERE patient_id IN (SELECT id FROM tmp_patients);
DELETE FROM staff_shift_templates WHERE staff_user_id IN (SELECT id FROM tmp_staff);
DELETE FROM branch_working_hours WHERE branch_id IN (SELECT id FROM tmp_branches);
DELETE FROM message_templates WHERE tenant_id IN (SELECT id FROM tmp_tenants);
DELETE FROM staff_invites WHERE tenant_id IN (SELECT id FROM tmp_tenants);

-- mid level
DELETE FROM stock_movements WHERE inventory_item_id IN (SELECT id FROM tmp_inventory_items);
DELETE FROM vaccination_records WHERE tenant_id IN (SELECT id FROM tmp_tenants);
DELETE FROM prescriptions WHERE id IN (SELECT id FROM tmp_prescriptions);
DELETE FROM imaging_records WHERE id IN (SELECT id FROM tmp_imaging);
DELETE FROM lab_results WHERE id IN (SELECT id FROM tmp_lab_results);
DELETE FROM invoices WHERE id IN (SELECT id FROM tmp_invoices);
DELETE FROM encounters WHERE id IN (SELECT id FROM tmp_encounters);
DELETE FROM cash_register_sessions WHERE branch_id IN (SELECT id FROM tmp_branches);
DELETE FROM inventory_items WHERE id IN (SELECT id FROM tmp_inventory_items);

DELETE FROM boarding_stays WHERE id IN (SELECT id FROM tmp_boarding_stays);
DELETE FROM appointments WHERE tenant_id IN (SELECT id FROM tmp_tenants);

DELETE FROM boarding_rooms WHERE id IN (SELECT id FROM tmp_boarding_rooms);
DELETE FROM patients WHERE id IN (SELECT id FROM tmp_patients);
DELETE FROM staff_users WHERE id IN (SELECT id FROM tmp_staff);
DELETE FROM owners WHERE id IN (SELECT id FROM tmp_owners);
DELETE FROM service_types WHERE tenant_id IN (SELECT id FROM tmp_tenants);

DELETE FROM branches WHERE id IN (SELECT id FROM tmp_branches);
DELETE FROM subscriptions WHERE tenant_id IN (SELECT id FROM tmp_tenants);
DELETE FROM tenants WHERE id IN (SELECT id FROM tmp_tenants);

-- global (tenant-independent) katalog kirliligi
DELETE FROM prescription_items WHERE drug_id IN (SELECT id FROM tmp_drugs);
DELETE FROM drug_catalog WHERE id IN (SELECT id FROM tmp_drugs);
DELETE FROM breeds WHERE id IN (SELECT id FROM tmp_breeds);
DELETE FROM species WHERE id IN (SELECT id FROM tmp_species);

COMMIT;
