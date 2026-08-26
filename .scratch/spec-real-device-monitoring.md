## Problem Statement

A Parent cannot yet run Family Guard on the real phones they own. The Child sees a Cover and the Parent has a dashboard, but both apps still talk to an emulator loopback address, HyperOS will kill background Monitoring, Usage does not show installed apps that were never opened, and Captured notifications are truncated, duplicated, and cannot be deleted. The Parent needs a debug backend reachable from the phones and two installable apps so they can pair one Child Device (Redmi 14C) with one Parent phone (Redmi Note 15 Pro 5G), watch Monitoring data, and scold the Child themselves. Blocking is not the problem; seeing what happened is.

## Solution

Keep Cover + Monitoring as they are conceptually: one Family, one Parent, one Child Device. Start the backend in debug with a public tunnel (ngrok) and rebuild both apps whenever that URL changes. On the Child Device, request OS and OEM settings (including autostart and battery) by opening the real settings screens, not by silent grants. Sync launcher apps into Usage with time (zero means installed, not opened). Persist every distinct Captured notification (app, title, text, time) with no list cap; skip identical copies that share the same time; let the Parent delete one or all. Hand the Parent two debug APKs for 14C and Note 15 Pro 5G.

## User Stories

1. As a Parent, I want to register a Family, so that I receive a Pairing code.
2. As a Parent, I want to log in again, so that I can open the dashboard on the same Family.
3. As a Parent, I want to see the Pairing code on the dashboard, so that I can type it on the Child Device.
4. As a Child, I want to enter the Pairing code, my name, and a Chat PIN, so that my Device joins the Family.
5. As a Child, I want to see only Cover (weather for Tashkent) after pairing, so that Monitoring stays hidden.
6. As a Child, I want Cover weather to keep working without GPS, so that the decoy stays simple.
7. As a Child, I want to long-press the temperature, so that I can enter the Chat PIN.
8. As a Child, I want a wrong Chat PIN to stay on Cover, so that a sibling cannot open chat by guessing poorly.
9. As a Child, I want a correct Chat PIN to open hidden chat with the Parent, so that we can message without a visible chat app.
10. As a Parent, I want to send and receive chat messages, so that I can talk to the Child through the hidden channel.
11. As a Parent, I want to see whether the Child Device is online and last seen, so that I know Monitoring is alive.
12. As a Parent, I want to see Wi-Fi name when the Device reports it, so that I have a coarse whereabouts signal without live location.
13. As a Parent, I want Usage for today, so that I know which apps the Child used and for how long.
14. As a Parent, I want launcher apps that were never opened to appear in Usage with zero time, so that I know what is installed.
15. As a Parent, I do not want a long list of system packages in Usage, so that I can scan what the Child can actually open.
16. As a Child Device agent, I want to sync Usage including those launcher apps, so that the Parent’s list is complete.
17. As a Parent, I want to read SMS synced from the Child Device, so that I can see who the Child is texting.
18. As a Parent, I want to see gallery images synced from the Child Device, so that I can review photos.
19. As a Parent, I want every Captured notification stored (app, title, text, posted time), so that I can review what appeared on the Child status bar.
20. As a Parent, I want Captured notifications without a 200-item cut-off in the dashboard, so that older items are not silently dropped.
21. As a Parent, I do not want the same Captured notification stored twice when it arrived at the same time with the same app, title, and text, so that the list stays readable.
22. As a Parent, I want to delete one Captured notification, so that I can remove a single item I no longer need.
23. As a Parent, I want a clear-all action for Captured notifications, so that I can wipe the history after reviewing.
24. As a Parent, I want delete actions to persist on the server, so that polling does not bring deleted items back.
25. As a Parent, I want Captured notifications to show time, so that I know when something appeared.
26. As a Parent, I do not need icons or screenshots of Captured notifications, so that the first version stays small.
27. As a Parent, I do not need push on my own phone, so that I open the Parent app and look (I will scold in person).
28. As a Child, I want runtime permissions requested, so that SMS, gallery, location permission (unused for weather), and notifications can be granted.
29. As a Child, I want Usage Access settings opened, so that Usage can be collected.
30. As a Child, I want Notification Listener settings opened, so that Captured notifications can be collected.
31. As a Child, I want battery-optimization exemption requested, so that the agent is less likely to sleep.
32. As a Child, I want HyperOS autostart settings opened, so that I can tap allow myself.
33. As a Parent setting up the 14C, I want those OEM screens reachable from the Child app, so that I am not hunting menus blindly.
34. As a Child, I want the foreground “family protection” service to keep syncing while Cover is on screen, so that Monitoring continues.
35. As a Child, I want sync after reboot if the OS allows the service to start, so that Monitoring resumes.
36. As a developer, I want debug backend to start Postgres, the API with reload, and ngrok, so that phones on cellular can reach it.
37. As a developer, I want the ngrok public URL printed, so that I can put it in both apps and rebuild.
38. As a developer, I want both apps to use that URL instead of emulator loopback, so that 14C and Note 15 Pro 5G can pair.
39. As a Parent, I want a debug APK for the Parent app, so that I can install it on Redmi Note 15 Pro 5G.
40. As a Parent, I want a debug APK for the Child app, so that I can install it on Redmi 14C.
41. As a Parent, I want both APKs to install on those two phones without being locked to those models, so that the same build is not a hardware whitelist.
42. As a Parent, I want one Child Device for this Family, so that the first test is a single 14C.
43. As a Parent, I want Cover to stay weather-only, so that the Child does not see a control panel.
44. As a Parent, I want Monitoring only (no app or site blocking), so that I decide when to scold.
45. As a Parent, I want existing dashboard tabs (home, chat, Usage, SMS, Captured notifications, gallery) to keep working against the tunneled API, so that I can demo the full MVP on hardware.
46. As a Child, I want my own Cover notification channel ignored when capturing, so that the agent does not record itself.
47. As a Parent, I want heartbeat to update last-seen, so that “online” is meaningful during the demo.
48. As a developer, I want a later production server to be a URL change plus APK rebuild, so that ngrok stays a temporary debug path.
49. As a Parent, I want pairing to fail clearly on a wrong Pairing code, so that I know to retype.
50. As a Parent, I want tokens stored on each phone after pairing or login, so that I do not register every launch.
51. As a Parent, I want to log out of the Parent app, so that I can leave the dashboard.
52. As a tester on 14C, I want cleartext HTTP to the ngrok URL allowed if the tunnel is HTTP, so that debug is not blocked by cleartext policy.
53. As a tester, I want HTTPS ngrok URLs to work too, so that either tunnel mode is fine once baked into the build.
54. As a Parent, I want Usage labels (human app names) next to package names where the Child can read them, so that I recognize apps.
55. As a Parent, I want Captured notification text empty if the OS omitted it, so that title-only items still appear.
56. As a Parent, I want delete-one to be forbidden for a Child token, so that only the Parent can wipe history.
57. As a Parent, I want clear-all to affect only my Family, so that another Family’s data is untouched.
58. As a Child agent, I want sync of Captured notifications to skip duplicates already stored for that Device, so that retries do not explode the list.
59. As a Parent, I want the notifications tab to refresh, so that new Captured notifications appear without reinstalling.
60. As a Parent, I want Usage to refresh, so that newly installed launcher apps appear after the next Child sync.

