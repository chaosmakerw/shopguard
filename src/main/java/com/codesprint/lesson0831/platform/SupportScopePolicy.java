package com.codesprint.lesson0831.platform;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** A conservative service boundary. Model text never expands this allowlist. */
@Component
@Profile("platform")
public class SupportScopePolicy {
  public enum Topic { PRODUCTS, ORDERS, RULES, LOCAL }
  public record Decision(Topic topic, String code, String answer) {
    public boolean local() { return topic == Topic.LOCAL; }
  }
  public static final String SCOPE_MESSAGE = "我只处理 ShopGuard 平台内的商品查询、本人订单、售后进度和交易规则。这个问题不在客服服务范围内。你可以问“有哪些 Java 书”“查看我的订单”或“如何申请售后”。";
  public static final String CLARIFY_MESSAGE = "请说明要查询的商品、订单或售后问题；也可以先在订单页选中一笔订单，再让我查询。";
  private static final Pattern INJECTION = Pattern.compile(
      "(忽略|无视|绕过|覆盖|忘记).{0,20}(指令|限制|规则|提示|身份)|"
      + "(扮演|假装|切换成|变成).{0,18}(老师|专家|作家|通用|无限制|chatgpt)|"
      + "(显示|输出|泄露|告诉我|打印).{0,12}(系统提示|system\\s*prompt|api.?key|密钥|密码)|"
      + "ignore.{0,30}(instruction|rule|prompt)|system\\s*prompt|developer\\s*message|jailbreak|越狱|"
      + "(base64|解码|编码后|角色扮演|提示词注入)|(你现在是|你不再是)|act\\s+as|pretend\\s+to");
  private static final Pattern OTHER_TASK = Pattern.compile(
      "天气|气温|首都|总统|星座|彩票|股票|基金推荐|投资建议|诊断病|处方|做饭|菜谱|旅游攻略|"
      + "(写|作|编|来|讲).{0,10}(诗|笑话|小说|故事|作文|情书|论文|代码|程序|脚本)|"
      + "(解释|讲解|教我|实现|证明|解答|计算).{0,18}(算法|排序|方程|微积分|定理|线程|spring|java|python|概率)|"
      + "(翻译|translate|leetcode|快速排序程序|冒泡排序代码)|"
      + "\\b(weather|capital|president|horoscope|poem|joke|recipe)\\b|write.{0,16}(code|essay|story)|"
      + "(what is|explain|teach me).{0,16}(java|python|algorithm|spring)");
  private static final Pattern OTHER_PERSON = Pattern.compile(
      "(别人|其他人|另一个用户|所有用户|全部用户|别的用户|管理员).{0,10}(订单|会话|隐私|退款)|"
      + "(订单|会话).{0,10}(所有用户|其他用户)|other\\s+(user|customer).{0,10}order");
  private static final Pattern ORDER = Pattern.compile("订单|售后|退款|退货|寄回|物流|运单|付款|支付|\\b(orders?|refunds?|returns?|shipments?)\\b");
  private static final Pattern PRODUCT = Pattern.compile("商品|书籍|书|台灯|阅读灯|键盘|耳机|库存|上架|售价|价格|购物|\\b(products?|books?|stock|catalog|prices?)\\b");
  private static final Pattern RULE = Pattern.compile("规则|规定|政策|条件|期限|多久|多长时间|能.{0,3}(取消|退|换)|可以.{0,3}(取消|退|换)|是否|能否|为什么|\\b(policy|rules|eligible)\\b");
  private static final Pattern FOLLOW_UP = Pattern.compile("^(那|它|这本|那本|这个|那个|这笔|那笔|还有|还能|还可以|现在|查一下|看一下|看看|继续|再试|重试|详细|便宜|贵|多少|有货|没货|库存|什么时候|接下来|能取消|可以取消|能退|可以退|怎么样|怎么办|what about|how much|in stock|try again|next).{0,70}$");
  private static final Pattern EXPLICIT_ID = Pattern.compile("(订单|商品|order|product).{0,8}[0-9]+");
  private static final Set<String> GREETINGS = Set.of("你好", "您好", "嗨", "hi", "hello", "谢谢", "谢谢你", "好的", "收到", "在吗");

