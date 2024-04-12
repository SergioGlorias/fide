package fide
package db
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

object StoreSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  private def resource: Resource[IO, Store] = Containers.createResource.map(x => Store.apply(x.postgres))

  test("put success"):
    resource
      .use(_.put("key", "value").map(_ => expect(true)))

  test("put then get success"):
    resource.use: store =>
      for
        _     <- store.put("key", "value")
        value <- store.get("key")
      yield expect(value == "value".some)

  test("put twice will overwrite"):
    resource.use: store =>
      for
        _     <- store.put("key", "value")
        _     <- store.put("key", "other value")
        value <- store.get("key")
      yield expect(value == "other value".some)

  test("get unknown key returns none"):
    resource.use: store =>
      for
        _     <- store.put("key", "value")
        value <- store.get("key1")
      yield expect(value.isEmpty)
