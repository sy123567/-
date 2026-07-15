-- 智能旅游平台常用查询视图
USE trip_adaptive;

CREATE OR REPLACE VIEW v_trip_overview AS
SELECT
  t.id AS trip_id,
  t.title AS trip_title,
  t.status AS trip_status,
  t.destination,
  t.start_date,
  t.end_date,
  g.id AS group_id,
  g.name AS group_name,
  g.room_code,
  COALESCE(m.member_count, 0) AS member_count,
  COALESCE(n.node_count, 0) AS node_count,
  t.total_budget,
  t.spent_budget,
  COALESCE(n.node_cost_total, 0.00) AS planned_node_cost,
  ROUND(t.total_budget - t.spent_budget, 2) AS remaining_budget
FROM trips AS t
JOIN travel_groups AS g ON g.id = t.group_id
LEFT JOIN (
  SELECT group_id, COUNT(*) AS member_count
  FROM group_members
  WHERE status = 'ACTIVE'
  GROUP BY group_id
) AS m ON m.group_id = g.id
LEFT JOIN (
  SELECT trip_id, COUNT(*) AS node_count, SUM(cost) AS node_cost_total
  FROM itinerary_nodes
  WHERE deleted_at IS NULL
  GROUP BY trip_id
) AS n ON n.trip_id = t.id
WHERE t.deleted_at IS NULL
  AND g.deleted_at IS NULL;

CREATE OR REPLACE VIEW v_trip_budget_summary AS
SELECT
  t.id AS trip_id,
  t.title AS trip_title,
  gm.id AS member_id,
  u.id AS user_id,
  u.name AS member_name,
  COALESCE(SUM(bs.share_amount), 0.00) AS allocated_share,
  COUNT(DISTINCT b.id) AS related_bill_count,
  t.total_budget,
  t.spent_budget
FROM trips AS t
JOIN group_members AS gm ON gm.group_id = t.group_id AND gm.status = 'ACTIVE'
JOIN users AS u ON u.id = gm.user_id
LEFT JOIN bills AS b ON b.trip_id = t.id
LEFT JOIN bill_shares AS bs ON bs.bill_id = b.id AND bs.member_id = gm.id
WHERE t.deleted_at IS NULL
GROUP BY t.id, t.title, gm.id, u.id, u.name, t.total_budget, t.spent_budget;

CREATE OR REPLACE VIEW v_active_risk_snapshot AS
SELECT
  t.id AS trip_id,
  t.title AS trip_title,
  t.destination,
  MAX(ia.risk_score) AS highest_risk_score,
  COUNT(DISTINCT ia.affected_node_id) AS affected_node_count,
  COUNT(DISTINCT ia.event_id) AS active_event_count,
  MAX(ia.assessed_at) AS last_assessed_at
FROM trips AS t
JOIN impact_assessments AS ia ON ia.trip_id = t.id
JOIN external_events AS e ON e.id = ia.event_id
WHERE t.deleted_at IS NULL
  AND e.status = 'ACTIVE'
  AND e.end_time >= CURRENT_TIMESTAMP(3)
GROUP BY t.id, t.title, t.destination;

CREATE OR REPLACE VIEW v_guide_hot_rank AS
SELECT
  g.id AS guide_id,
  g.title,
  g.city,
  g.theme,
  g.days,
  g.per_capita_budget,
  g.rating,
  g.rating_count,
  g.view_count,
  g.favorite_count,
  COUNT(DISTINCT gf.id) AS favorite_records,
  COUNT(DISTINCT gr.id) AS rating_records,
  ROUND(
    g.view_count * 0.20
    + g.favorite_count * 3.00
    + g.rating * GREATEST(g.rating_count, 1) * 5.00,
    2
  ) AS hot_score
FROM travel_guides AS g
LEFT JOIN guide_favorites AS gf ON gf.guide_id = g.id
LEFT JOIN guide_ratings AS gr ON gr.guide_id = g.id
WHERE g.status = 'PUBLISHED'
  AND g.deleted_at IS NULL
GROUP BY
  g.id, g.title, g.city, g.theme, g.days, g.per_capita_budget,
  g.rating, g.rating_count, g.view_count, g.favorite_count
ORDER BY hot_score DESC, g.rating DESC, g.view_count DESC;

CREATE OR REPLACE VIEW v_plan_vote_progress AS
SELECT
  ap.id AS plan_id,
  ap.trip_id,
  ap.title AS plan_title,
  ap.strategy,
  ap.status AS plan_status,
  COALESCE(v.total_votes, 0) AS total_votes,
  COALESCE(v.approve_votes, 0) AS approve_votes,
  COALESCE(v.reject_votes, 0) AS reject_votes,
  COALESCE(v.abstain_votes, 0) AS abstain_votes,
  COALESCE(gm.total_members, 0) AS total_members,
  ROUND(COALESCE(v.approve_votes, 0) / NULLIF(gm.total_members, 0) * 100, 2) AS approve_percent,
  CASE
    WHEN COALESCE(v.approve_votes, 0) > COALESCE(gm.total_members, 0) / 2 THEN 'PASSED'
    WHEN ap.status = 'VOTING' THEN 'IN_PROGRESS'
    ELSE ap.status
  END AS vote_progress
FROM alternative_plans AS ap
LEFT JOIN (
  SELECT
    plan_id,
    COUNT(*) AS total_votes,
    SUM(choice = 'APPROVE') AS approve_votes,
    SUM(choice = 'REJECT') AS reject_votes,
    SUM(choice = 'ABSTAIN') AS abstain_votes
  FROM plan_votes
  GROUP BY plan_id
) AS v ON v.plan_id = ap.id
JOIN trips AS t ON t.id = ap.trip_id
LEFT JOIN (
  SELECT group_id, COUNT(*) AS total_members
  FROM group_members
  WHERE status = 'ACTIVE'
  GROUP BY group_id
) AS gm ON gm.group_id = t.group_id;
