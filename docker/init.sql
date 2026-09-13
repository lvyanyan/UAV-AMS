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

-- ===== 2. 角色（role_code 主键；与 uav-system RbacInitializer 幂等种子保持一致） =====
CREATE TABLE IF NOT EXISTS sys_role (
    role_code   VARCHAR(32) PRIMARY KEY,
    role_name   VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    enabled     BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- 旧库升级兼容：老版本 sys_role 为 id 主键 + create_time，缺列则补齐（幂等）
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- ===== 3. 权限（perm_type 仅允许 MENU/BUTTON） =====
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGSERIAL PRIMARY KEY, perm_code VARCHAR(64) NOT NULL UNIQUE,
    perm_name VARCHAR(64) NOT NULL,
    perm_type VARCHAR(16) DEFAULT 'MENU' CHECK (perm_type IN ('MENU','BUTTON')),
    parent_id BIGINT DEFAULT 0, path VARCHAR(255), icon VARCHAR(64),
    sort_order INT DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE sys_permission ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- ===== 4. 角色权限关联（按 role_code + perm_code 关联，与单角色 role_code 字段并存） =====
CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_code VARCHAR(32) NOT NULL,
    perm_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_code, perm_code)
);
-- 老版本 role_id/perm_id 结构为空表，直接替换为新结构（幂等）
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'sys_role_permission' AND column_name = 'role_id') THEN
        DROP TABLE sys_role_permission;
        CREATE TABLE sys_role_permission (
            role_code VARCHAR(32) NOT NULL,
            perm_code VARCHAR(64) NOT NULL,
            PRIMARY KEY (role_code, perm_code)
        );
    END IF;
END $$;

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
    ip_address VARCHAR(50), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE sys_audit_log ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

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
    actual_start TIMESTAMP, actual_end TIMESTAMP, cmd_sent_at TIMESTAMP,
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

INSERT INTO sys_role (role_code, role_name, description, enabled) VALUES
('ADMIN','系统管理员','最高权限，拥有全部菜单与操作权限',TRUE),
('OPERATOR','操作员','登记备案与飞行计划创建、提交',TRUE),
('REGULATOR','监管员','业务监管与飞行计划审批',TRUE),
('MILITARY','军民协调员','军事调度、军事审批与一键清场',TRUE),
('SUPERVISOR','上级领导','只读查看所有业务数据',TRUE),
('PILOT','驾驶员','飞行计划填报与基础查询',TRUE)
ON CONFLICT (role_code) DO UPDATE SET
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    created_at = COALESCE(sys_role.created_at, EXCLUDED.created_at);

-- 清理旧版单词风格权限种子（已被 域:资源:操作 风格取代）
DELETE FROM sys_permission WHERE perm_code IN
    ('dashboard','flight_monitor','flight_plan','airspace_mgmt','registry','pilot_mgmt',
     'alarm_center','violation','system_mgmt','military_ops','plan_approve',
     'military_approve','airspace_clear');

-- 菜单 + 按钮权限（parent_id 先置 0，插入后统一挂接父节点，保证幂等）
INSERT INTO sys_permission (perm_code, perm_name, perm_type, parent_id, path, icon, sort_order) VALUES
    ('dashboard:menu',    '仪表盘',     'MENU',   0, '/dashboard',      'Odometer',             1),
    ('monitor:menu',      '飞行监控',   'MENU',   0, '/flight-monitor', 'Monitor',              2),
    ('replay:menu',       '消息重放',   'MENU',   0, '/message-replay', 'VideoPlay',            3),
    ('flightplan:menu',   '飞行计划',   'MENU',   0, '/flight-plan',    'Document',             4),
    ('airspace:menu',     '空域管理',   'MENU',   0, '/airspace',       'MapLocation',          5),
    ('airroute:menu',     '航路管理',   'MENU',   0, '/air-route',      'Guide',                6),
    ('airport:menu',      '起降场管理', 'MENU',   0, '/airport',        'LocationInformation',  7),
    ('registry:menu',     '实名登记',   'MENU',   0, '/registry',       'Files',                8),
    ('pilot:menu',        '飞手管理',   'MENU',   0, '/pilot',          'UserFilled',           9),
    ('alarm:menu',        '告警中心',   'MENU',   0, '/alarm',          'Bell',                10),
    ('violation:menu',    '违规处置',   'MENU',   0, '/violation',      'WarningFilled',       11),
    ('military:menu',     '军事调度',   'MENU',   0, '/military',       'Medal',               12),
    ('system:menu',       '系统管理',   'MENU',   0, '/system',         'Setting',             13),
    ('system:user:menu',  '用户管理',   'MENU',   0, '/system/users',   'UserFilled',           1),
    ('system:role:menu',  '角色管理',   'MENU',   0, '/system/roles',   'Avatar',               2),
    ('system:audit:menu', '审计日志',   'MENU',   0, '/system/audit',   'Memo',                 3),
    ('system:dict:menu',  '字典管理',   'MENU',   0, '/system/dict',    'Collection',           4),
    ('flightplan:create',     '计划创建', 'BUTTON', 0, '', '', 1),
    ('flightplan:submit',     '计划提交', 'BUTTON', 0, '', '', 2),
    ('flightplan:approve',    '计划审批', 'BUTTON', 0, '', '', 3),
    ('flightplan:military',   '军事协调', 'BUTTON', 0, '', '', 4),
    ('military:approve',      '军事批准', 'BUTTON', 0, '', '', 1),
    ('alarm:suppress',        '告警抑制', 'BUTTON', 0, '', '', 1),
    ('system:user:create',    '新增用户', 'BUTTON', 0, '', '', 1),
    ('system:user:update',    '编辑用户', 'BUTTON', 0, '', '', 2),
    ('system:user:delete',    '删除用户', 'BUTTON', 0, '', '', 3),
    ('system:user:reset-pwd', '重置密码', 'BUTTON', 0, '', '', 4),
    ('system:user:enable',    '启停用户', 'BUTTON', 0, '', '', 5),
    ('system:role:create',    '新增角色', 'BUTTON', 0, '', '', 1),
    ('system:role:update',    '编辑角色', 'BUTTON', 0, '', '', 2),
    ('system:role:delete',    '删除角色', 'BUTTON', 0, '', '', 3),
    ('system:role:assign',    '分配权限', 'BUTTON', 0, '', '', 4),
    ('system:dict:manage',    '字典维护', 'BUTTON', 0, '', '', 1),
    ('system:perm:manage',    '权限项维护', 'BUTTON', 0, '', '', 5)
