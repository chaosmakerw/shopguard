package com.codesprint.lesson0831.platform.commerce;

import com.codesprint.lesson0831.platform.PlatformException;
import com.codesprint.lesson0831.platform.PlatformIdentity;
import com.codesprint.lesson0831.product.ProductCatalogChanged;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("platform")
public class CommerceService {
  private static final Set<String> CATEGORIES = Set.of("书籍", "数码", "生活", "其他");
  private static final Set<String> REASONS = Set.of("不再需要", "商品与描述不符", "质量问题", "其他原因");
  private static final SecureRandom IDS = new SecureRandom();
  private static final String PRODUCT_SELECT =
      """
SELECT p.id,p.title,p.description,p.price,p.available,i.available_quantity,i.reserved_quantity,i.sold_quantity,
  COALESCE(c.category,'其他') category,COALESCE(c.version,0) version
FROM products p JOIN trade_inventory i ON p.id=i.product_id
LEFT JOIN commerce_product_profiles c ON c.product_id=p.id
""";
  private static final String RETURN_SELECT =
      """
      SELECT r.*,o.product_name,d.product_id,d.quantity FROM commerce_returns r
      JOIN orders o ON o.order_id=r.order_id JOIN trade_order_details d ON d.order_id=r.order_id
      """;
  private final JdbcTemplate jdbc;
  private final DataSource dataSource;
  private final ApplicationEventPublisher events;

  public CommerceService(JdbcTemplate jdbc, DataSource dataSource, ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.dataSource = dataSource;
    this.events = events;
  }

  public record Product(
      String productId,
      String title,
      String description,
      String price,
      boolean available,
      String category,
      int availableQuantity,
      int reservedQuantity,
      int soldQuantity,
      int version) {}

  public record ProductInput(
      String title,
      String description,
      BigDecimal price,
      String category,
      boolean available,
      int version) {}

  public record ReturnInput(String type, String reason, String note) {}

  public record ReturnCase(
      String id,
      String orderId,
      String buyerId,
      String productId,
      String productName,
      int quantity,
      String type,
      String reason,
      String note,
      String status,
      String amount,
      String carrier,
      String trackingNumber,
      String decisionNote,
      long createdAt,
      long updatedAt,
      int version) {}

  public record OrderRow(
      String orderId,
      String buyerId,
      String buyerName,
      String productName,
      String totalAmount,
      int quantity,
      String status,
      long createdAt,
      String returnStatus) {}

  public record Page<T>(List<T> items, long total, int page, int size) {}

  private static long now() {
    return System.currentTimeMillis();
  }

  private static PlatformException invalid(String message) {
    return new PlatformException(400, "INVALID_INPUT", message);
  }

  private static PlatformException conflict(String message) {
    return new PlatformException(409, "STATE_CONFLICT", message);
  }

  private static PlatformException missing() {
    return new PlatformException(404, "NOT_FOUND", "记录不存在");
  }

  private static void admin(PlatformIdentity actor) {
    if (!"ADMIN".equals(actor.role())) throw new PlatformException(403, "FORBIDDEN", "需要管理员权限");
  }

  private static String text(String value, int max, boolean required) {
    String result = value == null ? "" : value.strip();
    if (result.length() > max
        || (required && result.isEmpty())
        || result.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n'))
      throw invalid("请检查必填内容、长度和特殊字符");
    return result;
  }

  public static long id(String value) {
    if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid("编号必须为正整数");
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw invalid("编号超出范围");
    }
  }

  private static int offset(int page, int size) {
    if (page < 1 || page > 10000 || size < 1 || size > 100) throw invalid("分页范围不正确");
    return (page - 1) * size;
  }

  private static String pattern(String query) {
    return "%"
        + text(query, 100, false).replace("!", "!!").replace("%", "!%").replace("_", "!_")
        + "%";
  }

