/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.r2dbc.migration

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.dispatch.ExecutionContexts
import pekko.persistence.r2dbc.internal.Sql.Interpolation
import pekko.persistence.r2dbc.internal.R2dbcExecutor
import pekko.persistence.r2dbc.journal.JournalDao.log
import io.r2dbc.spi.ConnectionFactory

/**
 * INTERNAL API
 */
@InternalApi private[r2dbc] object MigrationToolDao {
  final case class CurrentProgress(persistenceId: String, eventSeqNr: Long, snapshotSeqNr: Long)
}

/**
 * INTERNAL API
 */
@InternalApi private[r2dbc] class MigrationToolDao(
    connectionFactory: ConnectionFactory,
    logDbCallsExceeding: FiniteDuration)(implicit ec: ExecutionContext, system: ActorSystem[_]) {
  import MigrationToolDao.CurrentProgress

  private val r2dbcExecutor = new R2dbcExecutor(connectionFactory, log, logDbCallsExceeding)(ec, system)

  def createProgressTable(): Future[Done] = {
    r2dbcExecutor.executeDdl("create migration progress table") { connection =>
      connection.createStatement(sql"""
        CREATE TABLE IF NOT EXISTS migration_progress(
          persistence_id VARCHAR(255) NOT NULL,
          event_seq_nr BIGINT,
          snapshot_seq_nr BIGINT,
          PRIMARY KEY(persistence_id)
        )""")
    }
  }

  def updateEventProgress(persistenceId: String, seqNr: Long): Future[Done] = {
    r2dbcExecutor
      .updateOne(s"upsert migration progress [$persistenceId]") { connection =>
        connection
          .createStatement(sql"""
              INSERT INTO migration_progress
              (persistence_id, event_seq_nr)
              VALUES (?, ?)
              ON CONFLICT (persistence_id)
              DO UPDATE SET
              event_seq_nr = excluded.event_seq_nr""")
          .bind(0, persistenceId)
          .bind(1, seqNr)
      }
      .map(_ => Done)(ExecutionContexts.parasitic)
  }

  def updateSnapshotProgress(persistenceId: String, seqNr: Long): Future[Done] = {
    r2dbcExecutor
      .updateOne(s"upsert migration progress [$persistenceId]") { connection =>
        connection
          .createStatement(sql"""
              INSERT INTO migration_progress
              (persistence_id, snapshot_seq_nr)
              VALUES (?, ?)
              ON CONFLICT (persistence_id)
              DO UPDATE SET
              snapshot_seq_nr = excluded.snapshot_seq_nr""")
          .bind(0, persistenceId)
          .bind(1, seqNr)
      }
      .map(_ => Done)(ExecutionContexts.parasitic)
  }

  def currentProgress(persistenceId: String): Future[Option[CurrentProgress]] = {
    r2dbcExecutor.selectOne(s"read migration progress [$persistenceId]")(
      _.createStatement(sql"SELECT * FROM migration_progress WHERE persistence_id = ?")
        .bind(0, persistenceId),
      row =>
        CurrentProgress(
          persistenceId,
          eventSeqNr = zeroIfNull(row.get("event_seq_nr", classOf[java.lang.Long])),
          snapshotSeqNr = zeroIfNull(row.get("snapshot_seq_nr", classOf[java.lang.Long]))))
  }

  private def zeroIfNull(n: java.lang.Long): Long =
    if (n eq null) 0L else n

}
