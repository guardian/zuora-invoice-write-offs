import ZuoraService._
import scalaz.{-\/, \/, \/-}
import scalaz.Scalaz._

object InvoiceHelper extends App with Logging {

  def invoicesToProcess(accountId: String): String \/ List[Invoice] = {
    logInfo(accountId, "collecting invoice data")
    for {
      invoiceSummary <- getInvoiceTransactionSummary(accountId)
      unpaidInvoices <- identifyUnpaidInvoices(invoiceSummary.invoices)
    } yield unpaidInvoices
  }

  def identifyUnpaidInvoices(invoices: List[Invoice]): String \/ List[Invoice] = {
    val unpaidInvoices = invoices.filter(invoice => invoice.balance > 0)
    if (unpaidInvoices.isEmpty) "Failed to find an unpaid invoice on account".left else unpaidInvoices.right
  }

  def collectAdjustments(accountId: String, invoices: List[Invoice]): String \/ List[InvoiceItemAdjustment] = {
    val adjustments = invoices.map { invoice =>
      { val chargeAdjustments = invoice.invoiceItems.map { invoiceItem =>
          buildChargeAdjustment(invoice, invoiceItem)
        }
        val taxQuery = getTaxationItemDetails(accountId, invoice)
        taxQuery match {
          case \/-(taxationItems) => {
            // Need to filter out 0s because of US membership (which has 0% tax) - otherwise we try to make an adjustment of 0.00, which fails
            val taxAdjustments = taxationItems.filter(taxItem => taxItem.taxAmount > 0).map(item => buildTaxAdjustment(invoice, item))
            (taxAdjustments ++ chargeAdjustments).right
          }
          case -\/(error) => {
            s"Failed to obtain tax adjustment details: $error".left
          }
        }
      }
    }
    val failures = adjustments.collect { case -\/(error) => error }
    val allAdjustments = adjustments.collect { case \/-(adjustment) => adjustment }.flatten
    if (failures.isEmpty) allAdjustments.right else s"Failed when gathering adjustments: $failures".left
  }

  def buildChargeAdjustment(invoice: Invoice, invoiceItem: InvoiceItem): InvoiceItemAdjustment = {
    InvoiceItemAdjustment(
      amount = invoiceItem.chargeAmount,
      invoice = invoice,
      sourceType = "InvoiceDetail",
      sourceId = invoiceItem.id
    )
  }

  def buildTaxAdjustment(invoice: Invoice, taxationItem: TaxationItem): InvoiceItemAdjustment = {
    InvoiceItemAdjustment(
      amount = taxationItem.taxAmount,
      invoice = invoice,
      sourceType = "Tax",
      sourceId = taxationItem.id
    )
  }

  def processAdjustments(accountId: String, adjustments: List[InvoiceItemAdjustment]): String \/ List[CreateInvoiceItemAdjustmentResult] = {
    logInfo(accountId, s"processing adjustments")
    val adjustmentAttempts = adjustments.map { adjustment => createInvoiceItemAdjustment(accountId, adjustment) }
    val failures = adjustmentAttempts.collect { case -\/(error) => error }
    val successes = adjustmentAttempts.collect { case \/-(adjustmentResult) => adjustmentResult }
    if (failures.isEmpty) successes.right else failures.toString.left
  }

  def prepareForPayment(accountId: String) = for {
    account <- getAccountSummary(accountId)
    reset <- resetFailedPaymentsCounter(accountId, account.basicInfo.defaultPaymentMethod)
    autoPay <- turnOnAutoPay(accountId)
  } yield autoPay

  def processAccount(accountId: String): Unit = {
    val result = for {
      invoices <- invoicesToProcess(accountId)
      adjustments <- collectAdjustments(accountId, invoices)
      _ <- processAdjustments(accountId, adjustments)
      paymentPrep <- prepareForPayment(accountId)
    } yield paymentPrep
    result match {
      case \/-(successfulUpdate) => logSuccessfulResult(accountId)
      case -\/(error) => logFailureResult(accountId, error)
    }
  }

  // Script starts here
  val inputFile = "input.csv"
  logger.info(s"Starting invoice helper script: using input file $inputFile")
  val accounts = InputReader.readFile(inputFile)
  accounts.foreach(account => processAccount(account.accountId))

}
