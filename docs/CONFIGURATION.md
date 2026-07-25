# Better MC Screenshots - Configuration (`better_screenshots.json`)

This document describes the JSON config file, default values, and what each option does.

Config file location:
- Fabric: `config/better_screenshots.json`
- Forge: `config/better_screenshots.json`
- NeoForge: `config/better_screenshots.json`

Default config:

```json
{
  "corner": "BOTTOM_RIGHT",
  "shutterSound": "SOFT",
  "animations": true,
  "animationsMode": "ON",
  "chatNotification": "MODERN",
  "flashMode": "PREVIEW",
  "previewDurationSeconds": 4,
  "menuButtonPosition": "BOTTOM_LEFT",
  "menuButtonPosition26_2DefaultMigrated": false,
  "screenshotsFirstRowTopMargin": 0,
  "renderTopBar": true,
  "hideMiniPreviewActionButtons": false,
  "actionButtonTooltips": true,
  "pixelatedPreviews": false,
  "miniPreviewShowCorner": "TOP_RIGHT",
  "miniPreviewCopyCorner": "TOP_RIGHT",
  "miniPreviewUploadCorner": "TOP_RIGHT",
  "miniPreviewDeleteCorner": "TOP_RIGHT",
  "miniPreviewShowVisible": true,
  "miniPreviewCopyVisible": true,
  "miniPreviewUploadVisible": true,
  "miniPreviewDeleteVisible": true,
  "miniPreviewShowOrder": 0,
  "miniPreviewCopyOrder": 1,
  "miniPreviewUploadOrder": 2,
  "miniPreviewDeleteOrder": 3,
  "galleryShowCorner": "TOP_RIGHT",
  "galleryCopyCorner": "TOP_RIGHT",
  "galleryUploadCorner": "TOP_RIGHT",
  "galleryDeleteCorner": "TOP_RIGHT",
  "galleryShowVisible": true,
  "galleryCopyVisible": true,
  "galleryUploadVisible": true,
  "galleryDeleteVisible": true,
  "galleryShowOrder": 0,
  "galleryCopyOrder": 1,
  "galleryUploadOrder": 2,
  "galleryDeleteOrder": 3,
  "configMenuShowCorner": "TOP_RIGHT",
  "configMenuCopyCorner": "TOP_RIGHT",
  "configMenuUploadCorner": "TOP_RIGHT",
  "configMenuDeleteCorner": "TOP_RIGHT",
  "configMenuShowVisible": true,
  "configMenuCopyVisible": true,
  "configMenuUploadVisible": true,
  "configMenuDeleteVisible": true,
  "configMenuShowOrder": 0,
  "configMenuCopyOrder": 1,
  "configMenuUploadOrder": 2,
  "configMenuDeleteOrder": 3,
  "fullscreenCloseCorner": "TOP_LEFT",
  "fullscreenCopyCorner": "TOP_RIGHT",
  "fullscreenUploadCorner": "TOP_RIGHT",
  "fullscreenDeleteCorner": "TOP_RIGHT",
  "fullscreenCloseVisible": true,
  "fullscreenCopyVisible": true,
  "fullscreenUploadVisible": true,
  "fullscreenDeleteVisible": true,
  "fullscreenCloseOrder": 0,
  "fullscreenCopyOrder": 1,
  "fullscreenUploadOrder": 2,
  "fullscreenDeleteOrder": 3,
  "pauseButtonLayoutMigrated": false,
  "pauseSettingsAnchor": "CENTER",
  "pauseGalleryAnchor": "CENTER",
  "pauseScreenshotAnchor": "CENTER",
  "pauseSettingsVisible": true,
  "pauseGalleryVisible": true,
  "pauseScreenshotVisible": true,
  "pauseSettingsOrder": 0,
  "pauseGalleryOrder": 1,
  "pauseScreenshotOrder": 2,
  "pause26_1ButtonLayoutMigrated": false,
  "pause26_1SettingsCorner": "BOTTOM_LEFT",
  "pause26_1GalleryCorner": "BOTTOM_LEFT",
  "pause26_1ScreenshotCorner": "BOTTOM_LEFT",
  "pause26_1SettingsVisible": true,
  "pause26_1GalleryVisible": true,
  "pause26_1ScreenshotVisible": true,
  "pause26_1SettingsOrder": 0,
  "pause26_1GalleryOrder": 1,
  "pause26_1ScreenshotOrder": 2,
  "uploadProvider": "DISABLED",
  "uploadAutoUpload": false,
  "uploadChatNotification": true,
  "uploadCopyToClipboard": true,
  "imgurClientId": "",
  "imgurAccessToken": "",
  "s3Endpoint": "",
  "s3Region": "",
  "s3Bucket": "",
  "s3AccessKey": "",
  "s3SecretKey": "",
  "s3PathPrefix": "screenshots/",
  "customUploadUrl": "",
  "customUploadMethod": "POST",
  "customUploadBodyType": "MULTIPART",
  "customFileField": "file",
  "customResponseUrlJsonPath": "",
  "customFallbackUrl": "",
  "customCookieKey": "",
  "customCookieValue": "",
  "customHeaderKey": "",
  "customHeaderValue": "",
  "customPostKey": "",
  "customPostValue": ""
}
```

