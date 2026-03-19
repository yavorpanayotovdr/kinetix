-- Fix: include failed replays (NULL drift) in divergent index.
-- The original index in V22 only matched rows where var_drift > 0, but
-- failed replays have NULL replay_var_value which produces NULL drift.
-- Those failed replays are the most important to surface in alerts.
DROP INDEX IF EXISTS idx_replay_runs_divergent;

CREATE INDEX idx_replay_runs_divergent
    ON replay_runs (replayed_at DESC, manifest_id)
    WHERE input_digest_match = false
       OR var_drift > 0
       OR replay_var_value IS NULL;
