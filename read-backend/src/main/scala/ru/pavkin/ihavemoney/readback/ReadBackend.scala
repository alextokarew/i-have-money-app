package ru.pavkin.ihavemoney.readback

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import io.funcqrs.akka.EventsSourceProvider
import io.funcqrs.akka.backend.AkkaBackend
import io.funcqrs.backend.{Query, QueryByTag}
import io.funcqrs.config.api._
import ru.pavkin.ihavemoney.domain.fortune._
import ru.pavkin.ihavemoney.domain.user.UserId
import ru.pavkin.ihavemoney.readback.projections._
import ru.pavkin.ihavemoney.readback.repo.{DatabaseAssetsViewRepository, DatabaseLiabilitiesViewRepository, DatabaseMoneyViewRepository, InMemoryRepository}
import ru.pavkin.ihavemoney.readback.sources.{CurrentFortuneTagEventSourceProvider, FortuneTagEventSourceProvider}
import slick.driver.PostgresDriver
import slick.driver.PostgresDriver.api._

object ReadBackend extends App {

  println("Starting IHaveMoney read backend...")

  val config = ConfigFactory.load()
  val system: ActorSystem = ActorSystem(config.getString("app.system"))

  val database: PostgresDriver.Backend#Database = Database.forConfig("read-db")
  val moneyViewRepo = new DatabaseMoneyViewRepository(database)
  val assetsViewRepo = new DatabaseAssetsViewRepository(database)
  val liabilitiesViewRepo = new DatabaseLiabilitiesViewRepository(database)

  val categoriesRepo = new InMemoryRepository[FortuneId, (Set[IncomeCategory], Set[ExpenseCategory])] {}
  val userRegistryRepo = new InMemoryRepository[UserId, Set[FortuneId]] {}
  val fortuneInfoRepo = new InMemoryRepository[FortuneId, FortuneInfo] {}

  val fortuneEventsProvider = new FortuneTagEventSourceProvider(Fortune.tag)

  val backend = new AkkaBackend {
    val actorSystem: ActorSystem = system
    def sourceProvider(query: Query): EventsSourceProvider = {
      query match {
        case QueryByTag(Fortune.tag) ⇒ fortuneEventsProvider
      }
    }
  }.configure {
    projection(
      query = QueryByTag(Fortune.tag),
      projection = new MoneyViewProjection(moneyViewRepo, assetsViewRepo, liabilitiesViewRepo)
          .andThen(new AssetsViewProjection(assetsViewRepo))
          .andThen(new LiabilitiesViewProjection(liabilitiesViewRepo)),
      name = "FortuneViewProjection"
    ).withBackendOffsetPersistence()
  }.configure {
    projection(
      query = QueryByTag(Fortune.tag),
      projection = new CategoriesViewProjection(categoriesRepo)
          .andThen(new FortunesPerUserProjection(userRegistryRepo))
          .andThen(new FortuneInfoProjection(fortuneInfoRepo)),
      name = "InMemoryFortuneViewProjection"
    ).withoutOffsetPersistence()
  }

  val interface = system.actorOf(Props(new InterfaceActor(
    new CurrentFortuneTagEventSourceProvider(Fortune.tag),
    moneyViewRepo,
    assetsViewRepo,
    liabilitiesViewRepo,
    categoriesRepo,
    userRegistryRepo,
    fortuneInfoRepo)), "interface")
}
