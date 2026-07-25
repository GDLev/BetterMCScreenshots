# Better MC Screenshots - Custom Uploader Profiles

Custom uploader profiles let you add upload targets without changing the mod code. Profiles are loaded from:

```text
config/better_screenshots_uploaders/*.json
```

The folder is created automatically when uploader profiles are loaded. Every `.json` file in that folder is treated as a profile, except files whose name starts with `_`.

To use a profile in game:

1. Open the uploader configuration screen.
2. Set the upload provider to `External Profile`.
3. Select the profile by its `name`.

## Profile Format

```json
{
  "name": "My uploader",
  "url": "https://example.com/upload",
  "method": "POST",
  "bodyType": "MULTIPART",
  "fileField": "file",
  "headers": [
    { "key": "Authorization", "value": "Bearer YOUR_TOKEN" }
  ],
  "formFields": [
    { "key": "filename", "value": "{filename}" }
  ],
  "responseUrlJsonPath": "url",
  "responseUrlHeader": "",
  "fallbackUrl": "https://example.com/files/{filename}",
  "afterUploadRequests": []
}
```

## Fields

- `name`: Display name shown in the in-game profile selector. Required.
- `url`: Upload endpoint URL. Required.
- `method`: HTTP method for the upload request.
  - Allowed: `POST`, `PUT`, `PATCH`
  - Default: `POST`
- `bodyType`: Upload body format.
  - Allowed: `MULTIPART`, `RAW_PNG`
  - Default: `MULTIPART`
- `fileField`: Multipart field name used for the screenshot file.
  - Default: `file`
  - Used only when `bodyType` is `MULTIPART`.
- `headers`: Extra HTTP headers.
- `formFields`: Extra multipart form fields.
  - Used only when `bodyType` is `MULTIPART`.
- `responseUrlJsonPath`: Dot-separated path to the uploaded URL in the JSON response, for example `data.url`.
- `responseUrlHeader`: Header name that contains the uploaded URL.
- `fallbackUrl`: URL returned when no URL can be extracted from the response.
- `afterUploadRequests`: Optional extra requests executed after the main upload succeeds.

For `RAW_PNG`, the screenshot bytes are sent directly as the request body with `Content-Type: image/png`.

For `MULTIPART`, the mod sends the screenshot as multipart/form-data under `fileField` and appends every entry from `formFields`.

## Placeholders

Placeholders can be used in `url`, header values, form field values, `fallbackUrl`, and follow-up request bodies.

- `{filename}` or `{fileName}`: Screenshot file name.
- `{timestamp}`: Current Unix timestamp in milliseconds.
- `{isoNow}`: Current time in ISO-8601 format.
- `{isoModified}`: Screenshot file modification time in ISO-8601 format.
- `{uuid}`: Random UUID generated for this upload.
- `{sha1}`: SHA-1 hash of the screenshot bytes.
- `{sha256}`: SHA-256 hash of the screenshot bytes.
- `{response.path}`: Value from the main upload JSON response. For example `{response.id}` or `{response.data.url}`.

`{response.path}` placeholders are available only after the main upload request, so they are intended for `afterUploadRequests` and `fallbackUrl`.

Gameplay placeholders are filled only when the screenshot is uploaded while the player is in-world without an open GUI screen. Screenshots taken from menus/config screens use empty values.

- `{worldName}` or `{world}`: Singleplayer world name.
- `{serverName}` or `{server}`: Multiplayer server display name.
- `{serverAddress}` or `{serverIp}`: Multiplayer server address.
- `{dimension}`: Current dimension ID, for example `minecraft:overworld`.
- `{seed}`: Singleplayer world seed when available. Usually empty on multiplayer servers.
- `{x}`, `{y}`, `{z}`: Player block coordinates.
- `{blockX}`, `{blockY}`, `{blockZ}`: Player block coordinates.
- `{playerX}`, `{playerY}`, `{playerZ}`: Player exact coordinates with two decimal places.
- `{coords}`: Player block coordinates as `x,y,z`.
- `{worldTime}`: Total elapsed world days.
- `{dayTime}`: Current day time in ticks.
- `{timeOfDay}`: Minecraft clock time formatted as `HH:mm`.

