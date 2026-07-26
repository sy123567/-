-- 智能旅游平台演示数据
-- 所有账号统一使用演示密码 demo1234；password_hash 是由 BCrypt 生成的演示摘要。
USE trip_adaptive;

INSERT IGNORE INTO users
  (id, name, email, phone, password_hash, avatar, role, status, created_at)
VALUES
  (1, '林晓管理员', 'admin@trip-adaptive.demo', '13800000001',
   '$2b$10$abcdefghijklmnopqrstuubAuHEKkstfFTty6XLHR2tswEb0slScC',
   'https://images.example.com/avatar/admin.png', 'ADMIN', 'ACTIVE', '2027-01-02 09:00:00'),
  (2, '周宁', 'zhou.ning@trip-adaptive.demo', '13800000002',
   '$2b$10$abcdefghijklmnopqrstuubAuHEKkstfFTty6XLHR2tswEb0slScC',
   'https://images.example.com/avatar/zhou.png', 'USER', 'ACTIVE', '2027-01-02 09:05:00'),
  (3, '陈默', 'chen.mo@trip-adaptive.demo', '13800000003',
   '$2b$10$abcdefghijklmnopqrstuubAuHEKkstfFTty6XLHR2tswEb0slScC',
   'https://images.example.com/avatar/chen.png', 'USER', 'ACTIVE', '2027-01-02 09:10:00'),
  (4, '赵雨晴', 'zhao.yuqing@trip-adaptive.demo', '13800000004',
   '$2b$10$abcdefghijklmnopqrstuubAuHEKkstfFTty6XLHR2tswEb0slScC',
   'https://images.example.com/avatar/zhao.png', 'USER', 'ACTIVE', '2027-01-02 09:15:00'),
  (5, '王浩', 'wang.hao@trip-adaptive.demo', '13800000005',
   '$2b$10$abcdefghijklmnopqrstuubAuHEKkstfFTty6XLHR2tswEb0slScC',
   'https://images.example.com/avatar/wang.png', 'USER', 'ACTIVE', '2027-01-02 09:20:00'),
  (6, '苏婉', 'su.wan@trip-adaptive.demo', '13800000006',
   '$2b$10$abcdefghijklmnopqrstuubAuHEKkstfFTty6XLHR2tswEb0slScC',
   'https://images.example.com/avatar/su.png', 'USER', 'ACTIVE', '2027-01-02 09:25:00');

INSERT IGNORE INTO friendships
  (id, requester_id, addressee_id, pair_key, status, created_at)
VALUES
  (1, 2, 3, '2_3', 'ACCEPTED', '2027-01-03 10:00:00'),
  (2, 2, 4, '2_4', 'ACCEPTED', '2027-01-03 10:05:00'),
  (3, 3, 5, '3_5', 'ACCEPTED', '2027-01-03 10:10:00'),
  (4, 4, 6, '4_6', 'PENDING', '2027-01-03 10:15:00');

INSERT IGNORE INTO travel_groups
  (id, name, description, owner_user_id, room_code, room_code_expire_at,
   member_limit, status, created_at)
VALUES
  (1, '杭州春日慢游组', '西湖、灵隐寺与西溪湿地的多人出行计划', 2, 'HZSPRING27',
   '2027-03-31 23:59:59', 6, 'ACTIVE', '2027-01-04 08:00:00'),
  (2, '成都美食探索组', '宽窄巷子与熊猫基地周末路线', 6, 'CDFOOD27',
   '2027-04-30 23:59:59', 5, 'ACTIVE', '2027-01-05 08:00:00');

INSERT IGNORE INTO group_members
  (id, group_id, user_id, role, status, joined_at)
VALUES
  (1, 1, 2, 'OWNER', 'ACTIVE', '2027-01-04 08:01:00'),
  (2, 1, 3, 'MEMBER', 'ACTIVE', '2027-01-04 08:05:00'),
  (3, 1, 4, 'MEMBER', 'ACTIVE', '2027-01-04 08:06:00'),
  (4, 1, 5, 'MEMBER', 'ACTIVE', '2027-01-04 08:07:00'),
  (5, 2, 6, 'OWNER', 'ACTIVE', '2027-01-05 08:01:00'),
  (6, 2, 4, 'MEMBER', 'ACTIVE', '2027-01-05 08:03:00');

