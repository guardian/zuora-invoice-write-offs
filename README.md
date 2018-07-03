# Background
This script is to write off old invoices that we don't want to collect, this is useful
when a debt builds up for some reason and we want to reenable auto pay.

## How to use
1. clone the repo locally
1. create an input.csv file in the root of the local project with a list of account ids for processing.
2. Set the following configuration values as environment variables: `ZuoraUser`, `ZuoraPass`, `ZuoraUrl` 
3. Use `sbt run` to start the script (it usually takes a few seconds per id depending on the number of invoices)
4. Check the log file (in the TODO located file) for failures/successes.

## What the script does
1. Find all unpaid invoices for the specified account
2. Adjust them to zero, to avoid charging users for 'old debt'
3. If the above steps are unsuccessful, give up
4. Prepare the account for payment, by enabling autopay and resetting the consecutive failed payments counter

At the end of this process, users will not be charged for any old invoices. They will be automatically charged their next scheduled payment on their regular billing date.