## Response URL Resolution

After the upload succeeds, the mod decides which URL to copy/show in this order:

1. `responseUrlHeader`, if it is set and the response contains that header.
2. `responseUrlJsonPath`, if it is set and resolves to a primitive JSON value.
3. First URL found automatically in the response body.
4. `fallbackUrl`, if it is set.
5. The upload `url`.

If `responseUrlHeader` resolves successfully, the URL is returned immediately. Otherwise, `afterUploadRequests` are executed before returning the URL from `responseUrlJsonPath`, automatic URL detection, `fallbackUrl`, or the upload `url`.

## Follow-Up Requests

Follow-up requests are useful for APIs that need a second call after upload, for example adding an uploaded asset to an album.

```json
{
  "name": "Add to album",
  "url": "https://example.com/api/albums/assets",
  "method": "PUT",
  "bodyType": "JSON",
  "headers": [
    { "key": "Authorization", "value": "Bearer YOUR_TOKEN" }
  ],
  "body": "{\"albumId\":\"my-album\",\"assetId\":\"{response.id}\"}"
}
```

Follow-up fields:

- `name`: Name used in error messages.
- `url`: Follow-up endpoint URL.
- `method`: `POST`, `PUT`, or `PATCH`.
- `bodyType`: Follow-up body format.
  - `JSON`: Sends `body` as JSON.
  - `FORM`: Sends `formFields` as `application/x-www-form-urlencoded`.
  - `EMPTY`: Sends no request body.
- `headers`: Extra HTTP headers.
- `formFields`: Form fields for `FORM` requests.
- `body`: JSON body for `JSON` requests.

## Immich Example

This example uploads to Immich and then adds the uploaded asset to an album.

```json
{
  "name": "Immich",
  "url": "http://IP_ADDRESS:2283/api/assets",
  "method": "POST",
  "bodyType": "MULTIPART",
  "fileField": "assetData",
  "headers": [
    { "key": "Accept", "value": "application/json" },
    { "key": "x-api-key", "value": "YOUR_API_KEY" }
  ],
  "formFields": [
    { "key": "deviceAssetId", "value": "{sha1}" },
    { "key": "deviceId", "value": "better-mc-screenshots" },
    { "key": "fileCreatedAt", "value": "{isoModified}" },
    { "key": "fileModifiedAt", "value": "{isoModified}" },
    { "key": "filename", "value": "{filename}" },
    { "key": "isFavorite", "value": "false" }
  ],
  "fallbackUrl": "http://IP_ADDRESS:2283/photos/{response.id}",
  "afterUploadRequests": [
    {
      "name": "Add to Immich album",
      "url": "http://IP_ADDRESS:2283/api/albums/assets",
      "method": "PUT",
      "bodyType": "JSON",
      "headers": [
        { "key": "Accept", "value": "application/json" },
        { "key": "x-api-key", "value": "YOUR_API_KEY" }
      ],
      "body": "{\"albumIds\":[\"YOUR_ALBUM_ID\"],\"assetIds\":[\"{response.id}\"]}"
    }
  ]
}
```

Replace:

- `IP_ADDRESS:2283` with your Immich server address.
- `YOUR_API_KEY` with an Immich API key.
- `YOUR_ALBUM_ID` with the target album ID.

If you do not want album assignment, remove the whole `afterUploadRequests` array or leave it empty.

Immich returns an asset ID, not a direct photo URL. Keep `responseUrlJsonPath` unset in this profile and build the final link with `fallbackUrl` and `{response.id}`.

## Tips

- Do not commit real API keys into public modpacks or repositories.
- Keep disabled/test profiles with a leading underscore, for example `_immich-test.json`.
- If a multipart API fails, make sure `fileField` matches the API documentation.
- Avoid manually setting multipart `Content-Type`; the mod sets it with the correct boundary.
- Use `fallbackUrl` when the API does not return a direct public link.
