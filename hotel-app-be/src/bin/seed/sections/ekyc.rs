//! Opt-in synthetic eKYC verification fixtures (`--scenario ekyc`).
//! Excluded from default `full` / `--all` runs so real identity records are
//! never implied by standard dev seeding. Uses strictly synthetic dev markers
//! (`STG-SYNTH-*`, zero real PII).

use crate::engine::Tx;

pub async fn seed(tx: &mut Tx<'_>) -> Result<(), sqlx::Error> {
    sqlx::raw_sql(SQL).execute(&mut **tx).await?;
    Ok(())
}

const SQL: &str = r#"
INSERT INTO public.ekyc_verifications (
    id, user_id, guest_id, status, assigned_reviewer_id, reviewer_claimed_at,
    full_name, date_of_birth, nationality, phone, email, current_address,
    id_type, id_number, id_issuing_country,
    provider_name, provider_verification_result, document_authenticity_result,
    face_match_score, face_match_passed, liveness_score, liveness_passed,
    duplicate_check_result, watchlist_result,
    auto_verified, manual_review_required, risk_level, risk_score, risk_flags,
    recommended_action, fraud_suspected, verification_notes, customer_message,
    decision_reason_code, decision_reason, verified_by, verified_at,
    self_checkin_enabled, self_checkin_activated_at, submitted_at, created_at, updated_at
)
OVERRIDING SYSTEM VALUE VALUES
    (809001, 800013, 801020, 'approved', 800010,
     (SELECT today - 5 FROM staging_ref)::timestamptz + interval '9 hours',
     'Portal Guest', DATE '1990-01-01', 'Malaysian', '+60-12-600-1020', 'guest.portal@staging.hotel-app.test',
     'Kuala Lumpur, WP, Malaysia',
     'national_id', 'STG-SYNTH-IC-801020', 'Malaysia',
     'synthetic_dev', 'pass', 'pass',
     0.96, true, 0.98, true, 'clear', 'clear',
     true, false, 'low', 10, '[]'::jsonb,
     'approve', false, 'Synthetic approved fixture for portal self check-in',
     'Identity verified for self check-in.', NULL, NULL,
     800010, (SELECT today - 5 FROM staging_ref)::timestamptz + interval '10 hours',
     true, (SELECT today - 5 FROM staging_ref)::timestamptz + interval '10 hours',
     (SELECT today - 6 FROM staging_ref)::timestamptz + interval '8 hours',
     (SELECT today - 6 FROM staging_ref)::timestamptz + interval '8 hours',
     (SELECT today - 5 FROM staging_ref)::timestamptz + interval '10 hours'),
    (809002, 800002, 801001, 'submitted', NULL, NULL,
     'Aisha Rahman', DATE '1988-03-14', 'Malaysian', '+60-12-600-1001', 'aisha.rahman@staging.hotel-app.test',
     'Kuala Lumpur, WP, Malaysia',
     'national_id', 'STG-SYNTH-IC-801001', 'Malaysia',
     'synthetic_dev', 'pending', 'pending',
     0.91, true, 0.92, true, 'clear', 'clear',
     false, false, 'low', 18, '[]'::jsonb,
     'review', false, NULL, NULL, NULL, NULL, NULL, NULL,
     false, NULL,
     (SELECT today FROM staging_ref)::timestamptz + interval '8 hours',
     (SELECT today FROM staging_ref)::timestamptz + interval '8 hours',
     (SELECT today FROM staging_ref)::timestamptz + interval '8 hours'),
    (809003, 800003, 801003, 'pending_manual_review', NULL, NULL,
     'Emily Wilson', DATE '1991-06-21', 'British', '+44-7700-901003', 'emily.wilson@staging.hotel-app.test',
     'London, United Kingdom',
     'passport', 'STG-SYNTH-PP-801003', 'United Kingdom',
     'synthetic_dev', 'review', 'pass',
     0.83, true, 0.88, true, 'clear', 'clear',
     false, true, 'medium', 48, '["lighting_variance"]'::jsonb,
     'manual_review', false, 'Queued for manual compliance check', NULL, NULL, NULL, NULL, NULL,
     false, NULL,
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '10 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '10 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '11 hours'),
    (809004, 800004, 801004, 'in_review', 800010,
     (SELECT today FROM staging_ref)::timestamptz + interval '9 hours',
     'Kenji Takahashi', DATE '1985-01-30', 'Japanese', '+81-90-1234-0004', 'kenji.takahashi@staging.hotel-app.test',
     'Osaka, Japan',
     'passport', 'STG-SYNTH-PP-801004', 'Japan',
     'synthetic_dev', 'review', 'pass',
     0.86, true, 0.90, true, 'clear', 'clear',
     false, true, 'medium', 52, '["mrz_ocr_low_conf"]'::jsonb,
     'manual_review', false, 'Reviewer checking passport MRZ character alignment', NULL, NULL, NULL, NULL, NULL,
     false, NULL,
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '14 hours',
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '14 hours',
     (SELECT today FROM staging_ref)::timestamptz + interval '9 hours'),
    (809005, 800005, 801005, 'additional_information_required', 800010,
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '9 hours',
     'Priya Nair', DATE '1993-09-09', 'Indian', '+91-98-4500-0005', 'priya.nair@staging.hotel-app.test',
     'Bengaluru, Karnataka, India',
     'passport', 'STG-SYNTH-PP-801005', 'India',
     'synthetic_dev', 'fail', 'inconclusive',
     0.74, false, 0.85, true, 'clear', 'clear',
     false, true, 'medium', 55, '["glare_detected"]'::jsonb,
     'request_resubmission', false, 'Asked guest to re-upload clear passport biodata page',
     'Please upload a clear photo of your passport without flash glare.',
     'blurry_document', 'Synthetic fixture: ID photo out of focus',
     NULL, NULL, false, NULL,
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '8 hours',
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '8 hours',
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '10 hours'),
    (809006, 800006, 801016, 'escalated', 800010,
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '10 hours',
     'Gerald Frost', DATE '1980-09-17', 'Malaysian', '+60-12-600-1016', 'gerald.frost@staging.hotel-app.test',
     'Kuala Lumpur, WP, Malaysia',
     'national_id', 'STG-SYNTH-IC-801016', 'Malaysia',
     'synthetic_dev', 'review', 'fail',
     0.61, false, 0.72, false, 'match_found', 'review',
     false, true, 'high', 84, '["synthetic_blacklist_match"]'::jsonb,
     'escalate', true, 'Escalated synthetic high-risk fixture linked to blacklisted guest',
     'Your verification requires additional manual review by our compliance team.',
     'high_risk_escalation', 'Synthetic high-risk escalation fixture',
     NULL, NULL, false, NULL,
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '9 hours',
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '9 hours',
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '11 hours');

