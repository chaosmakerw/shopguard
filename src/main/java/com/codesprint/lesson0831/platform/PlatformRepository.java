package com.codesprint.lesson0831.platform;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Repository
@Profile("platform")
public class PlatformRepository {
    private final JdbcTemplate jdbc;
    public PlatformRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Account(PlatformIdentity identity, String hash) {}
    public Optional<Account> account(String username) {
        return jdbc.query("SELECT * FROM platform_accounts WHERE username = ?", (rs, row) ->
            new Account(new PlatformIdentity(rs.getLong("id"), rs.getString("username"),
                rs.getString("display_name"), rs.getString("account_role")), rs.getString("password_hash")), username)
            .stream().findFirst();
    }
    public void seedAccount(long id, String username, String name, String role, String hash) {
        jdbc.update("INSERT INTO platform_accounts(id,username,display_name,account_role,password_hash) " +
            "SELECT ?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM platform_accounts WHERE id = ?)", id,username,name,role,hash,id);
    }
    public List<Map<String,Object>> sessions(long user) {
        return jdbc.query("SELECT * FROM platform_sessions WHERE user_id = ? ORDER BY updated_at_ms DESC LIMIT 100",
            (rs,row)->Map.<String,Object>of("id",rs.getString("session_id"),"title",rs.getString("title"),"mode",rs.getString("session_mode"),"updatedAt",rs.getLong("updated_at_ms")),user);
    }
    public String createSession(long user, String mode) {
        if(sessions(user).size()>=100)throw new PlatformException(409,"SESSION_CAPACITY","最多保留 100 个会话，请先删除旧会话");
        String id = UUID.randomUUID().toString(); long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO platform_sessions VALUES (?,?,?,?,?,?)", user,id,"新对话",mode,now,now);
        return id;
    }
    public String sessionMode(long user, String id) {
        return jdbc.query("SELECT session_mode FROM platform_sessions WHERE user_id = ? AND session_id = ?",
            (rs, row) -> rs.getString(1), user,id).stream().findFirst()
            .orElseThrow(() -> new PlatformException(404,"SESSION_NOT_FOUND","会话不存在"));
    }
    public void touchSession(long user, String id, String question) {
        jdbc.update("UPDATE platform_sessions SET updated_at_ms = ?,title = CASE WHEN title = '新对话' THEN ? ELSE title END " +
            "WHERE user_id = ? AND session_id = ?", System.currentTimeMillis(),question.substring(0,Math.min(36,question.length())),user,id);
    }
    @Transactional public void deleteSession(long user, String id) {
        sessionMode(user,id);
        jdbc.update("DELETE FROM conversation_messages WHERE user_id = ? AND session_id = ?", Long.toString(user),id);
        jdbc.update("DELETE FROM platform_sessions WHERE user_id = ? AND session_id = ?", user,id);
    }
    public void recordRun(String requestId,long user,String session,String mode,String code,long duration,String detail) {
        jdbc.update("INSERT INTO platform_runs VALUES (?,?,?,?,?,?,?,?)", requestId,user,session,mode,code,duration,System.currentTimeMillis(),detail);
    }
    public List<Map<String,Object>> runs(long user, boolean admin) {
        String sql="SELECT request_id AS id,run_mode AS mode,result_code AS code,duration_ms AS durationMs," +
            "created_at_ms AS createdAt,detail_json AS details FROM platform_runs ";
        var rows=admin ? jdbc.queryForList(sql+"ORDER BY created_at_ms DESC LIMIT 100") :
            jdbc.queryForList(sql+"WHERE user_id = ? ORDER BY created_at_ms DESC LIMIT 100",user);
        return rows.stream().map(row -> Map.<String,Object>of("id",row.get("id"),"mode",row.get("mode"),"code",row.get("code"),
            "durationMs",row.get("durationMs"),"createdAt",row.get("createdAt"),"details",row.get("details"))).toList();
    }
    public List<String> sessionDetails(long user,String session) {
        return jdbc.query("SELECT detail_json FROM platform_runs WHERE user_id=? AND session_id=? ORDER BY created_at_ms ASC",(rs,row)->rs.getString(1),user,session);
    }
    public record RecentRun(String code,String details) {}
    public Optional<RecentRun> latestRun(long user,String session) {
        return jdbc.query("SELECT result_code,detail_json FROM platform_runs WHERE user_id=? AND session_id=? ORDER BY created_at_ms DESC,request_id DESC LIMIT 1",
            (rs,row)->new RecentRun(rs.getString(1),rs.getString(2)),user,session).stream().findFirst();
    }
}
