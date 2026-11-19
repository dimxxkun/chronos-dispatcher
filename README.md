# Chronos-Dispatcher

[![License: Noncommercial](https://img.shields.io/badge/License-Noncommercial-blue.svg)](LICENSE)

**Chronos-Dispatcher** is an asynchronous job scheduler and worker dispatch engine written in Java. Built for high-reliability backend microservices, Chronos provides thread-safe delayed queueing, idempotency deduplication windows, exponential backoff retries, and automated Dead-Letter Queue (DLQ) quarantining.

---

## Architecture

```
 Client Submissions
         │
         ▼
[ Idempotency Guard ] ──(Duplicate key?)──> Reject / Deduplicate
         │
         ▼ (Accepted)
[ DelayQueue Min-Heap ] ──(Trigger time reached)
         │
         ▼
[ Worker Pool (N threads) ]
         │
         ├───> [ Success ] ──> JobState.COMPLETED
         └───> [ Failure ] ──> Retry with Exponential Backoff
                                      │
                                      ▼ (Max Retries Exceeded)
                        [ Dead-Letter Queue (DLQ) ]
```

## Key Backend Features

- **DelayQueue Prioritization**: Tasks are ordered and popped strictly when their scheduled execution timestamp is reached.
- **Idempotency Deduplication**: Thread-safe concurrent registry rejecting identical transactions or deduplication tokens.
- **Exponential Backoff & Rescheduling**: Automatically computes backoff intervals ($50\text{ms} \times 2^{\text{attempt}-1}$) and returns failing jobs to the delayed min-heap.
- **Dead-Letter Containment**: Prevents poison pills from cycling endlessly, routing unrecoverable jobs to the DLQ.

## Quick Start & Verification

```bash
# Compile
javac -d bin (Get-ChildItem -Recurse -Filter *.java src/main).FullName

# Run Demo
java -cp bin com.chronos.dispatcher.App

# Run Deterministic Verifiers
javac -d bin -cp bin (Get-ChildItem -Recurse -Filter *.java src).FullName
java -cp bin com.chronos.dispatcher.JobDispatcherTest
```

## License
[Noncommercial license](LICENSE) © [dimxxkun](https://github.com/dimxxkun)

## Commercial permission

Original material in this repository is available under the [Noncommercial Use and Commercial Permission License](LICENSE). **Noncommercial use is free. Commercial use requires prior written permission and a separate written agreement specifying the scope and agreed fee.** Contact [work.abdulfatai@gmail.com](mailto:work.abdulfatai@gmail.com) with the repository name and intended use.

Portfolio review and recruitment evaluation are permitted without a fee. Third-party components retain their own licenses. Permissions already granted for earlier versions remain valid; the new terms do not revoke those grants. These are custom source-available terms, not an OSI-approved open-source license.
