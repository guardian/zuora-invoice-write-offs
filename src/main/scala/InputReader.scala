import purecsv.unsafe.CSVReader

object InputReader extends Logging {

  case class Account(accountId: String)

  def readFile(filename: String): List[Account] = {
    val allRows = CSVReader[Account].readCSVFromFileName(filename)
    val accounts = allRows.distinct // remove any duplicates, because we can only process an account once
    logger.info(s"Read ${accounts.size} accounts from file...")
    accounts
  }

}
