import java.time.LocalDate
import java.util.concurrent.TimeUnit
import okhttp3._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scalaz.{-\/, \/, \/-}
import scalaz.Scalaz._

object ZuoraService extends Logging {

  case class ZuoraRestConfig(username: String, password: String, baseUrl: String)

  val username = System.getenv("ZuoraUser")
  val password = System.getenv("ZuoraPass")
  val baseUrl = System.getenv("ZuoraUrl")

  val config = ZuoraRestConfig(username, password, baseUrl)

  val restClient = new OkHttpClient().newBuilder()
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

  def buildRequest(config: ZuoraRestConfig, route: String): Request.Builder = {
    new Request.Builder()
      .addHeader("apiSecretAccessKey", config.password)
      .addHeader("apiAccessKeyId", config.username)
      .url(s"${config.baseUrl}/$route")
  }

  case class InvoiceTransactionSummary(invoices: List[Invoice], success: Boolean)

  case class DefaultPaymentMethod(id: String, paymentMethodType: String)

  case class BasicAccountInfo(id: String, balance: Double, defaultPaymentMethod: DefaultPaymentMethod)

  case class AccountSummary(basicInfo: BasicAccountInfo, success: Boolean)

  case class Invoice(id: String, invoiceNumber: String, amount: Double, balance: Double, status: String, invoiceItems: List[InvoiceItem])

  case class InvoiceItem(id: String, chargeAmount: Double, taxAmount: Double)

  case class TaxationItemQuery(invoice: Invoice)

  case class TaxationItem(taxAmount: Double, id: String)

  case class TaxationItemQueryResult(taxationItems: List[TaxationItem], size: Int)

  case class InvoiceItemAdjustment(amount: Double, invoice: Invoice, sourceType: String, sourceId: String)

  case class CreateInvoiceItemAdjustmentResult(success: Boolean, id: String)

  case class AccountUpdate(autoPay: Boolean)

  case class UpdateAccountResult(success: Boolean)

  case class UpdateDefaultPaymentMethod(consecutiveFailureCount: Int)

  case class UpdateResult(success: Boolean)