INSERT IGNORE INTO member_constraints
  (id, member_id, available_from, available_to, max_budget,
   must_visit_places, fitness_level, dietary_needs, accessibility_needs)
VALUES
  (1, 1, '2027-03-18', '2027-03-21', 2600.00,
   JSON_ARRAY('西湖', '灵隐寺'), 'MEDIUM', JSON_ARRAY('无特殊要求'), JSON_ARRAY()),
  (2, 2, '2027-03-18', '2027-03-21', 2200.00,
   JSON_ARRAY('西溪国家湿地公园'), 'HIGH', JSON_ARRAY('少油'), JSON_ARRAY()),
  (3, 3, '2027-03-19', '2027-03-21', 2400.00,
   JSON_ARRAY('西湖'), 'MEDIUM', JSON_ARRAY('素食可选'), JSON_ARRAY()),
  (4, 4, '2027-03-18', '2027-03-20', 1800.00,
   JSON_ARRAY('雷峰塔'), 'LOW', JSON_ARRAY('无坚果'), JSON_ARRAY('避免长距离步行')),
  (5, 5, '2027-04-10', '2027-04-12', 1800.00,
   JSON_ARRAY('宽窄巷子'), 'MEDIUM', JSON_ARRAY('无特殊要求'), JSON_ARRAY()),
  (6, 6, '2027-04-10', '2027-04-12', 1600.00,
   JSON_ARRAY('成都大熊猫繁育研究基地'), 'MEDIUM', JSON_ARRAY('少辣'), JSON_ARRAY());

INSERT IGNORE INTO travel_guides
  (id, title, cover, author_id, city, tags, theme, days, per_capita_budget,
   season, rating, rating_count, favorite_count, view_count, description, status, created_at)
VALUES
  (1, '杭州西湖三日春游攻略', 'https://images.example.com/guide/hangzhou.jpg', 2,
   '杭州', JSON_ARRAY('西湖', '春游', '亲子友好'), '自然风光', 3, 1680.00,
   '春季', 4.80, 2, 5, 1260, '覆盖西湖、灵隐寺、西溪湿地的节奏舒适路线。', 'PUBLISHED', '2027-01-06 09:00:00'),
  (2, '成都两日美食与熊猫攻略', 'https://images.example.com/guide/chengdu.jpg', 3,
   '成都', JSON_ARRAY('美食', '熊猫', '城市漫游'), '城市探索', 2, 1280.00,
   '全年', 4.60, 1, 3, 860, '适合周末出行的成都美食和文化体验路线。', 'PUBLISHED', '2027-01-06 09:30:00');

INSERT IGNORE INTO guide_templates
  (id, guide_id, title, total_days)
VALUES
  (1, 1, '杭州西湖三日标准模板', 3),
  (2, 2, '成都美食熊猫两日模板', 2);

INSERT IGNORE INTO guide_template_nodes
  (id, template_id, name, place_name, latitude, longitude, node_type,
   day_offset, start_time, end_time, cost, sequence_order, note)
VALUES
  (1, 1, '西湖断桥漫步', '杭州市西湖区断桥残雪', 30.2590, 120.1480, 'ATTRACTION',
   0, '09:00:00', '11:00:00', 0.00, 1, '早晨人流较少'),
  (2, 1, '灵隐寺参访', '杭州市西湖区灵隐路法云弄1号', 30.2400, 120.1010, 'ATTRACTION',
   0, '13:30:00', '16:00:00', 75.00, 2, '预留排队时间'),
  (3, 1, '西溪湿地游船', '杭州市西湖区天目山路518号', 30.2720, 120.0620, 'ATTRACTION',
   1, '09:30:00', '12:00:00', 120.00, 1, '可按天气调整'),
  (4, 1, '河坊街晚餐', '杭州市上城区河坊街', 30.2440, 120.1710, 'DINING',
   1, '18:00:00', '20:00:00', 160.00, 2, '支持少油选项'),
  (5, 1, '雷峰塔日落', '杭州市西湖区夕照山', 30.2300, 120.1490, 'ATTRACTION',
   2, '15:00:00', '17:30:00', 80.00, 1, '视天气决定是否保留'),
  (6, 2, '宽窄巷子早餐', '成都市青羊区宽窄巷子', 30.6710, 104.0580, 'DINING',
   0, '09:00:00', '11:00:00', 90.00, 1, '尝试本地小吃'),
  (7, 2, '熊猫基地', '成都市成华区熊猫大道1375号', 30.7330, 104.1480, 'ATTRACTION',
   0, '13:00:00', '17:00:00', 55.00, 2, '建议提前预约');

