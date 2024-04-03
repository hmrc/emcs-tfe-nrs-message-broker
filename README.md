
# emcs-tfe-nrs-message-broker

This µService acts as a message broker to send data to the Non-Repudiation Service (NRS).

It uses Apache Pekko to run scheduled jobs at configured intervals (see [application.conf](conf/application.conf)).

## API Endpoints

<details>
<summary>Insert an NRS payload into Mongo

**`PUT`** /trader/:ern/nrs/submission
</summary>

### Responses

#### Success Response(s)

**Status**: 202 (ACCEPTED)

#### Error Response(s)

**Status**: 500 (ISE)

**Body**: `Failed to insert NRS payload` (see application logs for more information)

</details>

## Job specification and overview

Each job is defined in [application.conf](conf/application.conf) and contains 4 distinct entries:

- `description` - A description of what the job does used in setting up the schedule in Pekko
- `expression` - A CRON expression (delimited by `_`) that denotes how often the job should run (for readability this is usually commented with a friendly value i.e., `runs every 5 mins`)
- `enabled` - boolean value that enables/disables the configured job
- `mongoLockTimeout` - an integer value that represents the number of seconds until the lock forcefully expires (useful when the job crashes midway through processing and cannot unlock programmatically)

Pekko invokes each jobs `invoke` ([code](app/scheduler/ScheduledService.scala)) method. Because this service is multi-instance, Pekko will invoke the same job twice so to prevent any race conditions or unexpected behaviour a `tryLock` method is used.

`tryLock` uses the [HMRC Mongo lock](https://github.com/hmrc/hmrc-mongo?tab=readme-ov-file#lock) mechanism.

Once a lock is obtained for the job, the contents of the `invoke` method are ran. Once the job is complete, the lock is released (by deleting the record from the `lock` collection in Mongo).

## Scheduled jobs

<details>

<summary>Send records to NRS</summary>

### Details

This job is configured in [application.conf](conf/application.conf) under the `SendSubmissionToNRSJob` object.

The `invoke` method ([code](app/services/SendSubmissionToNRSService.scala)) picks up any records in the [`PENDING` or `FAILED_PENDING_RETRY`](app/models/mongo/RecordStatusEnum.scala) state.
The number of records returned is limited to a configurable value (see `numberOfRecordsToRetrieve` in [application.conf](conf/application.conf)).

It then sends all of these records (sequentially but with no pre-determined delay) to NRS and if an `OK` response is returned with a `nrSubmissionId` ([see response model](app/models/response/NRSSuccessResponse.scala)) then the record is set to `SENT`
in application memory and subsequently deleted from Mongo.

When a 5xx response is returned from NRS, the record is set to `FAILED_PENDING_RETRY` and will be retried on the next scheduled run.

When a 4xx response is returned from NRS, the record is set to `PERMANENTLY_FAILED` and will NOT be retried. A 4xx response should not occur in production and should be investigated manually.

Once all the records have been sent, the results are reflected in Mongo (with each records `updatedAt` timestamp updated). All successfully sent records are deleted from Mongo.

The TTL on each record is 30 days since it was last updated (to cover any outages).

</details>

<details>

<summary>Monitoring job</summary>

### Details

This job is configured in [application.conf](conf/application.conf) under the `MonitoringJob` object.

The `invoke` method ([code](app/services/MonitoringJobService.scala)) simply calls Mongo to count any records in the [`PENDING`, `PERMANENTLY_FAILED` or `FAILED_PENDING_RETRY`](app/models/mongo/RecordStatusEnum.scala) state and logs the result.

</details>


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
