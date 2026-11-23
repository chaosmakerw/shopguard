package com.codesprint.lesson0831.platform.commerce;

import com.codesprint.lesson0831.platform.PlatformIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("platform")
@RequestMapping("/api/platform")
public class CommerceController {
  private final CommerceService commerce;

  public CommerceController(CommerceService commerce) {
    this.commerce = commerce;
  }

  public record ProductBody(
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 1000) String description,
      @NotNull BigDecimal price,
      @NotBlank String category,
      boolean available,
      BigDecimal version,
      BigDecimal initialStock) {
    CommerceService.ProductInput input() {
      return new CommerceService.ProductInput(
          title,
          description,
          price,
          category,
          available,
          version == null ? 0 : version.intValueExact());
    }
  }

  public record StockBody(@NotNull BigDecimal delta, @NotBlank @Size(max = 200) String reason) {}

  public record ReturnBody(
      @NotBlank @Pattern(regexp = "REFUND_ONLY|RETURN_REFUND") String type,
      @NotBlank String reason,
      @NotBlank @Size(max = 500) String note) {}

  public record ReviewBody(
      @NotBlank @Pattern(regexp = "approve|reject|receive") String action,
      @Size(max = 500) String note) {}

  public record CustomerAction(
      @NotBlank @Pattern(regexp = "withdraw|ship") String action,
      @Size(max = 40) String carrier,
      @Size(max = 80) String trackingNumber) {}

  @GetMapping("/commerce/catalog")
  public Object catalog(
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int size) {
    return commerce.products(keyword, category, "active", page, size, false);
  }

  @GetMapping("/admin/commerce/overview")
  public Object overview(@AuthenticationPrincipal PlatformIdentity user) {
    return commerce.overview(user);
  }

  @GetMapping("/admin/commerce/products")
  public Object products(
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "all") String state,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int size) {
    return commerce.products(keyword, category, state, page, size, true);
  }

  @PostMapping("/admin/commerce/products")
  public Object create(
      @AuthenticationPrincipal PlatformIdentity user,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody ProductBody body) {
    return commerce.createProduct(
        user,
        body.input(),
        body.initialStock() == null ? 0 : body.initialStock().intValueExact(),
        key);
  }

  @PostMapping("/admin/commerce/products/{id}")
  public Object update(
      @AuthenticationPrincipal PlatformIdentity user,
      @PathVariable String id,
      @Valid @RequestBody ProductBody body) {
    return commerce.updateProduct(user, CommerceService.id(id), body.input());
  }

  @PostMapping("/admin/commerce/products/{id}/stock")
  public Object stock(
      @AuthenticationPrincipal PlatformIdentity user,
      @PathVariable String id,
      @Valid @RequestBody StockBody body) {
    return commerce.stock(
        user, CommerceService.id(id), body.delta().intValueExact(), body.reason());
  }

  @GetMapping("/admin/commerce/products/{id}/stock")
  public Object stockHistory(@PathVariable String id) {
    return commerce.stockHistory(CommerceService.id(id));
  }

  @GetMapping("/admin/commerce/orders")
  public Object orders(
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "all") String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int size) {
    return commerce.orders(keyword, status, page, size);
  }

  @GetMapping("/commerce/returns")
  public Object returns(
      @AuthenticationPrincipal PlatformIdentity user,
      @RequestParam(defaultValue = "all") String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int size) {
    return commerce.returns(user, false, status, page, size);
  }

  @GetMapping("/admin/commerce/returns")
  public Object adminReturns(
      @AuthenticationPrincipal PlatformIdentity user,
      @RequestParam(defaultValue = "all") String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int size) {
    return commerce.returns(user, true, status, page, size);
  }

  @GetMapping("/commerce/orders/{id}/return")
  public Object orderReturn(
      @AuthenticationPrincipal PlatformIdentity user, @PathVariable String id) {
    return commerce
        .orderReturn(user.id(), CommerceService.id(id))
        .<Object>map(value -> value)
        .orElse(Map.of());
  }

  @PostMapping("/commerce/orders/{id}/return")
  public Object submit(
      @AuthenticationPrincipal PlatformIdentity user,
      @PathVariable String id,
      @Valid @RequestBody ReturnBody body) {
    return commerce.submitReturn(
        user,
        CommerceService.id(id),
        new CommerceService.ReturnInput(body.type(), body.reason(), body.note()));
  }

  @GetMapping("/commerce/returns/{id}")
  public Object detail(@AuthenticationPrincipal PlatformIdentity user, @PathVariable String id) {
    return Map.of(
        "item",
        commerce.getReturn(user, id, false),
        "events",
        commerce.returnEvents(user, id, false));
  }

  @GetMapping("/admin/commerce/returns/{id}")
  public Object adminDetail(
      @AuthenticationPrincipal PlatformIdentity user, @PathVariable String id) {
    return Map.of(
        "item",
        commerce.getReturn(user, id, true),
        "events",
        commerce.returnEvents(user, id, true));
  }

  @PostMapping("/commerce/returns/{id}/action")
  public Object customer(
      @AuthenticationPrincipal PlatformIdentity user,
      @PathVariable String id,
      @Valid @RequestBody CustomerAction body) {
    return commerce.customerAction(user, id, body.action(), body.carrier(), body.trackingNumber());
  }

  @PostMapping("/admin/commerce/returns/{id}/review")
  public Object review(
      @AuthenticationPrincipal PlatformIdentity user,
      @PathVariable String id,
      @Valid @RequestBody ReviewBody body) {
    return commerce.review(user, id, body.action(), body.note());
  }

  @GetMapping("/admin/commerce/database")
  public Object database() {
    return commerce.database();
  }

  @GetMapping("/admin/commerce/database/{table}")
  public Object table(
      @PathVariable String table,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return commerce.table(table, page, size);
  }
}
