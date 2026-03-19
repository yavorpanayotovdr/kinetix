# Zero-Downtime Deployment Runbook

Audience: engineers performing planned releases on the Kinetix platform.
Last revised: 2026-03-19

---

## Prerequisites

- Helm 3.x installed and `kubectl` context pointed at the target cluster.
- You have access to the `kinetix` namespace and permission to run `helm` and `kubectl` commands.
- All images for this release have been pushed to the registry and the new tag is known.
- You have checked that no other deployment is in flight. Never run two releases concurrently.
- Grafana and the Kafka consumer-lag dashboard are open in a second window before you start.

---

## Blackout Window

**Do not deploy between 05:55 and 06:05 UTC.**

This is the start-of-day (SOD) snapshot window. The risk-orchestrator triggers scheduled
valuation jobs at 06:00 UTC. A deployment during this window will interrupt in-flight
gRPC calls to the risk-engine, causing jobs to fail and leaving `hourly_var_summary`
with a gap for that hour. If you miss the window, wait until 06:10 UTC before proceeding.

---

## Deployment Order

Deploy services strictly in this sequence. Each step must be healthy before the next begins.

### Step 1 — risk-engine

The risk-engine is a Python gRPC server. It has no Kafka consumers and no database writes,
so it is the safest service to update first. The risk-orchestrator's circuit breaker
(threshold: 5 consecutive failures, 30 s reset) will absorb any brief restart gap.

```bash
helm upgrade kinetix deploy/helm/kinetix \
  --namespace kinetix \
  --reuse-values \
  --set risk-engine.image.tag=<NEW_TAG>
```

Wait for the rollout to complete:

```bash
kubectl rollout status deployment/kinetix-risk-engine -n kinetix
```

Verify: send a test gRPC health check or watch the risk-orchestrator logs for successful
`CalculateVaR` calls before moving on.

**Critical constraint:** never upgrade the risk-engine and the risk-orchestrator in the same
`helm upgrade` invocation. The risk-orchestrator must connect to a healthy risk-engine during
its own restart. If both restart simultaneously, in-flight valuation jobs are lost and the
circuit breaker may open, requiring manual reset.

### Step 2 — risk-orchestrator

```bash
helm upgrade kinetix deploy/helm/kinetix \
  --namespace kinetix \
  --reuse-values \
  --set risk-orchestrator.image.tag=<NEW_TAG>
```

```bash
kubectl rollout status deployment/kinetix-risk-orchestrator -n kinetix
```

Verify: check the `risk.results` Kafka topic for new messages within 2 minutes of restart.
If silent, inspect pod logs for gRPC connection errors or Flyway migration failures.

### Step 3 — position-service and price-service

These two services can be deployed in the same `helm upgrade` call because they do not
call each other. Both consume from Kafka; the `RetryableConsumer` retry-with-backoff
(3 retries, exponential backoff) will handle any messages that arrive during pod restart.

```bash
helm upgrade kinetix deploy/helm/kinetix \
  --namespace kinetix \
  --reuse-values \
  --set position-service.image.tag=<NEW_TAG> \
  --set price-service.image.tag=<NEW_TAG>
```

```bash
kubectl rollout status deployment/kinetix-position-service -n kinetix
kubectl rollout status deployment/kinetix-price-service -n kinetix
```

### Step 4 — Other data services

Deploy the remaining backend services. These are read-heavy and have no critical ordering
constraint among themselves:

- audit-service
- regulatory-service
- notification-service
- rates-service (if applicable)
- volatility-service (if applicable)
- correlation-service (if applicable)
- reference-data-service (if applicable)

```bash
helm upgrade kinetix deploy/helm/kinetix \
  --namespace kinetix \
  --reuse-values \
  --set audit-service.image.tag=<NEW_TAG> \
  --set regulatory-service.image.tag=<NEW_TAG> \
  --set notification-service.image.tag=<NEW_TAG>
```

### Step 5 — gateway

The gateway is the ingress point for all UI and external API traffic. Deploy it last among
the backend services so that it is never routing to a service that has not yet started.

```bash
helm upgrade kinetix deploy/helm/kinetix \
  --namespace kinetix \
  --reuse-values \
  --set gateway.image.tag=<NEW_TAG>
```