## Implementation Decisions

- Test and persist new Monitoring behavior at the existing HTTP API seam (the same interface both apps already use). Do not add a second test seam for Compose screens or OEM settings.
- Debug run: one operator entry that brings up Postgres, the API with reload on all interfaces, and ngrok to the API port, then prints the public base URL.
- Both Android apps keep a compile-time API base URL. When the ngrok URL changes, both are rebuilt. No in-app URL field in this spec.
- Ngrok is debug-only; a later real server is the same contract with a new base URL and new APKs.
- Family model stays one Parent + one Child Device for this delivery.
- Usage GET/POST: include launcher (user-openable) apps for the Device’s day. `total_ms` of 0 means installed and not used that day. Do not sync the full system-package dump.
- Captured notification fields remain: package name, title, text, posted time. No icon, screenshot, or extra bundles.
- Captured notification list for the Parent is not capped at 200; return the full Family history (newest first). If the payload is large, still no silent truncation in this spec (pagination is not required if the list fits a single response; if a hard technical cap is unavoidable, it must be far above 200 and documented—prefer uncapped for the stated product rule).
- Dedupe key for Captured notifications: same Device, same package name, same title, same text, same posted time. A sync item matching an existing row is ignored.
- New Parent-only HTTP operations: delete one Captured notification by id; delete all Captured notifications for the Parent’s Family. Child tokens receive 403.
- Notification listener still records posts except the Child app’s own packages. Progress-style updates that share the same posted time and same fields collapse via dedupe; distinct times remain separate rows.
- Child permission UX: request runtime permissions; send the user to Usage Access, Notification Listener, ignore-battery-optimizations, and the HyperOS autostart (or equivalent OEM) screen. No silent OEM grant.
- Cover weather stays hardcoded Tashkent coordinates.
- Apps are not model-locked. Target the 14C and Note 15 Pro 5G as the test devices; do not special-case other phones.
- FCM stays unused. Parent discovers data by opening the app (existing polling).
- Uninstall protection / Device Owner is not implemented.
- Existing auth, chat, SMS, gallery, heartbeat, and dashboard summary remain; they must work against the tunneled base URL.

## Testing Decisions

- Good tests exercise HTTP behavior only: status codes, JSON bodies, and persistence across requests. They do not inspect Android views, OEM settings, or ngrok internals.
- The module under test is the HTTP API (auth as needed, then Usage sync/list, Captured notification sync/list/delete-one/delete-all, and Family isolation).
- There is no prior automated test suite in this repo; add API tests at that seam (in-process or HTTP against a test app with a test database).
- Manual only (out of automated tests): Cover, Chat PIN, HyperOS taps, APK install on 14C and Note 15 Pro 5G, ngrok reachability.

## Out of Scope

- App, site, or Play Store blocking; VPN; kiosk; Device Owner; uninstall prevention.
- Push / FCM to the Parent phone; “bad content” classifiers.
- GPS weather, live location, screen streaming.
- Multiple Child Devices per Family.
- In-app API URL editor; reserved ngrok domain as a product requirement.
- Production server deploy (only the debug tunnel + later rebuild assumption).
- Captured notification icons, screenshots, or extra platform fields.
- Per-item filters for notification progress spam beyond same-time exact-field dedupe.
- Model/OEM lockouts; any work aimed at phones other than 14C (Child test) and Note 15 Pro 5G (Parent test).

## Further Notes

- Glossary: Family, Parent, Child, Cover, Pairing code, Chat PIN, Device, Monitoring, Usage, Captured notification — see `CONTEXT.md`.
- Parent will scold in person after reading Monitoring; the product does not intervene.
- After “qil”, deliverable is debug backend + two debug APKs with the printed ngrok base URL baked in.
