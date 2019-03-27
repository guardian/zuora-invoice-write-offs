import ZuoraService.Subscription
import scalaz.{-\/, \/, \/-}

sealed trait ProcessedAccount
case object Cancelled extends ProcessedAccount
case class Skipped(skipReason: String) extends ProcessedAccount

object CancellationHelper extends App with Logging {

  def cancelIfApplicable(accountId: String, autoPay: Boolean, subscriptions: List[Subscription]): String \/ ProcessedAccount = {
    val singleActiveSub = subscriptions.count(_.status == "Active") == 1
    if (!autoPay && singleActiveSub) {
      ZuoraService.cancelSubscription(accountId, subscriptions.head.subscriptionNumber).map(_ => Cancelled)
    } else {
      val skipReason = if (autoPay) "user has added a payment method" else s"could not identify single active subscription"
      \/-(Skipped(skipReason))
    }
  }

  def processAccount(accountId: String) = {

    val result = for {
      account <- ZuoraService.getAccount(accountId)
      accountSummary <- ZuoraService.getAccountSummary(accountId)
      cancelOrSkip <- cancelIfApplicable(accountId, account.AutoPay, accountSummary.subscriptions)
    } yield cancelOrSkip

    result match {
      case \/-(processedAccount) => processedAccount match {
        case Cancelled => logSuccessfulResult(accountId)
        case Skipped(reason) => logSkipResult(accountId, reason)
      }
      case -\/(error) => logFailureResult(accountId, error)
    }

  }

  // Script starts here
  val inputFile = "input.csv"
  logger.info(s"Starting cancellation helper script: using input file $inputFile")
  val accounts = InputReader.readFile(inputFile)
  accounts.foreach(account => processAccount(account.accountId))

}