```bash
kubectl rollout status deployment/kinetix-gateway -n kinetix
```

### Step 6 — UI

The UI is a static Vite build served as a container. It has no server-side state. Deploy it
last so that any API contract changes are already live in the backend before the new UI ships.

```bash
helm upgrade kinetix deploy/helm/kinetix \
  --namespace kinetix \
  --reuse-values \
  --set ui.image.tag=<NEW_TAG>
```

---

## Post-Deployment Checks

Run all of the following checks before declaring the release complete. If any check fails,
follow the rollback procedure below.

### 1. DLQ message count

A healthy deployment should produce zero new DLQ messages. Check all DLQ topics:

```bash
for topic in \
  trades.lifecycle.dlq \
  price.updates.dlq \
  risk.results.dlq \
  risk.anomalies.dlq; do
  echo "--- $topic ---"
  kubectl exec -n kinetix deploy/kinetix-kafka -- \
    kafka-consumer-groups.sh \
      --bootstrap-server localhost:9092 \
      --topic "$topic" \
      --describe \
      2>/dev/null || \
  kubectl exec -n kinetix deploy/kinetix-kafka -- \
    kafka-log-dirs.sh \
      --bootstrap-server localhost:9092 \
      --topic-list "$topic" \
      --describe 2>/dev/null
done
```

Any non-zero offset on a DLQ topic that was at zero before the deployment indicates a
poison message produced during restart. Do not ignore DLQ growth — investigate before
proceeding to the DLQ drain procedure if needed.

### 2. Consumer group lag

Consumer lag should return to zero within 2 minutes of each service restart:

```bash
kubectl exec -n kinetix deploy/kinetix-kafka -- \
  kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 \
    --describe \
    --all-groups
```

Expected groups and their topics:

| Consumer group | Topic |
|---|---|
| `position-service` | `trades.lifecycle` |
| `price-service` | `price.updates` |
| `risk-orchestrator` | `risk.results`, `risk.anomalies` |
| `audit-service` | `risk.audit` |
| `notification-service` | `risk.results` |

Lag greater than a few hundred messages 5 minutes after restart is a red flag. Check
pod logs for processing errors or database connection timeouts.

### 3. Audit chain verification

The audit-service maintains a hash-chained audit trail. Verify chain integrity after
deployment to confirm no records were written with a broken predecessor hash:

```bash
curl -s http://<GATEWAY_HOST>/api/v1/audit/verify \
  -H "Authorization: Bearer <ADMIN_TOKEN>" | jq .
```

Expected response: `{ "valid": true, "checkedRecords": <N> }`. A `false` result or any
`chainBroken` field set to `true` must be treated as a P1 incident and escalated immediately.

### 4. hourly_var_summary job count

Confirm the continuous aggregate for the current UTC hour has at least one entry. This
catches cases where the risk-orchestrator restarted mid-hour and dropped a scheduled job:

```bash
kubectl exec -n kinetix deploy/kinetix-postgresql -- \
  psql -U kinetix -d kinetix -c "
    SELECT bucket, job_count
    FROM hourly_var_summary
    WHERE bucket >= date_trunc('hour', now()) - interval '2 hours'
    ORDER BY bucket DESC
    LIMIT 5;
  "
```

If `job_count` is zero for the current hour (and you are past 06:05 UTC on a business day),
the SOD job did not complete. Check risk-orchestrator logs and, if necessary, trigger a
manual valuation run via the API.

### 5. Smoke test (optional but recommended)

Run the smoke test suite against the live cluster:

```bash
./gradlew :smoke-tests:test
```

These tests exercise the critical paths — trade booking, risk calculation, audit event
emission, WebSocket push — against the deployed environment.

---

## DLQ Drain Procedure

Only replay DLQ messages after you have confirmed that the downstream service is healthy and
the root cause of the original failure is resolved. Replaying into a service that is still
broken will re-enqueue the messages back on the DLQ.

The DLQ topic naming convention is `{topic}.dlq`. For example, messages from
`trades.lifecycle` that exhausted retries land in `trades.lifecycle.dlq`.

### Check what is in the DLQ

```bash
kubectl exec -n kinetix deploy/kinetix-kafka -- \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic trades.lifecycle.dlq \
    --from-beginning \
    --max-messages 10
```

