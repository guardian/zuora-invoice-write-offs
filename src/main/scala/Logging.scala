import com.typesafe.scalalogging.StrictLogging

trait Logging extends StrictLogging {

  def logInfo(accountId: String, message: String): Unit = {
    logger.info(s"AccountId $accountId: $message")
  }

  def logError(accountId: String, message: String): Unit = {
    logger.error(s"AccountId $accountId: $message")
  }

  def logSuccessfulResult(accountId: String): Unit = {
    logInfo(accountId, s"SUCCESSFUL processing.")
  }

  def logFailureResult(accountId: String, errorMessage: String): Unit = {
    logError(accountId, s"FAILURE processing: $errorMessage")
  }

  def logSkipResult(accountId: String, skipReason: String): Unit = {
    logInfo(accountId, s"SKIPPED processing: $skipReason")
  }

}
