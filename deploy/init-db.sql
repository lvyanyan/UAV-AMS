-- =============================================================================
-- UAV-AMS 数据库初始化脚本 (PostgreSQL 16 + PostGIS)
-- =============================================================================

-- 启用 PostGIS 扩展
CREATE EXTENSION IF NOT EXISTS postgis;

-- =============================================================================
-- 1. 系统管理模块 (sys_)
-- =============================================================================

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(32)  NOT NULL UNIQUE,
    role_name   VARCHAR(64)  NOT NULL,
    description VARCHAR(255),
    create_time TIMESTAMP DEFAULT now()
);

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

-- 权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id         BIGSERIAL PRIMARY KEY,
    perm_code  VARCHAR(64) NOT NULL UNIQUE,
    perm_name  VARCHAR(64) NOT NULL,
    perm_type  VARCHAR(16) DEFAULT 'MENU',
    parent_id  BIGINT DEFAULT 0,
    path       VARCHAR(255),
    icon       VARCHAR(64),
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT now()
);

-- 审计日志表
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT,
    username     VARCHAR(64),
    action       VARCHAR(64),
    target       VARCHAR(255),
    detail       TEXT,
    ip_address   VARCHAR(64),
    create_time  TIMESTAMP DEFAULT now()
);

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
-- 预置角色数据
-- =============================================================================
INSERT INTO sys_role (role_code, role_name, description) VALUES
    ('ADMIN',      '系统管理员',   '最高权限'),
    ('OPERATOR',   '操作员',       '日常监控、飞行计划初审'),
    ('REGULATOR',  '监管员',       '空域管理、飞行计划终审'),
    ('MILITARY',   '军民协调员',   '军事调度、一键清场'),
    ('SUPERVISOR', '上级领导',     '查看所有数据'),
    ('PILOT',      '驾驶员',       '飞行计划填报')
ON CONFLICT (role_code) DO NOTHING;

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
