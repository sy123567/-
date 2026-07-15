-- 智能旅游平台数据库结构（MySQL 8）
-- 本文件不包含任何连接凭据。
CREATE DATABASE IF NOT EXISTS trip_adaptive
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE trip_adaptive;

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  email VARCHAR(190) NOT NULL,
  phone VARCHAR(32) NULL,
  password_hash VARCHAR(255) NOT NULL,
  avatar VARCHAR(500) NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'USER',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  deleted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  KEY idx_users_name (name),
  KEY idx_users_status (status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friendships (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  requester_id BIGINT UNSIGNED NOT NULL,
  addressee_id BIGINT UNSIGNED NOT NULL,
  pair_key VARCHAR(43) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_friendships_pair (pair_key),
  KEY idx_friendships_requester (requester_id, status),
  KEY idx_friendships_addressee (addressee_id, status),
  CONSTRAINT fk_friendships_requester FOREIGN KEY (requester_id) REFERENCES users(id),
  CONSTRAINT fk_friendships_addressee FOREIGN KEY (addressee_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS travel_groups (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  owner_user_id BIGINT UNSIGNED NOT NULL,
  room_code VARCHAR(16) NOT NULL,
  room_code_expire_at DATETIME(3) NULL,
  member_limit SMALLINT UNSIGNED NOT NULL DEFAULT 8,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  deleted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_travel_groups_room_code (room_code),
  KEY idx_travel_groups_owner (owner_user_id, status),
  CONSTRAINT fk_travel_groups_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS group_members (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  group_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  left_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_members_user (group_id, user_id),
  KEY idx_group_members_user (user_id, status),
  CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES travel_groups(id),
  CONSTRAINT fk_group_members_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member_constraints (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  member_id BIGINT UNSIGNED NOT NULL,
  available_from DATE NOT NULL,
  available_to DATE NOT NULL,
  max_budget DECIMAL(12,2) NOT NULL,
  must_visit_places JSON NOT NULL,
  fitness_level VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  dietary_needs JSON NOT NULL,
  accessibility_needs JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_constraints_member (member_id),
  CONSTRAINT fk_member_constraints_member FOREIGN KEY (member_id) REFERENCES group_members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS trips (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  group_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(160) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  total_budget DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  spent_budget DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  destination VARCHAR(160) NULL,
  room_code VARCHAR(32) NULL,
  source_guide_id BIGINT UNSIGNED NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  deleted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_trips_group_status (group_id, status, start_date),
  KEY idx_trips_time_status (status, start_date, end_date),
  KEY idx_trips_source_guide (source_guide_id),
  CONSTRAINT fk_trips_group FOREIGN KEY (group_id) REFERENCES travel_groups(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS itinerary_nodes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  trip_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(160) NOT NULL,
  place_name VARCHAR(160) NOT NULL,
  latitude DOUBLE NULL,
  longitude DOUBLE NULL,
  node_type VARCHAR(32) NOT NULL,
  planned_start DATETIME(3) NOT NULL,
  planned_end DATETIME(3) NOT NULL,
  cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  sequence_order INT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
  deleted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_nodes_trip_sequence (trip_id, sequence_order),
  KEY idx_nodes_trip_time (trip_id, planned_start, planned_end),
  KEY idx_nodes_trip_status (trip_id, status),
  KEY idx_nodes_geo (latitude, longitude),
  CONSTRAINT fk_nodes_trip FOREIGN KEY (trip_id) REFERENCES trips(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS routes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  trip_id BIGINT UNSIGNED NOT NULL,
  from_node_id BIGINT UNSIGNED NOT NULL,
  to_node_id BIGINT UNSIGNED NOT NULL,
  transport_mode VARCHAR(32) NOT NULL,
  distance_km DOUBLE NOT NULL DEFAULT 0,
  duration_minutes INT NOT NULL DEFAULT 0,
  cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  polyline TEXT NULL,
  source VARCHAR(32) NOT NULL DEFAULT 'ESTIMATED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_routes_trip_nodes (trip_id, from_node_id, to_node_id),
  KEY idx_routes_trip (trip_id),
  CONSTRAINT fk_routes_trip FOREIGN KEY (trip_id) REFERENCES trips(id),
  CONSTRAINT fk_routes_from_node FOREIGN KEY (from_node_id) REFERENCES itinerary_nodes(id),
  CONSTRAINT fk_routes_to_node FOREIGN KEY (to_node_id) REFERENCES itinerary_nodes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS external_events (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_type VARCHAR(40) NOT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT NULL,
  place_name VARCHAR(160) NULL,
  latitude DOUBLE NULL,
  longitude DOUBLE NULL,
  radius_km DOUBLE NOT NULL DEFAULT 1.0,
  severity VARCHAR(16) NOT NULL,
  start_time DATETIME(3) NOT NULL,
  end_time DATETIME(3) NOT NULL,
  source VARCHAR(80) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  payload JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_events_active_time (status, start_time, end_time),
  KEY idx_events_type_severity (event_type, severity, start_time),
  KEY idx_events_geo_time (latitude, longitude, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS impact_assessments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  trip_id BIGINT UNSIGNED NOT NULL,
  event_id BIGINT UNSIGNED NOT NULL,
  affected_node_id BIGINT UNSIGNED NOT NULL,
  risk_score TINYINT UNSIGNED NOT NULL DEFAULT 0,
  impact_level VARCHAR(16) NOT NULL,
  description VARCHAR(1000) NULL,
  assessment_hash CHAR(64) NULL,
  assessed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_impact_trip_event_node (trip_id, event_id, affected_node_id),
  KEY idx_impacts_trip_level (trip_id, impact_level, assessed_at),
  KEY idx_impacts_event (event_id),
  KEY idx_impacts_node (affected_node_id),
  CONSTRAINT fk_impacts_trip FOREIGN KEY (trip_id) REFERENCES trips(id),
  CONSTRAINT fk_impacts_event FOREIGN KEY (event_id) REFERENCES external_events(id),
  CONSTRAINT fk_impacts_node FOREIGN KEY (affected_node_id) REFERENCES itinerary_nodes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alternative_plans (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  trip_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(200) NOT NULL,
  strategy VARCHAR(32) NOT NULL,
  extra_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  extra_delay_minutes INT NOT NULL DEFAULT 0,
  changed_node_count INT NOT NULL DEFAULT 0,
  summary VARCHAR(1000) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PROPOSED',
  source_assessment_hash CHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_plans_trip_status (trip_id, status, created_at),
  KEY idx_plans_trip_strategy_hash (trip_id, strategy, source_assessment_hash),
  CONSTRAINT fk_plans_trip FOREIGN KEY (trip_id) REFERENCES trips(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS node_changes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plan_id BIGINT UNSIGNED NOT NULL,
  original_node_id BIGINT UNSIGNED NULL,
  change_type VARCHAR(16) NOT NULL,
  new_place_name VARCHAR(160) NULL,
  new_start DATETIME(3) NULL,
  new_end DATETIME(3) NULL,
  new_cost DECIMAL(12,2) NULL,
  new_latitude DOUBLE NULL,
  new_longitude DOUBLE NULL,
  note VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_node_changes_plan (plan_id),
  CONSTRAINT fk_node_changes_plan FOREIGN KEY (plan_id) REFERENCES alternative_plans(id),
  CONSTRAINT fk_node_changes_original FOREIGN KEY (original_node_id) REFERENCES itinerary_nodes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS plan_votes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plan_id BIGINT UNSIGNED NOT NULL,
  member_id BIGINT UNSIGNED NOT NULL,
  choice VARCHAR(16) NOT NULL,
  comment VARCHAR(500) NULL,
  voted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan_votes_plan_member (plan_id, member_id),
  KEY idx_plan_votes_plan_choice (plan_id, choice),
  CONSTRAINT fk_plan_votes_plan FOREIGN KEY (plan_id) REFERENCES alternative_plans(id),
  CONSTRAINT fk_plan_votes_member FOREIGN KEY (member_id) REFERENCES group_members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS change_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  trip_id BIGINT UNSIGNED NOT NULL,
  description VARCHAR(1000) NOT NULL,
  extra_cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  refund_deadline DATETIME(3) NULL,
  related_plan_id BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_change_logs_trip_created (trip_id, created_at),
  CONSTRAINT fk_change_logs_trip FOREIGN KEY (trip_id) REFERENCES trips(id),
  CONSTRAINT fk_change_logs_plan FOREIGN KEY (related_plan_id) REFERENCES alternative_plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bills (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  trip_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(200) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  category VARCHAR(40) NULL,
  paid_by_member_id BIGINT UNSIGNED NOT NULL,
  split_mode VARCHAR(24) NOT NULL DEFAULT 'EQUAL',
  occurred_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_bills_trip_created (trip_id, created_at),
  CONSTRAINT fk_bills_trip FOREIGN KEY (trip_id) REFERENCES trips(id),
  CONSTRAINT fk_bills_payer FOREIGN KEY (paid_by_member_id) REFERENCES group_members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bill_shares (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  bill_id BIGINT UNSIGNED NOT NULL,
  member_id BIGINT UNSIGNED NOT NULL,
  share_amount DECIMAL(12,2) NOT NULL,
  share_ratio DECIMAL(8,5) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_bill_shares_bill_member (bill_id, member_id),
  CONSTRAINT fk_bill_shares_bill FOREIGN KEY (bill_id) REFERENCES bills(id),
  CONSTRAINT fk_bill_shares_member FOREIGN KEY (member_id) REFERENCES group_members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS comments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  author_id BIGINT UNSIGNED NOT NULL,
  target_type VARCHAR(16) NOT NULL,
  target_id BIGINT UNSIGNED NOT NULL,
  parent_id BIGINT UNSIGNED NULL,
  content VARCHAR(1000) NOT NULL,
  mentions JSON NULL,
  like_count INT UNSIGNED NOT NULL DEFAULT 0,
  deleted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_comments_target (target_type, target_id, created_at),
  KEY idx_comments_author (author_id, created_at),
  CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users(id),
  CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  type VARCHAR(24) NOT NULL,
  title VARCHAR(200) NOT NULL,
  detail VARCHAR(1000) NULL,
  payload JSON NULL,
  read_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_notifications_user_read (user_id, read_at, created_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS travel_guides (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  cover VARCHAR(500) NULL,
  author_id BIGINT UNSIGNED NOT NULL,
  city VARCHAR(80) NOT NULL,
  tags JSON NOT NULL,
  theme VARCHAR(40) NOT NULL,
  days SMALLINT UNSIGNED NOT NULL,
  per_capita_budget DECIMAL(12,2) NOT NULL,
  season VARCHAR(40) NULL,
  rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
  rating_count INT UNSIGNED NOT NULL DEFAULT 0,
  favorite_count INT UNSIGNED NOT NULL DEFAULT 0,
  view_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
  description VARCHAR(2000) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
  deleted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_guides_filter (status, city, theme, days, per_capita_budget, rating),
  KEY idx_guides_city_theme (status, city, theme, created_at),
  KEY idx_guides_rating (status, rating DESC, rating_count DESC),
  KEY idx_guides_hot (status, favorite_count DESC, view_count DESC),
  FULLTEXT KEY ft_guides_text (title, description),
  CONSTRAINT fk_guides_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS guide_templates (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  guide_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(200) NOT NULL,
  total_days SMALLINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_guide_templates_guide (guide_id),
  CONSTRAINT fk_guide_templates_guide FOREIGN KEY (guide_id) REFERENCES travel_guides(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS guide_template_nodes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(160) NOT NULL,
  place_name VARCHAR(160) NOT NULL,
  latitude DOUBLE NULL,
  longitude DOUBLE NULL,
  node_type VARCHAR(32) NOT NULL,
  day_offset SMALLINT UNSIGNED NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  cost DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  sequence_order INT NOT NULL,
  note VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_nodes_order (template_id, day_offset, sequence_order),
  KEY idx_template_nodes_template_day (template_id, day_offset),
  CONSTRAINT fk_template_nodes_template FOREIGN KEY (template_id) REFERENCES guide_templates(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS guide_favorites (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  guide_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_guide_favorites_guide_user (guide_id, user_id),
  KEY idx_guide_favorites_user (user_id, created_at),
  CONSTRAINT fk_guide_favorites_guide FOREIGN KEY (guide_id) REFERENCES travel_guides(id),
  CONSTRAINT fk_guide_favorites_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS guide_ratings (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  guide_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  rating TINYINT UNSIGNED NOT NULL,
  comment VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_guide_ratings_guide_user (guide_id, user_id),
  KEY idx_guide_ratings_guide (guide_id, rating),
  CONSTRAINT fk_guide_ratings_guide FOREIGN KEY (guide_id) REFERENCES travel_guides(id),
  CONSTRAINT fk_guide_ratings_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE trips
  ADD CONSTRAINT fk_trips_source_guide
  FOREIGN KEY (source_guide_id) REFERENCES travel_guides(id);

SET FOREIGN_KEY_CHECKS = 1;
