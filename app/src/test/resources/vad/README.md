# Hand-labelled VAD clips

The milestone 6 gate asks for VAD durations within 10% of hand-labelled truth on 10 real clips.
Put 16-bit mono WAV recordings here (any sample rate; they are resampled to 16 kHz) and a
`labels.json` mapping each file name to its labelled speech span in milliseconds:

```json
{ "dhal_attempt_01.wav": { "onsetMs": 420, "offsetMs": 1180 } }
```

`VadTest` runs every labelled clip it finds here and fails if any measured duration is off by more
than 10%. With no clips present the test is skipped and only the synthetic clips are checked.
