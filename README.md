## Script steps
1. Find all unpaid invoices for the specified account
2. Adjust them, to avoid charging users for 'old debt'
3. Prepare the account for payment, by enabling autopay and resetting the consecutive failed payments counter

At the end of this process, users will not be charged for any old invoices. They will be automatically charged their next scheduled payment on their regular billing date.

## Getting started

1. Add an input.csv file with a list of account ids for processing.
2. Set the following configuration values as environment variables: `ZuoraUser`, `ZuoraPass`, `ZuoraUrl` 
3. Use `sbt run` to start the script.
4. Check the log file for failures/successes.