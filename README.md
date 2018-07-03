# Background
This script is to write off old invoices that we don't want to collect, this is useful
when a debt builds up for some reason and we want to reenable auto pay.

## How to use
1. Clone the repo locally
1. Create an input.csv file in the root of the local project with a list of account ids for processing and the header 'accountId'
1. Set the following configuration values as environment variables: `ZuoraUser`, `ZuoraPass`, `ZuoraUrl` 
1. Use `sbt run` to start the script (it usually takes a few seconds per id depending on the number of invoices)
1. Check the log file (in logs/invoice-helper.log) for failures/successes, by grepping for 'FAILURE' or 'SUCCESSFUL', respectively.

## What the script does
1. Find all unpaid invoices for the specified account
1. Adjust them to zero, to avoid charging users for 'old debt'
1. If the above steps are unsuccessful, give up
1. Prepare the account for payment, by enabling autopay and resetting the consecutive failed payments counter

At the end of this process, users will not be charged for any old invoices. They will be automatically charged their next scheduled payment on their regular billing date.