Read a sample of messages to understand the failure reason before replaying blindly.

### Replay DLQ to source topic

Use `kafka-console-consumer` piped to `kafka-console-producer` to copy messages back to the
source topic. Replay at a controlled rate — do not flood the source topic:

```bash
kubectl exec -n kinetix deploy/kinetix-kafka -- \
  kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic trades.lifecycle.dlq \
    --from-beginning \
    --timeout-ms 5000 | \
kubectl exec -i -n kinetix deploy/kinetix-kafka -- \
  kafka-console-producer.sh \
    --bootstrap-server localhost:9092 \
    --topic trades.lifecycle
```

Repeat for each DLQ topic that has accumulated messages. After replay:

1. Wait 2 minutes and recheck consumer group lag — it should return to zero.
2. Recheck the DLQ message count — it should not grow again.
3. Re-run the audit chain verification if `risk.audit.dlq` was drained.

---

## Rollback

Helm tracks deployment history. To roll back to the previous release:

```bash
# List recent revisions
helm history kinetix --namespace kinetix

# Roll back to the previous revision
helm rollback kinetix --namespace kinetix

# Or roll back to a specific revision number
helm rollback kinetix <REVISION> --namespace kinetix
```

```bash
# Confirm all pods are back on the previous image
kubectl get pods -n kinetix -o wide
```

After rollback, run the same post-deployment checks listed above. A rollback does not
automatically drain or replay DLQ messages — assess whether any messages produced during
the failed deployment need to be replayed.

**Database schema note:** Helm rollback reverts the Kubernetes deployment but does not
roll back Flyway migrations. If the new release included a schema migration, the rolled-back
application must be able to run against the new schema. This is guaranteed by the migration
policy below — new columns are always nullable, so the old application binary will continue
to function correctly while ignoring the new column.

---

## Database Migration Policy

These rules ensure that any migration is safe to deploy without downtime and safe to roll
back without a schema change.

### Rules

1. **New columns must be nullable or have a default value.** Never add a `NOT NULL` column
   without a default in the same migration that creates it. The running application pods
   will be writing rows without the new column during the rolling restart window.

2. **Backfill data separately.** After the new code is fully deployed and healthy, run a
   separate migration (or a one-off script) to populate the new column for existing rows.
   Only then consider adding a `NOT NULL` constraint if the column semantics require it.

3. **Drop old columns in a future release.** Once you are certain the old column is no longer
   read or written by any deployed code, schedule its removal in a subsequent release. Never
   drop a column in the same release that stops using it — the previous binary may still be
   reading it during the rollout window.

4. **Never rename a column in a single migration.** A rename is a simultaneous add-and-drop
   from the application's perspective. Instead:
   - Migration N: add the new column (nullable).
   - Deploy: update the application to write both the old and new columns.
   - Migration N+1 (next release): backfill the new column, stop writing the old one.
   - Migration N+2 (following release): drop the old column.

5. **No `CREATE INDEX CONCURRENTLY` inside Flyway migrations.** Flyway runs migrations inside
   transactions; `CREATE INDEX CONCURRENTLY` is incompatible with a transaction and will
   cause the migration to fail. Use `CREATE INDEX` (blocking) inside the migration, or
   create the index outside Flyway in a one-off maintenance script during a low-traffic window.

6. **Test migrations against a copy of production data volume** before the release, especially
   for tables that hold position, audit, or time-series data. A migration that takes 30 seconds
   on a dev dataset can take 20 minutes on 2 years of `valuation_jobs`.

---

## Quick Reference

| Action | Command |
|---|---|
| Deploy single service | `helm upgrade kinetix deploy/helm/kinetix --namespace kinetix --reuse-values --set <service>.image.tag=<TAG>` |
| Check rollout | `kubectl rollout status deployment/kinetix-<service> -n kinetix` |
| List Helm revisions | `helm history kinetix --namespace kinetix` |
| Roll back | `helm rollback kinetix [REVISION] --namespace kinetix` |
| Consumer group lag | `kubectl exec -n kinetix deploy/kinetix-kafka -- kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups` |
| Audit chain verify | `curl http://<GATEWAY>/api/v1/audit/verify -H "Authorization: Bearer <TOKEN>"` |
| Smoke tests | `./gradlew :smoke-tests:test` |
