package com.codesprint.lesson0831.agent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 持久保存会话。跨实例同时写入同会话可能因主键冲突回滚，不提供分布式顺序保证。 */
public final class JdbcConversationMemory implements ConversationMemory {
    private static final String INSERT_MESSAGE = """
            INSERT INTO conversation_messages(user_id, session_id, message_sequence, role, content)
            VALUES (?, ?, ?, ?, ?)
            """;
    private final DataSource dataSource;

    public JdbcConversationMemory(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource 不能为空");
    }

    @Override
    public void append(ConversationKey key, ChatMessage message) {
        validateKey(key);
        if (message == null || message.role() == null || message.role().isBlank()
                || message.role().length() > 20 || message.content() == null) {
            throw new IllegalArgumentException("会话消息不合法");
        }
        try (Connection connection = dataSource.getConnection()) {
            insert(connection, key, message);
        } catch (SQLException exception) {
            throw new IllegalStateException("会话保存失败", exception);
        }
    }

    @Override
    public List<ChatMessage> load(ConversationKey key) {
        validateKey(key);
        String sql = """
                SELECT role, content, message_sequence FROM conversation_messages
                WHERE user_id = ? AND session_id = ? ORDER BY message_sequence ASC
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKey(statement, key);
            List<ChatMessage> messages = new ArrayList<>();
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new ChatMessage(result.getString("role"),
                            result.getString("content"), result.getLong("message_sequence")));
                }
            }
            return List.copyOf(messages);
        } catch (SQLException exception) {
            throw new IllegalStateException("会话读取失败", exception);
        }
    }

    @Override
    public void appendTurn(ConversationKey key, String question, String answer) {
        validateKey(key);
        if (question == null || answer == null) {
            throw new IllegalArgumentException("问答内容不能为空");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            Throwable pendingFailure = null;
            boolean transactionResolved = false;
            try {
                long sequence = nextSequence(connection, key);
                long assistantSequence = Math.addExact(sequence, 1L);
                insert(connection, key, new ChatMessage("user", question, sequence));
                insert(connection, key, new ChatMessage("assistant", answer, assistantSequence));
                connection.commit();
                transactionResolved = true;
            } catch (SQLException | RuntimeException | Error exception) {
                pendingFailure = exception;
                try {
                    connection.rollback();
                    transactionResolved = true;
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            } finally {
                // rollback 失败时不能切回 autoCommit=true，否则可能提交半轮数据。
                if (transactionResolved) {
                    try {
                        connection.setAutoCommit(originalAutoCommit);
                    } catch (SQLException restoreFailure) {
                        if (pendingFailure == null) throw restoreFailure;
                        pendingFailure.addSuppressed(restoreFailure);
                    }
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("会话保存失败", exception);
        }
    }

    private long nextSequence(Connection connection, ConversationKey key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(MAX(message_sequence), 0) FROM conversation_messages
                WHERE user_id = ? AND session_id = ?
                """)) {
            bindKey(statement, key);
            try (var result = statement.executeQuery()) {
                result.next();
                return Math.addExact(result.getLong(1), 1L);
            }
        }
    }

    private void insert(Connection connection, ConversationKey key, ChatMessage message) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MESSAGE)) {
            bindKey(statement, key);
            statement.setLong(3, message.sequence());
            statement.setString(4, message.role());
            statement.setString(5, message.content());
            statement.executeUpdate();
        }
    }

    private void bindKey(PreparedStatement statement, ConversationKey key) throws SQLException {
        statement.setString(1, key.userId());
        statement.setString(2, key.sessionId());
    }

    private void validateKey(ConversationKey key) {
        if (key == null || key.userId() == null || key.userId().isBlank() || key.userId().length() > 100
                || key.sessionId() == null || key.sessionId().isBlank() || key.sessionId().length() > 100) {
            throw new IllegalArgumentException("会话标识不合法");
        }
    }
}
