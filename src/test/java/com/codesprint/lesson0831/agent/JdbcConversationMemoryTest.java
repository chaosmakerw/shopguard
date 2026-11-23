package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.ToolCallExecutor;
import com.codesprint.lesson0831.agent.toolcalling.ToolCallingRunner;
import com.codesprint.lesson0831.product.ToolDispatcher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/** 使用真实 H2 JDBC 和事务，不启动 Spring、不访问网络或真实模型。 */
class JdbcConversationMemoryTest {
    @TempDir Path directory;
    private JdbcDataSource dataSource;
    private JdbcConversationMemory memory;
    private final ConversationKey key = new ConversationKey("1001", "shared-session");

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = newDataSource();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE conversation_messages (
                        user_id VARCHAR(100) NOT NULL,
                        session_id VARCHAR(100) NOT NULL,
                        message_sequence BIGINT NOT NULL,
                        role VARCHAR(20) NOT NULL,
                        content TEXT NOT NULL,
                        PRIMARY KEY(user_id, session_id, message_sequence)
                    )
                    """);
        }
        memory = new JdbcConversationMemory(dataSource);
    }

    @Test
    void shouldRecoverFromClosedFileDatabaseAndAllocateSequenceFromStoredMaximum() {
        memory.append(key, new ChatMessage("system", "系统规则", 20));
        memory.appendTurn(key, "第一问", "第一答");

        // 所有 JDBC 连接已经关闭；重新创建 DataSource 和 adapter，读取同一磁盘库。
        var reopened = new JdbcConversationMemory(newDataSource());
        assertEquals(List.of(new ChatMessage("system", "系统规则", 20),
                new ChatMessage("user", "第一问", 21),
                new ChatMessage("assistant", "第一答", 22)), reopened.load(key));
        reopened.appendTurn(key, "第二问", "第二答");
        assertEquals(List.of(20L, 21L, 22L, 23L, 24L),
                memory.load(key).stream().map(ChatMessage::sequence).toList());
    }

    @Test
    void shouldIsolateByBothUserAndSession() {
        var otherUser = new ConversationKey("1002", key.sessionId());
        var otherSession = new ConversationKey(key.userId(), "another-session");
        memory.appendTurn(key, "我的问题", "我的回答");
        memory.appendTurn(otherUser, "另一用户", "另一用户的回答");
        memory.appendTurn(otherSession, "另一会话", "另一会话的回答");

        assertEquals("我的问题", memory.load(key).get(0).content());
        assertEquals("另一用户", memory.load(otherUser).get(0).content());
        assertEquals("另一会话", memory.load(otherSession).get(0).content());
        assertEquals(2, memory.load(key).size());
        assertTrue(memory.load(new ConversationKey("9999", key.sessionId())).isEmpty());
    }

    @Test
    void shouldRollbackQuestionWhenAnswerInsertFailsAndAllowNextTurn() throws SQLException {
        memory.appendTurn(key, "已有问题", "已有回答");
        List<ChatMessage> before = memory.load(key);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    ALTER TABLE conversation_messages ADD CONSTRAINT reject_answer
                    CHECK (role <> 'assistant' OR content <> 'reject-this-answer')
                    """);
        }

        var failure = assertThrows(IllegalStateException.class,
                () -> memory.appendTurn(key, "这条问题也必须回滚", "reject-this-answer"));
        assertEquals("会话保存失败", failure.getMessage());
        assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals(before, memory.load(key));

        memory.appendTurn(key, "恢复后的问题", "恢复后的回答");
        assertEquals(List.of(1L, 2L, 3L, 4L),
                memory.load(key).stream().map(ChatMessage::sequence).toList());
    }

    @Test
    void shouldNotPersistFailedModelTurn() {
        memory.appendTurn(key, "旧问题", "旧回答");
        List<ChatMessage> before = memory.load(key);
        ChatModel failingModel = (request, history) -> { throw new ModelCallException("model offline"); };
        var service = new AssistantService(new ToolCallingRunner(failingModel,
                new ToolCallExecutor(mock(ToolDispatcher.class)), 1, Duration.ofSeconds(2)), memory);

        assertThrows(ModelCallException.class,
                () -> service.ask(new ChatRequest(key.userId(), key.sessionId(), "失败问题")));
        assertEquals(before, memory.load(key));
    }

    @Test
    void shouldPreserveInsertFailureWhenRestoringConnectionAlsoFails() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    ALTER TABLE conversation_messages ADD CONSTRAINT reject_content
                    CHECK (content <> 'reject-this-answer')
                    """);
        }
        var connection = spy(dataSource.getConnection());
        doThrow(new SQLException("simulated restore failure")).when(connection).setAutoCommit(true);
        DataSource wrapped = mock(DataSource.class);
        when(wrapped.getConnection()).thenReturn(connection);
        var isolated = new JdbcConversationMemory(wrapped);

        var failure = assertThrows(IllegalStateException.class,
                () -> isolated.appendTurn(key, "不能留下的半轮", "reject-this-answer"));
        assertEquals("会话保存失败", failure.getMessage());
        assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals(1, failure.getCause().getSuppressed().length);
        assertEquals("simulated restore failure", failure.getCause().getSuppressed()[0].getMessage());
        assertTrue(connection.isClosed());
        assertTrue(memory.load(key).isEmpty());
    }

    @Test
    void rollbackFailureShouldStaySuppressedAndNeverSwitchToAutoCommit() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    ALTER TABLE conversation_messages ADD CONSTRAINT reject_content
                    CHECK (content <> 'reject-this-answer')
                    """);
        }
        var connection = spy(dataSource.getConnection());
        doThrow(new SQLException("simulated rollback failure")).when(connection).rollback();
        DataSource wrapped = mock(DataSource.class);
        when(wrapped.getConnection()).thenReturn(connection);

        var failure = assertThrows(IllegalStateException.class,
                () -> new JdbcConversationMemory(wrapped).appendTurn(key, "不能提交", "reject-this-answer"));
        assertEquals("simulated rollback failure", failure.getCause().getSuppressed()[0].getMessage());
        verify(connection, never()).setAutoCommit(true);
        assertTrue(connection.isClosed());
        assertTrue(memory.load(key).isEmpty());
    }

    @Test
    void shouldRejectInvalidKeyBeforeObtainingDatabaseConnection() {
        DataSource unused = mock(DataSource.class);
        var isolated = new JdbcConversationMemory(unused);
        assertThrows(IllegalArgumentException.class,
                () -> isolated.load(new ConversationKey("1001", "s".repeat(101))));
        assertThrows(IllegalArgumentException.class,
                () -> isolated.appendTurn(new ConversationKey(" ", "s"), "q", "a"));
        assertThrows(IllegalArgumentException.class, () -> isolated.appendTurn(key, "q", null));
        verifyNoInteractions(unused);
    }

    @Test
    void shouldReturnImmutableSnapshot() {
        memory.appendTurn(key, "第一问", "第一答");
        List<ChatMessage> snapshot = memory.load(key);
        memory.appendTurn(key, "第二问", "第二答");
        assertEquals(2, snapshot.size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(new ChatMessage("user", "外部修改", 99)));
        assertEquals(4, memory.load(key).size());
    }

    private JdbcDataSource newDataSource() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:file:" + directory.resolve("conversation").toAbsolutePath()
                + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE");
        source.setUser("sa");
        source.setPassword("");
        return source;
    }
}
