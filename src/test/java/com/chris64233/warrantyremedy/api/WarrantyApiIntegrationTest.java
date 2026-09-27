package com.chris64233.warrantyremedy.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chris64233.warrantyremedy.DatabaseCleaner;
import com.chris64233.warrantyremedy.FixedClockConfig;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * REST API 端到端测试：产品/库存登记、申请、批准/执行/撤销/纠正、查询与错误映射。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfig.class)
class WarrantyApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    private long postAndGetId(String url, Object body) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Map<String, Object> product(String serial, String saleDate, int months) {
        return Map.of("serialNumber", serial, "saleDate", saleDate, "warrantyMonths", months);
    }

    private Map<String, Object> stock(String serial, int months) {
        return Map.of("serialNumber", serial, "warrantyMonths", months);
    }

    private Map<String, Object> evidence(String type, String ref) {
        return Map.of("type", type, "reference", ref, "note", "凭证");
    }

    private Map<String, Object> claim(String ref, String serial, String symptom) {
        return Map.of("externalRef", ref, "serialNumber", serial, "symptom", symptom,
                "evidences", List.of(evidence("PURCHASE_PROOF", "invoice-" + ref + ".pdf")));
    }

    @Test
    void fullReplacementFlowOverHttp() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                product("API-OLD", LocalDate.of(2025, 6, 1).toString(), 12))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.warrantyEnd").value("2026-06-01"));

        mockMvc.perform(post("/api/products/stock-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stock("API-NEW", 12))))
                .andExpect(status().isCreated());

        long claimId = postAndGetId("/api/claims", claim("API-EXT-1", "API-OLD", "无法开机"));

        // 批准换货
        mockMvc.perform(post("/api/claims/" + claimId + "/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "REPLACEMENT", "replacementSerial", "API-NEW", "note", "换货"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.replacementSerial").value("API-NEW"));

        // 替换品被占用，库存列表中消失
        mockMvc.perform(get("/api/products/stock-units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 并发/重复批准 -> 409
        mockMvc.perform(post("/api/claims/" + claimId + "/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "REPAIR"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));

        // 执行
        mockMvc.perform(post("/api/claims/" + claimId + "/execution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));

        // 已执行不能撤销
        mockMvc.perform(post("/api/claims/" + claimId + "/cancellation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "后悔"))))
                .andExpect(status().isConflict());

        // 追加纠正
        mockMvc.perform(post("/api/claims/" + claimId + "/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "OTHER", "detail", "延长服务说明"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detail").value("延长服务说明"));

        // 保修链
        mockMvc.perform(get("/api/products/API-NEW/warranty-chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chain[0].serialNumber").value("API-OLD"))
                .andExpect(jsonPath("$.chain[1].serialNumber").value("API-NEW"))
                .andExpect(jsonPath("$.currentSerial").value("API-NEW"));

        // 申请详情：证据、资格判断、替换关系、处置记录
        mockMvc.perform(get("/api/claims/by-ref/API-EXT-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.eligibility.eligible").value(true))
                .andExpect(jsonPath("$.evidences[0].type").value("PURCHASE_PROOF"))
                .andExpect(jsonPath("$.disposition.type").value("REPLACEMENT"))
                .andExpect(jsonPath("$.disposition.replacementSerial").value("API-NEW"))
                .andExpect(jsonPath("$.disposition.corrections[0].type").value("OTHER"));

        // 产品处置记录查询（原序列号）
        mockMvc.perform(get("/api/claims").param("serialNumber", "API-OLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].replacementSerial").value("API-NEW"));
    }

    @Test
    void cancelReleasesReplacementOverHttp() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(product("API-C", "2025-06-01", 12))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/products/stock-units").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(stock("API-CR", 12))))
                .andExpect(status().isCreated());

        long claimId = postAndGetId("/api/claims", claim("API-C-1", "API-C", "故障"));

        mockMvc.perform(post("/api/claims/" + claimId + "/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "REPLACEMENT", "replacementSerial", "API-CR"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/claims/" + claimId + "/cancellation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "客户改主意"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.disposition.status").value("CANCELLED"));

        mockMvc.perform(get("/api/products/stock-units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serialNumber").value("API-CR"))
                .andExpect(jsonPath("$[0].status").value("IN_STOCK"));
    }

    @Test
    void errorMappingsForValidationAndMissingResource() throws Exception {
        // 缺少购买凭证 -> 422
        String noProof = objectMapper.writeValueAsString(Map.of(
                "externalRef", "BAD-1", "serialNumber", "NOPE", "symptom", "x", "evidences", List.of()));
        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(noProof))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        // Bean Validation：字段缺失 -> 400
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("serialNumber", "X"))))
                .andExpect(status().isBadRequest());

        // 资源不存在 -> 404
        mockMvc.perform(get("/api/claims/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        // 占用不存在的替换品 -> 404
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(product("API-V", "2025-06-01", 12))))
                .andExpect(status().isCreated());
        long claimId = postAndGetId("/api/claims", claim("BAD-2", "API-V", "故障"));
        mockMvc.perform(post("/api/claims/" + claimId + "/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "REPLACEMENT", "replacementSerial", "GHOST"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateExternalRefIsIdempotentOverHttp() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(product("API-D", "2025-06-01", 12))))
                .andExpect(status().isCreated());

        MvcResult first = mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(claim("DUP-X", "API-D", "故障A"))))
                .andExpect(status().isCreated()).andReturn();
        long firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(claim("DUP-X", "API-D", "故障B"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.symptom").value("故障A"));
    }
}
