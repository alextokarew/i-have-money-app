package ru.pavkin.ihavemoney.frontend.components

import diode.data.Pot
import diode.react.ModelProxy
import diode.react.ReactPot._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import ru.pavkin.ihavemoney.frontend.redux.AppCircuit
import ru.pavkin.ihavemoney.frontend.redux.actions.LoadTransactionLog
import ru.pavkin.ihavemoney.protocol.Transaction

object TransactionLogComponent {

  case class Props(log: ModelProxy[Pot[List[Transaction]]])

  class Backend($: BackendScope[Props, Unit]) {

    def loadTransactionLog(props: Props) = Callback {
      AppCircuit.dispatch(LoadTransactionLog())
    }

    def render(props: Props) = {
      div(
        props.log().renderEmpty("Loading..."),
        props.log().renderPending(_ => div("Loading...")),
        props.log().renderReady(log ⇒
          div(
            table(className := "table table-striped table-hover table-condensed",
              thead(tr(th("Date"), th("Category"), th("Editor"), th("Currency"), th("Amount"))),
              tbody(
                log.map(t ⇒
                  tr(td(t.date.toString), td(t.category), td(t.user), td(t.currency.code), td(t.amount.toString))
                )
              )
            )
          )
        )
      )
    }
  }

  val component = ReactComponentB[Props]("LogComponent")
    .renderBackend[Backend]
    .componentDidMount(s ⇒ s.backend.loadTransactionLog(s.props))
    .build
}
