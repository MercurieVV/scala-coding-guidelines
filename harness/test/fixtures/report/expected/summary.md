# Gate A/B results

Runs: 17 scored; 1 smoke runs (rep 9) skipped: noise-g-9.
Unreadable score.json skipped: broken-g-1: Expecting value: line 1 column 1 (char 0)

## Per arm (g = gated, u = ungated)

| metric | gated | ungated |
|---|---|---|
| n (headline) | 5 | 5 |
| solved | 4 | 1 |
| solve rate [Wilson 95%] | 0.80 [0.38, 0.96] | 0.20 [0.04, 0.62] |
| false_done (rate) | 0 (0.00) | 2 (0.40) |
| timeouts (counted, unsolved) | 0 | 1 |
| tampering runs | 1 | 0 |
| runs with build_edits | 1 | 0 |
| contaminated (excluded) | 1 | 0 |
| error (excluded) | 0 | 1 |
| median wall s | 200 | 62 |
| median turns (n) | 9.0 (5) | 8.5 (4) |
| median cost USD (n) | 1.00 (5) | 0.20 (5) |
| total cost USD | 5.18 | 3.74 |

## Excluded from headline

- adsr-t-3 (contaminated: read holdout)
- adsr-u-3 (error: scoring incomplete: mill "x", rc=2)
- fr-pan-g-3 (contaminated: read holdout;ls runs/x)

## Regression markers (headline runs; runs per kind)

| kind | gated | ungated |
|---|---|---|
| <compile:core> | 0 | 1 |
| <timeout> | 0 | 1 |
| test/compile id | 0 | 1 |

## Per task (headline runs)

| task | gated solved | ungated solved | diff |
|---|---|---|---|
| adsr | 1/2 | 0/2 | +1 |
| fr-pan | 2/2 | 1/2 | +1 |
| noise | 1/1 | 0/1 | +1 |

Sign test over per-task solved-count differences (ties dropped): p = 0.2500 (n tasks = 3)

## Gate blocks (gated arm, headline runs)

| hook | blocks | runs fired in |
|---|---|---|
| a,b | 0 | 0 |
| compile-gate | 2 | 2 |
| turn-end | 6 | 3 |

Verdict: does not pay off

Rule: pays off if (g.solve_rate - u.solve_rate >= 0.20 or (u.false_done >= 3 and g.false_done_rate <= u.false_done_rate / 2)) and g.median_wall <= 2 * u.median_wall; else inconclusive if |g.solve_rate - u.solve_rate| < 0.25; else does not pay off. false_done_rate = false_done / headline runs of the arm; timeouts are reported beside it (a timeout is neither solved nor false_done)

false_done: gated 0/5, ungated 2/5; timeouts: gated 0, ungated 1

## Ablation: gate without turn-end (t)

Tasks: adsr, fr-pan

Strict (contamination flags excluded):

| metric | gated | no turn-end | ungated |
|---|---|---|---|
| n (headline) | 4 | 4 | 4 |
| solved | 3 | 3 | 1 |
| solve rate [Wilson 95%] | 0.75 [0.30, 0.95] | 0.75 [0.30, 0.95] | 0.25 [0.05, 0.70] |
| false_done | 0 | 0 | 2 |
| timeouts (counted, unsolved) | 0 | 0 | 0 |
| contaminated (excluded) | 1 | 1 | 0 |
| error (excluded) | 0 | 0 | 1 |
| median wall s | 174 | 75 | 62 |
| median cost USD | 0.69 | 0.38 | 0.25 |

Ablation verdict (strict): incomplete (4 of 8)

Audited (cleared: adsr-t-3, fr-pan-g-3):

| metric | gated | no turn-end | ungated |
|---|---|---|---|
| n (headline) | 5 | 5 | 4 |
| solved | 4 | 4 | 1 |
| solve rate [Wilson 95%] | 0.80 [0.38, 0.96] | 0.80 [0.38, 0.96] | 0.25 [0.05, 0.70] |
| false_done | 0 | 0 | 2 |
| timeouts (counted, unsolved) | 0 | 1 | 0 |
| contaminated (excluded) | 1 | 1 | 0 |
| error (excluded) | 0 | 0 | 1 |
| median wall s | 100 | 80 | 62 |
| median cost USD | 0.69 | 0.50 | 0.25 |

Ablation verdict (audited): incomplete (5 of 8)

Ablation rule: over 8 counted runs of arm t (gate without the turn-end Stop hook): solved <= 5 -> turn-end carries the gain; solved = 8 -> the gain comes without turn-end; 6 or 7 -> unclear; fewer than 8 counted runs -> incomplete
