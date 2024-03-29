/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package docs.home.query

import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.persistence.query.NoOffset
import pekko.persistence.typed.PersistenceId
import pekko.stream.scaladsl.Sink

object QueryDocCompileOnly {
  implicit val system: ActorSystem[_] = ???
  trait MyEvent
  trait MyState

  // #readJournalFor
  import org.apache.pekko
  import pekko.persistence.query.PersistenceQuery
  import pekko.persistence.r2dbc.query.scaladsl.R2dbcReadJournal

  val eventQueries = PersistenceQuery(system)
    .readJournalFor[R2dbcReadJournal](R2dbcReadJournal.Identifier)
  // #readJournalFor

  // #durableStateStoreFor
  import org.apache.pekko
  import pekko.persistence.state.DurableStateStoreRegistry
  import pekko.persistence.r2dbc.state.scaladsl.R2dbcDurableStateStore

  val stateQueries = DurableStateStoreRegistry(system)
    .durableStateStoreFor[R2dbcDurableStateStore[MyState]](R2dbcDurableStateStore.Identifier)
  // #durableStateStoreFor

  {
    // #currentEventsByPersistenceId
    val persistenceId = PersistenceId("MyEntity", "id1")
    eventQueries
      .currentEventsByPersistenceId(persistenceId.id, 1, 101)
      .map(envelope => s"event with seqNr ${envelope.sequenceNr}: ${envelope.event}")
      .runWith(Sink.foreach(println))
    // #currentEventsByPersistenceId
  }

  {
    // #currentEventsBySlices
    import org.apache.pekko.persistence.query.typed.EventEnvelope

    // Slit the slices into 4 ranges
    val numberOfSliceRanges: Int = 4
    val sliceRanges = eventQueries.sliceRanges(numberOfSliceRanges)

    // Example of using the first slice range
    val minSlice: Int = sliceRanges.head.min
    val maxSlice: Int = sliceRanges.head.max
    val entityType: String = "MyEntity"
    eventQueries
      .currentEventsBySlices[MyEvent](entityType, minSlice, maxSlice, NoOffset.getInstance)
      .map(envelope =>
        s"event from persistenceId ${envelope.persistenceId} with " +
        s"seqNr ${envelope.sequenceNr}: ${envelope.event}")
      .runWith(Sink.foreach(println))
    // #currentEventsBySlices
  }

  {
    // #currentChangesBySlices
    import org.apache.pekko.persistence.query.UpdatedDurableState

    // Slit the slices into 4 ranges
    val numberOfSliceRanges: Int = 4
    val sliceRanges = stateQueries.sliceRanges(numberOfSliceRanges)

    // Example of using the first slice range
    val minSlice: Int = sliceRanges.head.min
    val maxSlice: Int = sliceRanges.head.max
    val entityType: String = "MyEntity"
    stateQueries
      .currentChangesBySlices(entityType, minSlice, maxSlice, NoOffset.getInstance)
      .collect { case change: UpdatedDurableState[MyState] => change }
      .map(change =>
        s"state change from persistenceId ${change.persistenceId} with " +
        s"revision ${change.revision}: ${change.value}")
      .runWith(Sink.foreach(println))
    // #currentChangesBySlices
  }
}
