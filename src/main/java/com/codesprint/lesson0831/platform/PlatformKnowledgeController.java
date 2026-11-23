package com.codesprint.lesson0831.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Map;

@RestController
@Profile("platform")
@RequestMapping("/api/platform")
public class PlatformKnowledgeController {
    private final PlatformKnowledgeService knowledge;
    public PlatformKnowledgeController(PlatformKnowledgeService knowledge){this.knowledge=knowledge;}
    public record DocumentInput(@NotBlank @Size(max=160) String title,@NotBlank @Size(max=20000) String content){}
    @GetMapping("/knowledge") public Object list(){return knowledge.documents();}
    @GetMapping("/knowledge/chunks/{id}") public Object source(@PathVariable String id){return knowledge.source(id);}
    @PostMapping("/admin/knowledge") public Object create(@Valid @RequestBody DocumentInput input){return knowledge.ingest(input.title(),input.content());}
    @PostMapping("/admin/knowledge/upload") public Object upload(@RequestParam("file") MultipartFile file)throws Exception{
        String name=file.getOriginalFilename();
        if(name==null || !(name.toLowerCase(java.util.Locale.ROOT).endsWith(".txt") || name.toLowerCase(java.util.Locale.ROOT).endsWith(".md")) || name.length()>160 || file.getSize()>262144)
            throw new IllegalArgumentException("INVALID_DOCUMENT");
        String content=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.getBytes())).toString();
        String title=name.replace('\\','/');title=title.substring(title.lastIndexOf('/')+1);
        return knowledge.ingest(title,content);
    }
    @DeleteMapping("/admin/knowledge/{id}") public Object delete(@PathVariable String id){knowledge.delete(id);return Map.of("success",true);}
    @PostMapping("/admin/knowledge/sample") public Object sample(){
        return knowledge.ingest("校园交易平台规则","未付款订单可以主动取消，取消后已占用的库存会归还。待支付订单超过付款期限后会自动关闭，库存也会归还。\n\n已支付订单可以在订单详情申请整单售后，包括仅退款和退货退款。仅退款由管理员审核通过后完成；退货退款需要审核通过、用户登记寄回物流、管理员确认收货，随后退款并归还库存。申请待审核时可以撤销；被驳回或撤销后可以补充说明重新提交。\n\nAI 可以查询本人订单及售后进度，但不能替代管理员审核或执行退款。身份由服务器确定，不能通过聊天切换身份。\n\n支付和退款均为本地模拟，不涉及真实资金；寄回物流为用户登记的信息，未接入物流承运商。商品数量必须是 1 到 20 的整数，退款金额以订单成交快照为准。");
    }
}