## General UI and behavior options

- `corner`: Where the mini preview appears on screen.
  - Allowed: `BOTTOM_RIGHT`, `BOTTOM_LEFT`, `TOP_RIGHT`, `TOP_LEFT`
- `shutterSound`: Screenshot shutter sound profile.
  - Allowed: `NONE`, `SOFT`, `CLASSIC`
- `animations`: Legacy compatibility field.
  - Kept for old configs. Internally synced with `animationsMode`.
- `animationsMode`: Controls UI animations globally.
  - Allowed: `ON`, `OFF`, `REDUCED`
  - `ON`: full animations.
  - `OFF`: no preview/fullscreen animations.
  - `REDUCED`: reduced animations.
- `chatNotification`: Style for screenshot chat messages.
  - Allowed: `MODERN`, `DEFAULT`, `DISABLED`
- `flashMode`: Visual flash effect mode when taking/copying screenshots.
  - Allowed: `PREVIEW`, `SCREEN`
- `previewDurationSeconds`: How long mini preview stays visible.
  - Integer seconds.
- `menuButtonPosition`: Position of the gallery/config entry buttons in game menus.
  - Allowed: `CENTER`, `TOP_RIGHT`, `TOP_LEFT`, `BOTTOM_RIGHT`, `BOTTOM_LEFT`, `DISABLED`
  - `CENTER` is used for Minecraft 26.2-style menu button rows.
- `screenshotsFirstRowTopMargin`: Extra top margin only for the first row of screenshots in gallery/config grids.
  - Intended for modpack/custom menu layouts.
  - Useful when custom widgets overlap the first row.
- `renderTopBar`: Whether the gallery renders its top bar/container behind Back, Sort, and Folder controls.
  - File-only advanced option.
  - Set to `false` when another menu mod provides its own header/background.
- `hideMiniPreviewActionButtons`: Hides mini-preview action buttons and disables their click actions.
  - File-only advanced option for pack makers/custom UIs.
- `actionButtonTooltips`: Shows tooltips for action buttons.
- `pixelatedPreviews`: Controls preview scaling style in gallery/config thumbnails.
  - `false`: smoother thumbnails.
  - `true`: pixelated thumbnails.

## Gallery behavior

- The gallery sort button cycles through newest first, oldest first, A-Z, and Z-A.
- The Folder button opens Minecraft's `screenshots` directory.
- Screenshot names can be edited from the gallery by using the edit icon next to the timestamp/name.
  - Enter accepts the new file name.
  - Escape cancels editing.
  - The file is renamed on disk.
  - If a target name already exists, a numeric suffix is added.

## Action button layout fields

Action buttons use three related values:
- `*Corner`: The corner where an action button appears.
- `*Visible`: Whether that action button is enabled in that surface.
- `*Order`: Ordering inside the same corner. Lower values are placed first from the corner toward the center.

Allowed values for `*Corner`:
- `TOP_LEFT`
- `TOP_RIGHT`
- `BOTTOM_LEFT`
- `BOTTOM_RIGHT`

The configured action names are:
- `Show`: opens fullscreen preview.
- `Copy`: copies the screenshot.
- `Upload`: uploads the screenshot when an uploader is configured.
- `Delete`: deletes the screenshot.
- `Close`: closes fullscreen preview.

Surface prefixes:
- `miniPreview*`: action buttons shown on the small screenshot preview.
- `gallery*`: action buttons shown on screenshot thumbnails in the gallery.
- `configMenu*`: action buttons shown on thumbnails inside the configuration screen.
- `fullscreen*`: action buttons shown in fullscreen preview.

For the configuration screen preview, only the top-left and top-right corners are used visually.

## Pause menu button layout fields