INSERT INTO public.ekyc_notes (
    id, application_id, note_type, body, customer_visible, created_by, created_at, updated_at
)
OVERRIDING SYSTEM VALUE VALUES
    (809101, 809001, 'internal', 'Synthetic auto-verification passed all biometric checks.', false, 800010,
     (SELECT today - 5 FROM staging_ref)::timestamptz + interval '10 hours',
     (SELECT today - 5 FROM staging_ref)::timestamptz + interval '10 hours'),
    (809102, 809004, 'internal', 'Verifying Japanese passport MRZ digits against guest record.', false, 800010,
     (SELECT today FROM staging_ref)::timestamptz + interval '9 hours',
     (SELECT today FROM staging_ref)::timestamptz + interval '9 hours'),
    (809103, 809005, 'customer', 'Please upload a sharper photo with all four passport corners visible.', true, 800010,
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '10 hours',
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '10 hours'),
    (809104, 809006, 'escalation', 'Matched internal risk note on guest 801016; awaiting duty manager review.', false, 800010,
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '11 hours',
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '11 hours');

INSERT INTO public.ekyc_decision_history (
    id, application_id, actor_id, action, from_status, to_status, reason_code, reason, details, created_at
)
OVERRIDING SYSTEM VALUE VALUES
    (809201, 809001, 800010, 'approve', 'submitted', 'approved', NULL,
     'Synthetic approval for portal self check-in', '{"staging_seed":"ekyc"}'::jsonb,
     (SELECT today - 5 FROM staging_ref)::timestamptz + interval '10 hours'),
    (809202, 809002, NULL, 'submit', NULL, 'submitted', NULL,
     'Guest submitted synthetic verification', '{"staging_seed":"ekyc"}'::jsonb,
     (SELECT today FROM staging_ref)::timestamptz + interval '8 hours'),
    (809203, 809003, NULL, 'route_manual', 'automated_review', 'pending_manual_review', NULL,
     'Face match borderline — queued for manual review', '{"staging_seed":"ekyc"}'::jsonb,
     (SELECT today - 1 FROM staging_ref)::timestamptz + interval '11 hours'),
    (809204, 809004, 800010, 'claim', 'pending_manual_review', 'in_review', NULL,
     'Reviewer claimed application', '{"staging_seed":"ekyc"}'::jsonb,
     (SELECT today FROM staging_ref)::timestamptz + interval '9 hours'),
    (809205, 809005, 800010, 'request_info', 'in_review', 'additional_information_required', 'blurry_document',
     'Synthetic fixture: ID photo out of focus', '{"staging_seed":"ekyc"}'::jsonb,
     (SELECT today - 2 FROM staging_ref)::timestamptz + interval '10 hours'),
    (809206, 809006, 800010, 'escalate', 'pending_manual_review', 'escalated', 'high_risk_escalation',
     'Synthetic high-risk escalation fixture', '{"staging_seed":"ekyc"}'::jsonb,
     (SELECT today - 3 FROM staging_ref)::timestamptz + interval '11 hours');
"#;
