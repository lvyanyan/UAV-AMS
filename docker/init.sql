-- UAV-AMS 数据库初始化 (PostgreSQL 16 + PostGIS)
CREATE EXTENSION IF NOT EXISTS postgis;

-- ===== 1. 组织机构 =====
CREATE TABLE IF NOT EXISTS sys_organization (
    id BIGSERIAL PRIMARY KEY, org_name VARCHAR(100) NOT NULL,
    org_code VARCHAR(50) NOT NULL UNIQUE, parent_id BIGINT DEFAULT 0,
    org_type VARCHAR(20) DEFAULT 'DEPARTMENT', address VARCHAR(255),
    contact VARCHAR(50), phone VARCHAR(20), enabled BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 2. 角色 =====
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGSERIAL PRIMARY KEY, role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL, description VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 3. 权限 =====
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGSERIAL PRIMARY KEY, perm_code VARCHAR(100) NOT NULL UNIQUE,
    perm_name VARCHAR(100) NOT NULL, perm_type VARCHAR(20) DEFAULT 'MENU',
    parent_id BIGINT DEFAULT 0, path VARCHAR(255), icon VARCHAR(100),
    sort_order INT DEFAULT 0, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 4. 角色权限关联 =====
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGSERIAL PRIMARY KEY, role_id BIGINT NOT NULL,
    perm_id BIGINT NOT NULL, UNIQUE (role_id, perm_id)
);

-- ===== 5. 用户 =====
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY, username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL, real_name VARCHAR(50),
    phone VARCHAR(20), email VARCHAR(100), org_id BIGINT DEFAULT 0,
    role_code VARCHAR(50) DEFAULT 'OPERATOR', enabled BOOLEAN DEFAULT TRUE,
    mfa_enabled BOOLEAN DEFAULT FALSE, mfa_type VARCHAR(20),
    mfa_secret VARCHAR(255), ukey_cert_sn VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 6. MFA日志 =====
