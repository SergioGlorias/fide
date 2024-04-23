package fide

import cats.effect.*
import cats.syntax.all.*
import fide.db.Db
import fide.domain.Models
import fide.domain.Models.Pagination
import fide.spec.*
import fide.types.Natural
import io.github.arainko.ducktape.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

class FederationServiceImpl(db: Db)(using Logger[IO]) extends FederationService[IO]:

  import FederationTransformers.*
  import PlayerTransformers.*

  override def getFederationPlayersById(
      id: FederationId,
      page: Natural,
      pageSize: Natural,
      sortBy: Option[SortBy],
      order: Option[Order],
      isActive: Option[Boolean],
      standardMin: Option[Rating],
      standardMax: Option[Rating],
      rapidMin: Option[Rating],
      rapidMax: Option[Rating],
      blitzMin: Option[Rating],
      blitzMax: Option[Rating],
      name: Option[String]
  ): IO[GetFederationPlayersByIdOutput] =
    val paging  = Models.Pagination.fromPageAndSize(page, pageSize)
    val sorting = Models.Sorting.fromOption(sortBy.map(_.to[Models.SortBy]), order.map(_.to[Models.Order]))
    val filter = Models.PlayerFilter(
      isActive,
      Models.RatingRange(standardMin.map(_.value), standardMax.map(_.value)),
      Models.RatingRange(rapidMin.map(_.value), rapidMax.map(_.value)),
      Models.RatingRange(blitzMin.map(_.value), blitzMax.map(_.value)),
      id.value.some
    )
    name
      .fold(db.allPlayers(sorting, paging, filter))(db.playersByName(_, sorting, paging, filter))
      .handleErrorWith: e =>
        error"Error in getPlayers with $filter, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .map(_.map(_.transform))
      .map: xs =>
        GetFederationPlayersByIdOutput(
          xs,
          Option.when(xs.size == pageSize)(page.succ)
        )

  override def getFederationSummaryById(id: FederationId): IO[FederationSummary] =
    db.federationSummaryById(id.value)
      .handleErrorWith: e =>
        error"Error in getFederationSummaryById: $id, $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .flatMap:
        _.fold(IO.raiseError(FederationNotFound(id)))(_.transform.pure)

  override def getFederationsSummary(
      page: Natural,
      pageSize: Natural
  ): IO[GetFederationsSummaryOutput] =
    db.allFederationsSummary(Pagination.fromPageAndSize(page, pageSize))
      .handleErrorWith: e =>
        error"Error in getFederationsSummary: $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
      .map(_.map(_.transform))
      .map: xs =>
        GetFederationsSummaryOutput(
          xs,
          Option.when(xs.size == pageSize)(page.succ)
        )

object FederationTransformers:
  given Transformer.Derived[String, FederationId] = Transformer.Derived.FromFunction(FederationId.apply)
  extension (p: fide.domain.FederationSummary)
    def transform: FederationSummary =
      p.to[FederationSummary]