  private Product mapProduct(ResultSet r, int row) throws SQLException {
    return new Product(
        r.getString("id"),
        r.getString("title"),
        r.getString("description"),
        r.getBigDecimal("price").toPlainString(),
        r.getBoolean("available"),
        r.getString("category"),
        r.getInt("available_quantity"),
        r.getInt("reserved_quantity"),
        r.getInt("sold_quantity"),
        r.getInt("version"));
  }

  private ReturnCase returnCase(ResultSet r, int row) throws SQLException {
    return new ReturnCase(
        r.getString("id"),
        r.getString("order_id"),
        r.getString("buyer_id"),
        r.getString("product_id"),
        r.getString("product_name"),
        r.getInt("quantity"),
        r.getString("return_type"),
        r.getString("reason"),
        r.getString("customer_note"),
        r.getString("status"),
        r.getBigDecimal("amount").toPlainString(),
        r.getString("carrier"),
        r.getString("tracking_number"),
        r.getString("decision_note"),
        r.getLong("created_at_ms"),
        r.getLong("updated_at_ms"),
        r.getInt("version"));
  }

  private void activity(
      PlatformIdentity actor, String entity, String entityId, String action, String summary) {
    // The activity feed is a preview; the complete note remains in the return event.
    if (summary.length() > 255) {
      int end = Character.isHighSurrogate(summary.charAt(253)) ? 253 : 254;
      summary = summary.substring(0, end) + "…";
    }
    jdbc.update(
        "INSERT INTO commerce_activity VALUES (?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        entity,
        entityId,
        action,
        summary,
        actor.id(),
        now());
  }

  private void event(PlatformIdentity actor, String id, String from, String to, String note) {
    jdbc.update(
        "INSERT INTO commerce_return_events VALUES (?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        id,
        from,
        to,
        actor.id(),
        actor.role(),
        note,
        now());
  }