  implicit val invoiceItemReads: Reads[InvoiceItem] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "chargeAmount").read[Double] and
    (JsPath \ "taxAmount").read[Double]
  )(InvoiceItem.apply _)

  implicit val invoiceReads: Reads[Invoice] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "invoiceNumber").read[String] and
    (JsPath \ "amount").read[Double] and
    (JsPath \ "balance").read[Double] and
    (JsPath \ "status").read[String] and
    (JsPath \ "invoiceItems").read[List[InvoiceItem]]
  )(Invoice.apply _)

  implicit val invoiceTransactionSummaryReads: Reads[InvoiceTransactionSummary] = (
    (JsPath \ "invoices").read[List[Invoice]] and
    (JsPath \ "success").read[Boolean]
  )(InvoiceTransactionSummary.apply _)

  implicit val defaultPaymentMethodReads: Reads[DefaultPaymentMethod] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "paymentMethodType").read[String]
  )(DefaultPaymentMethod.apply _)

  implicit val basicAccountInfoReads: Reads[BasicAccountInfo] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "balance").read[Double] and
    (JsPath \ "defaultPaymentMethod").read[DefaultPaymentMethod]
  )(BasicAccountInfo.apply _)

  implicit val accountSummaryReads: Reads[AccountSummary] = (
    (JsPath \ "basicInfo").read[BasicAccountInfo] and
    (JsPath \ "success").read[Boolean]
  )(AccountSummary.apply _)

  implicit val taxationItemReads: Reads[TaxationItem] = (
    (JsPath \ "TaxAmount").read[Double] and
    (JsPath \ "Id").read[String]
  )(TaxationItem.apply _)

  implicit val taxationItemQueryReads: Reads[TaxationItemQueryResult] = (
    (JsPath \ "records").read[List[TaxationItem]] and
    (JsPath \ "size").read[Int]
  )(TaxationItemQueryResult.apply _)

  implicit val createInvoiceItemAdjustmentResultReads: Reads[CreateInvoiceItemAdjustmentResult] = (
    (JsPath \\ "Success").read[Boolean] and
    (JsPath \\ "Id").read[String]
  )(CreateInvoiceItemAdjustmentResult.apply _)

  implicit val updateAccountResultReads: Reads[UpdateAccountResult] = (JsPath \ "success").read[Boolean].map {
    success => UpdateAccountResult(success)
  }

  implicit val updateResultReads: Reads[UpdateResult] = (JsPath \ "Success").read[Boolean].map {
    success => UpdateResult(success)
  }

  implicit val taxationItemQueryWrites = new Writes[TaxationItemQuery] {
    def writes(taxationItemQuery: TaxationItemQuery) = Json.obj(
      "queryString" -> s"select id, TaxAmount from taxationItem where invoiceId='${taxationItemQuery.invoice.id}'"
    )
  }

  implicit val invoiceItemAdjustmentWrites = new Writes[InvoiceItemAdjustment] {
    def writes(invoiceItemAdjustment: InvoiceItemAdjustment) = Json.obj(
      "objects" -> Json.arr(
        Json.obj(
          "Amount" -> invoiceItemAdjustment.amount,
          "AdjustmentDate" -> LocalDate.now(),
          "InvoiceId" -> invoiceItemAdjustment.invoice.id,
          "ReasonCode" -> "Write-off",
          "SourceType" -> invoiceItemAdjustment.sourceType,
          "SourceId" -> invoiceItemAdjustment.sourceId,
          "Type" -> "Credit"
        )
      ),
      "type" -> "InvoiceItemAdjustment"
    )
  }

  implicit val updatePaymentMethodWrites = new Writes[UpdateDefaultPaymentMethod] {
    def writes(updateDefaultPaymentMethod: UpdateDefaultPaymentMethod) = Json.obj(
      "NumConsecutiveFailures" -> updateDefaultPaymentMethod.consecutiveFailureCount
    )
  }

  implicit val accountUpdateWrites = new Writes[AccountUpdate] {
    def writes(accountUpdate: AccountUpdate) = Json.obj(
      "autoPay" -> accountUpdate.autoPay
    )
  }

  def convertResponseToCaseClass[T](accountId: String, response: Response)(implicit r: Reads[T]): String \/ T = {
    if (response.isSuccessful) {
      val bodyAsJson = Json.parse(response.body.string)
      bodyAsJson.validate[T] match {
        case success: JsSuccess[T] => success.get.right
        case error: JsError => {
          s"failed to convert Zuora response to case case. Response body was: \n ${bodyAsJson}".left
        }
      }
    } else {
      s"request to Zuora was unsuccessful, the response was: \n $response | body was: \n ${response.body.string}".left
    }
  }

  def getInvoiceTransactionSummary(accountId: String): String \/ InvoiceTransactionSummary = {
    logInfo(accountId, s"getting invoice transaction summary from Zuora")
    val request = buildRequest(config, s"transactions/invoices/accounts/${accountId}").get().build()
    val call = restClient.newCall(request)
    val response = call.execute
    convertResponseToCaseClass[InvoiceTransactionSummary](accountId, response)
  }

  def getAccountSummary(accountId: String): String \/ AccountSummary = {
    logger.info(s"Getting account summary from Zuora for Account Id: $accountId")
    val request = buildRequest(config, s"accounts/$accountId/summary").get().build()
    val call = restClient.newCall(request)
    val response = call.execute
    convertResponseToCaseClass[AccountSummary](accountId, response)
  }

  def getTaxationItemDetails(accountId: String, invoice: Invoice): String \/ List[TaxationItem] = {
    val query = TaxationItemQuery(invoice)
    logInfo(accountId, s"attempting to identify taxation item information for invoice id ${invoice.id}")
    val body = RequestBody.create(MediaType.parse("application/json"), Json.toJson(query).toString)
    val request = buildRequest(config, s"action/query").post(body).build()
    val call = restClient.newCall(request)
    val response = convertResponseToCaseClass[TaxationItemQueryResult](accountId, call.execute)
    response match {
      case \/-(result) => result.taxationItems.right
      case -\/(error) => error.left
    }
  }

  def createInvoiceItemAdjustment(accountId: String, invoiceItemAdjustment: InvoiceItemAdjustment): String \/ CreateInvoiceItemAdjustmentResult = {
    logInfo(accountId, s"attempting to process invoice item adjustment with the following details: ${invoiceItemAdjustment}")
    val body = RequestBody.create(MediaType.parse("application/json"), Json.toJson(invoiceItemAdjustment).toString)
    val request = buildRequest(config, s"action/create").post(body).build()
    val call = restClient.newCall(request)
    val response = call.execute
    convertResponseToCaseClass[CreateInvoiceItemAdjustmentResult](accountId, response)
  }

  def turnOnAutoPay(accountId: String): String \/ Unit = {
    val accountUpdate = AccountUpdate(autoPay = true)
    logInfo(accountId, s"attempting to turn on autoPay with the following command: $accountUpdate")
    val body = RequestBody.create(MediaType.parse("application/json"), Json.toJson(accountUpdate).toString)
    val request = buildRequest(config, s"accounts/${accountId}").put(body).build()
    val call = restClient.newCall(request)
    val response = call.execute
    convertResponseToCaseClass[UpdateAccountResult](accountId, response) match {
      case \/-(result) => if (result.success) { \/-(()) } else { -\/("Zuora result indicated a failure when attempting to toggle autopay") }
      case -\/(error) => -\/(error)
    }
  }

  def resetFailedPaymentsCounter(accountId: String, defaultPaymentMethod: DefaultPaymentMethod): String \/ Unit = {
    val update = UpdateDefaultPaymentMethod(consecutiveFailureCount = 0)
    logInfo(accountId, s"attempting to update default payment method with the following details: ${update}")
    val body = RequestBody.create(MediaType.parse("application/json"), Json.toJson(update).toString)
    val request = buildRequest(config, s"object/payment-method/${defaultPaymentMethod.id}").put(body).build()
    val call = restClient.newCall(request)
    val response = call.execute
    convertResponseToCaseClass[UpdateResult](accountId, response) match {
      case \/-(result) => if (result.success) { \/-(()) } else { -\/("Zuora result indicated a failure when attempting to reset failed payments counter") }
      case -\/(error) => -\/(error)
    }
  }

  def clearDefaultPaymentMethod(accountId: String): String \/ Unit = {
    logInfo(accountId, s"attempting to clear default payment method")
    val json = Json.obj(
      "objects" -> Json.arr(
        Json.obj(
          "fieldsToNull" -> Json.arr("DefaultPaymentMethodId"),
          "Id" -> accountId
        )
      ),
      "type" -> "Account"
    )
    val body = RequestBody.create(MediaType.parse("application/json"), json.toString)
    val request = buildRequest(config, s"action/update").put(body).build()
    val call = restClient.newCall(request)
    val response = call.execute
    convertResponseToCaseClass[List[UpdateResult]](accountId, response).map(_.head)  match {
      case \/-(result) => if (result.success) { \/-(()) } else { -\/("Zuora result indicated a failure when attempting to clear the default payment method") }
      case -\/(error) => -\/(error)
    }
  }

}
