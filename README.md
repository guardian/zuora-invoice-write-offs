# Background
This repo contains scripts which allow you to:

* write off old invoices that we don't want to collect - this is useful when a debt builds up for some reason and we want to reenable auto pay.
* cancel any subscriptions linked to accounts with autopay = false - this is useful for tidying up if users who end up in the aforementioned state choose not to continue with their subscriptions

## How to use
1. Clone the repo locally
1. Create an input.csv file in the root of the local project with a list of account ids for processing and the header 'accountId'
1. Set the following configuration values as environment variables: `ZuoraUser`, `ZuoraPass`, `ZuoraUrl` 
1. `sbt run` and select whether to run InvoiceHelper or CancellationHelper when prompted 
1. Check the log file (in logs/script.log) for failures/successes/skips, by grepping for 'FAILURE', 'SUCCESSFUL' or 'SKIPPED', respectively. It usually takes a few seconds to process each account.

## What the InvoiceHelper script does
1. Find all unpaid invoices for the specified account
1. Adjust them to zero, to avoid charging users for 'old debt'
1. If the above steps are unsuccessful, give up
1. Prepare the account for payment, by enabling autopay and resetting the consecutive failed payments counter

At the end of this process, users will not be charged for any old invoices. They will be automatically charged their next scheduled payment on their regular billing date.

## What the CancellationHelper script does
1. Checks whether an account is configured correctly (autopay = true)
1. For any accounts where autopay = false, attempt to cancel the subscription (if it can identify a single active subscription associated with the account)
