# Better MC Screenshots - Player Help

This guide explains how to use the mod in normal gameplay, what each feature does, and how every action button works.

## What this mod does

- Improves screenshot workflow in Minecraft.
- Shows a mini preview after taking a screenshot.
- Adds quick actions directly on screenshots.
- Adds a fullscreen preview with navigation.
- Adds a screenshot gallery screen.
- Adds optional online upload support (Imgur, S3, Custom HTTP/HTTPS, Catbox).

## Basic flow

- Take a screenshot as usual.
- A mini preview appears for a short time.
- Use action buttons on the mini preview if needed.
- Open fullscreen preview for a closer look.
- Open gallery/configuration screens for older screenshots and advanced actions.

## Mini preview behavior

- The preview appears in the configured corner of your screen.
- It stays visible for `previewDurationSeconds`.
- If UI animations are enabled, it uses animated enter/exit transitions.
- If upload is enabled, an upload progress bar can appear on the thumbnail.
- If auto-upload is enabled, upload starts automatically after capture.

## Fullscreen preview behavior

- Opens the current screenshot in a large centered view.
- Supports next/previous navigation (arrows and mouse wheel, depending on context).
- Supports action buttons (copy, upload, delete) in supported contexts.
- Uses animated transitions if enabled.
- Can be read-only in specific contexts (for example config-preview behavior depending on version/rules).

## Gallery behavior

- Shows screenshot thumbnails in a scrollable grid.
- Lets you select screenshots and use action buttons on the selected item.
- Supports opening fullscreen preview from gallery.
- Shows filename overlay on hover for thumbnails.
- Shows upload progress overlay when uploading from gallery.

## Configuration screen behavior

- Lets you change visual/notification settings quickly.
- Shows recent thumbnails and action buttons for them.
- Lets you open uploader configuration.
- Lets you open full gallery.

## Chat notifications and clipboard behavior

- The mod can send screenshot/upload messages in chat (depending on config).
- The uploaded URL can be copied automatically to clipboard.
- If auto-copy is disabled in uploader flow, the chat message can include a copy button (modern-style flow).

## Upload system behavior

- Upload can be disabled globally.
- Providers available: Imgur, S3, Custom HTTP/HTTPS, Catbox.
- Auto-upload can upload screenshots right after capture.
- Progress bar colors:
- Blue while uploading.
- Green on success.
- Red on error.
- A warning icon is shown in uploader config when uploader is enabled (internet-sharing risk warning).

## Action buttons (icons)

| Icon | Name | What it does |
|---|---|---|
| ![Show](../template/resources/assets/better_screenshots/textures/gui/show.png) | Show / Open | Opens the selected screenshot in fullscreen preview. |
| ![Copy](../template/resources/assets/better_screenshots/textures/gui/copy.png) | Copy | Copies the screenshot to clipboard. |
| ![Upload](../template/resources/assets/better_screenshots/textures/gui/upload.png) | Upload | Uploads the screenshot using the currently selected uploader provider. |
| ![Delete](../template/resources/assets/better_screenshots/textures/gui/delete.png) | Delete | Deletes the screenshot file (used in gallery/config/fullscreen contexts where deletion is allowed). |
| ![Close](../template/resources/assets/better_screenshots/textures/gui/close.png) | Close / Hide | Hides the mini preview only (does not delete the screenshot file). |

## Where action buttons are used

- Mini preview:
- `Show`, `Copy`, optional `Upload`, `Close`.
- Important: `Close` here only hides preview.
- Gallery selected thumbnail:
- `Show`, `Copy`, optional `Upload`, `Delete`.
- Config screen selected thumbnail:
- `Show`, `Copy`, optional `Upload`, `Delete`.
- Fullscreen preview:
- `Copy`, optional `Upload`, `Delete`.

## Animation and visual effects

- If animations are enabled, preview and fullscreen transitions are animated.
- Copy action can show a visual flash/frame effect on the image.
- Screen or preview flash mode can be selected in config.

## Troubleshooting

- If screenshot actions do not respond:
- Verify that the correct UI context is active (mini preview vs gallery vs fullscreen).
- Check whether uploader is enabled if upload button is missing.
- If upload fails:
- Re-check provider settings in uploader config.
- For S3/custom providers, verify endpoint, credentials, and URL format.
- If config UI/background effects look wrong:
- Confirm animations mode and flash/chat settings.
- Verify your modpack UI customizations are not overriding screen behavior.
