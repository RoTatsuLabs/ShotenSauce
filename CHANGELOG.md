# Changelog

## Unreleased

### New
- Personal notes on a title, opened from the details menu on their own screen: a Markdown editor with a formatting toolbar (bold, italic, strikethrough, headings, lists, quote, code, link, undo and redo) and a preview
- Title details show the type (Manga, Manhwa, Manhua, Webtoon...) in an info grid with rating, language and last update, and a card lists every alternative title
- Track untracked favourites can be run again any time: also search titles that need review again, or send progress for every favourite again

### Improved
- Title details redesigned: cover and title on top with the Favourite button under them, a progress card beside chapters and last update, genres inside the description card, and the Tracking card at the bottom
- Tracking options: the running "Track untracked favourites" pass has a Cancel button
- Downloads stop retrying dead or forbidden links, spread retries out with a little randomness and check for free space before starting a chapter
- Bookmarks can be labelled with a colour and filtered by it
- Smart categories in Tracking options: bind each tracker status to a category and favourites move there when the status changes
- The library database is checked at start; a damaged one is set aside and replaced by the newest of the last three daily snapshots, with a notification
- Find duplicates in the Favourites menu lists titles you saved more than once and keeps the copy you choose
- Every backup and restore (local, Mihon import and export, scheduled, Telegram, onboarding) now runs in the background with a progress notification, a cancel button and a result notification; progress and cancel also show on the Backup screen, and only one runs at a time
- Title details: the chapter list button sits left of Continue in the bottom bar
- Title details: wide (panorama) covers keep their shape beside the title instead of being cropped to portrait; can be turned off in Appearance
- Title details take their accent colour from the cover (Appearance, Colours from the cover)
- New Time format setting in Appearance: follow the system, or force 12 or 24 hours (reader clock, statistics)
- Restoring a Mihon backup runs in the background with a progress notification and bar, and keeps going when you leave the screen; exporting to Mihon no longer stops either
- Tracking untracked favourites shows a progress bar on its screen, a visible notification and a result notification
- Telegram backups have their own frequency, separate from the device backup
- Needs review: a summary with the count and what to do, tracker filter chips, readable source names and tidier rows
- The app id is now com.rotatsulabs.shotensauce so it installs next to DropSauce; move your library with a backup and restore. The F-Droid build is gone
- The type of a tracked title also comes from AniList, MyAnimeList and Kitsu (country of origin or format), which is more reliable than a source's genres
- Chapter volumes from extensions ("Vol.2 Ch.10") are recognised, so the chapter list groups them by volume when every chapter names one
- Titles that an extension marks as fetch-once are no longer re-checked for new chapters, like in Mihon
- Extension titles keep their alternative titles, which also helps duplicate detection, search and tracker matching
- The type is no longer mixed into the genre chips
- Tracking settings moved to their own "Tracking options" screen with Progress and Actions sections, and Pause stale titles shows its number of days on the same row
- The tracker sync report is removed, since progress already follows your reading and refreshes
- Needs review is a full screen list with covers and full titles, grouped by tracker

### Removed
- Google Drive sync, with its settings entry, sign-in step in the welcome screen and Google Play Services dependency

## 1.1.5 (2026-11-20)

### New
- Smart reading status: a tracker title moves to Reading when you read it, comes back from On hold or Dropped, becomes Re-reading when you restart it and Completed at the last chapter (switch in Services)
- Tracker start and finish dates for AniList, MyAnimeList, Kitsu and MangaBaka
- Pause stale titles: Reading titles you have not opened for 30 to 180 days move to On hold (off by default)
- Tracker sync report in Services, with Sync all
- "Link all to nearest entry" in Needs review
- Blur sensitive covers: 18+ titles and the genres you pick are blurred, and holding a cover lets you peek
- Downloads redesign: Active, Failed and Completed tabs, filters, sorting, a menu on every row, queue reordering and a storage card
- Downloads can wait for the charger, retry failed pages with growing delays and use a speed per source
- Pick your own font (.ttf or .otf) in Settings > Appearance > Font
- Pick your app icon: default, mono, or one that matches your theme (settings > appearance)
- Search several genres at once, and add your own genre chips
- Up to 6 sections in the bottom bar
- Show only manga or only novels in Explore (just hold the tab)
- Sleep timer for text to speech
- Retry all failed downloads in one go
- Refreshing a comic also sends your progress to trackers that are behind
- The tracker sheet shows the score in the tracker's own scale next to the stars, and you can tap it to type an exact score

### Improved
- The app is now called ShotenSauce, and the Themed and Mono icons use the new logo
- Titles already in your favourites under another source get a duplicate icon and that source's logo on the cover
- Download settings are regrouped into Storage, Network and speed, and Speed and retries
- Frosted glass is removed
- More of the app is translated to Indonesian
- Synced with DropSauce 1.1.5
- PDFs open instantly, even huge ones, and don't take up extra space
- Genre search gives much better results, the same way Mihon does it
- Migrate any extension, not just broken ones, and choose where to look
- Alternatives finds a lot more matches
- Better on tablets and in landscape: chapters sit next to the details, and the continue button shows everywhere
- The favorite button is back to one simple tap

### Fixed
- Secret space no longer adds manga shortcuts to the launcher
- Restoring a Mihon backup no longer crashes
- Auto-track finds titles with quotes, backticks and other symbols
- Tracker messages in Services show in a dialog instead of a snackbar
- Kotatsu backups restore properly from around 90 sources like MangaDex, Webtoons and Asura
- Deleting one chapter of an imported folder no longer deletes the whole thing
- Folders with both comics and EPUBs now import both
- Search suggestions are back
- Plenty of download, reader and other small fixes

## 1.1.4 (2026-11-15)

This is a small update you can skip: it is mainly there to get the app onto F-Droid, with some small UI changes.

### Improved
- Redesigned the display options menu and the reader options menu for manga and novels
- Two page layout options moved to settings > reader, and now on by default
- Backups now include pinned sources, extension settings, saved filters, novel plugin settings and every manga's reader mode

### Fixed
- Bookmarking a page again after removing its bookmark failing with an error
- Random crash when the app was reopened in the background
- Backups missing source settings and chapters, and restores stopping early on a damaged entry
- Backups no longer carry device-only settings like storage folders or the Discord login

## 1.1.3 (2026-11-13)

### New
- JPEG XL image support (fixes sources that use it, like Kagane)
- Quick add to default category button on the details page (hold it to change or disable)
- Option to split wide pages manually with a button instead of automatically (settings > reader > fit wide pages)
- Extensions per row option (settings > extensions)

### Improved
- Refreshed settings design with visual previews for theme, list layout, and reader mode
- Smooth fade transition when switching between light and dark theme
- Faster, lower-memory loading for AVIF and JPEG XL pages
- Pull to refresh in extensions now fully reloads the list
- Sort order and chapter sort menus are now submenus
- Smoother description expand and collapse animation on the details page
- Outlined popups

### Fixed
- Excluded genres showing up in suggestions
- OLED dark mode backgrounds
- EPUB dictionary popup not expanding or scrolling properly
- Other small bug fixes
