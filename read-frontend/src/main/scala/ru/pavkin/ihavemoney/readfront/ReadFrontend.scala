package ru.pavkin.ihavemoney.readfront

import java.util.UUID
import io.circe.syntax._
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.AskTimeoutException
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.CirceSupport
import akka.http.scaladsl.model.StatusCodes._
import scala.concurrent.duration._
import ru.pavkin.ihavemoney.domain.fortune.FortuneId
import ru.pavkin.ihavemoney.domain.query._
import ru.pavkin.ihavemoney.protocol.FailedRequest
import ru.pavkin.ihavemoney.protocol.readfront._

object ReadFrontend extends App with CirceSupport {

  implicit val system = ActorSystem("IHaveMoneyReadFront")
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(30.seconds)

  val config = ConfigFactory.load()
  val logger = Logging(system, getClass)

  val readBack = new ReadBackClient(system, config.getString("read-backend.interface"))

  val writeFrontURL = s"http://${config.getString("write-frontend.host")}:${config.getString("write-frontend.port")}"

  def sendQuery(q: Query) =
    readBack.query(q)
      .map(kv ⇒ kv._2 match {
        case CategoriesQueryResult(id, inc, exp) ⇒
          kv._1 → (FrontendCategories(id.value, inc.map(_.name), exp.map(_.name)): FrontendQueryResult).asJson
        case MoneyBalanceQueryResult(id, balance) ⇒
          kv._1 → (FrontendMoneyBalance(id.value, balance.map(kv ⇒ kv._1.code → kv._2)): FrontendQueryResult).asJson
        case LiabilitiesQueryResult(id, liabilities) =>
          kv._1 → (FrontendLiabilities(id.value, liabilities): FrontendQueryResult).asJson
        case AssetsQueryResult(id, assets) =>
          kv._1 → (FrontendAssets(id.value, assets): FrontendQueryResult).asJson
        case e: EntityNotFound ⇒
          kv._1 → FailedRequest(e.id.value.toString, e.error).asJson
        case e: QueryFailed ⇒
          kv._1 → FailedRequest(e.id.value.toString, e.error).asJson
      })
      .recover {
        case timeout: AskTimeoutException ⇒
          RequestTimeout → FailedRequest(q.id.toString, s"Query ${q.id} timed out").asJson
      }

  val routes = {
    logRequestResult("i-have-money-read-frontend") {
      path("write_front_url") {
        get {
          complete {
            HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`), writeFrontURL))
          }
        }
      } ~
        pathPrefix(JavaUUID.map(i ⇒ FortuneId(i.toString))) { fortuneId: FortuneId ⇒
          get {
            path("categories") {
              complete {
                sendQuery(Categories(QueryId(UUID.randomUUID.toString), fortuneId))
              }
            } ~
              path("balance") {
                complete {
                  sendQuery(MoneyBalance(QueryId(UUID.randomUUID.toString), fortuneId))
                }
              } ~
              path("assets") {
                complete {
                  sendQuery(Assets(QueryId(UUID.randomUUID.toString), fortuneId))
                }
              } ~
              path("liabilities") {
                complete {
                  sendQuery(Liabilities(QueryId(UUID.randomUUID.toString), fortuneId))
                }
              }

          }
        } ~
        get {
          pathSingleSlash {
            getFromResource("index.html")
          }
        }
    } ~ getFromResourceDirectory("")
  }
  Http().bindAndHandle(routes, config.getString("app.host"), config.getInt("app.http-port"))
}
