import purecsv.unsafe.CSVReader

object InputReader extends Logging {

  case class Account(accountId: String)

  def readFile(filename: String): List[Account] = {
    val accounts = CSVReader[Account].readCSVFromFileName(filename, skipHeader = true)
    logger.info(s"Read ${accounts.size} accounts from file...")
    accounts
  }

}