ON CONFLICT (perm_code) DO UPDATE SET
    perm_name  = EXCLUDED.perm_name,
    perm_type  = EXCLUDED.perm_type,
    path       = EXCLUDED.path,
    icon       = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order;

-- 系统管理子菜单挂到 system:menu 下
UPDATE sys_permission child SET parent_id = parent.id
FROM sys_permission parent
WHERE parent.perm_code = 'system:menu'
  AND child.perm_code IN ('system:user:menu','system:role:menu','system:audit:menu','system:dict:menu');

-- 按钮权限挂到同域菜单下（flightplan:x → flightplan:menu、system:dict:manage → system:dict:menu 等）
UPDATE sys_permission child SET parent_id = parent.id
FROM sys_permission parent
WHERE child.perm_type = 'BUTTON'
  AND parent.perm_code = split_part(child.perm_code, ':', 1) || ':menu';

-- =============================================================================
-- 角色-权限映射种子（幂等）
-- =============================================================================
-- ADMIN：全部权限
INSERT INTO sys_role_permission (role_code, perm_code)
SELECT 'ADMIN', perm_code FROM sys_permission
ON CONFLICT (role_code, perm_code) DO NOTHING;

-- REGULATOR：业务菜单 + 审批类
INSERT INTO sys_role_permission (role_code, perm_code) VALUES
    ('REGULATOR','dashboard:menu'),('REGULATOR','monitor:menu'),('REGULATOR','replay:menu'),
    ('REGULATOR','flightplan:menu'),('REGULATOR','flightplan:create'),('REGULATOR','flightplan:submit'),
    ('REGULATOR','flightplan:approve'),
    ('REGULATOR','airspace:menu'),('REGULATOR','airroute:menu'),('REGULATOR','airport:menu'),
    ('REGULATOR','registry:menu'),('REGULATOR','pilot:menu'),
    ('REGULATOR','alarm:menu'),('REGULATOR','violation:menu')
ON CONFLICT (role_code, perm_code) DO NOTHING;

-- OPERATOR：登记备案 / 计划创建提交类
INSERT INTO sys_role_permission (role_code, perm_code) VALUES
    ('OPERATOR','dashboard:menu'),('OPERATOR','monitor:menu'),('OPERATOR','replay:menu'),
    ('OPERATOR','flightplan:menu'),('OPERATOR','flightplan:create'),('OPERATOR','flightplan:submit'),
    ('OPERATOR','airspace:menu'),('OPERATOR','airroute:menu'),('OPERATOR','airport:menu'),
    ('OPERATOR','registry:menu'),('OPERATOR','pilot:menu'),('OPERATOR','alarm:menu')
ON CONFLICT (role_code, perm_code) DO NOTHING;

-- MILITARY：军事调度 / 军事审批相关
INSERT INTO sys_role_permission (role_code, perm_code) VALUES
    ('MILITARY','dashboard:menu'),('MILITARY','monitor:menu'),('MILITARY','replay:menu'),
    ('MILITARY','flightplan:menu'),('MILITARY','flightplan:military'),
    ('MILITARY','military:menu'),('MILITARY','military:approve'),('MILITARY','alarm:menu')
ON CONFLICT (role_code, perm_code) DO NOTHING;

-- SUPERVISOR：只读业务菜单
INSERT INTO sys_role_permission (role_code, perm_code) VALUES
    ('SUPERVISOR','dashboard:menu'),('SUPERVISOR','monitor:menu'),('SUPERVISOR','replay:menu'),
    ('SUPERVISOR','flightplan:menu'),('SUPERVISOR','airspace:menu'),('SUPERVISOR','airroute:menu'),
    ('SUPERVISOR','airport:menu'),('SUPERVISOR','registry:menu'),('SUPERVISOR','pilot:menu'),
    ('SUPERVISOR','alarm:menu'),('SUPERVISOR','violation:menu'),('SUPERVISOR','military:menu')
ON CONFLICT (role_code, perm_code) DO NOTHING;

-- PILOT：基础菜单 + 计划填报
INSERT INTO sys_role_permission (role_code, perm_code) VALUES
    ('PILOT','dashboard:menu'),('PILOT','flightplan:menu'),
    ('PILOT','flightplan:create'),('PILOT','flightplan:submit'),('PILOT','alarm:menu')
ON CONFLICT (role_code, perm_code) DO NOTHING;

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
