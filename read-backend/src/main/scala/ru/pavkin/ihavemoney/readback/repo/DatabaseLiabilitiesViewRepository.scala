package ru.pavkin.ihavemoney.readback.repo

import ru.pavkin.ihavemoney.domain.fortune.{RealEstate ⇒ _, Stocks ⇒ _, _}
import ru.pavkin.ihavemoney.domain.{fortune ⇒ domain}
import ru.pavkin.ihavemoney.readback.db._
import slick.driver.PostgresDriver.api.{Database, _}

import scala.concurrent.{ExecutionContext, Future}

class DatabaseLiabilitiesViewRepository(db: Database) extends LiabilitiesViewRepository {

  private def liabilitiesFindQuery(id: LiabilityId) =
    Debts.table
      .filter(_.liabilityId === id.value.toString)
      .take(1)

  private def liabilitiesRowToDomain(s: DebtRow) = s.interestRate match {
    case None ⇒ domain.NoInterestDebt(s.name, s.amount, s.currency)
    case Some(interest) ⇒ domain.Loan(s.name, s.amount, s.currency, interest)
  }

  private def find(id: LiabilityId)(implicit ec: ExecutionContext): Future[Option[Liability]] = db.run {
    liabilitiesFindQuery(id)
      .result
      .map(_.headOption.map(liabilitiesRowToDomain))
  }

  def findAll(id: FortuneId)(implicit ec: ExecutionContext): Future[Map[LiabilityId, Liability]] = db.run {
    Debts.table.filter(_.fortuneId === id.value).result
  }.map(_.map(a ⇒ a.liabilityId → liabilitiesRowToDomain(a)).toMap)

  def byId(id: (LiabilityId, FortuneId))(implicit ec: ExecutionContext): Future[Option[Liability]] = find(id._1)

  def replaceById(id: (LiabilityId, FortuneId), newRow: Liability)(implicit ec: ExecutionContext): Future[Unit] =
    db.run {
      liabilitiesFindQuery(id._1).update(DebtRow(id._1, id._2, newRow.name, newRow.amount, newRow.currency, newRow match {
        case n: NoInterestDebt => None
        case l: Loan => Some(l.interestRate)
      }))
    }.map(_ ⇒ ())

  def insert(id: (LiabilityId, FortuneId), row: Liability)(implicit ec: ExecutionContext): Future[Unit] =
    replaceById(id, row)

  def remove(id: (LiabilityId, FortuneId))(implicit ec: ExecutionContext): Future[Unit] = db.run {
    liabilitiesFindQuery(id._1).delete
  }.map(_ ⇒ ())
}
