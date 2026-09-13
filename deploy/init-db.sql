-- =============================================================================
-- UAV-AMS 数据库初始化脚本 (PostgreSQL 16 + PostGIS)
-- =============================================================================

-- 启用 PostGIS 扩展
CREATE EXTENSION IF NOT EXISTS postgis;

-- =============================================================================
-- 1. 系统管理模块 (sys_)
-- =============================================================================

-- 角色表（role_code 主键；与 uav-system RbacInitializer 幂等种子保持一致）
CREATE TABLE IF NOT EXISTS sys_role (
    role_code   VARCHAR(32)  PRIMARY KEY,
    role_name   VARCHAR(64)  NOT NULL,
    description VARCHAR(255),
    enabled     BOOLEAN DEFAULT true,
    created_at  TIMESTAMP DEFAULT now()
);
-- 旧库升级兼容：老版本 sys_role 为 id 主键 + create_time，缺列则补齐（幂等）
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT true;
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT now();

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password      VARCHAR(255) NOT NULL,
    real_name     VARCHAR(64),
    phone         VARCHAR(20),
    email         VARCHAR(128),
    org_id        BIGINT,
    role_code     VARCHAR(32)  NOT NULL,
    enabled       BOOLEAN DEFAULT true,
    mfa_enabled   BOOLEAN DEFAULT false,
    mfa_type      VARCHAR(16),
    mfa_secret    VARCHAR(128),
    ukey_cert_sn  VARCHAR(128),
    create_time   TIMESTAMP DEFAULT now(),
    update_time   TIMESTAMP DEFAULT now()
);

-- 权限表（perm_type 仅允许 MENU/BUTTON）
CREATE TABLE IF NOT EXISTS sys_permission (
    id         BIGSERIAL PRIMARY KEY,
    perm_code  VARCHAR(64) NOT NULL UNIQUE,
    perm_name  VARCHAR(64) NOT NULL,
    perm_type  VARCHAR(16) DEFAULT 'MENU' CHECK (perm_type IN ('MENU','BUTTON')),
    parent_id  BIGINT DEFAULT 0,
    path       VARCHAR(255),
    icon       VARCHAR(64),
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT now()
);
ALTER TABLE sys_permission ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT now();

-- 角色权限关联表（按 role_code + perm_code 关联，与单角色 role_code 字段并存）
CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_code   VARCHAR(32) NOT NULL,
    perm_code   VARCHAR(64) NOT NULL,
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

-- 审计日志表
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT,
    username     VARCHAR(64),
    action       VARCHAR(64),
    target       VARCHAR(255),
    detail       TEXT,
    ip_address   VARCHAR(64),
    created_at   TIMESTAMP DEFAULT now(),
    create_time  TIMESTAMP DEFAULT now()
);
ALTER TABLE sys_audit_log ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT now();

-- 组织机构表
CREATE TABLE IF NOT EXISTS sys_organization (
    id          BIGSERIAL PRIMARY KEY,
    org_name    VARCHAR(128) NOT NULL,
    parent_id   BIGINT DEFAULT 0,
    org_type    VARCHAR(32),
    create_time TIMESTAMP DEFAULT now()
);

-- =============================================================================
-- 2. 无人机实名登记模块
-- =============================================================================