INSERT IGNORE INTO trips
  (id, group_id, title, status, start_date, end_date, total_budget,
   spent_budget, destination, room_code, source_guide_id, version, created_at)
VALUES
  (1, 1, '杭州春日三日慢游', 'IN_PROGRESS', '2027-03-18', '2027-03-20',
   8200.00, 2460.00, '杭州', 'HZSPRING27', 1, 3, '2027-01-07 10:00:00'),
  (2, 2, '成都周末美食之旅', 'PLANNED', '2027-04-10', '2027-04-11',
   4800.00, 0.00, '成都', 'CDFOOD27', 2, 1, '2027-01-07 10:30:00');

INSERT IGNORE INTO itinerary_nodes
  (id, trip_id, name, place_name, latitude, longitude, node_type,
   planned_start, planned_end, cost, sequence_order, status)
VALUES
  (1, 1, '西湖断桥漫步', '杭州市西湖区断桥残雪', 30.2590, 120.1480, 'ATTRACTION',
   '2027-03-18 09:00:00', '2027-03-18 11:00:00', 0.00, 1, 'PLANNED'),
  (2, 1, '灵隐寺参访', '杭州市西湖区灵隐路法云弄1号', 30.2400, 120.1010, 'ATTRACTION',
   '2027-03-18 13:30:00', '2027-03-18 16:00:00', 300.00, 2, 'PLANNED'),
  (3, 1, '西溪湿地游船', '杭州市西湖区天目山路518号', 30.2720, 120.0620, 'ATTRACTION',
   '2027-03-19 09:30:00', '2027-03-19 12:00:00', 480.00, 3, 'PLANNED'),
  (4, 1, '河坊街晚餐', '杭州市上城区河坊街', 30.2440, 120.1710, 'DINING',
   '2027-03-19 18:00:00', '2027-03-19 20:00:00', 640.00, 4, 'PLANNED'),
  (5, 1, '雷峰塔日落', '杭州市西湖区夕照山', 30.2300, 120.1490, 'ATTRACTION',
   '2027-03-20 15:00:00', '2027-03-20 17:30:00', 320.00, 5, 'AFFECTED'),
  (6, 2, '宽窄巷子早餐', '成都市青羊区宽窄巷子', 30.6710, 104.0580, 'DINING',
   '2027-04-10 09:00:00', '2027-04-10 11:00:00', 180.00, 1, 'PLANNED'),
  (7, 2, '熊猫基地', '成都市成华区熊猫大道1375号', 30.7330, 104.1480, 'ATTRACTION',
   '2027-04-10 13:00:00', '2027-04-10 17:00:00', 110.00, 2, 'PLANNED');

INSERT IGNORE INTO routes
  (id, trip_id, from_node_id, to_node_id, transport_mode, distance_km,
   duration_minutes, cost, polyline, source)
VALUES
  (1, 1, 1, 2, 'TAXI', 8.40, 28, 35.00, NULL, 'ESTIMATED'),
  (2, 1, 2, 3, 'TAXI', 12.10, 38, 48.00, NULL, 'MAP_PROVIDER'),
  (3, 1, 3, 4, 'SUBWAY', 9.60, 42, 12.00, NULL, 'MAP_PROVIDER'),
  (4, 1, 4, 5, 'TAXI', 5.20, 20, 24.00, NULL, 'ESTIMATED'),
  (5, 2, 6, 7, 'TAXI', 13.50, 45, 52.00, NULL, 'MAP_PROVIDER');

INSERT IGNORE INTO external_events
  (id, event_type, title, description, place_name, latitude, longitude,
   radius_km, severity, start_time, end_time, source, status, payload)
