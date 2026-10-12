# Chained feature work

| arm | chain | step | task | solved | false_done | regressions | chain regressions | erosion | wall s | cost USD |
|---|---|---|---|---|---|---|---|---|---|---|
| u | 1 | 1 | fr-pan | True | False | 1 | 0 | 0.01 | 100 | 0.123 |
| u | 1 | 2 | adsr | False | True | 0 | 1 | 0.06 | 107 | 1.0 |
| u | 1 | 3 | noise | True | False | 0 | 0 | 0.11 | 114 | 1 |
| u | 2 | 1 | fr-pan | True | False | 0 | 1 | 0.02 | 121 | 2.675 |
| u | 2 | 2 | adsr | False | True | -1 | 0 | 0.07 | 128 | 0.0 |
| u | 2 | 3 | noise | True | False | 0 | 1 | 0.12 | 135 | 3.142 |
| u | 3 | 1 | fr-pan | True | False | 0 | 0 | 0.03 | 142 | 0.1 |
| u | 3 | 2 | adsr | False | True | 0 | 1 | 0.08 | 149 | 0.2 |
| u | 3 | 3 | noise | True | False | 1 | 0 | 0.13 | 156 | None |
| gs | 1 | 1 | fr-pan | True | False | 0 | 1 | 0.02 | 163 | 0.123 |
| gs | 1 | 2 | adsr | False | True | 0 | 0 | None | 170 | 1.0 |
| gs | 1 | 3 | noise | True | False | 0 | 1 | 0.12 | 177 | 1 |
| gs | 2 | 1 | fr-pan | True | False | 1 | None | None | 184 | 2.675 |
| gs | 2 | 2 | adsr | False | True | 0 | 1 | 0.09 | 191 | 0.0 |
| gs | 2 | 3 | noise | True | False | 0 | 0 | 0.14 | 198 | 3.142 |
| gs | 3 | 1 | fr-pan | True | False | 0 | 1 | 0.06 | 205 | 0.1 |
| gs | 3 | 2 | adsr | False | True | 1 | 0 | 0.11 | 212 | 0.2 |
| gs | 3 | 3 | noise | True | False | 0 | 1 | 0.16 | 219 | 0.3 |

u: failures 8
gs: failures 7
Gate: incomplete — unscored steps adsr-u-2, adsr-gs-1, fr-pan-gs-2, noise-gs-3 (u - gs = 1 so far)
Item 16: build item 16 (erosion rising in 5 of 5 chains; reference step 3 erosion 0.1)
Item 15: read the transcripts of false_done: adsr-u-1, adsr-u-2, adsr-u-3, adsr-gs-1, adsr-gs-2, adsr-gs-3
Unscored or errored steps: adsr-u-2, adsr-gs-1, fr-pan-gs-2, noise-gs-3
u: median wall 128 s, median cost USD 0.200
gs: median wall 191 s, median cost USD 0.300
