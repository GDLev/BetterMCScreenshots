# Better MC Screenshots - Player Help

This guide explains how to use the mod in normal gameplay, what each feature does, and how every action button works.

## What this mod does

- Improves screenshot workflow in Minecraft.
- Shows a mini preview after taking a screenshot.
- Adds quick actions directly on screenshots.
- Adds a fullscreen preview with navigation and image actions.
- Adds a screenshot gallery screen.
- Adds optional online upload support (Imgur, S3, Custom HTTP/HTTPS, Catbox).

## Basic flow

- Take a screenshot as usual.
- A mini preview appears for a short time.
- Use action buttons on the mini preview if needed.
- Double-click the mini preview to open fullscreen preview.
- Open gallery/configuration screens for older screenshots and advanced actions.
- Double-click a thumbnail in gallery or configuration to open it in fullscreen preview.

## Mini preview behavior

- The preview appears in the configured corner of your screen.
- It stays visible for `previewDurationSeconds`.
- If UI animations are enabled, it uses animated enter/exit transitions.
- If upload is enabled, an upload progress bar can appear on the thumbnail.
- If auto-upload is enabled, upload starts automatically after capture.
- The mini preview can be opened in fullscreen by double-clicking the image.
- The mini preview no longer has a hide-only close button. Its last action button is `Delete`, which deletes the screenshot file and closes the preview.

## Fullscreen preview behavior

- Opens the current screenshot in a large centered view.
- Supports next/previous navigation with arrows and mouse wheel when a screenshot list is available.
- Supports action buttons (`Copy`, optional `Upload`, `Delete`) in supported contexts.
- Shows a `Close` button on the left side of the image. It closes fullscreen preview just like pressing `Esc`.
- Uses animated transitions if enabled.
- In reduced/read-only fullscreen contexts, the close button remains available, while file actions and navigation arrows are hidden when there is no screenshot list/action context.

## Gallery behavior

- Shows screenshot thumbnails in a scrollable grid.
- Lets you select screenshots and use action buttons on the selected item.
- Supports opening fullscreen preview from the `Show` action button.
- Supports opening fullscreen preview by double-clicking a thumbnail.
- Shows filename overlay on hover for thumbnails.
- Shows upload progress overlay when uploading from gallery.

## Configuration screen behavior

- Lets you change visual/notification settings quickly.
- Shows recent thumbnails and action buttons for them.
- Supports opening fullscreen preview from the `Show` action button.
- Supports opening fullscreen preview by double-clicking a thumbnail.
- Lets you open uploader configuration.
- Lets you open full gallery.

## Chat notifications and clipboard behavior

- The mod can send screenshot/upload messages in chat, depending on config.
- The uploaded URL can be copied automatically to clipboard.
- If auto-copy is disabled in uploader flow, the chat message can include a copy button.

## Upload system behavior

- Upload can be disabled globally.
- Providers available: Imgur, S3, Custom HTTP/HTTPS, Catbox.
- Auto-upload can upload screenshots right after capture.
- Progress bar colors:
  - Blue while uploading.
  - Green on success.
  - Red on error.
- A warning icon is shown in uploader config when uploader is enabled, because uploaded screenshots may become publicly reachable depending on provider/settings.

## Action buttons (icons)

| Icon | Name | What it does |
|---|---|---|
| ![Show](../common/src/main/resources/assets/better_screenshots/textures/gui/show.png) | Show / Open | Opens the selected screenshot in fullscreen preview. |
| ![Copy](../common/src/main/resources/assets/better_screenshots/textures/gui/copy.png) | Copy | Copies the screenshot image to clipboard. |
| ![Upload](../common/src/main/resources/assets/better_screenshots/textures/gui/upload.png) | Upload | Uploads the screenshot using the currently selected uploader provider. |
| ![Delete](../common/src/main/resources/assets/better_screenshots/textures/gui/delete.png) | Delete | Deletes the screenshot file. |
| ![Close](../common/src/main/resources/assets/better_screenshots/textures/gui/close.png) | Close | Closes fullscreen preview, like `Esc`. |

## Where action buttons are used

- Mini preview:
  - `Show`, `Copy`, optional `Upload`, `Delete`.
  - `Upload` is hidden when auto-upload is enabled.
  - `Delete` deletes the screenshot file and closes the mini preview.
- Gallery selected thumbnail:
  - `Show`, `Copy`, optional `Upload`, `Delete`.
- Config screen selected thumbnail:
  - `Show`, `Copy`, optional `Upload`, `Delete`.
- Fullscreen preview:
  - Left side: `Close`.
  - Right side: `Copy`, optional `Upload`, `Delete` when file actions are available.
  - Reduced/read-only fullscreen preview may show only `Close`.

## Other controls

- Double-click mini preview: open fullscreen preview.
- Double-click gallery/config thumbnail: open fullscreen preview.
- `Esc` in fullscreen preview: close fullscreen preview.
- Mouse wheel in fullscreen preview: navigate screenshots when navigation is available.
- Left/right arrow keys in fullscreen preview: navigate screenshots when navigation is available.

## Animation and visual effects

- If animations are enabled, preview and fullscreen transitions are animated.
- Copy action can show a visual flash/frame effect on the image.
- Screen or preview flash mode can be selected in config.

## Troubleshooting

- If screenshot actions do not respond:
  - Verify that the correct UI context is active (mini preview vs gallery vs fullscreen).
  - Check whether uploader is enabled if upload button is missing.
  - In reduced fullscreen preview, only closing may be available.
- If upload fails:
  - Re-check provider settings in uploader config.
  - For S3/custom providers, verify endpoint, credentials, and URL format.
- If config UI/background effects look wrong:
  - Confirm animations mode and flash/chat settings.
  - Verify your modpack UI customizations are not overriding screen behavior.