VALUES
  (1, 'WEATHER', '西湖片区短时强降雨', '预计午后有雷阵雨，户外游览体验下降。',
   '杭州市西湖区', 30.2450, 120.1450, 8.0, 'HIGH',
   '2027-03-20 12:00:00', '2027-03-20 19:00:00', '杭州气象服务', 'ACTIVE',
   JSON_OBJECT('temperature', 18, 'rainfall_mm', 35)),
  (2, 'TRAFFIC', '灵隐路周末交通管制', '景区周边部分路段限行。',
   '杭州市西湖区灵隐路', 30.2400, 120.1010, 2.0, 'MEDIUM',
   '2027-03-18 08:00:00', '2027-03-18 18:00:00', '杭州交警', 'ACTIVE',
   JSON_OBJECT('road', '灵隐路', 'restriction', '单向通行')),
  (3, 'NOTICE', '成都春熙路活动提醒', '市中心周末大型活动，建议预留交通时间。',
   '成都市锦江区', 30.6570, 104.0810, 3.0, 'LOW',
   '2027-04-10 08:00:00', '2027-04-10 22:00:00', '成都文旅', 'ACTIVE',
   JSON_OBJECT('event', '城市文化节'));

INSERT IGNORE INTO impact_assessments
  (id, trip_id, event_id, affected_node_id, risk_score, impact_level,
   description, assessment_hash, assessed_at)
VALUES
  (1, 1, 1, 5, 86, 'HIGH', '雷峰塔日落时段与降雨时间窗重叠，建议调整为室内活动。',
   'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', '2027-03-17 08:00:00'),
  (2, 1, 2, 2, 62, 'MEDIUM', '灵隐路交通管制可能增加接驳时间。',
   'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', '2027-03-17 08:00:01'),
  (3, 2, 3, 7, 31, 'LOW', '春熙路活动对熊猫基地路线影响较小。',
   'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', '2027-03-17 08:00:02');

INSERT IGNORE INTO alternative_plans
  (id, trip_id, title, strategy, extra_cost, extra_delay_minutes,
   changed_node_count, summary, status, source_assessment_hash)
VALUES
  (1, 1, '杭州雨天低成本方案', 'MIN_COST', 80.00, 15, 1,
   '将雷峰塔日落替换为中国丝绸博物馆，尽量控制新增费用。', 'ACCEPTED',
   'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'),
  (2, 1, '杭州雨天最少延误方案', 'MIN_DELAY', 150.00, 5, 1,
   '将雷峰塔调整到上午并压缩河坊街停留时间。', 'PROPOSED',
   'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'),
  (3, 1, '杭州雨天最少改动方案', 'MIN_CHANGE', 30.00, 25, 1,
   '保留大部分节点，仅将雷峰塔改为室内候雨时段。', 'REJECTED',
   'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');

INSERT IGNORE INTO node_changes
  (id, plan_id, original_node_id, change_type, new_place_name, new_start,
   new_end, new_cost, new_latitude, new_longitude, note)
VALUES
  (1, 1, 5, 'REPLACE', '中国丝绸博物馆', '2027-03-20 15:30:00',
   '2027-03-20 17:30:00', 400.00, 30.2270, 120.1540, '室内替代景点'),
  (2, 2, 5, 'RESCHEDULE', '雷峰塔', '2027-03-20 09:00:00',
   '2027-03-20 11:00:00', 320.00, 30.2300, 120.1490, '提前游览避开降雨'),
  (3, 3, 5, 'RESCHEDULE', '雷峰塔', '2027-03-20 18:00:00',
   '2027-03-20 20:00:00', 320.00, 30.2300, 120.1490, '候雨后再出发');

INSERT IGNORE INTO plan_votes
  (id, plan_id, member_id, choice, comment, voted_at)
VALUES
  (1, 1, 1, 'APPROVE', '室内替代更适合团队成员的无障碍需求。', '2027-03-17 09:00:00'),
  (2, 1, 2, 'APPROVE', '新增费用可接受。', '2027-03-17 09:02:00'),
  (3, 1, 3, 'APPROVE', '希望保留西湖整体节奏。', '2027-03-17 09:03:00'),
  (4, 1, 4, 'REJECT', '更倾向于提前游览。', '2027-03-17 09:04:00'),
  (5, 2, 1, 'ABSTAIN', '等待天气进一步确认。', '2027-03-17 09:05:00'),
  (6, 2, 2, 'APPROVE', '可以减少延误。', '2027-03-17 09:06:00');

INSERT IGNORE INTO change_logs
  (id, trip_id, description, extra_cost, refund_deadline, related_plan_id)
VALUES
  (1, 1, '采纳杭州雨天低成本方案，将雷峰塔替换为中国丝绸博物馆。', 80.00,
   '2027-03-19 18:00:00', 1),
  (2, 1, '系统记录灵隐路交通管制事件对原行程的影响评估。', 0.00,
   NULL, NULL);