  public Page<Product> products(
      String keyword, String category, String state, int page, int size, boolean management) {
    int start = offset(page, size);
    String where = " WHERE (p.title LIKE ? ESCAPE '!' OR p.description LIKE ? ESCAPE '!')";
    List<Object> args = new ArrayList<>(List.of(pattern(keyword), pattern(keyword)));
    if (category != null && !category.isBlank()) {
      if (!CATEGORIES.contains(category)) throw invalid("未知分类");
      where += " AND COALESCE(c.category,'其他')=?";
      args.add(category);
    }
    if (!management || "active".equals(state)) where += " AND p.available=TRUE";
    else if ("inactive".equals(state)) where += " AND p.available=FALSE";
    else if ("low".equals(state)) where += " AND i.available_quantity<=3";
    else if (state != null && !state.isBlank() && !"all".equals(state)) throw invalid("未知筛选条件");
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM (" + PRODUCT_SELECT + where + ") counted",
            Long.class,
            args.toArray());
    args.add(size);
    args.add(start);
    return new Page<>(
        jdbc.query(
            PRODUCT_SELECT + where + " ORDER BY p.id DESC LIMIT ? OFFSET ?",
            this::mapProduct,
            args.toArray()),
        total == null ? 0 : total,
        page,
        size);
  }

  public Product product(long id) {
    return jdbc.query(PRODUCT_SELECT + " WHERE p.id=?", this::mapProduct, id).stream()
        .findFirst()
        .orElseThrow(CommerceService::missing);
  }

  private ProductInput validated(ProductInput input) {
    if (input == null || input.price() == null) throw invalid("请输入商品信息与价格");
    BigDecimal price;
    try {
      price = input.price().setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw invalid("价格最多两位小数");
    }
    if (price.signum() <= 0 || price.compareTo(new BigDecimal("9999999999.99")) > 0)
      throw invalid("价格超出范围");
    if (input.category() == null || !CATEGORIES.contains(input.category()) || input.version() < 0)
      throw invalid("分类或版本无效");
    return new ProductInput(
        text(input.title(), 120, true),
        text(input.description(), 1000, true),
        price,
        input.category(),
        input.available(),
        input.version());
  }

  @Transactional
  public Product createProduct(
      PlatformIdentity actor, ProductInput raw, int initialStock, String key) {
    admin(actor);
    var input = validated(raw);
    if (initialStock < 0 || initialStock > 100000) throw invalid("初始库存应为 0 至 100000");
    if (key == null || !key.matches("[A-Za-z0-9_-]{8,64}")) throw invalid("缺少有效的幂等标识");
    List<Long> existing =
        jdbc.queryForList(
            "SELECT product_id FROM commerce_product_profiles WHERE request_key=?",
            Long.class,
            key);
    if (!existing.isEmpty()) {
      Product old = product(existing.get(0));
      Integer originalStock =
          jdbc.queryForObject(
              "SELECT quantity_delta FROM commerce_stock_movements WHERE product_id=? AND"
                  + " reason='商品建档' ORDER BY created_at_ms LIMIT 1",
              Integer.class,
              existing.get(0));
      if (!old.title().equals(input.title())
          || !old.description().equals(input.description())
          || new BigDecimal(old.price()).compareTo(input.price()) != 0
          || !old.category().equals(input.category())
          || old.available() != input.available()
          || !Objects.equals(originalStock, initialStock)) throw conflict("此提交标识已用于其他商品内容");
      return old;
    }
    long id = IDS.nextLong() & Long.MAX_VALUE;
    if (id == 0) id = 1;
    jdbc.update(
        "INSERT INTO products VALUES (?,?,?,?,?)",
        id,
        input.title(),
        input.description(),
        input.price(),
        input.available());
    jdbc.update("INSERT INTO trade_inventory VALUES (?,?,0,0)", id, initialStock);
    jdbc.update(
        "INSERT INTO commerce_product_profiles VALUES (?,?,0,?,?)",
        id,
        input.category(),
        key,
        now());
    jdbc.update(
        "INSERT INTO commerce_stock_movements VALUES (?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        id,
        initialStock,
        "商品建档",
        actor.id(),
        now());
    activity(actor, "PRODUCT", Long.toString(id), "PRODUCT_CREATED", "新建商品：" + input.title());
    events.publishEvent(new ProductCatalogChanged(id));
    return product(id);
  }

  @Transactional
  public Product updateProduct(PlatformIdentity actor, long id, ProductInput raw) {
    admin(actor);
    var input = validated(raw);
    if (jdbc.update(
            "UPDATE commerce_product_profiles SET category=?,version=version+1,updated_at_ms=?"
                + " WHERE product_id=? AND version=?",
            input.category(),
            now(),
            id,
            input.version())
        != 1) throw conflict("商品已被其他操作更新，请刷新后重试");
    jdbc.update(
        "UPDATE products SET title=?,description=?,price=?,available=? WHERE id=?",
        input.title(),
        input.description(),
        input.price(),
        input.available(),
        id);
    activity(actor, "PRODUCT", Long.toString(id), "PRODUCT_UPDATED", "更新商品：" + input.title());
    events.publishEvent(new ProductCatalogChanged(id));
    return product(id);
  }

  @Transactional
  public Product stock(PlatformIdentity actor, long id, int delta, String rawReason) {
    admin(actor);
    String reason = text(rawReason, 200, true);
    if (delta == 0 || delta < -100000 || delta > 100000)
      throw invalid("库存调整量应在 -100000 至 100000 之间且不为零");
    if (jdbc.update(
            "UPDATE trade_inventory SET available_quantity=available_quantity+? WHERE product_id=?"
                + " AND available_quantity+? BETWEEN 0 AND 1000000000",
            delta,
            id,
            delta)
        != 1) throw conflict("可用库存不足或商品不存在，预占库存不能直接调整");
    jdbc.update(
        "INSERT INTO commerce_stock_movements VALUES (?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        id,
        delta,
        reason,
        actor.id(),
        now());
    activity(
        actor,
        "PRODUCT",
        Long.toString(id),
        "STOCK_ADJUSTED",
        "库存调整 " + (delta > 0 ? "+" : "") + delta + "：" + reason);
    return product(id);
  }

  public List<Map<String, Object>> stockHistory(long id) {
    return jdbc.queryForList(
        "SELECT quantity_delta AS quantityDelta,reason,actor_id AS actorId,created_at_ms AS"
            + " createdAt FROM commerce_stock_movements WHERE product_id=? ORDER BY created_at_ms"
            + " DESC LIMIT 30",
        id);
  }

  public Page<OrderRow> orders(String query, String status, int page, int size) {
    int start = offset(page, size);
    List<Object> args = new ArrayList<>(List.of(pattern(query), pattern(query)));
    String where =
        " WHERE (o.product_name LIKE ? ESCAPE '!' OR CAST(o.order_id AS CHAR) LIKE ? ESCAPE '!')";
    if (status != null && !status.isBlank() && !"all".equals(status)) {
      if (!Set.of("PAID", "PENDING_PAYMENT", "CANCELLED", "REFUNDED").contains(status))
        throw invalid("未知订单状态");
      where += " AND o.status=?";
      args.add(status);
    }
    String from =
        " FROM orders o JOIN trade_order_details d ON d.order_id=o.order_id LEFT JOIN"
            + " platform_accounts a ON a.id=o.buyer_id LEFT JOIN commerce_returns r ON"
            + " r.order_id=o.order_id";
    long total =
        Objects.requireNonNull(
            jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray()));
    args.add(size);
    args.add(start);
    var items =
        jdbc.query(
            "SELECT o.*,d.total_amount,d.quantity,d.created_at_ms,a.display_name,r.status"
                + " return_status"
                + from
                + where
                + " ORDER BY d.created_at_ms DESC,o.order_id DESC LIMIT ? OFFSET ?",
            (r, n) ->
                new OrderRow(
                    r.getString("order_id"),
                    r.getString("buyer_id"),
                    Objects.requireNonNullElse(r.getString("display_name"), "用户"),
                    r.getString("product_name"),
                    r.getBigDecimal("total_amount").toPlainString(),
                    r.getInt("quantity"),
                    r.getString("status"),
                    r.getLong("created_at_ms"),
                    r.getString("return_status")),
            args.toArray());
    return new Page<>(items, total, page, size);
  }

  public ReturnCase getReturn(PlatformIdentity actor, String id, boolean management) {
    if (management) admin(actor);
    String where = management ? " WHERE r.id=?" : " WHERE r.id=? AND r.buyer_id=?";
    return jdbc
        .query(
            RETURN_SELECT + where,
            this::returnCase,
            management ? new Object[] {id} : new Object[] {id, actor.id()})
        .stream()
        .findFirst()
        .orElseThrow(CommerceService::missing);
  }

  public Optional<ReturnCase> orderReturn(long buyer, long orderId) {
    return jdbc
        .query(
            RETURN_SELECT + " WHERE r.order_id=? AND r.buyer_id=?",
            this::returnCase,
            orderId,
            buyer)
        .stream()
        .findFirst();
  }

  public Page<ReturnCase> returns(
      PlatformIdentity actor, boolean management, String status, int page, int size) {
    if (management) admin(actor);
    int start = offset(page, size);
    String where = management ? " WHERE 1=1" : " WHERE r.buyer_id=?";
    List<Object> args = new ArrayList<>();
    if (!management) args.add(actor.id());
    if (status != null && !status.isBlank() && !"all".equals(status)) {
      if (!Set.of("REQUESTED", "APPROVED", "RETURNED", "REFUNDED", "REJECTED", "CANCELLED")
          .contains(status)) throw invalid("未知售后状态");
      where += " AND r.status=?";
      args.add(status);
    }
    long count =
        Objects.requireNonNull(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce_returns r" + where, Long.class, args.toArray()));
    args.add(size);
    args.add(start);
    return new Page<>(
        jdbc.query(
            RETURN_SELECT + where + " ORDER BY r.updated_at_ms DESC,r.id LIMIT ? OFFSET ?",
            this::returnCase,
            args.toArray()),
        count,
        page,
        size);
  }

  public List<Map<String, Object>> returnEvents(
      PlatformIdentity actor, String id, boolean management) {
    getReturn(actor, id, management);
    return jdbc.queryForList(
        "SELECT from_status AS fromStatus,to_status AS toStatus,actor_role AS"
            + " actorRole,note,created_at_ms AS createdAt FROM commerce_return_events WHERE"
            + " return_id=? ORDER BY created_at_ms,id",
        id);
  }

  @Transactional
  public ReturnCase submitReturn(PlatformIdentity actor, long orderId, ReturnInput input) {
    if (input == null
        || input.type() == null
        || input.reason() == null
        || !Set.of("REFUND_ONLY", "RETURN_REFUND").contains(input.type())
        || !REASONS.contains(input.reason())) throw invalid("请选择售后类型和原因");
    String note = text(input.note(), 500, true);
    var order =
        jdbc.queryForList(
            "SELECT o.status,d.total_amount FROM orders o JOIN trade_order_details d ON"
                + " d.order_id=o.order_id WHERE o.order_id=? AND o.buyer_id=? FOR UPDATE",
            orderId,
            actor.id());
    if (order.isEmpty()) throw missing();
    if (!"PAID".equals(order.get(0).get("status"))) throw conflict("只有已支付的订单可以申请售后");
    var previous = orderReturn(actor.id(), orderId);
    String id = previous.map(ReturnCase::id).orElseGet(() -> UUID.randomUUID().toString());
    String from = "";
    if (previous.isPresent()) {
      var old = previous.get();
      from = old.status();
      if (!Set.of("REJECTED", "CANCELLED").contains(from)) {
        if (old.type().equals(input.type())
            && old.reason().equals(input.reason())
            && old.note().equals(note)) return old;
        throw conflict("此订单已有处理中售后，请先查看已有申请");
      }
      jdbc.update(
          "UPDATE commerce_returns SET"
              + " return_type=?,reason=?,customer_note=?,status='REQUESTED',carrier='',tracking_number='',decision_note='',updated_at_ms=?,version=version+1"
              + " WHERE id=?",
          input.type(),
          input.reason(),
          note,
          now(),
          id);
    } else {
      jdbc.update(
          "INSERT INTO commerce_returns"
              + " (id,order_id,buyer_id,return_type,reason,customer_note,status,amount,created_at_ms,updated_at_ms)"
              + " VALUES (?,?,?,?,?,?,'REQUESTED',?,?,?)",
          id,
          orderId,
          actor.id(),
          input.type(),
          input.reason(),
          note,
          order.get(0).get("total_amount"),
          now(),
          now());
    }
    event(actor, id, from, "REQUESTED", note);
    activity(actor, "RETURN", id, "RETURN_REQUESTED", "提交售后申请 · " + input.reason());
    return getReturn(actor, id, false);
  }

  private ReturnCase lockedReturn(PlatformIdentity actor, String id, boolean management) {
    ReturnCase known = getReturn(actor, id, management);
    // All transitions lock order -> return -> inventory, matching the payment path.
    jdbc.queryForList(
        "SELECT order_id FROM orders WHERE order_id=? FOR UPDATE", Long.parseLong(known.orderId()));
    // A locking read sees the newest committed state under MySQL REPEATABLE READ.
    // Repeating a plain SELECT here would reuse the snapshot created by getReturn.
    return jdbc.query(RETURN_SELECT + " WHERE r.id=? FOR UPDATE", this::returnCase, id).stream()
        .findFirst()
        .orElseThrow(CommerceService::missing);
  }

  @Transactional
  public ReturnCase customerAction(
      PlatformIdentity actor, String id, String action, String carrier, String tracking) {
    ReturnCase old = lockedReturn(actor, id, false);
    String next, note;
    if ("withdraw".equals(action)) {
      if ("CANCELLED".equals(old.status())) return old;
      if (!"REQUESTED".equals(old.status())) throw conflict("仅待审核的申请可以撤销");
      next = "CANCELLED";
      note = "用户撤销申请";
    } else if ("ship".equals(action)) {
      carrier = text(carrier, 40, true);
      tracking = text(tracking, 80, true);
      if (!tracking.matches("[A-Za-z0-9-]{4,80}")) throw invalid("请填写 4 至 80 位字母、数字或连字符组成的运单号");
      if ("RETURNED".equals(old.status())
          && carrier.equals(old.carrier())
          && tracking.equals(old.trackingNumber())) return old;
      if (!"APPROVED".equals(old.status()) || !"RETURN_REFUND".equals(old.type()))
        throw conflict("当前申请不能登记寄回");
      next = "RETURNED";
      note = "寄回商品：" + carrier + " " + tracking;
      jdbc.update(
          "UPDATE commerce_returns SET carrier=?,tracking_number=? WHERE id=?",
          carrier,
          tracking,
          id);
    } else throw invalid("未知售后操作");
    transition(actor, old, next, note);
    return getReturn(actor, id, false);
  }

  @Transactional
  public ReturnCase review(PlatformIdentity actor, String id, String action, String rawNote) {
    admin(actor);
    ReturnCase old = lockedReturn(actor, id, true);
    String note = text(rawNote, 500, "reject".equals(action));
    if ("approve".equals(action)) {
      if (Set.of("APPROVED", "RETURNED", "REFUNDED").contains(old.status())) return old;
      if (!"REQUESTED".equals(old.status())) throw conflict("当前申请不能审核通过");
      jdbc.update("UPDATE commerce_returns SET decision_note=? WHERE id=?", note, id);
      if ("REFUND_ONLY".equals(old.type()))
        refund(actor, old, note.isBlank() ? "审核通过，模拟退款完成" : note);
      else transition(actor, old, "APPROVED", note.isBlank() ? "审核通过，请登记退回物流" : note);
    } else if ("reject".equals(action)) {
      if ("REJECTED".equals(old.status()) && note.equals(old.decisionNote())) return old;
      if (!"REQUESTED".equals(old.status())) throw conflict("仅待审核申请可以驳回");
      jdbc.update("UPDATE commerce_returns SET decision_note=? WHERE id=?", note, id);
      transition(actor, old, "REJECTED", note);
    } else if ("receive".equals(action)) {
      if ("REFUNDED".equals(old.status())) return old;
      if (!"RETURNED".equals(old.status())) throw conflict("登记寄回后才能确认收货");
      refund(actor, old, note.isBlank() ? "商品已收回，模拟退款完成" : note);
    } else throw invalid("未知审核操作");
    return getReturn(actor, id, true);
  }

  private void transition(PlatformIdentity actor, ReturnCase old, String next, String note) {
    if (jdbc.update(
            "UPDATE commerce_returns SET status=?,version=version+1,updated_at_ms=? WHERE id=? AND"
                + " version=?",
            next,
            now(),
            old.id(),
            old.version())
        != 1) throw conflict("售后状态已变化，请刷新");
    event(actor, old.id(), old.status(), next, note);
    activity(actor, "RETURN", old.id(), "RETURN_" + next, note);
  }

  private void refund(PlatformIdentity actor, ReturnCase old, String note) {
    long orderId = Long.parseLong(old.orderId()), productId = Long.parseLong(old.productId());
    int quantity = old.quantity();
    if (jdbc.update(
            "UPDATE orders SET status='REFUNDED' WHERE order_id=? AND buyer_id=? AND status='PAID'",
            orderId,
            Long.parseLong(old.buyerId()))
        != 1) throw conflict("订单状态已变化，不能重复退款");
    if (jdbc.update(
            "UPDATE trade_inventory SET"
                + " sold_quantity=sold_quantity-?,available_quantity=available_quantity+? WHERE"
                + " product_id=? AND sold_quantity>=? AND available_quantity<=?",
            quantity,
            quantity,
            productId,
            quantity,
            Integer.MAX_VALUE - quantity)
        != 1) throw conflict("库存账不一致，退款已回滚，请核对库存");
    jdbc.update(
        "INSERT INTO commerce_refunds VALUES (?,?,?,?,TRUE,?)",
        UUID.randomUUID().toString(),
        old.id(),
        orderId,
        new BigDecimal(old.amount()),
        now());
    jdbc.update(
        "INSERT INTO commerce_stock_movements VALUES (?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        productId,
        quantity,
        "售后退款归还库存",
        actor.id(),
        now());
    transition(actor, old, "REFUNDED", note);
  }

  public Map<String, Object> overview(PlatformIdentity actor) {
    admin(actor);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(
        "products",
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM products p JOIN trade_inventory i ON i.product_id=p.id WHERE"
                + " p.available=TRUE",
            Long.class));
    result.put(
        "orders", jdbc.queryForObject("SELECT COUNT(*) FROM trade_order_details", Long.class));
    result.put(
        "paidAmount",
        jdbc.queryForObject(
                "SELECT COALESCE(SUM(d.total_amount),0) FROM trade_order_details d JOIN orders o ON"
                    + " o.order_id=d.order_id WHERE o.status='PAID'",
                BigDecimal.class)
            .toPlainString());
    result.put(
        "refundAmount",
        jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM commerce_refunds", BigDecimal.class)
            .toPlainString());
    result.put(
        "pendingReturns",
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM commerce_returns WHERE status IN ('REQUESTED','RETURNED')",
            Long.class));
    result.put(
        "lowStock",
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM trade_inventory i JOIN products p ON p.id=i.product_id WHERE"
                + " p.available=TRUE AND i.available_quantity<=3",
            Long.class));
    result.put(
        "inventory",
        jdbc.queryForList(
                "SELECT COALESCE(SUM(available_quantity),0) AS"
                    + " available,COALESCE(SUM(reserved_quantity),0) AS"
                    + " reserved,COALESCE(SUM(sold_quantity),0) AS sold FROM trade_inventory")
            .get(0));
    result.put(
        "statuses",
        jdbc.queryForList(
            "SELECT o.status,COUNT(*) AS count FROM orders o JOIN trade_order_details d ON"
                + " d.order_id=o.order_id GROUP BY o.status"));
    ZoneId zone = ZoneId.of("Asia/Shanghai");
    LocalDate today = LocalDate.now(zone);
    List<Map<String, Object>> days = new ArrayList<>();
    for (int i = 6; i >= 0; i--) {
      LocalDate date = today.minusDays(i);
      long start = date.atStartOfDay(zone).toInstant().toEpochMilli(),
          end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
      long count =
          Objects.requireNonNull(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM trade_order_details WHERE created_at_ms>=? AND"
                      + " created_at_ms<?",
                  Long.class,
                  start,
                  end));
      BigDecimal amount =
          jdbc.queryForObject(
              "SELECT COALESCE(SUM(d.total_amount),0) FROM trade_order_details d JOIN orders o ON"
                  + " o.order_id=d.order_id WHERE d.created_at_ms>=? AND d.created_at_ms<? AND"
                  + " o.status='PAID'",
              BigDecimal.class,
              start,
              end);
      days.add(
          Map.of(
              "date",
              date.toString(),
              "orders",
              count,
              "amount",
              Objects.requireNonNull(amount).toPlainString()));
    }
    result.put("days", days);
    result.put("recentOrders", orders("", "all", 1, 5).items());
    result.put(
        "activity",
        jdbc.queryForList(
            "SELECT entity_type AS entityType,entity_id AS entityId,action,summary,created_at_ms AS"
                + " createdAt FROM commerce_activity ORDER BY created_at_ms DESC LIMIT 8"));
    result.put("database", databaseInfo());
    return result;
  }

  private Map<String, Object> databaseInfo() {
    try (var connection = dataSource.getConnection()) {
      var meta = connection.getMetaData();
      return Map.of(
          "engine",
          meta.getDatabaseProductName(),
          "version",
          meta.getDatabaseProductVersion(),
          "persistent",
          !meta.getURL().startsWith("jdbc:h2:mem:"),
          "connected",
          connection.isValid(2));
    } catch (SQLException e) {
      throw new PlatformException(503, "DATABASE_UNAVAILABLE", "数据库连接暂不可用");
    }
  }

  private record Table(String label, String purpose, String columns, String sort) {}

  private static final Map<String, Table> TABLES;

  static {
    var tables = new LinkedHashMap<String, Table>();
    tables.put("products", new Table("商品", "商品资料与上下架", "id,title,price,available", "id"));
    tables.put(
        "trade_inventory",
        new Table(
            "库存",
            "可售、预占与已售数量",
            "product_id,available_quantity,reserved_quantity,sold_quantity",
            "product_id"));
    tables.put(
        "trade_order_details",
        new Table(
            "订单明细",
            "成交快照与幂等提交",
            "order_id,buyer_id,product_id,quantity,total_amount,created_at_ms",
            "created_at_ms"));
    tables.put(
        "orders",
        new Table("订单状态", "订单归属与当前状态", "order_id,buyer_id,product_name,status", "order_id"));
    tables.put(
        "commerce_returns",
        new Table(
            "售后申请",
            "审核与退回进度",
            "id,order_id,buyer_id,return_type,status,amount,version",
            "updated_at_ms"));
    tables.put(
        "commerce_refunds",
        new Table(
            "退款流水",
            "退款幂等记录",
            "id,order_id,return_id,amount,simulated,created_at_ms",
            "created_at_ms"));
    tables.put(
        "commerce_stock_movements",
        new Table(
            "库存流水", "补货与退款归还", "product_id,quantity_delta,reason,created_at_ms", "created_at_ms"));
    tables.put(
        "platform_sessions",
        new Table(
            "AI 会话", "会话元数据", "user_id,session_id,session_mode,updated_at_ms", "updated_at_ms"));
    tables.put(
        "platform_runs",
        new Table(
            "AI 执行",
            "实际执行结果与耗时",
            "request_id,run_mode,result_code,duration_ms,created_at_ms",
            "created_at_ms"));
    tables.put(
        "platform_documents",
        new Table(
            "知识文档",
            "知识版本与索引信息",
            "id,title,document_version,embedding_model,chunk_count",
            "created_at_ms"));
    TABLES = Collections.unmodifiableMap(tables);
  }

  public Map<String, Object> database() {
    var result = new LinkedHashMap<>(databaseInfo());
    result.put(
        "tables",
        TABLES.entrySet().stream()
            .map(
                e ->
                    Map.of(
                        "name",
                        e.getKey(),
                        "label",
                        e.getValue().label(),
                        "purpose",
                        e.getValue().purpose(),
                        "rows",
                        Objects.requireNonNull(
                            jdbc.queryForObject("SELECT COUNT(*) FROM " + e.getKey(), Long.class))))
            .toList());
    return result;
  }

  public Map<String, Object> table(String name, int page, int size) {
    var table = TABLES.get(name);
    if (table == null) throw missing();
    int start = offset(page, size);
    String sql =
        "SELECT " + table.columns() + " FROM " + name + " ORDER BY " + table.sort() + " DESC";
    List<Map<String, Object>> rows =
        jdbc.query(
            sql + " LIMIT ? OFFSET ?",
            (r, n) -> {
              Map<String, Object> row = new LinkedHashMap<>();
              for (String column : table.columns().split(",")) {
                Object value = r.getObject(column);
                row.put(
                    column,
                    value instanceof Long || value instanceof BigDecimal
                        ? value.toString()
                        : value);
              }
              return row;
            },
            size,
            start);
    return Map.of(
        "name",
        name,
        "columns",
        List.of(table.columns().split(",")),
        "items",
        rows,
        "total",
        Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + name, Long.class)),
        "page",
        page,
        "size",
        size,
        "query",
        sql + " LIMIT " + size + " OFFSET " + start);
  }
}
