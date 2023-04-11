/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.projection

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import pekko.persistence.query.Offset
import docs.home.CborSerializable
import org.slf4j.LoggerFactory

//#handler
//#grouped-handler
import org.apache.pekko
import pekko.projection.r2dbc.scaladsl.R2dbcHandler
import pekko.projection.r2dbc.scaladsl.R2dbcSession
import pekko.persistence.query.typed.EventEnvelope

//#grouped-handler
//#handler

object R2dbcProjectionDocExample {

  object ShoppingCart {
    val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShoppingCart")

    sealed trait Command extends CborSerializable

    sealed trait Event extends CborSerializable {
      def cartId: String
    }

    final case class ItemAdded(cartId: String, itemId: String, quantity: Int) extends Event
    final case class ItemRemoved(cartId: String, itemId: String) extends Event
    final case class ItemQuantityAdjusted(cartId: String, itemId: String, newQuantity: Int) extends Event
    final case class CheckedOut(cartId: String, eventTime: Instant) extends Event
  }

  // #handler
  class ShoppingCartHandler()(implicit ec: ExecutionContext) extends R2dbcHandler[EventEnvelope[ShoppingCart.Event]] {
    private val logger = LoggerFactory.getLogger(getClass)

    override def process(session: R2dbcSession, envelope: EventEnvelope[ShoppingCart.Event]): Future[Done] = {
      envelope.event match {
        case ShoppingCart.CheckedOut(cartId, time) =>
          logger.info(s"Shopping cart $cartId was checked out at $time")
          val stmt = session
            .createStatement("INSERT into order (id, time) VALUES ($1, $2)")
            .bind(0, cartId)
            .bind(1, time)
          session
            .updateOne(stmt)
            .map(_ => Done)

        case otherEvent =>
          logger.debug(s"Shopping cart ${otherEvent.cartId} changed by $otherEvent")
          Future.successful(Done)
      }
    }
  }
  // #handler

  // #grouped-handler
  import scala.collection.immutable

  class GroupedShoppingCartHandler()(implicit ec: ExecutionContext)
      extends R2dbcHandler[immutable.Seq[EventEnvelope[ShoppingCart.Event]]] {
    private val logger = LoggerFactory.getLogger(getClass)

    override def process(
        session: R2dbcSession,
        envelopes: immutable.Seq[EventEnvelope[ShoppingCart.Event]]): Future[Done] = {

      // save all events in DB
      val stmts = envelopes
        .map(_.event)
        .collect { case ShoppingCart.CheckedOut(cartId, time) =>
          logger.info(s"Shopping cart $cartId was checked out at $time")

          session
            .createStatement("INSERT into order (id, time) VALUES ($1, $2)")
            .bind(0, cartId)
            .bind(1, time)

        }
        .toVector

      session.update(stmts).map(_ => Done)
    }
  }
  // #grouped-handler

  implicit val system = ActorSystem[Nothing](Behaviors.empty, "Example")
  implicit val ec: ExecutionContext = system.executionContext

  object IllustrateInit {
    // #initProjections
    import org.apache.pekko
    import pekko.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
    import pekko.projection.r2dbc.scaladsl.R2dbcProjection
    import pekko.persistence.r2dbc.query.scaladsl.R2dbcReadJournal
    import pekko.projection.ProjectionId
    import pekko.projection.eventsourced.scaladsl.EventSourcedProvider
    import pekko.projection.Projection
    import pekko.projection.ProjectionBehavior
    import pekko.projection.scaladsl.SourceProvider
    import pekko.persistence.query.typed.EventEnvelope

    def initProjections(): Unit = {
      def sourceProvider(sliceRange: Range): SourceProvider[Offset, EventEnvelope[ShoppingCart.Event]] =
        EventSourcedProvider
          .eventsBySlices[ShoppingCart.Event](
            system,
            readJournalPluginId = R2dbcReadJournal.Identifier,
            entityType,
            sliceRange.min,
            sliceRange.max)

      def projection(sliceRange: Range): Projection[EventEnvelope[ShoppingCart.Event]] = {
        val minSlice = sliceRange.min
        val maxSlice = sliceRange.max
        val projectionId = ProjectionId("ShoppingCarts", s"carts-$minSlice-$maxSlice")

        R2dbcProjection
          .exactlyOnce(
            projectionId,
            settings = None,
            sourceProvider(sliceRange),
            handler = () => new ShoppingCartHandler)
      }

      // Split the slices into 4 ranges
      val numberOfSliceRanges: Int = 4
      val sliceRanges = EventSourcedProvider.sliceRanges(system, R2dbcReadJournal.Identifier, numberOfSliceRanges)

      ShardedDaemonProcess(system).init(
        name = "ShoppingCartProjection",
        numberOfInstances = sliceRanges.size,
        behaviorFactory = i => ProjectionBehavior(projection(sliceRanges(i))),
        stopMessage = ProjectionBehavior.Stop)
    }
    // #initProjections
  }

  // #sourceProvider
  import org.apache.pekko
  import pekko.projection.eventsourced.scaladsl.EventSourcedProvider
  import pekko.persistence.r2dbc.query.scaladsl.R2dbcReadJournal
  import pekko.projection.scaladsl.SourceProvider

  // Slit the slices into 4 ranges
  val numberOfSliceRanges: Int = 4
  val sliceRanges = EventSourcedProvider.sliceRanges(system, R2dbcReadJournal.Identifier, numberOfSliceRanges)

  // Example of using the first slice range
  val minSlice: Int = sliceRanges.head.min
  val maxSlice: Int = sliceRanges.head.max
  val entityType: String = ShoppingCart.EntityKey.name

  val sourceProvider: SourceProvider[Offset, EventEnvelope[ShoppingCart.Event]] =
    EventSourcedProvider
      .eventsBySlices[ShoppingCart.Event](
        system,
        readJournalPluginId = R2dbcReadJournal.Identifier,
        entityType,
        minSlice,
        maxSlice)
  // #sourceProvider

  object IllustrateExactlyOnce {
    // #exactlyOnce
    import org.apache.pekko
    import pekko.projection.r2dbc.scaladsl.R2dbcProjection
    import pekko.projection.ProjectionId

    val projectionId = ProjectionId("ShoppingCarts", s"carts-$minSlice-$maxSlice")

    val projection =
      R2dbcProjection
        .exactlyOnce(projectionId, settings = None, sourceProvider, handler = () => new ShoppingCartHandler)
    // #exactlyOnce
  }

  object IllustrateAtLeastOnce {
    // #atLeastOnce
    import org.apache.pekko
    import pekko.projection.r2dbc.scaladsl.R2dbcProjection
    import pekko.projection.ProjectionId

    val projectionId = ProjectionId("ShoppingCarts", s"carts-$minSlice-$maxSlice")

    val projection =
      R2dbcProjection
        .atLeastOnce(projectionId, settings = None, sourceProvider, handler = () => new ShoppingCartHandler)
        .withSaveOffset(afterEnvelopes = 100, afterDuration = 500.millis)
    // #atLeastOnce
  }

  object IllustrateGrouped {
    // #grouped
    import org.apache.pekko
    import pekko.projection.r2dbc.scaladsl.R2dbcProjection
    import pekko.projection.ProjectionId

    val projectionId = ProjectionId("ShoppingCarts", s"carts-$minSlice-$maxSlice")

    val projection =
      R2dbcProjection
        .groupedWithin(projectionId, settings = None, sourceProvider, handler = () => new GroupedShoppingCartHandler)
        .withGroup(groupAfterEnvelopes = 20, groupAfterDuration = 500.millis)
    // #grouped
  }

}