INSERT IGNORE INTO bills
  (id, trip_id, title, amount, category, paid_by_member_id, split_mode, occurred_at)
VALUES
  (1, 1, '杭州往返高铁票', 1680.00, 'TRANSPORT', 1, 'EQUAL', '2027-03-10 12:00:00'),
  (2, 1, '西湖附近三晚住宿定金', 720.00, 'ACCOMMODATION', 2, 'EQUAL', '2027-03-11 15:00:00'),
  (3, 1, '河坊街团队晚餐', 640.00, 'DINING', 3, 'EQUAL', '2027-03-19 20:30:00'),
  (4, 1, '中国丝绸博物馆门票', 400.00, 'TICKET', 1, 'EQUAL', '2027-03-20 15:30:00');

INSERT IGNORE INTO bill_shares
  (id, bill_id, member_id, share_amount, share_ratio)
VALUES
  (1, 1, 1, 420.00, 0.25000), (2, 1, 2, 420.00, 0.25000),
  (3, 1, 3, 420.00, 0.25000), (4, 1, 4, 420.00, 0.25000),
  (5, 2, 1, 180.00, 0.25000), (6, 2, 2, 180.00, 0.25000),
  (7, 2, 3, 180.00, 0.25000), (8, 2, 4, 180.00, 0.25000),
  (9, 3, 1, 160.00, 0.25000), (10, 3, 2, 160.00, 0.25000),
  (11, 3, 3, 160.00, 0.25000), (12, 3, 4, 160.00, 0.25000),
  (13, 4, 1, 100.00, 0.25000), (14, 4, 2, 100.00, 0.25000),
  (15, 4, 3, 100.00, 0.25000), (16, 4, 4, 100.00, 0.25000);

INSERT IGNORE INTO guide_favorites (id, guide_id, user_id, created_at)
VALUES
  (1, 1, 3, '2027-01-07 10:00:00'),
  (2, 1, 4, '2027-01-07 10:01:00'),
  (3, 1, 5, '2027-01-07 10:02:00'),
  (4, 2, 2, '2027-01-07 10:03:00'),
  (5, 2, 4, '2027-01-07 10:04:00');

INSERT IGNORE INTO guide_ratings
  (id, guide_id, user_id, rating, comment, created_at)
VALUES
  (1, 1, 3, 5, '节点安排合理，适合多人同行。', '2027-01-08 10:00:00'),
  (2, 1, 4, 5, '对预算和饮食约束考虑得很细。', '2027-01-08 10:01:00'),
  (3, 2, 2, 5, '美食和熊猫基地的组合很顺。', '2027-01-08 10:02:00');

INSERT IGNORE INTO comments
  (id, author_id, target_type, target_id, parent_id, content, mentions, like_count)
VALUES
  (1, 3, 'TRIP', 1, NULL, '西溪湿地那天建议预留打车时间。', JSON_ARRAY(2), 3),
  (2, 2, 'TRIP', 1, 1, '收到，我会把路线缓冲加到方案里。', JSON_ARRAY(), 1),
  (3, 4, 'PLAN', 1, NULL, '中国丝绸博物馆作为雨天备选很合适。', JSON_ARRAY(), 2);

INSERT IGNORE INTO notifications
  (id, user_id, type, title, detail, payload, read_at, created_at)
VALUES
  (1, 2, 'EVENT', '杭州出现强降雨预警', '雷峰塔节点可能受影响，请查看替代方案。',
   JSON_OBJECT('eventId', 1, 'tripId', 1), NULL, '2027-03-17 08:01:00'),
  (2, 3, 'NEW_PLAN', '新的替代方案等待投票', '杭州雨天低成本方案已生成。',
   JSON_OBJECT('planId', 1, 'tripId', 1), '2027-03-17 09:10:00', '2027-03-17 08:02:00'),
  (3, 4, 'ACCEPTED', '方案已采纳', '杭州行程已应用中国丝绸博物馆替代节点。',
   JSON_OBJECT('planId', 1, 'tripId', 1), NULL, '2027-03-17 09:10:00'),
  (4, 6, 'VOTE', '成都行程有新的活动提醒', '请查看成都周末路线。',
   JSON_OBJECT('tripId', 2, 'eventId', 3), NULL, '2027-04-01 10:00:00');