CREATE TABLE IF NOT EXISTS uav_owner (
    id            BIGSERIAL PRIMARY KEY,
    owner_name    VARCHAR(128) NOT NULL,
    id_type       VARCHAR(32),
    id_number     VARCHAR(64),
    phone         VARCHAR(20),
    address       VARCHAR(255),
    status        VARCHAR(16) DEFAULT 'ACTIVE',
    create_time   TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS uav_registration (
    id              BIGSERIAL PRIMARY KEY,
    owner_id        BIGINT REFERENCES uav_owner(id),
    drone_sn        VARCHAR(64) NOT NULL UNIQUE,
    model           VARCHAR(64),
    manufacturer    VARCHAR(64),
    weight_kg       NUMERIC(6,2),
    category        VARCHAR(16),
    registration_no VARCHAR(64),
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    create_time     TIMESTAMP DEFAULT now()
);

-- =============================================================================
-- 3. 驾驶员管理模块
-- =============================================================================

CREATE TABLE IF NOT EXISTS uav_pilot (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(64) NOT NULL,
    id_type         VARCHAR(32),
    id_number       VARCHAR(64),
    phone           VARCHAR(20),
    license_no      VARCHAR(64),
    license_type    VARCHAR(16),
    license_expire  DATE,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    create_time     TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS uav_pilot_medical (
    id              BIGSERIAL PRIMARY KEY,
    pilot_id        BIGINT NOT NULL,
    exam_date       DATE,
    exam_result     VARCHAR(32),
    attachment_url  VARCHAR(512),
    expire_date     DATE,
    create_time     TIMESTAMP DEFAULT now()
);

-- =============================================================================
-- 4. 空域管理模块
-- =============================================================================

CREATE TABLE IF NOT EXISTS uav_airspace_zone (
    id          BIGSERIAL PRIMARY KEY,
    zone_code   VARCHAR(32) NOT NULL UNIQUE,
    zone_name   VARCHAR(128),
    zone_type   VARCHAR(32),
    boundary    GEOMETRY(POLYGON, 4326),
    min_alt_m   NUMERIC(6,1),
    max_alt_m   NUMERIC(8,1),
    is_active   BOOLEAN DEFAULT true,
    create_time TIMESTAMP DEFAULT now()
);

-- 航路表
CREATE TABLE IF NOT EXISTS uav_route (
    id          BIGSERIAL PRIMARY KEY,
    route_code  VARCHAR(32) NOT NULL UNIQUE,
    route_name  VARCHAR(128),
    corridor    GEOMETRY(LINESTRING, 4326),
    width_m     NUMERIC(6,1),
    min_alt_m   NUMERIC(6,1),
    max_alt_m   NUMERIC(8,1),
    is_active   BOOLEAN DEFAULT true,
    create_time TIMESTAMP DEFAULT now()
);

-- =============================================================================
-- 5. 飞行计划模块
-- =============================================================================

CREATE TABLE IF NOT EXISTS flight_plan (
    id              BIGSERIAL PRIMARY KEY,
    plan_no         VARCHAR(32) NOT NULL UNIQUE,
    drone_sn        VARCHAR(64),
    pilot_id        BIGINT,
    plan_status     VARCHAR(16) DEFAULT 'DRAFT',
    takeoff_lat     NUMERIC(10,7),
    takeoff_lon     NUMERIC(10,7),
    landing_lat     NUMERIC(10,7),
    landing_lon     NUMERIC(10,7),
    planned_alt_m   NUMERIC(6,1),
    planned_route   GEOMETRY(LINESTRING, 4326),
    takeoff_time    TIMESTAMP,
    landing_time    TIMESTAMP,
    risk_level      VARCHAR(16),
    create_by       BIGINT,
    create_time     TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS flight_plan_approval (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         BIGINT NOT NULL,
    approve_node    VARCHAR(32),
    approver_id     BIGINT,
    approve_result  VARCHAR(16),
    approve_comment VARCHAR(512),
    approve_time    TIMESTAMP DEFAULT now()
);

-- =============================================================================
-- 6. 告警与违规模块
-- =============================================================================

CREATE TABLE IF NOT EXISTS alarm_record (
    id              BIGSERIAL PRIMARY KEY,
    drone_sn        VARCHAR(64),
    alarm_type      VARCHAR(32),
    alarm_level     VARCHAR(16),
    alarm_msg       VARCHAR(512),
    lat             NUMERIC(10,7),
    lon             NUMERIC(10,7),
    alt_m           NUMERIC(7,1),
    detail_json     TEXT,
    video_clip_path VARCHAR(512),
    is_handled      BOOLEAN DEFAULT false,
    create_time     TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS violation_record (
    id              BIGSERIAL PRIMARY KEY,
    drone_sn        VARCHAR(64),
    pilot_id        BIGINT,
    violation_type  VARCHAR(32),
    violation_level VARCHAR(16),
    description     VARCHAR(512),
    evidence_url    VARCHAR(512),
    penalty         VARCHAR(255),
    status          VARCHAR(16) DEFAULT 'RECORDED',
    create_time     TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS violation_penalty_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_code       VARCHAR(16) UNIQUE,
    rule_name       VARCHAR(128),
    violation_type  VARCHAR(32),
    violation_level VARCHAR(16),
    penalty_desc    VARCHAR(512),
    penalty_action  VARCHAR(255),
    is_active       BOOLEAN DEFAULT true
);

-- =============================================================================
-- 7. 军民协调模块
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_military_clearance (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         BIGINT,
    coordinator_id  BIGINT,
    action          VARCHAR(32),
    reason          VARCHAR(512),
    cleared_zones   TEXT,
    create_time     TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sys_mfa_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    mfa_type        VARCHAR(16),
    verify_result   BOOLEAN,
    ip_address      VARCHAR(64),
    create_time     TIMESTAMP DEFAULT now()
);

-- =============================================================================
-- 预置角色数据（幂等：冲突时仅更新名称/描述，不覆盖 enabled 状态）
-- =============================================================================
INSERT INTO sys_role (role_code, role_name, description, enabled) VALUES
    ('ADMIN',      '系统管理员', '最高权限，拥有全部菜单与操作权限', true),
    ('OPERATOR',   '操作员',     '登记备案与飞行计划创建、提交',     true),
    ('REGULATOR',  '监管员',     '业务监管与飞行计划审批',           true),
    ('MILITARY',   '军民协调员', '军事调度、军事审批与一键清场',     true),
    ('SUPERVISOR', '上级领导',   '只读查看所有业务数据',             true),
    ('PILOT',      '驾驶员',     '飞行计划填报与基础查询',           true)
ON CONFLICT (role_code) DO UPDATE SET
    role_name   = EXCLUDED.role_name,
    description = EXCLUDED.description,
    created_at  = COALESCE(sys_role.created_at, EXCLUDED.created_at);

-- 预置管理员 (密码: admin123)
INSERT INTO sys_user (username, password, real_name, role_code, enabled, mfa_enabled, mfa_type, mfa_secret)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        '系统管理员', 'ADMIN', true, false, NULL, NULL)
ON CONFLICT (username) DO NOTHING;

-- 预置军民协调员 (密码: military123, 二次验证: 123456)
INSERT INTO sys_user (username, password, real_name, role_code, enabled, mfa_enabled, mfa_type, mfa_secret)
VALUES ('military', '$2a$10$8KWE8Rp1iWqMkK0pFK6AP.xTLiUs5H4DBkANrLnJMWl3YRrQF9H3a',
        '军民协调员', 'MILITARY', true, true, 'TOTP', 'DEV_FIXED_123456')
ON CONFLICT (username) DO NOTHING;

-- 预置操作员
INSERT INTO sys_user (username, password, real_name, role_code, enabled)
VALUES ('operator', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        '操作员01', 'OPERATOR', true)
ON CONFLICT (username) DO NOTHING;

-- =============================================================================
-- RBAC 权限种子（perm_code 统一采用 域:资源:操作 风格，与 RbacInitializer 一致）
-- =============================================================================
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

-- 预置违规处罚规则
INSERT INTO violation_penalty_rule (rule_code, rule_name, violation_type, violation_level, penalty_desc, penalty_action) VALUES
    ('R001', '一般禁飞区闯入', 'GEOFENCE', 'GENERAL', '警告通知', '发送警告消息'),
    ('R002', '严重禁飞区闯入', 'GEOFENCE', 'SERIOUS', '罚款5000-20000元', '强制返航+记录违规'),
    ('R003', '核心禁飞区闯入', 'GEOFENCE', 'CRITICAL', '强制返航+上报UOM', '强制返航+违规归档+上报'),
    ('R004', '超速飞行', 'SPEED', 'GENERAL', '警告', '发送警告消息'),
    ('R005', '超高速飞行', 'SPEED', 'SERIOUS', '罚款3000-10000元', '强制降速+记录'),
    ('R006', '偏离航路', 'ROUTE_DEVIATION', 'GENERAL', '引导至航路', '发送纠正指令'),
    ('R007', '严重偏离航路', 'ROUTE_DEVIATION', 'CRITICAL', '禁飞365天', '强制返航+驾驶员禁飞')
ON CONFLICT (rule_code) DO NOTHING;

-- 创建空间索引
CREATE INDEX IF NOT EXISTS idx_airspace_boundary ON uav_airspace_zone USING GIST(boundary);
CREATE INDEX IF NOT EXISTS idx_route_corridor ON uav_route USING GIST(corridor);
CREATE INDEX IF NOT EXISTS idx_flight_plan_route ON flight_plan USING GIST(planned_route);

SELECT 'UAV-AMS 数据库初始化完成' AS status;
