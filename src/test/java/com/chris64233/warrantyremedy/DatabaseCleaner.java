package com.chris64233.warrantyremedy;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 集成测试辅助：每个测试方法前清空业务表（并发测试不能用方法级事务回滚，
 * 因为工作线程需要各自独立提交事务才能验证锁与唯一约束）。
 */
public final class DatabaseCleaner {

    private static final String[] TABLES_IN_DELETE_ORDER = {
            "disposition_correction",
            "claim_evidence",
            "disposition",
            "warranty_claim",
            "eligibility_decision",
            "product"
    };

    private DatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbc) {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table : TABLES_IN_DELETE_ORDER) {
                jdbc.execute("TRUNCATE TABLE " + table);
            }
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
