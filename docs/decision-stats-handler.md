# Decision: Stats Handler (not a Service)

## Decision
Use a dedicated **`StatsRepository`** (a plain Kotlin class) for all stats logic. Do **not** create a separate Android `Service` for stats.

## Why
- An Android `Service` is for work that must run in the background on its own. Stats are just a few database writes per event and a few queries when the screen opens.
- `TapListenerService` is already running, so it can pass events along.
- A second service adds manifest entries and lifecycle bugs for no benefit.
- A single handler gives stats one home, so the listener, the classifier, and the UI never touch the database directly.

## What it records
One row per event in a Room table:
- timestamp
- package name
- type: `TAP` or `DISMISS`
- age in ms (`System.currentTimeMillis() - sbn.postTime`)
- bait or important
- caught or not

Weekly and monthly stats are queries over these rows (counts and averages between two dates). Store events, not running totals.

## Interface

```kotlin
class StatsRepository(private val dao: EventDao) {
    suspend fun recordTap(pkg: String, ageMillis: Long, wasCaught: Boolean)
    suspend fun recordDismiss(pkg: String, ageMillis: Long)
    suspend fun weekly(): StatsSummary
    suspend fun monthly(): StatsSummary
}
```

`StatsSummary` holds tap rate, average and median acknowledge times, and counts, so the UI computes nothing.

## Where each piece lives
- **Listener (`service/`):** calls `onTap(...)` for taps and `onDismiss(...)` for swipes and clear-all.
- **Game logic (`logic/`, `stats/`):** `StatsRepository`, the SQLite table and the queries. Events are recorded after classification. Bypassed and unsure events are logged too, with a flag.
- **UI (`ui/stats`):** the stats screen calls only `weekly()` and `monthly()`, with a week/month toggle.
- **Debug (`debug/StatsHarnessPanel.kt`):** generates fake history (a one-day build can't produce weeks of data), writing through the repository.

## Rules
- Database writes happen off the main thread. The listener callback hands the event to a coroutine and returns quickly.
- Build the repository once at app start (for example in the `Application` class) and share it.
- The backend writes it; the UI only reads it.

## Definitions to settle
- **Ratio:** show tap rate (taps divided by taps plus dismissals) as a percentage, not a raw ratio, which breaks at zero taps.
- **Average time:** show tap and dismiss averages separately, plus a combined one. Use the median or cap times at about an hour, because "clear all" and time away from the phone skew averages.
- **Which notifications count:** bait only by default, with a toggle to include important ones.
- **Removal reasons:** count `REASON_CLICK` as a tap, and `REASON_CANCEL` (and probably `REASON_CANCEL_ALL`) as a dismissal. Ignore the rest. Action-button behavior (e.g. "Mark as read") is unverified, so log every removal reason on the Pixel and check.

## Priority
Build this week's stats first, then the month view, after the core (listener, classifier, grass health, lockout) works.

## Implementation status (harness)

Built: the pipeline from the listener to a SQLite table, the summary math, and a debug view. The real stats screen (`ui/stats`) reads the same `weekly()` / `monthly()` summaries.

| Piece | Where | Notes |
|---|---|---|
| Dismiss detection | `service/TapDetection.removalKind` | `REASON_CLICK` = tap, `REASON_CANCEL` / `REASON_CANCEL_ALL` = dismissal, everything else ignored. Same filters as taps (group summaries, ongoing, our own non-test notifications). |
| Contract | `TapHandler.onDismiss(context)`, `TapDispatcher.dispatchDismiss` | Takes the full `NotificationContext` (package + `ageMillis`) instead of `onDismiss(packageName, ageMillis)`, like `onTap`. Default no-op. |
| Recording | `GameRepository` | Taps: after the game decides, with classification and caught. Dismissals: classified with the same rules, no roll, no game effect. |
| Repository | `stats/StatsRepository` | `recordTap`, `recordDismiss`, `weekly`, `monthly`, `summary(from, to)`, plus `submit` for non-coroutine callers and `changes` to re-query. One background thread. Built in `TaptationApplication` (`app.stats`). |
| Storage | `stats/SqliteStatsStore` | Plain `SQLiteOpenHelper` (no Room, no new dependencies), table `events` in `stats.db`, index on `timestamp`. |
| Summary | `stats/StatsCalculator` | Tap rate as a percentage (null with no events), medians, averages with each age capped at 1 h. Bait only by default; `includeImportant = true` adds important and unsure. |
| Debug | Debug tab > *Stats harness* | Week/month, include important, generate 30 days of fake history, clear. |

Choices made where this doc was open:
- **Week / month** are the last 7 and 30 days up to now, not calendar weeks.
- **`recordTap` / `recordDismiss`** also take the classification, since stats split bait from important.
- **Ages** come from `System.currentTimeMillis() - sbn.postTime`, set by the listener.

Found while testing on the emulator: when Android bundles notifications from one app and the user swipes the bundle, the summary arrives as `REASON_CANCEL` (ignored, it's a summary) and each child as `REASON_GROUP_SUMMARY_CANCELED` (ignored by the rules above). Those dismissals are not counted yet. Check on the Pixel how often real apps get bundled before deciding whether to count children removed with their summary.
