# Better MC Screenshots - Configuration (`better_screenshots.json`)

This document describes the full JSON config file, default values, and what each option does.

Config file location:
- Fabric: `config/better_screenshots.json`
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
  "screenshotsFirstRowTopMargin": 0,
  "hideMiniPreviewActionButtons": false,
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
  - `REDUCED`: reduced animations (preview-level behavior is still partially animated).
- `chatNotification`: Style for screenshot chat messages.
  - Allowed: `MODERN`, `DEFAULT`, `DISABLED`
- `flashMode`: Visual flash effect mode when taking/copying screenshots.
  - Allowed: `PREVIEW`, `SCREEN`
- `previewDurationSeconds`: How long mini preview stays visible.
  - Integer seconds.
- `menuButtonPosition`: Position of the gallery/config entry button in menus.
  - Allowed: `TOP_RIGHT`, `TOP_LEFT`, `BOTTOM_RIGHT`, `BOTTOM_LEFT`, `DISABLED`
- `screenshotsFirstRowTopMargin`: Extra top margin only for the first row of screenshots in gallery/config grids.
  - Intended for modpack/custom menu layouts.
  - Useful when custom widgets overlap the first row.
- `hideMiniPreviewActionButtons`: Hides mini-preview action buttons and disables their click actions.
  - File-only advanced option for pack makers/custom UIs.
  - Default: `false`

## Uploader options

- `uploadProvider`: Upload backend to use.
  - Allowed: `DISABLED`, `IMGUR`, `S3`, `CUSTOM_HTTP`, `CATBOX`
  - `DISABLED` means uploader is off.
- `uploadAutoUpload`: Automatically upload newly taken screenshots.
- `uploadChatNotification`: Show post-upload notification in chat.
- `uploadCopyToClipboard`: Copy uploaded URL to clipboard.
  - If `uploadChatNotification` is `false`, this value is forced to `true` at load time.

## Imgur provider

- `imgurClientId`: Imgur API Client ID.
- `imgurAccessToken`: Optional OAuth access token (depends on workflow/provider usage).

## S3 provider

- `s3Endpoint`: S3-compatible endpoint URL (AWS S3 or compatible providers).
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
  - Allowed: `POST`, `PUT`
- `customCookieKey`: Cookie name to send.
- `customCookieValue`: Cookie value to send.
- `customHeaderKey`: Custom header name to send.
- `customHeaderValue`: Custom header value to send.
- `customPostKey`: Extra form/body key for POST-style payloads.
- `customPostValue`: Extra form/body value for `customPostKey`.

## Runtime normalization and migration behavior

- If `animationsMode` is missing, it is derived from legacy `animations`.
- Legacy `animations` is then synchronized from `animationsMode`.
- If `menuButtonPosition` is missing, it defaults to `BOTTOM_LEFT`.
- If `uploadProvider` is missing, it defaults to `DISABLED`.
- If `customUploadMethod` is missing, it defaults to `POST`.
- Null upload strings are normalized to empty strings.
- Blank/missing `s3PathPrefix` is normalized to `screenshots/`.

## Notes for pack makers

- `screenshotsFirstRowTopMargin` is intentionally file-only advanced tuning.
- `hideMiniPreviewActionButtons` is also file-only advanced tuning.
- You can ship preconfigured uploader settings in modpacks, but warn users about privacy/security implications of automatic online sharing.
