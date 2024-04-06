package fide
package db

import cats.effect.*
import cats.syntax.all.*
import fide.db.Db.Pagination
import fide.domain.*
import skunk.*

trait Db:
  def upsert(player: NewPlayer, federation: Option[NewFederation]): IO[Unit]
  def upsert(xs: List[(NewPlayer, Option[NewFederation])]): IO[Unit]
  def playerById(id: PlayerId): IO[Option[PlayerInfo]]
  def allPlayers(page: Pagination): IO[List[PlayerInfo]]
  def allFederations: IO[List[FederationInfo]]
  def playersByName(name: String, page: Pagination): IO[List[PlayerInfo]]
  def playersByFederationId(id: FederationId): IO[List[PlayerInfo]]

object Db:

  case class Pagination(limit: Int, offset: Int):
    def next     = copy(offset = offset + limit)
    def nextPage = (offset / limit) + 1

  object Pagination:
    val defaultLimit  = 30
    val defaultPage   = 1
    val defaultOffset = 0
    val default       = Pagination(defaultLimit, defaultOffset)

    def apply(limit: Option[Int], page: Option[Int]): Pagination =
      val _limit = limit.getOrElse(defaultLimit)
      val _page  = (page.getOrElse(defaultPage) - 1) * _limit
      Pagination(_limit, _page)

  import io.github.arainko.ducktape.*
  def apply(postgres: Resource[IO, Session[IO]]): Db = new:
    def upsert(newPlayer: NewPlayer, federation: Option[NewFederation]): IO[Unit] =
      val player = newPlayer.toInsertPlayer(federation.map(_.id))
      postgres.use: s =>
        for
          playerCmd     <- s.prepare(Sql.upsertPlayer)
          federationCmd <- s.prepare(Sql.upsertFederation)
          _ <- s.transaction.use: _ =>
            federation.traverse(federationCmd.execute) *>
              playerCmd.execute(player)
        yield ()

    def upsert(xs: List[(NewPlayer, Option[NewFederation])]): IO[Unit] =
      val players = xs.map((p, f) => p.toInsertPlayer(f.map(_.id)))
      val feds    = xs.flatMap(_._2).distinct
      postgres.use: s =>
        for
          playerCmd     <- s.prepare(Sql.upsertPlayers(players.size))
          federationCmd <- s.prepare(Sql.upsertFederations(feds.size))
          _ <- s.transaction.use: _ =>
            federationCmd.execute(feds) *>
              playerCmd.execute(players)
        yield ()

    def playerById(id: PlayerId): IO[Option[PlayerInfo]] =
      postgres.use(_.option(Sql.playerById)(id))

    def allPlayers(page: Pagination): IO[List[PlayerInfo]] =
      postgres.use(_.execute(Sql.allPlayers)(page.limit -> page.offset))

    def allFederations: IO[List[FederationInfo]] =
      postgres.use(_.execute(Sql.allFederations))

    def playersByName(name: String, page: Pagination): IO[List[PlayerInfo]] =
      postgres.use(_.execute(Sql.playersByName)(s"%$name%", page.limit, page.offset))

    def playersByFederationId(id: FederationId): IO[List[PlayerInfo]] =
      postgres.use(_.execute(Sql.playersByFederationId)(id))

  extension (p: NewPlayer)
    def toInsertPlayer(fedId: Option[FederationId]) =
      p.into[InsertPlayer].transform(Field.const(_.federation, fedId))

private object Codecs:

  import skunk.codec.all.*
  import skunk.data.Type

  val title: Codec[Title] = `enum`[Title](_.value, Title.apply, Type("title"))

  val insertPlayer: Codec[InsertPlayer] =
    (int4 *: text *: title.opt *: int4.opt *: int4.opt *: int4.opt *: int4.opt *: bool *: text.opt)
      .to[InsertPlayer]

  val newFederation: Codec[NewFederation] =
    (text *: text).to[NewFederation]

  val federationInfo: Codec[FederationInfo] =
    (text *: text).to[FederationInfo]

  val playerInfo: Codec[PlayerInfo] =
    (int4 *: text *: title.opt *: int4.opt *: int4.opt *: int4.opt *: int4.opt *: bool *: timestamptz *: timestamptz *: federationInfo.opt)
      .to[PlayerInfo]

private object Sql:

  import skunk.codec.all.*
  import skunk.implicits.*
  import Codecs.*

  // TODO use returning
  val upsertPlayer: Command[InsertPlayer] =
    sql"""
        INSERT INTO players (id, name, title, standard, rapid, blitz, year, active, federation_id)
        VALUES ($insertPlayer)
        ON CONFLICT (id) DO UPDATE SET (name, title, standard, rapid, blitz, year, active, federation_id) =
        (EXCLUDED.name, EXCLUDED.title, EXCLUDED.standard, EXCLUDED.rapid, EXCLUDED.blitz, EXCLUDED.year, EXCLUDED.active, EXCLUDED.federation_id)
       """.command

  // TODO use returning
  def upsertPlayers(n: Int): Command[List[InsertPlayer]] =
    val players = insertPlayer.values.list(n)
    sql"""
        INSERT INTO players (id, name, title, standard, rapid, blitz, year, active, federation_id)
        VALUES $players
        ON CONFLICT (id) DO UPDATE SET (name, title, standard, rapid, blitz, year, active, federation_id) =
        (EXCLUDED.name, EXCLUDED.title, EXCLUDED.standard, EXCLUDED.rapid, EXCLUDED.blitz, EXCLUDED.year, EXCLUDED.active, EXCLUDED.federation_id)
       """.command

  val playerById: Query[PlayerId, PlayerInfo] =
    sql"""
        SELECT p.id, p.name, p.title, p.standard, p.rapid, p.blitz, p.year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p, federations AS f
        WHERE p.id = $int4 AND p.federation_id = f.id
       """.query(playerInfo)

  val playersByFederationId: Query[FederationId, PlayerInfo] =
    sql"""
        SELECT p.id, p.name, p.title, p.standard, p.rapid, p.blitz, p.year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p, federations AS f
        WHERE p.federation_id = $text AND p.federation_id = f.id
       """.query(playerInfo)

  val upsertFederation: Command[NewFederation] =
    sql"""
        INSERT INTO federations (id, name)
        VALUES ($newFederation)
        ON CONFLICT DO NOTHING
       """.command

  def upsertFederations(n: Int): Command[List[NewFederation]] =
    val feds = newFederation.values.list(n)
    sql"""
        INSERT INTO federations (id, name)
        VALUES $feds
        ON CONFLICT DO NOTHING
       """.command

  val allFederations: Query[Void, FederationInfo] =
    sql"""
        SELECT id, name
        FROM federations
       """.query(federationInfo)

  val allPlayers: Query[(Int ~ Int), PlayerInfo] =
    sql"""
        SELECT p.id, p.name, p.title, p.standard, p.rapid, p.blitz, p.year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p, federations AS f
        WHERE p.federation_id = f.id
        LIMIT ${int4} OFFSET ${int4}
       """.query(playerInfo)

  val playersByName: Query[(String, Int, Int), PlayerInfo] =
    sql"""
        SELECT p.id, p.name, p.title, p.standard, p.rapid, p.blitz, p.year, p.active, p.updated_at, p.created_at, f.id, f.name
        FROM players AS p, federations AS f
        WHERE p.federation_id = f.id AND p.name LIKE $text
        LIMIT ${int4} OFFSET ${int4}
       """.query(playerInfo)