CREATE TABLE IF NOT EXISTS sys_mfa_log (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL,
    mfa_type VARCHAR(20), success BOOLEAN, ip_address VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 7. 审计日志 =====
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT, username VARCHAR(50),
    action VARCHAR(100), target VARCHAR(255), detail TEXT,
    ip_address VARCHAR(50), create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 8. 空域 =====
CREATE TABLE IF NOT EXISTS uav_airspace (
    id BIGSERIAL PRIMARY KEY, airspace_name VARCHAR(100) NOT NULL,
    airspace_code VARCHAR(50) NOT NULL UNIQUE,
    airspace_type VARCHAR(30) NOT NULL, geo_json TEXT NOT NULL,
    alt_floor_m DOUBLE PRECISION DEFAULT 0, alt_ceiling_m DOUBLE PRECISION DEFAULT 120,
    is_active BOOLEAN DEFAULT TRUE, start_time TIMESTAMP, end_time TIMESTAMP,
    description TEXT, h3_cells TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
SELECT AddGeometryColumn('uav_airspace', 'geom', 4326, 'POLYGON', 2);
CREATE INDEX IF NOT EXISTS idx_airspace_geom ON uav_airspace USING GIST (geom);

-- ===== 9. 地理围栏 =====
CREATE TABLE IF NOT EXISTS uav_geofence (
    id BIGSERIAL PRIMARY KEY, fence_name VARCHAR(100) NOT NULL,
    fence_type VARCHAR(30) NOT NULL, geo_json TEXT NOT NULL,
    alt_floor_m DOUBLE PRECISION DEFAULT 0, alt_ceiling_m DOUBLE PRECISION DEFAULT 5000,
    is_active BOOLEAN DEFAULT TRUE, description TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
SELECT AddGeometryColumn('uav_geofence', 'geom', 4326, 'POLYGON', 2);
CREATE INDEX IF NOT EXISTS idx_geofence_geom ON uav_geofence USING GIST (geom);

-- ===== 10. 航路 =====
CREATE TABLE IF NOT EXISTS uav_route (
    id BIGSERIAL PRIMARY KEY, route_name VARCHAR(100) NOT NULL,
    route_code VARCHAR(50) NOT NULL UNIQUE, waypoints TEXT NOT NULL,
    corridor_width_m DOUBLE PRECISION DEFAULT 100,
    direction VARCHAR(10) DEFAULT 'BOTH', is_active BOOLEAN DEFAULT TRUE,
    description TEXT, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
SELECT AddGeometryColumn('uav_route', 'geom', 4326, 'LINESTRING', 2);
CREATE INDEX IF NOT EXISTS idx_route_geom ON uav_route USING GIST (geom);

-- ===== 11. 飞行计划 =====
CREATE TABLE IF NOT EXISTS flight_plan (
    id BIGSERIAL PRIMARY KEY, plan_code VARCHAR(50) NOT NULL UNIQUE,
    plan_status VARCHAR(30) DEFAULT 'DRAFT', drone_sn VARCHAR(50),
    pilot_id BIGINT, route_id BIGINT, departure VARCHAR(255),
    destination VARCHAR(255), planned_start TIMESTAMP, planned_end TIMESTAMP,
    alt_floor_m DOUBLE PRECISION, alt_ceiling_m DOUBLE PRECISION,
    flight_purpose VARCHAR(255), risk_level VARCHAR(20),
    submitter_id BIGINT, submit_time TIMESTAMP,
    military_approval_id BIGINT, military_approved BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 12. 审批记录 =====
CREATE TABLE IF NOT EXISTS flight_plan_approval (
    id BIGSERIAL PRIMARY KEY, plan_id BIGINT NOT NULL,
    approver_id BIGINT NOT NULL, approval_level INT DEFAULT 1,
    result VARCHAR(20), comment TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 13-14. 实名登记 =====
CREATE TABLE IF NOT EXISTS uav_owner (
    id BIGSERIAL PRIMARY KEY, owner_name VARCHAR(100) NOT NULL,
    id_type VARCHAR(20), id_number VARCHAR(50), phone VARCHAR(20),
    email VARCHAR(100), address VARCHAR(255),
    register_status VARCHAR(20) DEFAULT 'PENDING',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS uav_registration (
    id BIGSERIAL PRIMARY KEY, registration_id VARCHAR(50) NOT NULL UNIQUE,
    drone_sn VARCHAR(50) NOT NULL UNIQUE, owner_id BIGINT NOT NULL,
    drone_model VARCHAR(100), drone_type VARCHAR(30),
    weight_g DOUBLE PRECISION, manufacturer VARCHAR(100),
    register_status VARCHAR(20) DEFAULT 'PENDING',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 15-16. 驾驶员 =====
CREATE TABLE IF NOT EXISTS uav_pilot (
    id BIGSERIAL PRIMARY KEY, pilot_name VARCHAR(100) NOT NULL,
    id_number VARCHAR(50), phone VARCHAR(20), email VARCHAR(100),
    license_level VARCHAR(20), license_no VARCHAR(50) UNIQUE,
    license_expire TIMESTAMP, status VARCHAR(20) DEFAULT 'ACTIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS uav_pilot_medical (
    id BIGSERIAL PRIMARY KEY, pilot_id BIGINT NOT NULL,
    exam_date TIMESTAMP, exam_result VARCHAR(30),
    exam_report_url VARCHAR(255), expire_date TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 17-18. 告警/冲突 =====
CREATE TABLE IF NOT EXISTS alarm_record (
    id BIGSERIAL PRIMARY KEY, drone_sn VARCHAR(50) NOT NULL,
    alarm_type VARCHAR(30) NOT NULL, alarm_level VARCHAR(20) NOT NULL,
    alarm_content TEXT, lat DOUBLE PRECISION, lng DOUBLE PRECISION,
    alt DOUBLE PRECISION, handled BOOLEAN DEFAULT FALSE,
    video_clip_path VARCHAR(500), video_duration_s INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS conflict_record (
    id BIGSERIAL PRIMARY KEY, drone_a_sn VARCHAR(50) NOT NULL,
    drone_b_sn VARCHAR(50) NOT NULL, conflict_type VARCHAR(30),
    collision_prob DOUBLE PRECISION, tcpa_s DOUBLE PRECISION,
    min_distance_m DOUBLE PRECISION, resolution_cmd TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 19. 违规规则 =====
CREATE TABLE IF NOT EXISTS violation_penalty_rule (
    id BIGSERIAL PRIMARY KEY, rule_code VARCHAR(50) NOT NULL UNIQUE,
    violation_type VARCHAR(100) NOT NULL,
    violation_level VARCHAR(20) NOT NULL, penalty_desc TEXT,
    penalty_type VARCHAR(50), fine_min_yuan DOUBLE PRECISION,
    fine_max_yuan DOUBLE PRECISION, grounding_days INT,
    is_active BOOLEAN DEFAULT TRUE, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== 20-21. 视频/外部集成 =====
CREATE TABLE IF NOT EXISTS video_clip (
    id BIGSERIAL PRIMARY KEY, drone_sn VARCHAR(50) NOT NULL,
    alarm_id BIGINT, clip_path VARCHAR(500), duration_s INT,
    file_size_bytes BIGINT, start_time TIMESTAMP, end_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS external_integration_log (
    id BIGSERIAL PRIMARY KEY, system_code VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL, payload TEXT,
    status VARCHAR(20), retry_count INT DEFAULT 0, error_msg TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ================================================================
-- 种子数据: 角色、权限、组织、规则
-- 用户由 uav-system 的 DataInitializer 在启动时创建 (BCrypt 编码)
-- ================================================================

INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN','系统管理员','全部权限'),
('REGULATOR','监管员','飞行审批、违规处置'),
('OPERATOR','操作员','飞行计划管理、数据查看'),
('SUPERVISOR','监察员','只读查看'),
('MILITARY','军民协调员','军事调度、一键批准'),
('PILOT','驾驶员','飞行申报、个人数据')
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO sys_permission (perm_code, perm_name, perm_type, parent_id, path, icon, sort_order) VALUES
('dashboard','仪表盘','MENU',0,'/dashboard','Odometer',1),
('flight_monitor','飞行监控','MENU',0,'/flight-monitor','Monitor',2),
('flight_plan','飞行计划','MENU',0,'/flight-plan','Document',3),
('airspace_mgmt','空域管理','MENU',0,'/airspace','MapLocation',4),
('registry','实名登记','MENU',0,'/registry','Files',5),
('pilot_mgmt','驾驶员管理','MENU',0,'/pilot','UserFilled',6),
('alarm_center','告警中心','MENU',0,'/alarm','Bell',7),
('violation','违规处置','MENU',0,'/violation','WarningFilled',8),
('system_mgmt','系统管理','MENU',0,'/system','Setting',9),
('military_ops','军事调度','MENU',0,'/military','Medal',10),
('plan_approve','计划审批','BUTTON',3,'','',0),
('military_approve','军事批准','BUTTON',10,'','',0),
('airspace_clear','空域清场','BUTTON',10,'','',0)
ON CONFLICT (perm_code) DO NOTHING;

INSERT INTO sys_organization (org_name, org_code, parent_id, org_type) VALUES
('无人机监管中心','UAV-REG-001',0,'HEADQUARTER'),
('飞行管理部','UAV-FLIGHT',1,'DEPARTMENT'),
('技术保障部','UAV-TECH',1,'DEPARTMENT'),
('军民协调办公室','UAV-MIL',1,'DEPARTMENT')
ON CONFLICT (org_code) DO NOTHING;

INSERT INTO violation_penalty_rule (rule_code, violation_type, violation_level, penalty_desc, penalty_type, fine_min_yuan, fine_max_yuan, grounding_days) VALUES
('R001','一般禁飞区闯入','GENERAL','警告通知，责令改正','WARNING',0,0,0),
('R002','严重禁飞区闯入','SERIOUS','罚款并暂扣飞行资格','FINE',5000,20000,30),
('R003','核心禁飞区闯入','CRITICAL','强制返航、上报民航局','REPORT',20000,100000,365),
('R004','超速飞行','GENERAL','警告通知','WARNING',0,0,0),
('R005','超高度飞行','SERIOUS','罚款','FINE',3000,10000,15),
('R006','未申报飞行','CRITICAL','禁飞并罚款','FINE',10000,50000,90),
('R007','危险操作','CRITICAL','吊销飞行资格','GROUNDING',0,0,365)
ON CONFLICT (rule_code) DO NOTHING;

-- ================================================================
-- 空域种子数据（北京周边样例）
-- ================================================================
INSERT INTO uav_airspace (airspace_name, airspace_code, airspace_type, geo_json, alt_floor_m, alt_ceiling_m, is_active, description) VALUES
('北京首都机场管制空域','ZBA-CTR','CTR','{"type":"Polygon","coordinates":[[[116.45,40.18],[116.85,40.15],[116.95,39.95],[116.80,39.75],[116.50,39.72],[116.30,39.78],[116.25,39.95],[116.30,40.10],[116.45,40.18]]]}',0,6000,true,'首都机场周边管制空域，未经批准禁止无人机进入'),
('天安门核心禁飞区','ZBA-NFZ1','NO_FLY','{"type":"Polygon","coordinates":[[[116.365,39.915],[116.410,39.915],[116.410,39.905],[116.400,39.895],[116.390,39.897],[116.378,39.902],[116.365,39.905],[116.365,39.915]]]}',0,1000,true,'天安门广场及周边绝对禁飞区'),
('大兴机场管制空域','ZBAD-CTR','CTR','{"type":"Polygon","coordinates":[[[116.20,39.62],[116.55,39.68],[116.60,39.55],[116.50,39.40],[116.30,39.38],[116.05,39.42],[116.00,39.52],[116.20,39.62]]]}',0,6000,true,'大兴国际机场管制空域'),
('延庆无人机测试区','ZBA-TEST','TEST','{"type":"Polygon","coordinates":[[[115.90,40.55],[116.10,40.55],[116.10,40.35],[115.90,40.35],[115.90,40.55]]]}',50,300,true,'延庆无人机测试飞行区'),
('通州限飞区','ZBA-RST','RESTRICTED','{"type":"Polygon","coordinates":[[[116.58,39.90],[116.68,39.90],[116.68,39.82],[116.58,39.82],[116.58,39.90]]]}',0,200,true,'通州行政副中心限飞区')
ON CONFLICT (airspace_code) DO NOTHING;

-- 同步更新 geom 字段（PostGIS 空间索引）
UPDATE uav_airspace SET geom = ST_SetSRID(ST_GeomFromGeoJSON(geo_json), 4326) WHERE geom IS NULL;
