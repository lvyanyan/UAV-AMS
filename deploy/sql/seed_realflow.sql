-- =====================================================================
-- 真实飞行流程演示种子数据（幂等，可重复执行）
-- 对应 uav-simulator「真实飞行流程」场景：REG-UAV-000x 执行 FP-20260911-000x
-- =====================================================================

-- ===== 运营主体（运营人） =====
insert into uav_owner (id, owner_name, id_type, id_number, phone, email, address, register_status) values
 (1, '华北低空运营集团有限公司', 'USCC', '91110108MA01A2X345', '010-66001201', 'ops@hbdk-uav.cn', '北京市大兴区宏业路9号', 'APPROVED'),
 (2, '中翼航空科技服务有限公司', 'USCC', '91110109MA01B3Y456', '010-66001202', 'ops@zyhk-tech.cn', '北京市通州区经海路5号', 'APPROVED'),
 (3, '京南通用航空股份有限公司', 'USCC', '91110115MA01C4Z567', '010-66001203', 'ops@jngeneralaviation.cn', '北京市大兴区机场路1号', 'APPROVED')
on conflict (id) do nothing;

-- ===== 无人机实名登记（SN 与仿真器 REG 机队对应） =====
insert into uav_registration (registration_id, drone_sn, owner_id, drone_model, drone_type, weight_g, manufacturer, register_status) values
 ('UAS-BJ-2026-0001', 'REG-UAV-0001', 1, 'Matrice 350 RTK',   'MULTIROTOR', 9200,  'DJI',      'APPROVED'),
 ('UAS-BJ-2026-0002', 'REG-UAV-0002', 1, 'Matrice 30T',      'MULTIROTOR', 3770,  'DJI',      'APPROVED'),
 ('UAS-BJ-2026-0003', 'REG-UAV-0003', 2, 'CW-25E 垂起固定翼', 'FIXED_WING', 6500,  '纵横股份',  'APPROVED'),
 ('UAS-BJ-2026-0004', 'REG-UAV-0004', 2, 'FlyCart 30',       'MULTIROTOR', 25000, 'DJI',      'APPROVED'),
 ('UAS-BJ-2026-0005', 'REG-UAV-0005', 3, 'Mavic 3 行业版',    'MULTIROTOR', 895,   'DJI',      'APPROVED'),
 ('UAS-BJ-2026-0006', 'REG-UAV-0006', 3, 'Air 3S',           'MULTIROTOR', 720,   'DJI',      'APPROVED'),
 ('UAS-BJ-2026-0007', 'SIM-DRONE-001', 2, 'FCONE-2700',      'MULTIROTOR', 2700,  '丰翼科技',  'APPROVED')
on conflict (drone_sn) do nothing;

-- ===== 驾驶员 =====
insert into uav_pilot (id, pilot_name, id_number, phone, license_level, license_no, license_expire, status) values
 (1, '张伟', '110101199001011234', '13801010001', 'CAAC 超视距教员',   'UAT-LICENSE-2024-0101', '2027-12-31', 'ACTIVE'),
 (2, '李强', '110101199102022345', '13801010002', 'CAAC 超视距驾驶员', 'UAT-LICENSE-2024-0102', '2027-06-30', 'ACTIVE'),
 (3, '王芳', '110101199203033456', '13801010003', 'CAAC 视距内驾驶员', 'UAT-LICENSE-2025-0103', '2028-03-31', 'ACTIVE'),
 (4, '赵磊', '110101199304044567', '13801010004', 'CAAC 超视距驾驶员', 'UAT-LICENSE-2025-0104', '2027-09-30', 'ACTIVE'),
 (5, '陈晨', '110101199505055678', '13801010005', 'CAAC 教员级',       'UAT-LICENSE-2024-0105', '2028-06-30', 'ACTIVE'),
 (6, '刘洋', '110101199606066789', '13801010006', 'CAAC 视距内驾驶员', 'UAT-LICENSE-2025-0106', '2027-12-31', 'SUSPENDED')
on conflict (id) do nothing;

-- ===== 空域 =====
insert into uav_airspace (airspace_name, airspace_code, airspace_type, geo_json, alt_floor_m, alt_ceiling_m, is_active, description) values
 ('大兴低空示范区',   'BJ-DAXING-01',   'DEMO',      '{"type":"Polygon","coordinates":[[[116.33,39.68],[116.45,39.68],[116.45,39.78],[116.33,39.78],[116.33,39.68]]]}', 0, 300, true,  '大兴区低空经济示范区（临时空域）'),
 ('亦庄物流走廊',     'BJ-YIZHUANG-02', 'CORRIDOR',  '{"type":"Polygon","coordinates":[[[116.48,39.75],[116.56,39.75],[116.56,39.82],[116.48,39.82],[116.48,39.75]]]}', 50, 120, true,  '亦庄—通州物流配送走廊'),
 ('通州巡检作业区',   'BJ-TONGZHOU-03', 'OPERATION', '{"type":"Polygon","coordinates":[[[116.60,39.82],[116.72,39.82],[116.72,39.92],[116.60,39.92],[116.60,39.82]]]}', 0, 150, true,  '电网巡检作业空域'),
 ('首都机场管制区',   'BJ-PEK-CTL',     'CONTROL',   '{"type":"Polygon","coordinates":[[[116.55,40.02],[116.65,40.02],[116.65,40.12],[116.55,40.12],[116.55,40.02]]]}', 0, 600, false, '首都机场管制区（禁飞示例）')
on conflict (airspace_code) do nothing;

-- ===== 飞行计划（已审批，与仿真器真实流程场景绑定） =====
insert into flight_plan (plan_code, plan_status, drone_sn, pilot_id, departure, destination, planned_start, planned_end, alt_floor_m, alt_ceiling_m, flight_purpose, risk_level, military_approved) values
 ('FP-20260911-0001', 'APPROVED', 'REG-UAV-0001', 1, '大兴机场南起降点',   '廊坊高新区物流枢纽',  now(), now() + interval '2 hours',     50,  300, '物流配送',     'MEDIUM', true),
 ('FP-20260911-0002', 'APPROVED', 'REG-UAV-0002', 2, '亦庄健康监测点',     '通州大运河巡检段',    now(), now() + interval '2 hours',     80,  300, '电力巡检',     'MEDIUM', true),
 ('FP-20260911-0003', 'APPROVED', 'REG-UAV-0003', 3, '通州航测基准点',     '亦庄东工业区',        now(), now() + interval '3 hours',    100,  300, '航空测绘',     'LOW',    true),
 ('FP-20260911-0004', 'APPROVED', 'REG-UAV-0004', 4, '大兴区应急物资库',   '廊坊高新区物流枢纽',  now(), now() + interval '90 minutes',  60,  280, '应急通信保障', 'HIGH',   true),
 ('FP-20260911-0005', 'APPROVED', 'REG-UAV-0005', 5, '通州大运河巡检段',   '大兴区观测场',        now(), now() + interval '3 hours',    120,  400, '航空测绘',     'LOW',    true),
 ('FP-20260911-0006', 'APPROVED', 'REG-UAV-0006', 6, '亦庄滨河公园',       '大兴区机场路沿线',    now(), now() + interval '90 minutes',  50,  200, '警用巡逻',     'MEDIUM', true)
on conflict (plan_code) do nothing;
