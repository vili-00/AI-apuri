package com.aiapuri.data.conversation;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ConversationDao_Impl implements ConversationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ConversationEntity> __insertionAdapterOfConversationEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateTimestamp;

  private final SharedSQLiteStatement __preparedStmtOfUpdatePersona;

  private final SharedSQLiteStatement __preparedStmtOfDeleteConversation;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllConversations;

  public ConversationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfConversationEntity = new EntityInsertionAdapter<ConversationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `conversations` (`id`,`title`,`created_at`,`updated_at`,`model`,`persona_id`,`system_prompt_snapshot`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ConversationEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindLong(3, entity.getCreatedAt());
        statement.bindLong(4, entity.getUpdatedAt());
        statement.bindString(5, entity.getModel());
        if (entity.getPersonaId() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getPersonaId());
        }
        if (entity.getSystemPromptSnapshot() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getSystemPromptSnapshot());
        }
      }
    };
    this.__preparedStmtOfUpdateTimestamp = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE conversations SET updated_at = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdatePersona = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE conversations SET persona_id = ?, updated_at = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteConversation = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM conversations WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllConversations = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM conversations";
        return _query;
      }
    };
  }

  @Override
  public Object insertConversation(final ConversationEntity conversation,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfConversationEntity.insertAndReturnId(conversation);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateTimestamp(final String id, final long updatedAt,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateTimestamp.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, updatedAt);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateTimestamp.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updatePersona(final String id, final String personaId, final long updatedAt,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdatePersona.acquire();
        int _argIndex = 1;
        if (personaId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, personaId);
        }
        _argIndex = 2;
        _stmt.bindLong(_argIndex, updatedAt);
        _argIndex = 3;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdatePersona.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteConversation(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteConversation.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteConversation.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllConversations(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllConversations.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllConversations.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ConversationEntity>> observeAllConversations() {
    final String _sql = "SELECT * FROM conversations ORDER BY updated_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"conversations"}, new Callable<List<ConversationEntity>>() {
      @Override
      @NonNull
      public List<ConversationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfPersonaId = CursorUtil.getColumnIndexOrThrow(_cursor, "persona_id");
          final int _cursorIndexOfSystemPromptSnapshot = CursorUtil.getColumnIndexOrThrow(_cursor, "system_prompt_snapshot");
          final List<ConversationEntity> _result = new ArrayList<ConversationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ConversationEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final String _tmpModel;
            _tmpModel = _cursor.getString(_cursorIndexOfModel);
            final String _tmpPersonaId;
            if (_cursor.isNull(_cursorIndexOfPersonaId)) {
              _tmpPersonaId = null;
            } else {
              _tmpPersonaId = _cursor.getString(_cursorIndexOfPersonaId);
            }
            final String _tmpSystemPromptSnapshot;
            if (_cursor.isNull(_cursorIndexOfSystemPromptSnapshot)) {
              _tmpSystemPromptSnapshot = null;
            } else {
              _tmpSystemPromptSnapshot = _cursor.getString(_cursorIndexOfSystemPromptSnapshot);
            }
            _item = new ConversationEntity(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpUpdatedAt,_tmpModel,_tmpPersonaId,_tmpSystemPromptSnapshot);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getConversationById(final String id,
      final Continuation<? super ConversationEntity> $completion) {
    final String _sql = "SELECT * FROM conversations WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ConversationEntity>() {
      @Override
      @Nullable
      public ConversationEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfPersonaId = CursorUtil.getColumnIndexOrThrow(_cursor, "persona_id");
          final int _cursorIndexOfSystemPromptSnapshot = CursorUtil.getColumnIndexOrThrow(_cursor, "system_prompt_snapshot");
          final ConversationEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final String _tmpModel;
            _tmpModel = _cursor.getString(_cursorIndexOfModel);
            final String _tmpPersonaId;
            if (_cursor.isNull(_cursorIndexOfPersonaId)) {
              _tmpPersonaId = null;
            } else {
              _tmpPersonaId = _cursor.getString(_cursorIndexOfPersonaId);
            }
            final String _tmpSystemPromptSnapshot;
            if (_cursor.isNull(_cursorIndexOfSystemPromptSnapshot)) {
              _tmpSystemPromptSnapshot = null;
            } else {
              _tmpSystemPromptSnapshot = _cursor.getString(_cursorIndexOfSystemPromptSnapshot);
            }
            _result = new ConversationEntity(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpUpdatedAt,_tmpModel,_tmpPersonaId,_tmpSystemPromptSnapshot);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllConversations(
      final Continuation<? super List<ConversationEntity>> $completion) {
    final String _sql = "SELECT * FROM conversations ORDER BY updated_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ConversationEntity>>() {
      @Override
      @NonNull
      public List<ConversationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final int _cursorIndexOfModel = CursorUtil.getColumnIndexOrThrow(_cursor, "model");
          final int _cursorIndexOfPersonaId = CursorUtil.getColumnIndexOrThrow(_cursor, "persona_id");
          final int _cursorIndexOfSystemPromptSnapshot = CursorUtil.getColumnIndexOrThrow(_cursor, "system_prompt_snapshot");
          final List<ConversationEntity> _result = new ArrayList<ConversationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ConversationEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final String _tmpModel;
            _tmpModel = _cursor.getString(_cursorIndexOfModel);
            final String _tmpPersonaId;
            if (_cursor.isNull(_cursorIndexOfPersonaId)) {
              _tmpPersonaId = null;
            } else {
              _tmpPersonaId = _cursor.getString(_cursorIndexOfPersonaId);
            }
            final String _tmpSystemPromptSnapshot;
            if (_cursor.isNull(_cursorIndexOfSystemPromptSnapshot)) {
              _tmpSystemPromptSnapshot = null;
            } else {
              _tmpSystemPromptSnapshot = _cursor.getString(_cursorIndexOfSystemPromptSnapshot);
            }
            _item = new ConversationEntity(_tmpId,_tmpTitle,_tmpCreatedAt,_tmpUpdatedAt,_tmpModel,_tmpPersonaId,_tmpSystemPromptSnapshot);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ConversationDao.ConversationSummaryRow>> observeConversationSummaries() {
    final String _sql = "\n"
            + "        SELECT c.id, c.title, c.updated_at, c.model,\n"
            + "               p.name AS persona_name,\n"
            + "               (SELECT m.content FROM messages m\n"
            + "                WHERE m.conversation_id = c.id\n"
            + "                ORDER BY m.created_at DESC LIMIT 1) AS last_message\n"
            + "        FROM conversations c\n"
            + "        LEFT JOIN personas p ON c.persona_id = p.id\n"
            + "        ORDER BY c.updated_at DESC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"messages", "conversations",
        "personas"}, new Callable<List<ConversationDao.ConversationSummaryRow>>() {
      @Override
      @NonNull
      public List<ConversationDao.ConversationSummaryRow> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = 0;
          final int _cursorIndexOfTitle = 1;
          final int _cursorIndexOfUpdatedAt = 2;
          final int _cursorIndexOfModel = 3;
          final int _cursorIndexOfPersonaName = 4;
          final int _cursorIndexOfLastMessage = 5;
          final List<ConversationDao.ConversationSummaryRow> _result = new ArrayList<ConversationDao.ConversationSummaryRow>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ConversationDao.ConversationSummaryRow _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final String _tmpModel;
            if (_cursor.isNull(_cursorIndexOfModel)) {
              _tmpModel = null;
            } else {
              _tmpModel = _cursor.getString(_cursorIndexOfModel);
            }
            final String _tmpPersonaName;
            if (_cursor.isNull(_cursorIndexOfPersonaName)) {
              _tmpPersonaName = null;
            } else {
              _tmpPersonaName = _cursor.getString(_cursorIndexOfPersonaName);
            }
            final String _tmpLastMessage;
            if (_cursor.isNull(_cursorIndexOfLastMessage)) {
              _tmpLastMessage = null;
            } else {
              _tmpLastMessage = _cursor.getString(_cursorIndexOfLastMessage);
            }
            _item = new ConversationDao.ConversationSummaryRow(_tmpId,_tmpTitle,_tmpUpdatedAt,_tmpModel,_tmpPersonaName,_tmpLastMessage);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
