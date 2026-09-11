package com.uav.system.controller;

import com.uav.common.base.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 字典服务：全局枚举值 → 中文标签 的统一管理与查询
 * 前端契约见 uav-frontend/src/api/dict.ts（useDict 组合式带本地缓存与回退）
 */
@RestController
@RequestMapping("/api/dict")
public class SysDictController {

    private final JdbcTemplate jdbc;

    public SysDictController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 全量字典：{ dictType: [{value,label,sort}] }，前端一次拉取全局缓存 */
    @GetMapping("/all")
    public R<Map<String, List<Map<String, Object>>>> all() {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        jdbc.query("select id, dict_type, dict_value, dict_label, sort_order from sys_dict order by dict_type, sort_order, id", rs -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("value", rs.getString("dict_value"));
            item.put("label", rs.getString("dict_label"));
            item.put("sort", rs.getInt("sort_order"));
            out.computeIfAbsent(rs.getString("dict_type"), k -> new ArrayList<>()).add(item);
        });
        return R.ok(out);
    }

    /** 字典类型元数据（含业务分组），供管理页分组展示与下拉选择 */
    @GetMapping("/meta")
    public R<List<Map<String, Object>>> meta() {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query("select dict_type, dict_name, business_group, sort_order from sys_dict_type "
                + "order by sort_order, dict_type", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dictType", rs.getString("dict_type"));
            m.put("dictName", rs.getString("dict_name"));
            m.put("businessGroup", rs.getString("business_group"));
            m.put("sort", rs.getInt("sort_order"));
            out.add(m);
        });
        return R.ok(out);
    }

    /** 按类型查询 */
    @GetMapping("/data/{dictType}")
    public R<List<Map<String, Object>>> byType(@PathVariable String dictType) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbc.query("select id, dict_value, dict_label, sort_order from sys_dict where dict_type = ? order by sort_order, id", rs -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("value", rs.getString("dict_value"));
            item.put("label", rs.getString("dict_label"));
            item.put("sort", rs.getInt("sort_order"));
            out.add(item);
        }, dictType);
        return R.ok(out);
    }

    /** 新增字典项 */
    @PostMapping
    public R<String> create(@RequestBody Map<String, Object> body) {
        String type = str(body.get("dictType"));
        String value = str(body.get("dictValue"));
        String label = str(body.get("dictLabel"));
        if (type == null || value == null || label == null) {
            return R.fail("dictType/dictValue/dictLabel 不能为空");
        }
        Integer sort = body.get("sortOrder") == null ? 0 : ((Number) body.get("sortOrder")).intValue();
        try {
            jdbc.update("INSERT INTO sys_dict (dict_type, dict_value, dict_label, sort_order) VALUES (?,?,?,?) "
                + "ON CONFLICT (dict_type, dict_value) DO UPDATE SET dict_label = EXCLUDED.dict_label, sort_order = EXCLUDED.sort_order",
                type, value, label, sort);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return R.fail("该字典项已存在");
        }
        return R.ok("ok");
    }

    /** 修改标签/排序 */
    @PutMapping("/{id}")
    public R<String> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String label = str(body.get("dictLabel"));
        if (label == null) return R.fail("dictLabel 不能为空");
        Integer sort = body.get("sortOrder") == null ? 0 : ((Number) body.get("sortOrder")).intValue();
        jdbc.update("update sys_dict set dict_label = ?, sort_order = ? where id = ?", label, sort, id);
        return R.ok("ok");
    }

    /** 删除字典项 */
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable long id) {
        jdbc.update("delete from sys_dict where id = ?", id);
        return R.ok("ok");
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}
