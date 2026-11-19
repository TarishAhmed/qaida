# Changelog

All builds so far are previews on placeholder content (`in.qaida.app.preview`).

## 0.9.0
- Real letter pack: 29 letters from Unicode, look-alike tiers derived from the font, sound-alike sets from the spec
- 66 letter recordings by Ruaa GHAREEB (Wikimedia Commons, CC BY-SA 4.0), not yet verified by a fluent reader
- Settings > Check the recordings: a review screen for a fluent reader; release builds require every clip verified
- Previews now run on the real pack; debug builds keep the generated test pack

## 0.8.0
- Accessibility: answer tiles read as numbered options (never the letter), results announced, every control labelled; a session is completable with TalkBack alone
- Launcher icon; light and true-dark window backgrounds
- Reproducible build (verified in CI by building twice); no dependency-metadata block; pinned Gradle checksum
- F-Droid metadata and draft build recipe

## 0.7.0
- Letter detail, Progress (heat grid, weakest letters, export/import), Settings
- Malayalam interface; UI and spoken-instruction languages chosen independently of the phone
- Optional daily reminder, off by default

## 0.6.0
- Speaking practice: record, compare with the reference, self-rate; never scored
- Hand-written signal processing: FFT, MFCC, voice activity detection, DTW
- Boss lines: a real line of text once a tier is complete

## 0.5.0
- All five listening and reading card types; support that fades as recall grows
- Home mastery grid and tier unlocking

## 0.3.0
- First preview: listening sessions with spaced review
