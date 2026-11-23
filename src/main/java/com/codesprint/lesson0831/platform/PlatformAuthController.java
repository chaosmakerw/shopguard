package com.codesprint.lesson0831.platform;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Profile("platform")
@RequestMapping("/api/platform")
public class PlatformAuthController {
    private final PlatformRepository repository; private final PasswordEncoder encoder;
    private final HttpSessionSecurityContextRepository contexts;
    private final Map<String,Attempt> attempts = new ConcurrentHashMap<>();
    private final String dummyHash;
    private record Attempt(long started,int count) {}
    public record Login(@NotBlank @Size(max=64) String username,@NotBlank @Size(max=128) String password) {}
    public PlatformAuthController(PlatformRepository repository, PasswordEncoder encoder,HttpSessionSecurityContextRepository contexts) {
        this.repository=repository; this.encoder=encoder; this.contexts=contexts; this.dummyHash=encoder.encode(UUID.randomUUID().toString());
    }
    @GetMapping("/session") public Map<String,Object> session(HttpServletRequest request) {
        CsrfToken csrf=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        var auth=SecurityContextHolder.getContext().getAuthentication();
        Object user=auth != null && auth.getPrincipal() instanceof PlatformIdentity identity ? identity : null;
        Map<String,Object> result=new LinkedHashMap<>(); result.put("user",user); result.put("csrf",csrf.getToken());
        return result;
    }
    @PostMapping("/login") public PlatformIdentity login(@Valid @RequestBody Login login,HttpServletRequest request,HttpServletResponse response) {
        long now=System.currentTimeMillis(); String address=request.getRemoteAddr();
        attempts.entrySet().removeIf(entry -> now-entry.getValue().started()>60_000);
        if (attempts.size()>=1024 && !attempts.containsKey(address)) throw new PlatformException(429,"LOGIN_LIMIT","尝试过于频繁，请稍后重试");
        Attempt attempt=attempts.compute(address,(key,old) -> old==null ? new Attempt(now,1) : new Attempt(old.started(),old.count()+1));
        if (attempt.count()>10) throw new PlatformException(429,"LOGIN_LIMIT","尝试过于频繁，请稍后重试");
        var account=repository.account(login.username());
        boolean valid=encoder.matches(login.password(),account.map(PlatformRepository.Account::hash).orElse(dummyHash));
        if (!valid || account.isEmpty()) throw new PlatformException(401,"INVALID_LOGIN","用户名或密码不正确");
        PlatformIdentity identity=account.get().identity();
        request.getSession(true); request.changeSessionId();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(identity,null,List.of(new SimpleGrantedAuthority("ROLE_"+identity.role()))));
        SecurityContextHolder.setContext(context); contexts.saveContext(context,request,response); attempts.remove(address);
        return identity;
    }
    @PostMapping("/logout") public Map<String,Boolean> logout(HttpServletRequest request) {
        var session=request.getSession(false); if(session!=null) session.invalidate(); SecurityContextHolder.clearContext();
        return Map.of("success",true);
    }
}
