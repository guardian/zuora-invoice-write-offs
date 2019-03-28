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
      val positiveCharges = invoice.invoiceItems.filter(_.chargeAmount > 0)
      val negativeCharges = invoice.invoiceItems.filter(_.chargeAmount < 0)
      val chargeAdjustments = {
        val chargeTuples = positiveCharges.zipAll(negativeCharges.map(discount => Some(discount)), positiveCharges.maxBy(_.chargeAmount), None)
        chargeTuples.map { chargeAndDiscount => buildChargeAdjustment(invoice, chargeAndDiscount._1, chargeAndDiscount._2) }
      }
      val taxQuery = getTaxationItemDetails(accountId, invoice)
      taxQuery match {
        case \/-(taxationItems) => {
          val positiveTaxAmounts = taxationItems.filter(taxItem => taxItem.taxAmount > 0)
          val negativeTaxAmounts = taxationItems.filter(taxItem => taxItem.taxAmount < 0)
          val taxAdjustments = {
            if (positiveTaxAmounts.nonEmpty) {
              val taxTuples = positiveTaxAmounts.zipAll(negativeTaxAmounts.map(taxDiscount => Some(taxDiscount)), positiveTaxAmounts.maxBy(_.taxAmount), None)
              taxTuples.map { taxChargeAndDiscount => buildTaxAdjustment(invoice, taxChargeAndDiscount._1, taxChargeAndDiscount._2) }
            } else List()
          }
          val allAdjustments = taxAdjustments ++ chargeAdjustments
          allAdjustments.right
        }
        case -\/(error) => {
          s"Failed to obtain tax adjustment details: $error".left
        }
      }
    }
    val failures = adjustments.collect { case -\/(error) => error }
    val allAdjustments = adjustments.collect { case \/-(adjustment) => adjustment }.flatten
    if (failures.isEmpty) allAdjustments.right else s"Failed when gathering adjustments: $failures".left
  }

  def buildChargeAdjustment(invoice: Invoice, positiveCharge: InvoiceItem, negativeCharge: Option[InvoiceItem]): InvoiceItemAdjustment = {
    val amount = negativeCharge match {
      case Some(discount) => {
        logger.info(s"Found a discount of ${discount.chargeAmount}")
        positiveCharge.chargeAmount + discount.chargeAmount
      }
      case None => positiveCharge.chargeAmount
    }
    InvoiceItemAdjustment(
      amount = amount,
      invoice = invoice,
      sourceType = "InvoiceDetail",
      sourceId = positiveCharge.id
    )
  }

  def buildTaxAdjustment(invoice: Invoice, taxationItem: TaxationItem, negativeTax: Option[TaxationItem]): InvoiceItemAdjustment = {
    val amount = negativeTax match {
      case Some(taxDiscount) => {
        logger.info(s"Found a tax discount of ${taxDiscount.taxAmount}")
        taxationItem.taxAmount + taxDiscount.taxAmount
      }
      case None => taxationItem.taxAmount
    }
    InvoiceItemAdjustment(
      amount = amount,
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
    reset <- account.basicInfo.defaultPaymentMethod.map {
      paymentMethod => resetFailedPaymentsCounter(accountId, paymentMethod)
    }.getOrElse(-\/(s"Account has no default payment method so cannot be prepared for payment"))
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