Minecraft 26.2-style pause menus support center-row and corner placement:
- `pauseSettingsAnchor`
- `pauseGalleryAnchor`
- `pauseScreenshotAnchor`

Allowed values:
- `CENTER`
- `TOP_LEFT`
- `TOP_RIGHT`
- `BOTTOM_LEFT`
- `BOTTOM_RIGHT`

Each pause menu button also has:
- `pauseSettingsVisible`, `pauseGalleryVisible`, `pauseScreenshotVisible`
- `pauseSettingsOrder`, `pauseGalleryOrder`, `pauseScreenshotOrder`

Older menu layouts use the `pause26_1*` fields:
- `pause26_1SettingsCorner`, `pause26_1GalleryCorner`, `pause26_1ScreenshotCorner`
- `pause26_1SettingsVisible`, `pause26_1GalleryVisible`, `pause26_1ScreenshotVisible`
- `pause26_1SettingsOrder`, `pause26_1GalleryOrder`, `pause26_1ScreenshotOrder`

## Uploader options

- `uploadProvider`: Upload backend to use.
  - Allowed: `DISABLED`, `IMGUR`, `S3`, `CUSTOM_HTTP`, `CATBOX`, `IMMICH`, `EXTERNAL_CUSTOM`
  - `DISABLED` means uploader is off.
- `uploadAutoUpload`: Automatically upload newly taken screenshots.
- `uploadChatNotification`: Show post-upload notification in chat.
- `uploadCopyToClipboard`: Copy uploaded URL to clipboard.
  - If `uploadChatNotification` is `false`, this value is forced to `true` at load time.
- Custom uploader profiles are loaded from `config/better_screenshots_uploaders/*.json`.
  - See [CUSTOM_UPLOADERS.md](CUSTOM_UPLOADERS.md) for the full profile format and examples.

## Imgur provider

- `imgurClientId`: Imgur API Client ID.
- `imgurAccessToken`: Optional OAuth access token.

## S3 provider

- `s3Endpoint`: S3-compatible endpoint URL.
- `s3Region`: Bucket region.
- `s3Bucket`: Bucket name.
- `s3AccessKey`: Access key ID.
- `s3SecretKey`: Secret key.
- `s3PathPrefix`: Key prefix/folder for uploaded screenshots.
  - Default: `screenshots/`
  - If blank or missing, it is normalized back to `screenshots/`.

## Custom HTTP/HTTPS provider

- `customUploadUrl`: Full upload endpoint URL.
- `customUploadMethod`: HTTP method.
  - Allowed: `POST`, `PUT`, `PATCH`
- `customUploadBodyType`: Upload body format.
  - Allowed: `MULTIPART`, `RAW_PNG`
- `customFileField`: Multipart field name used for the screenshot file.
- `customResponseUrlJsonPath`: Dot-separated JSON path used to extract the uploaded URL from the response.
- `customFallbackUrl`: URL returned when the response does not contain a usable URL.
  - Supports upload placeholders such as `{filename}`, `{sha1}`, and `{response.id}`.
- `customCookieKey`: Cookie name to send.
- `customCookieValue`: Cookie value to send.
- `customHeaderKey`: Custom header name to send.
- `customHeaderValue`: Custom header value to send.
- `customPostKey`: Extra form/body key for POST-style payloads.
- `customPostValue`: Extra form/body value for `customPostKey`.

## Runtime normalization and migration behavior

- If `animationsMode` is missing, it is derived from legacy `animations`.
- Legacy `animations` is synchronized from `animationsMode`.
- If `menuButtonPosition` is missing, it defaults to `CENTER` on Minecraft 26.2 and to `BOTTOM_LEFT` on older versions.
- On Minecraft 26.2, an unmigrated `BOTTOM_LEFT` menu button position is migrated to `CENTER`.
- On older Minecraft versions, `CENTER` is normalized back to `BOTTOM_LEFT`.
- If `uploadProvider` is missing, it defaults to `DISABLED`.
- If `customUploadMethod` is missing, it defaults to `POST`.
- Null upload strings are normalized to empty strings.
- Blank/missing `s3PathPrefix` is normalized to `screenshots/`.

## Notes for pack makers

- `screenshotsFirstRowTopMargin` is intentionally file-only advanced tuning.
- `renderTopBar` is also file-only and is useful for FancyMenu/custom gallery headers.
- `hideMiniPreviewActionButtons` is file-only advanced tuning.
- The `*Migrated` fields are internal migration flags. They should normally be left alone.
- You can ship preconfigured uploader settings in modpacks, but warn users about privacy/security implications of automatic online sharing.
