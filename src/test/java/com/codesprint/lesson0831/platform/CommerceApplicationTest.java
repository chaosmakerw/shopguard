package com.codesprint.lesson0831.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.codesprint.lesson0831.agent.ChatModel;
import com.codesprint.lesson0831.order.OrderService;
import com.codesprint.lesson0831.platform.commerce.CommerceService;
import com.codesprint.lesson0831.trade.core.JdbcTradeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "SHOPGUARD_DB_URL=jdbc:h2:mem:commerce-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "SHOPGUARD_ADMIN_PASSWORD=VerificationAdmin!2026",
      "SHOPGUARD_STUDENT_PASSWORD=VerificationStudent!2026",
      "GATEWAY_API_KEY=platform-test-key",
      "GATEWAY_MODEL=campus-chat",
      "GATEWAY_TIMEOUT=60s",
      "MODEL_MODE=stub"
    })
@ActiveProfiles({"prod", "platform", "gateway", "platform-simulation"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommerceApplicationTest {
  @Autowired CommerceService service;
  @Autowired JdbcTradeService trades;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired OrderService orderToolService;
  @MockBean ChatModel model;
  @MockBean PlatformGatewayClient gateway;
  @MockBean PlatformVectorIndex vectors;
  private final PlatformIdentity admin = new PlatformIdentity(1001, "admin", "管理员", "ADMIN");
  private final PlatformIdentity buyer = new PlatformIdentity(1002, "student", "体验用户", "USER");

  @BeforeEach
  void reset() {
    for (String table :
        List.of(
            "commerce_return_events",
            "commerce_refunds",
            "commerce_returns",
            "commerce_stock_movements",
            "commerce_activity",
            "trade_payment_requests",
            "trade_order_details",
            "orders")) jdbc.update("DELETE FROM " + table);
    jdbc.update(
        "UPDATE trade_inventory SET available_quantity=5,reserved_quantity=0,sold_quantity=0");
    jdbc.update("UPDATE products SET available=TRUE,price=39.00 WHERE id=9001");
  }

  long paid() {
    var order = trades.create(buyer.id(), 9001, 2, UUID.randomUUID().toString());
    trades.simulatePayment(buyer.id(), order.orderId(), UUID.randomUUID().toString());
    return order.orderId();
  }

  CommerceService.ReturnCase submit(long id, String type) {
    return service.submitReturn(
        buyer, id, new CommerceService.ReturnInput(type, "不再需要", "商品完整，希望办理售后"));
  }

  long count(String table) {
    return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
  }

  void assertState(String id, String expected) {
    assertEquals(expected, service.getReturn(buyer, id, false).status());
  }

  @Test
  void refundOnlyIsAtomicAndRepeatedApprovalDoesNotRefundOrRestockTwice() {
    long order = paid();
    var item = submit(order, "REFUND_ONLY");
    assertEquals(3, service.product(9001).availableQuantity());
    service.review(admin, item.id(), "approve", "同意退款");
    service.review(admin, item.id(), "approve", "同意退款");
    assertState(item.id(), "REFUNDED");
    assertEquals("REFUNDED", trades.order(buyer.id(), order).status());
    assertEquals(5, service.product(9001).availableQuantity());
    assertEquals(0, service.product(9001).soldQuantity());
    assertEquals(1, count("commerce_refunds"));
    assertEquals(2, service.returnEvents(buyer, item.id(), false).size());
  }

  @Test
  void returnRequiresApprovalAndReturnShipmentBeforeRefund() {
    var item = submit(paid(), "RETURN_REFUND");
    assertThrows(
        PlatformException.class,
        () -> service.customerAction(buyer, item.id(), "ship", "顺丰", "SF123456"));
    assertThrows(PlatformException.class, () -> service.review(admin, item.id(), "receive", ""));
    service.review(admin, item.id(), "approve", "");
    assertState(item.id(), "APPROVED");
    assertEquals(3, service.product(9001).availableQuantity());
    service.customerAction(buyer, item.id(), "ship", "顺丰", "SF123456");
    service.customerAction(buyer, item.id(), "ship", "顺丰", "SF123456");
    assertState(item.id(), "RETURNED");
    assertEquals(0, count("commerce_refunds"));
    service.review(admin, item.id(), "receive", "");
    assertState(item.id(), "REFUNDED");
    assertEquals(5, service.product(9001).availableQuantity());
    assertEquals(4, service.returnEvents(buyer, item.id(), false).size());
  }

  @Test
  void foreignOrderAndReturnAreInvisibleAndUnpaidOrdersAreRejected() {
    var other = trades.create(admin.id(), 9001, 1, UUID.randomUUID().toString());
    trades.simulatePayment(admin.id(), other.orderId(), UUID.randomUUID().toString());
    assertEquals(
        404,
        assertThrows(PlatformException.class, () -> submit(other.orderId(), "REFUND_ONLY"))
            .status());
    var pending = trades.create(buyer.id(), 9001, 1, UUID.randomUUID().toString());
    assertEquals(
        409,
        assertThrows(PlatformException.class, () -> submit(pending.orderId(), "REFUND_ONLY"))
            .status());
    var item = submit(paid(), "REFUND_ONLY");
    assertEquals(
        404,
        assertThrows(PlatformException.class, () -> service.getReturn(admin, item.id(), false))
            .status());
    assertEquals(
        403,
        assertThrows(PlatformException.class, () -> service.review(buyer, item.id(), "approve", ""))
            .status());
    assertEquals(0, count("commerce_refunds"));
  }

  @Test
  void inventoryFailureRollsBackOrderRefundAndEvent() {
    long order = paid();
    var item = submit(order, "REFUND_ONLY");
    jdbc.update("UPDATE trade_inventory SET sold_quantity=0 WHERE product_id=9001");
    assertThrows(PlatformException.class, () -> service.review(admin, item.id(), "approve", ""));
    assertState(item.id(), "REQUESTED");
    assertEquals("PAID", trades.order(buyer.id(), order).status());
    assertEquals(0, count("commerce_refunds"));
    assertEquals(1, service.returnEvents(buyer, item.id(), false).size());
    jdbc.update("UPDATE trade_inventory SET sold_quantity=2 WHERE product_id=9001");
    service.review(admin, item.id(), "approve", "");
    assertState(item.id(), "REFUNDED");
  }

  @Test
  void maximumLengthReviewNoteIsRetainedWithoutBreakingRefund() {
    var item = submit(paid(), "REFUND_ONLY");
    String note = "已核对".repeat(166) + "通过";
    assertEquals(500, note.length());
    var result = service.review(admin, item.id(), "approve", note);
    assertEquals("REFUNDED", result.status());
    assertEquals(note, result.decisionNote());
    assertEquals(note, service.returnEvents(buyer, item.id(), false).get(1).get("note"));
    assertEquals(1, count("commerce_refunds"));
    assertEquals(5, service.product(9001).availableQuantity());
    assertEquals(
        255,
        jdbc.queryForObject(
            "SELECT LENGTH(summary) FROM commerce_activity WHERE action='RETURN_REFUNDED'",
            Integer.class));
  }

  @Test
  void simultaneousRefundApprovalsProduceOneLedgerEntry() throws Exception {
    var item = submit(paid(), "REFUND_ONLY");
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<String>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                  return service.review(admin, item.id(), "approve", "").status();
                }));
      assertTrue(ready.await(3, TimeUnit.SECONDS));
      start.countDown();
      for (var future : futures) assertEquals("REFUNDED", future.get(8, TimeUnit.SECONDS));
      assertEquals(1, count("commerce_refunds"));
      assertEquals(5, service.product(9001).availableQuantity());
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void duplicateApplicationIsIdempotentAndRejectionCanBeResubmitted() {
    long order = paid();
    var item = submit(order, "RETURN_REFUND");
    assertEquals(item.id(), submit(order, "RETURN_REFUND").id());
    assertEquals(1, count("commerce_returns"));
    service.review(admin, item.id(), "reject", "请补充说明");
    assertState(item.id(), "REJECTED");
    var again =
        service.submitReturn(
            buyer, order, new CommerceService.ReturnInput("REFUND_ONLY", "质量问题", "补充了故障描述"));
    assertEquals(item.id(), again.id());
    assertState(item.id(), "REQUESTED");
    assertEquals(3, service.returnEvents(buyer, item.id(), false).size());
  }

  @Test
  void withdrawalPreservesOrderAndInventory() {
    long order = paid();
    var item = submit(order, "RETURN_REFUND");
    service.customerAction(buyer, item.id(), "withdraw", null, null);
    service.customerAction(buyer, item.id(), "withdraw", null, null);
    assertEquals("PAID", trades.order(buyer.id(), order).status());
    assertEquals(3, service.product(9001).availableQuantity());
    assertEquals(0, count("commerce_refunds"));
    assertThrows(PlatformException.class, () -> service.review(admin, item.id(), "approve", ""));
  }

  @Test
  void productEditsUseOptimisticVersionAndStockAdjustmentCannotConsumeReservations() {
    var original = service.product(9001);
    var update =
        new CommerceService.ProductInput(
            original.title(),
            original.description(),
            new BigDecimal("42.50"),
            "书籍",
            true,
            original.version());
    var updated = service.updateProduct(admin, 9001, update);
    assertEquals("42.50", updated.price());
    assertEquals(original.version() + 1, updated.version());
    assertThrows(PlatformException.class, () -> service.updateProduct(admin, 9001, update));
    trades.create(buyer.id(), 9001, 2, UUID.randomUUID().toString());
    assertThrows(PlatformException.class, () -> service.stock(admin, 9001, -4, "盘点"));
    assertEquals(2, service.product(9001).reservedQuantity());
    service.stock(admin, 9001, 5, "采购入库");
    assertEquals(8, service.product(9001).availableQuantity());
  }

  @Test
  void newProductIsPersistedAndUnlistedProductsCannotBeBought() {
    var input =
        new CommerceService.ProductInput("验收商品", "资料完整", new BigDecimal("19.90"), "数码", false, 0);
    String key = UUID.randomUUID().toString();
    var created = service.createProduct(admin, input, 6, key);
    assertEquals(created.productId(), service.createProduct(admin, input, 6, key).productId());
    assertThrows(PlatformException.class, () -> service.createProduct(admin, input, 7, key));
    assertEquals(6, service.product(Long.parseLong(created.productId())).availableQuantity());
    assertThrows(
        RuntimeException.class,
        () ->
            trades.create(
                buyer.id(), Long.parseLong(created.productId()), 1, UUID.randomUUID().toString()));
    assertThrows(
        PlatformException.class,
        () ->
            service.createProduct(
                admin,
                new CommerceService.ProductInput(
                    "商品", "资料", new BigDecimal("1.001"), "书籍", true, 0),
                1,
                UUID.randomUUID().toString()));
  }

  @Test
  void orderToolIncludesActualAfterSalesStatusAndRemainsOwnerScoped() {
    long id = paid();
    var item = submit(id, "RETURN_REFUND");
    service.review(admin, item.id(), "approve", "");
    assertEquals(
        "APPROVED",
        orderToolService.findMyOrder(buyer.id(), id).orElseThrow().afterSales().get("status"));
    assertTrue(orderToolService.findMyOrder(admin.id(), id).isEmpty());
  }

  @Test
  void databaseExplorerOnlyAcceptsAllowlistedTables() {
    assertEquals("H2", service.database().get("engine"));
    assertThrows(PlatformException.class, () -> service.table("platform_accounts", 1, 20));
    assertThrows(
        PlatformException.class, () -> service.table("products; DROP TABLE orders", 1, 20));
    assertFalse(service.database().toString().contains("password"));
    var rows = (List<?>) service.table("products", 1, 20).get("items");
    assertFalse(rows.isEmpty());
  }

  record Browser(MockHttpSession session, String csrf) {}

  Browser login(String user) throws Exception {
    var response = mvc.perform(get("/api/platform/session")).andReturn();
    var session = (MockHttpSession) response.getRequest().getSession(false);
    String csrf = json.readTree(response.getResponse().getContentAsString()).path("csrf").asText();
    mvc.perform(
            post("/api/platform/login")
                .session(session)
                .header("X-CSRF-TOKEN", csrf)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "username",
                            user,
                            "password",
                            user.equals("admin")
                                ? "VerificationAdmin!2026"
                                : "VerificationStudent!2026"))))
        .andExpect(status().isOk());
    return new Browser(session, csrf);
  }

  @Test
  void httpAdminReadsAndWritesRequireRoleAndCsrf() throws Exception {
    var student = login("student");
    for (String route : List.of("overview", "products", "orders", "returns", "database"))
      mvc.perform(get("/api/platform/admin/commerce/" + route).session(student.session()))
          .andExpect(status().isForbidden());
    mvc.perform(get("/api/platform/admin/commerce/database")).andExpect(status().isUnauthorized());
    var owner = login("admin");
    mvc.perform(
            post("/api/platform/admin/commerce/products/9001/stock")
                .session(owner.session())
                .contentType("application/json")
                .content("{\"delta\":1,\"reason\":\"盘点\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/platform/admin/commerce/products/9001/stock")
                .session(owner.session())
                .header("X-CSRF-TOKEN", owner.csrf())
                .contentType("application/json")
                .content("{\"delta\":1.2,\"reason\":\"盘点\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/platform/admin/commerce/database").session(owner.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.engine").value("H2"));
  }
}
