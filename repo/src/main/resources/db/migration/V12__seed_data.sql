-- V12: Seed data — five users (BCrypt-12 hashes verified), sample campaigns, coupons, content.
-- The hashes below are real BCrypt $2a$12$ hashes for the passwords listed in README.md.
-- They were generated and verified before commit. Do not regenerate without re-verifying.

INSERT INTO users (username, password_hash, full_name, role, is_active) VALUES
  ('admin',    '$2a$12$ETnmcFpJCMd1ep.ZsB8MH.g6rAjqtVmKCsY1UbCnOBFZhOupe/Raa', 'Alice Administrator', 'ADMIN',            TRUE),
  ('ops',      '$2a$12$prLol6wBCUJF8Hh1rBuACuZj6XIbvCpnGYkqidAVnsPxT6zsJ/9r6', 'Oscar Operations',    'OPERATIONS',       TRUE),
  ('reviewer', '$2a$12$KnqSQmlpVm5b96xCPFx6GOf6U2YnoSPlGDKXHv78QbWyHPpG1Ltx.', 'Riley Reviewer',      'REVIEWER',         TRUE),
  ('finance',  '$2a$12$Ay4JtGbVFLtggnmbDuOQh.ivuHLup2nVc4lVuCyV6XTSvUWAuc7Be', 'Fiona Finance',       'FINANCE',          TRUE),
  ('cs',       '$2a$12$0P581srYyCTaH5rYTE8jueCbMKaMJHIcjxsPvoTwwyi7k0yK52nL2', 'Casey Customer',      'CUSTOMER_SERVICE', TRUE);

-- Sample campaigns: 2 draft, 1 active, 1 pending review.
INSERT INTO campaigns (name, description, type, status, receipt_wording, store_id, risk_level, created_by, start_date, end_date) VALUES
  ('Spring Refresh 15% Off',     'Storewide spring promotion with a flat 15% discount on all categories.',
   'COUPON',   'ACTIVE',         'SAVE 15% — code SPRING15 at register. Offer valid through Apr 30.', 'STORE-001', 'LOW',    'ops', '2026-04-01', '2026-04-30'),
  ('Summer Loyalty Bonus',       'Loyalty members receive a $20 voucher when spending over $100.',
   'COUPON',   'DRAFT',          'LOYALTY $20 OFF when you spend $100 or more — code LOYAL20.',         'STORE-001', 'MEDIUM', 'ops', '2026-06-01', '2026-08-31'),
  ('Clearance Weekend',          'Three-day clearance event — 30% off selected items.',
   'DISCOUNT', 'PENDING_REVIEW', '30% OFF clearance items this weekend only. No coupon needed.',        'STORE-002', 'HIGH',   'ops', '2026-04-12', '2026-04-14'),
  ('Back to School Bundle',      'Bundle promotion for back-to-school stationery.',
   'DISCOUNT', 'DRAFT',          'BUY 3 GET 1 FREE on all stationery — automatically applied.',         'STORE-003', 'LOW',    'ops', '2026-08-15', '2026-09-15');

-- Sample coupons (linked to campaigns 1 and 2).
INSERT INTO coupons (campaign_id, code, discount_type, discount_value, min_purchase_amount, max_uses, uses_count, is_stackable, mutual_exclusion_group, valid_from, valid_until) VALUES
  (1, 'SPRING15', 'PERCENT', 15.00, 0.00,   1000, 142, FALSE, 'SEASONAL', '2026-04-01', '2026-04-30'),
  (2, 'LOYAL20',  'FIXED',   20.00, 100.00, 500,  0,   TRUE,  NULL,       '2026-06-01', '2026-08-31');

-- Sample content items for the content integrity module.
INSERT INTO content_items (campaign_id, source_url, normalized_url, title, body_text, sha256_fingerprint, sim_hash, status, imported_by) VALUES
  (1, 'https://intranet.local/marketing/spring-refresh.html',
      'https://intranet.local/marketing/spring-refresh.html',
      'Spring Refresh — Save 15% Storewide',
      'Spring is here! Save 15% storewide with code SPRING15 at the register. Offer valid through April 30.',
      '6f1a6e5e9b2d2a86bb9c1f5a3e7b4e9c0b2a1d0c8e7f6a5b4c3d2e1f0a9b8c7d',
      4827593028174629301,
      'ACTIVE', 'ops'),
  (2, 'https://intranet.local/marketing/loyalty-bonus.html',
      'https://intranet.local/marketing/loyalty-bonus.html',
      'Loyalty Bonus — $20 Off',
      'Loyalty members get a $20 voucher when spending over $100 with code LOYAL20.',
      '7a2b8f6e0c3d4a97cc0d2e6b4f8c5e0d1c3b2e1d9f8e7b6a5c4d3e2f1b0a9c8d',
      4827593028174629290,
      'ACTIVE', 'ops');

-- Sample coupon redemptions for the analytics dashboard.
INSERT INTO coupon_redemptions (coupon_id, store_id, redeemed_at, discount_applied, order_total) VALUES
  (1, 'STORE-001', '2026-04-02 10:15:00', 7.50,  50.00),
  (1, 'STORE-001', '2026-04-03 14:32:00', 12.30, 82.00),
  (1, 'STORE-002', '2026-04-04 09:21:00', 3.00,  20.00),
  (1, 'STORE-001', '2026-04-05 16:48:00', 18.00, 120.00),
  (1, 'STORE-003', '2026-04-06 11:02:00', 9.45,  63.00);