  public Decision decide(String question, String previousTopic, boolean orderSelected) {
    String q = normalize(question);
    if (INJECTION.matcher(q).find()) return outside();
    // Book titles and a request for a programming book are product data, not a request to write code.
    String taskText=q.replaceAll("《[^》]{1,120}》", "书名");
    boolean bookDiscovery=taskText.matches("^(请)?(推荐|帮我找|找|有没有|有卖).{0,40}(书|书籍)[?？。！!]*$")
        && !taskText.matches(".*(顺便|然后|之前|之后|并且|同时|先|再|另外).*" );
    if (!bookDiscovery && OTHER_TASK.matcher(taskText).find()) return outside();
    if (OTHER_PERSON.matcher(q).find()) return local("ACCESS_BOUNDARY", "客服只能查询当前登录账号自己的订单和售后记录，无法提供其他用户的数据。管理员可使用“订单管理”处理授权范围内的运营工作。");
    String shortText = q.replaceAll("[\\p{P}\\s]", "");
    if (GREETINGS.contains(shortText) || q.matches(".*(你能做什么|你是谁|客服能做什么|能帮我什么|服务范围|what can you do|who are you).*")) {
      return local("SUPPORT_HELP", "你好，我是 ShopGuard 客服，可以帮你查平台商品、本人订单、售后进度和交易规则。下单、付款、取消订单和退款申请，需要你在对应页面确认操作。");
    }
    if (q.matches(".*(帮我|替我|直接|自动|代我).{0,12}(下单|付款|支付|取消订单|退款|退货|删除订单).*")) {
      return local("ACTION_REQUIRED", "客服可以查询状态和说明流程，不能代你提交交易或退款。请打开“商品与订单”选择订单；待付款订单可取消，已支付订单可进入售后申请。涉及变更的操作需要你在页面确认。");
    }
    if (q.matches(".*(如何|怎么|怎样|哪里).{0,8}(申请售后|申请退货|申请退款|提交售后).*")) {
      return local("SUPPORT_HELP", "打开“商品与订单”，选择已支付订单，点击“申请 / 查看售后”。仅退款由管理员审核；退货退款审核通过后登记寄回，由管理员确认收货并退款。可以在“售后中心”查看进度。本项目退款为模拟操作，不发生真实资金转账。");
    }
    if (q.matches(".*(如何|怎么|怎样).{0,6}(下单|购买商品|买东西).*")) {
      return local("SUPPORT_HELP", "进入“商品与订单”，选择商品和数量，提交订单后在订单详情确认付款。当前付款入口为模拟付款，不扣除真实资金。");
    }
    boolean orders = ORDER.matcher(q).find();
    boolean products = PRODUCT.matcher(q).find();
    if (q.matches(".*(平台|shopguard).{0,8}(规则|规定|政策).*")) return remote(Topic.RULES);
    if ((orders || q.contains("交易")) && RULE.matcher(q).find()
        && !q.matches(".*(我的|这笔|当前|进度|状态|my order).*")) return remote(Topic.RULES);
    if (orders) return remote(Topic.ORDERS);
    if (products || q.matches(".*(有没有|有卖|在卖|帮我找|搜一下|推荐几本|推荐一本|show me products|find a book).*")) return remote(Topic.PRODUCTS);
    if (FOLLOW_UP.matcher(q).matches()) {
      if (orderSelected) return remote(Topic.ORDERS);
      if (previousTopic != null && Set.of("PRODUCTS", "ORDERS", "RULES").contains(previousTopic)) return remote(Topic.valueOf(previousTopic));
      return local("NEEDS_CLARIFICATION", CLARIFY_MESSAGE);
    }
    return outside();
  }
  public static boolean suspicious(String text) { return INJECTION.matcher(normalize(text)).find(); }
  public boolean followsPrevious(String question) {
    String q=normalize(question);
    return FOLLOW_UP.matcher(q).matches() && !EXPLICIT_ID.matcher(q).find();
  }
  public boolean refersToSameEntity(String question) {
    return followsPrevious(question) && !normalize(question).matches(".*(其他|其它|别的|类似|同类|全部|所有|另一本|另一笔).*" );
  }
  static String normalize(String text) {
    return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
        .replaceAll("\\p{Cf}", "").replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
  }
  private Decision outside() { return local("OUT_OF_SCOPE", SCOPE_MESSAGE); }
  private Decision local(String code, String answer) { return new Decision(Topic.LOCAL, code, answer); }
  private Decision remote(Topic topic) { return new Decision(topic, "SUCCESS", ""); }
}
